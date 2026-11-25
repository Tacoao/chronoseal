import hashlib
from dataclasses import replace
from datetime import datetime, timedelta

import pytest

from app.keys import MasterKey
from app.sealer import (
    MAX_PLAINTEXT_SIZE,
    InvalidSealRequest,
    StillSealed,
    TamperedPayload,
    TimeLockSealer,
    UnknownKey,
)
from tests.conftest import START

CAPSULE_ID = "7f6c1d1e-5b0a-4c55-9a43-0c7c2f0e9a11"
UNLOCK_AT = START + timedelta(hours=1)
MESSAGE = "France 2 - 1".encode()


def test_opens_after_unlock_date(sealer, clock):
    sealed = sealer.seal(CAPSULE_ID, MESSAGE, UNLOCK_AT)
    clock.advance(hours=1)

    opened = sealer.unseal(CAPSULE_ID, UNLOCK_AT, sealed.payload)

    assert opened.plaintext == MESSAGE


def test_ciphertext_does_not_contain_plaintext(sealer):
    sealed = sealer.seal(CAPSULE_ID, MESSAGE, UNLOCK_AT)

    assert MESSAGE not in sealed.payload.ciphertext


def test_refuses_to_open_before_unlock_date(sealer, clock):
    sealed = sealer.seal(CAPSULE_ID, MESSAGE, UNLOCK_AT)
    clock.advance(minutes=59)

    with pytest.raises(StillSealed) as error:
        sealer.unseal(CAPSULE_ID, UNLOCK_AT, sealed.payload)

    assert error.value.remaining == timedelta(minutes=1)


def test_changing_unlock_date_does_not_open_capsule_early(sealer, clock):
    """Avancer la date en base de données rend le chiffré inexploitable."""
    sealed = sealer.seal(CAPSULE_ID, MESSAGE, UNLOCK_AT)
    forged_unlock_at = START + timedelta(minutes=5)
    clock.advance(minutes=10)

    with pytest.raises(TamperedPayload):
        sealer.unseal(CAPSULE_ID, forged_unlock_at, sealed.payload)


def test_payload_cannot_be_moved_to_another_capsule(sealer, clock):
    sealed = sealer.seal(CAPSULE_ID, MESSAGE, UNLOCK_AT)
    clock.advance(hours=1)

    with pytest.raises(TamperedPayload):
        sealer.unseal("another-capsule", UNLOCK_AT, sealed.payload)


def test_detects_modified_ciphertext(sealer, clock):
    sealed = sealer.seal(CAPSULE_ID, MESSAGE, UNLOCK_AT)
    flipped = bytes([sealed.payload.ciphertext[0] ^ 0x01]) + sealed.payload.ciphertext[1:]
    clock.advance(hours=1)

    with pytest.raises(TamperedPayload):
        sealer.unseal(CAPSULE_ID, UNLOCK_AT, replace(sealed.payload, ciphertext=flipped))


def test_rejects_payload_from_unknown_key(sealer, clock):
    sealed = sealer.seal(CAPSULE_ID, MESSAGE, UNLOCK_AT)
    clock.advance(hours=1)

    with pytest.raises(UnknownKey):
        sealer.unseal(CAPSULE_ID, UNLOCK_AT, replace(sealed.payload, key_id="old-key"))


def test_another_master_key_cannot_open_capsule(sealer, clock):
    sealed = sealer.seal(CAPSULE_ID, MESSAGE, UNLOCK_AT)
    clock.advance(hours=1)
    impostor = TimeLockSealer(MasterKey("test-key", bytes(32)), clock)

    with pytest.raises(TamperedPayload):
        impostor.unseal(CAPSULE_ID, UNLOCK_AT, sealed.payload)


def test_commitment_can_be_verified_after_opening(sealer, clock):
    sealed = sealer.seal(CAPSULE_ID, MESSAGE, UNLOCK_AT)
    clock.advance(hours=1)

    opened = sealer.unseal(CAPSULE_ID, UNLOCK_AT, sealed.payload)

    assert hashlib.sha256(opened.salt + opened.plaintext).hexdigest() == sealed.commitment


def test_same_content_gives_different_ciphertexts_and_commitments(sealer):
    first = sealer.seal(CAPSULE_ID, MESSAGE, UNLOCK_AT)
    second = sealer.seal(CAPSULE_ID, MESSAGE, UNLOCK_AT)

    assert first.payload.nonce != second.payload.nonce
    assert first.payload.ciphertext != second.payload.ciphertext
    assert first.commitment != second.commitment


@pytest.mark.parametrize(
    ("capsule_id", "plaintext", "unlock_at"),
    [
        ("", MESSAGE, UNLOCK_AT),
        ("x" * 65, MESSAGE, UNLOCK_AT),
        (CAPSULE_ID, b"", UNLOCK_AT),
        (CAPSULE_ID, b"x" * (MAX_PLAINTEXT_SIZE + 1), UNLOCK_AT),
        (CAPSULE_ID, MESSAGE, START),
        (CAPSULE_ID, MESSAGE, START - timedelta(seconds=1)),
        (CAPSULE_ID, MESSAGE, datetime(2030, 1, 1)),
    ],
    ids=[
        "identifiant-vide",
        "identifiant-trop-long",
        "contenu-vide",
        "contenu-trop-gros",
        "date-egale-a-maintenant",
        "date-passee",
        "date-sans-fuseau",
    ],
)
def test_rejects_invalid_seal_requests(sealer, capsule_id, plaintext, unlock_at):
    with pytest.raises(InvalidSealRequest):
        sealer.seal(capsule_id, plaintext, unlock_at)
