"""AES-256-GCM, une clé par capsule (HKDF), date d'ouverture liée au chiffré."""

from __future__ import annotations

import hashlib
import os
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

from app.keys import KEY_SIZE, MasterKey

NONCE_SIZE = 12
SALT_SIZE = 16
MAX_PLAINTEXT_SIZE = 64 * 1024
MAX_CAPSULE_ID_LENGTH = 64
KDF_INFO_PREFIX = b"chronoseal/seal/v1/"

Clock = Callable[[], datetime]


def utc_now() -> datetime:
    return datetime.now(UTC)


class SealError(Exception):
    pass


class InvalidSealRequest(SealError):
    pass


class StillSealed(SealError):
    def __init__(self, unlock_at: datetime, remaining: timedelta) -> None:
        super().__init__(f"Capsule scellée jusqu'au {unlock_at.isoformat()}.")
        self.unlock_at = unlock_at
        self.remaining = remaining


class UnknownKey(SealError):
    pass


class TamperedPayload(SealError):
    pass


@dataclass(frozen=True)
class SealedPayload:
    key_id: str
    nonce: bytes = field(repr=False)
    ciphertext: bytes = field(repr=False)


@dataclass(frozen=True)
class SealResult:
    payload: SealedPayload
    commitment: str


@dataclass(frozen=True)
class UnsealResult:
    plaintext: bytes = field(repr=False)
    salt: bytes


def commitment_of(salt: bytes, plaintext: bytes) -> str:
    return hashlib.sha256(salt + plaintext).hexdigest()


class TimeLockSealer:
    def __init__(self, master_key: MasterKey, clock: Clock = utc_now) -> None:
        self._key = master_key
        self._clock = clock

    def seal(self, capsule_id: str, plaintext: bytes, unlock_at: datetime) -> SealResult:
        _check_capsule_id(capsule_id)
        if not plaintext:
            raise InvalidSealRequest("Le contenu à sceller est vide.")
        if len(plaintext) > MAX_PLAINTEXT_SIZE:
            raise InvalidSealRequest(f"Le contenu dépasse {MAX_PLAINTEXT_SIZE} octets.")
        unlock_at = _as_utc(unlock_at)
        if unlock_at <= self._clock():
            raise InvalidSealRequest("La date d'ouverture doit être dans le futur.")

        salt = os.urandom(SALT_SIZE)
        nonce = os.urandom(NONCE_SIZE)
        ciphertext = AESGCM(self._derive_key(capsule_id)).encrypt(
            nonce, salt + plaintext, _associated_data(capsule_id, unlock_at)
        )
        payload = SealedPayload(self._key.key_id, nonce, ciphertext)
        return SealResult(payload, commitment_of(salt, plaintext))

    def unseal(self, capsule_id: str, unlock_at: datetime, payload: SealedPayload) -> UnsealResult:
        _check_capsule_id(capsule_id)
        unlock_at = _as_utc(unlock_at)
        now = self._clock()
        if now < unlock_at:
            raise StillSealed(unlock_at, unlock_at - now)
        if payload.key_id != self._key.key_id:
            raise UnknownKey(f"Clé maîtresse inconnue : {payload.key_id!r}.")
        if len(payload.nonce) != NONCE_SIZE:
            raise TamperedPayload("Nonce de taille invalide.")

        try:
            content = AESGCM(self._derive_key(capsule_id)).decrypt(
                payload.nonce, payload.ciphertext, _associated_data(capsule_id, unlock_at)
            )
        except InvalidTag as error:
            raise TamperedPayload("Échec de l'authentification du chiffré.") from error
        return UnsealResult(plaintext=content[SALT_SIZE:], salt=content[:SALT_SIZE])

    def _derive_key(self, capsule_id: str) -> bytes:
        hkdf = HKDF(
            algorithm=hashes.SHA256(),
            length=KEY_SIZE,
            salt=None,
            info=KDF_INFO_PREFIX + capsule_id.encode(),
        )
        return hkdf.derive(self._key.material)


def _check_capsule_id(capsule_id: str) -> None:
    if not capsule_id or len(capsule_id) > MAX_CAPSULE_ID_LENGTH:
        raise InvalidSealRequest("Identifiant de capsule absent ou trop long.")


def _as_utc(moment: datetime) -> datetime:
    if moment.tzinfo is None:
        raise InvalidSealRequest("La date d'ouverture doit porter un fuseau horaire.")
    return moment.astimezone(UTC)


def _associated_data(capsule_id: str, unlock_at: datetime) -> bytes:
    # À la seconde près, pour ne pas dépendre de la précision des dates en base.
    return f"{capsule_id}|{int(unlock_at.timestamp())}".encode()
