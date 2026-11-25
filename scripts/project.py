#!/usr/bin/env python3
"""Commandes portables du projet Chronoseal, utilisées en local et par la CI."""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROTO_DIR = ROOT / "proto"
CAPSULE_SERVICE = ROOT / "apps" / "capsule-service"
SEAL_SERVICE = ROOT / "apps" / "seal-service"
SEAL_GENERATED = SEAL_SERVICE / "gen"


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


def test() -> None:
    test_java()
    test_python()


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
    "prerequisites": prerequisites,
    "proto-python": proto_python,
    "test": test,
    "test-java": test_java,
    "test-python": test_python,
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
