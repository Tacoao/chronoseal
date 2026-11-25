#!/usr/bin/env python3
"""Commandes du projet Chronoseal, les mêmes en local et dans la CI."""

# Compatible Python 3.9 pour que `prerequisites` puisse dire que la version est trop vieille.

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
PROTO_DIR = ROOT / "proto"
CAPSULE_SERVICE = ROOT / "apps" / "capsule-service"
SEAL_SERVICE = ROOT / "apps" / "seal-service"
SEAL_GENERATED = SEAL_SERVICE / "gen"
WORKFLOWS = ROOT / ".github" / "workflows"
API_URL = f"http://localhost:{os.environ.get('CAPSULE_HTTP_PORT', '8080')}"

ACTION_SHA = re.compile(r"^[0-9a-f]{40}$")
IMAGE_DIGEST = re.compile(r"@sha256:[0-9a-f]{64}$")


def run(command: list[str], *, cwd: Path = ROOT) -> None:
    print(f"+ {' '.join(command)}", flush=True)
    subprocess.run(command, cwd=cwd, check=True)


def gradle_command() -> list[str]:
    if os.name == "nt":
        return ["cmd", "/c", "gradlew.bat"]
    return ["./gradlew"]


def require_command(command: str) -> None:
    if shutil.which(command) is None:
        raise RuntimeError(f"Commande introuvable : {command}")


def proto_python() -> None:
    shutil.rmtree(SEAL_GENERATED, ignore_errors=True)
    SEAL_GENERATED.mkdir(parents=True)
    protos = sorted(str(path.relative_to(PROTO_DIR)) for path in PROTO_DIR.rglob("*.proto"))
    run(
        [
            sys.executable,
            "-m",
            "grpc_tools.protoc",
            f"--proto_path={PROTO_DIR}",
            f"--python_out={SEAL_GENERATED}",
            f"--pyi_out={SEAL_GENERATED}",
            f"--grpc_python_out={SEAL_GENERATED}",
            *protos,
        ]
    )


def test_java() -> None:
    run([*gradle_command(), "clean", "test"], cwd=CAPSULE_SERVICE)


def test_python() -> None:
    proto_python()
    run([sys.executable, "-m", "pytest"], cwd=SEAL_SERVICE)


def lint_java() -> None:
    run([*gradle_command(), "checkstyleMain", "checkstyleTest"], cwd=CAPSULE_SERVICE)


def lint_python() -> None:
    ruff = [sys.executable, "-m", "ruff"]
    run([*ruff, "check", "app", "tests"], cwd=SEAL_SERVICE)
    run([*ruff, "format", "--check", "app", "tests"], cwd=SEAL_SERVICE)
    run([*ruff, "check", "scripts"])
    run([*ruff, "format", "--check", "scripts"])


def lint() -> None:
    lint_java()
    lint_python()


def workflow_reference_errors(path: Path) -> list[str]:
    errors = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        stripped = line.strip().removeprefix("- ").strip()
        if not stripped.startswith("uses:"):
            continue
        reference = stripped.removeprefix("uses:").split("#", maxsplit=1)[0].strip().strip("'\"")
        location = f"{path.relative_to(ROOT)}:{number}"
        if reference.startswith("./"):
            continue
        if reference.startswith("docker://"):
            if not IMAGE_DIGEST.search(reference):
                errors.append(f"{location}: image {reference} sans digest SHA-256")
            continue
        version = reference.rsplit("@", maxsplit=1)[1] if "@" in reference else ""
        if not ACTION_SHA.fullmatch(version):
            errors.append(f"{location}: action {reference} non épinglée par un SHA complet")
    return errors


def dockerfile_reference_errors(path: Path) -> list[str]:
    errors = []
    stages: set[str] = set()
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        words = line.split()
        if not words or words[0].upper() != "FROM":
            continue
        arguments = [word for word in words[1:] if not word.startswith("--")]
        image = arguments[0] if arguments else ""
        # FROM vers une étape précédente : rien à télécharger, donc pas de digest.
        if image.lower() not in stages and not IMAGE_DIGEST.search(image):
            errors.append(f"{path.relative_to(ROOT)}:{number}: image {image} sans digest SHA-256")
        if len(arguments) >= 3 and arguments[1].upper() == "AS":
            stages.add(arguments[2].lower())
    return errors


