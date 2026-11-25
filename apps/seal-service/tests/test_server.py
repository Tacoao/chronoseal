from datetime import timedelta

import grpc
import pytest
from chronoseal.seal.v1 import seal_pb2, seal_pb2_grpc
from google.protobuf.timestamp_pb2 import Timestamp
from grpc_health.v1 import health_pb2, health_pb2_grpc

from app.server import SERVICE_NAME, build_server
from tests.conftest import START

CAPSULE_ID = "7f6c1d1e-5b0a-4c55-9a43-0c7c2f0e9a11"


def timestamp(moment) -> Timestamp:
    value = Timestamp()
    value.FromDatetime(moment)
    return value


@pytest.fixture
def channel(sealer):
    server, port, _ = build_server(sealer, "127.0.0.1:0", max_workers=2)
    server.start()
    with grpc.insecure_channel(f"127.0.0.1:{port}") as client_channel:
        yield client_channel
    server.stop(None)


@pytest.fixture
def stub(channel):
    return seal_pb2_grpc.SealServiceStub(channel)


def seal(stub, unlock_at=START + timedelta(hours=1)):
    return stub.Seal(
        seal_pb2.SealRequest(
            capsule_id=CAPSULE_ID, plaintext=b"bonjour", unlock_at=timestamp(unlock_at)
        )
    )


def unseal(stub, sealed, unlock_at=START + timedelta(hours=1)):
    return stub.Unseal(
        seal_pb2.UnsealRequest(
            capsule_id=CAPSULE_ID, unlock_at=timestamp(unlock_at), payload=sealed.payload
        )
    )


def test_round_trip_over_grpc(stub, clock):
    sealed = seal(stub)
    clock.advance(hours=1)

    opened = unseal(stub, sealed)

    assert opened.plaintext == b"bonjour"
    assert len(opened.salt) == 16
    assert len(sealed.commitment) == 64


def test_still_sealed_maps_to_failed_precondition(stub):
    sealed = seal(stub)

    with pytest.raises(grpc.RpcError) as error:
        unseal(stub, sealed)

    assert error.value.code() == grpc.StatusCode.FAILED_PRECONDITION


def test_forged_unlock_date_maps_to_data_loss(stub, clock):
    sealed = seal(stub)
    clock.advance(minutes=10)

    with pytest.raises(grpc.RpcError) as error:
        unseal(stub, sealed, unlock_at=START + timedelta(minutes=5))

    assert error.value.code() == grpc.StatusCode.DATA_LOSS


def test_past_unlock_date_maps_to_invalid_argument(stub):
    with pytest.raises(grpc.RpcError) as error:
        seal(stub, unlock_at=START - timedelta(days=1))

    assert error.value.code() == grpc.StatusCode.INVALID_ARGUMENT


def test_missing_unlock_date_is_rejected(stub):
    with pytest.raises(grpc.RpcError) as error:
        stub.Seal(seal_pb2.SealRequest(capsule_id=CAPSULE_ID, plaintext=b"x"))

    assert error.value.code() == grpc.StatusCode.INVALID_ARGUMENT


def test_unknown_key_maps_to_not_found(stub, clock):
    sealed = seal(stub)
    sealed.payload.key_id = "old-key"
    clock.advance(hours=1)

    with pytest.raises(grpc.RpcError) as error:
        unseal(stub, sealed)

    assert error.value.code() == grpc.StatusCode.NOT_FOUND


@pytest.mark.parametrize("service", ["", SERVICE_NAME])
def test_health_service_reports_serving(channel, service):
    response = health_pb2_grpc.HealthStub(channel).Check(
        health_pb2.HealthCheckRequest(service=service)
    )

    assert response.status == health_pb2.HealthCheckResponse.SERVING
