import base64

import pytest

from app.keys import KeyConfigurationError, MasterKey, load_master_key


def encoded(size: int) -> str:
    return base64.b64encode(bytes(range(size))).decode()


def test_loads_key_and_identifier_from_environment():
    key = load_master_key({"SEAL_MASTER_KEY": encoded(32), "SEAL_KEY_ID": "2026-11"})

    assert key.key_id == "2026-11"
    assert key.material == bytes(range(32))


def test_uses_default_identifier():
    assert load_master_key({"SEAL_MASTER_KEY": encoded(32)}).key_id == "k1"


def test_refuses_to_start_without_key():
    with pytest.raises(KeyConfigurationError, match="absente"):
        load_master_key({})


def test_rejects_invalid_base64():
    with pytest.raises(KeyConfigurationError, match="base64"):
        load_master_key({"SEAL_MASTER_KEY": "pas du base64 !"})


def test_rejects_key_of_wrong_size():
    with pytest.raises(KeyConfigurationError, match="32 octets"):
        load_master_key({"SEAL_MASTER_KEY": encoded(16)})


def test_generates_ephemeral_key_only_when_allowed():
    key = load_master_key({"SEAL_ALLOW_EPHEMERAL_KEY": "true"})

    assert key.key_id.startswith("ephemeral-")
    assert len(key.material) == 32


def test_key_material_is_hidden_from_repr():
    assert "material" not in repr(MasterKey("k1", bytes(32)))