def supply_chain_policy() -> None:
    errors = []
    for workflow in sorted([*WORKFLOWS.glob("*.yml"), *WORKFLOWS.glob("*.yaml")]):
        errors.extend(workflow_reference_errors(workflow))
    for dockerfile in sorted((ROOT / "apps").glob("*/Dockerfile")):
        errors.extend(dockerfile_reference_errors(dockerfile))
    if errors:
        raise RuntimeError("Politique de chaîne logicielle non respectée :\n" + "\n".join(errors))
    print("OK politique de chaîne logicielle")


def verify() -> None:
    test()
    lint()
    supply_chain_policy()


def test() -> None:
    test_java()
    test_python()


def compose_up() -> None:
    require_command("docker")
    run(["docker", "compose", "up", "--build", "--detach"])


def compose_down() -> None:
    require_command("docker")
    run(["docker", "compose", "down"])


def http(method: str, path: str, body: dict | None = None) -> tuple[int, dict]:
    data = None if body is None else json.dumps(body).encode()
    request = Request(
        f"{API_URL}{path}", data=data, method=method, headers={"Content-Type": "application/json"}
    )
    try:
        with urlopen(request, timeout=5) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        try:
            return error.code, json.load(error)
        except ValueError:
            return error.code, {}


def wait_for(url: str, *, timeout_seconds: int = 90) -> None:
    deadline = time.monotonic() + timeout_seconds
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            with urlopen(url, timeout=3) as response:
                if response.status == 200:
                    print(f"OK {url}")
                    return
        except (URLError, TimeoutError, ConnectionError) as error:
            last_error = error
        time.sleep(1)
    raise RuntimeError(f"Service indisponible sur {url} : {last_error}")


def expect(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)
    print(f"OK {message}")


def smoke() -> None:
    wait_for(f"{API_URL}/actuator/health/readiness")
    unlock_at = datetime.now(timezone.utc).replace(microsecond=0) + timedelta(seconds=3)
    status, created = http(
        "POST",
        "/api/capsules",
        {"title": "Smoke test", "message": "Bonjour le futur", "unlockAt": unlock_at.isoformat()},
    )
    expect(status == 201, f"capsule déposée (HTTP {status})")
    expect("message" not in created, "le contenu n'apparaît pas dans la réponse")

    status, _ = http("GET", f"/api/capsules/{created['id']}/content")
    expect(status == 423, f"ouverture refusée avant l'échéance (HTTP {status})")

    time.sleep(max(0.0, (unlock_at - datetime.now(timezone.utc)).total_seconds()) + 1.5)
    status, opened = http("GET", f"/api/capsules/{created['id']}/content")
    expect(status == 200, f"capsule ouverte après l'échéance (HTTP {status})")
    expect(opened.get("message") == "Bonjour le futur", "contenu restitué à l'identique")

    digest = hashlib.sha256(bytes.fromhex(opened["salt"]) + opened["message"].encode()).hexdigest()
    expect(digest == created["commitment"], "engagement publié au dépôt vérifié")


def prerequisites() -> None:
    checks = {
        "Python": sys.executable,
        "Java": shutil.which("java"),
        "Docker": shutil.which("docker"),
    }
    missing = False
    for name, location in checks.items():
        if location:
            print(f"OK {name} : {location}")
        else:
            print(f"ABSENT {name}")
            missing = True
    if sys.version_info < (3, 12):
        print(f"ABSENT Python >= 3.12 (version courante : {sys.version.split()[0]})")
        missing = True
    if ":" in str(ROOT):
        print(f"ATTENTION le chemin {ROOT} contient ':', ce qui casse le classpath Java.")
        missing = True
    if missing:
        raise RuntimeError("Prérequis manquants")


COMMANDS = {
    "compose-down": compose_down,
    "compose-up": compose_up,
    "lint": lint,
    "lint-java": lint_java,
    "lint-python": lint_python,
    "prerequisites": prerequisites,
    "proto-python": proto_python,
    "smoke": smoke,
    "supply-chain-policy": supply_chain_policy,
    "test": test,
    "test-java": test_java,
    "test-python": test_python,
    "verify": verify,
}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=sorted(COMMANDS))
    args = parser.parse_args()
    try:
        COMMANDS[args.command]()
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"ERREUR : {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
