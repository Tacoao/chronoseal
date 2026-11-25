from __future__ import annotations

import base64
import binascii
import logging
import os
import secrets
from collections.abc import Mapping
from dataclasses import dataclass, field

KEY_SIZE = 32
DEFAULT_KEY_ID = "k1"

LOGGER = logging.getLogger(__name__)


class KeyConfigurationError(RuntimeError):
    """La clé maîtresse est absente ou invalide."""


@dataclass(frozen=True)
class MasterKey:
    key_id: str
    material: bytes = field(repr=False)

    def __post_init__(self) -> None:
        if not self.key_id:
            raise KeyConfigurationError("L'identifiant de clé ne peut pas être vide.")
        if len(self.material) != KEY_SIZE:
            raise KeyConfigurationError(
                f"La clé maîtresse doit faire {KEY_SIZE} octets, pas {len(self.material)}."
            )


def load_master_key(environ: Mapping[str, str] | None = None) -> MasterKey:
    """Sans SEAL_MASTER_KEY, refuse de démarrer, sauf clé éphémère autorisée (dev)."""
    env = os.environ if environ is None else environ
    key_id = env.get("SEAL_KEY_ID", DEFAULT_KEY_ID)
    encoded = env.get("SEAL_MASTER_KEY", "").strip()

    if encoded:
        try:
            material = base64.b64decode(encoded, validate=True)
        except binascii.Error as error:
            raise KeyConfigurationError("SEAL_MASTER_KEY n'est pas un base64 valide.") from error
        return MasterKey(key_id, material)

    if env.get("SEAL_ALLOW_EPHEMERAL_KEY", "").lower() == "true":
        ephemeral = MasterKey(f"ephemeral-{secrets.token_hex(4)}", secrets.token_bytes(KEY_SIZE))
        LOGGER.warning(
            "Clé maîtresse éphémère %s : réservé au développement, les capsules "
            "ne survivront pas à un redémarrage.",
            ephemeral.key_id,
        )
        return ephemeral

    raise KeyConfigurationError(
        "SEAL_MASTER_KEY est absente. Fournir une clé de 32 octets encodée en base64 "
        "(Secret Kubernetes) ou SEAL_ALLOW_EPHEMERAL_KEY=true en développement."
    )
