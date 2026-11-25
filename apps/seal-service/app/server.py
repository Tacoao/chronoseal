from __future__ import annotations

import logging
import os
import signal
from concurrent import futures
from datetime import UTC

import grpc
from chronoseal.seal.v1 import seal_pb2, seal_pb2_grpc
from grpc_health.v1 import health, health_pb2, health_pb2_grpc

from app.keys import load_master_key
from app.sealer import (
    InvalidSealRequest,
    SealedPayload,
    StillSealed,
    TamperedPayload,
    TimeLockSealer,
    UnknownKey,
)

SERVICE_NAME = seal_pb2.DESCRIPTOR.services_by_name["SealService"].full_name
DEFAULT_PORT = 50051
MAX_MESSAGE_SIZE = 256 * 1024
SHUTDOWN_GRACE_SECONDS = 10

LOGGER = logging.getLogger(__name__)


class SealServicer(seal_pb2_grpc.SealServiceServicer):
    def __init__(self, sealer: TimeLockSealer) -> None:
        self._sealer = sealer

    def Seal(self, request: seal_pb2.SealRequest, context: grpc.ServicerContext):
        if not request.HasField("unlock_at"):
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "unlock_at est obligatoire.")
        try:
            result = self._sealer.seal(
                request.capsule_id,
                request.plaintext,
                request.unlock_at.ToDatetime(tzinfo=UTC),
            )
        except InvalidSealRequest as error:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(error))

        LOGGER.info("Capsule %s scellée avec la clé %s", request.capsule_id, result.payload.key_id)
        return seal_pb2.SealResponse(
            payload=seal_pb2.SealedPayload(
                key_id=result.payload.key_id,
                nonce=result.payload.nonce,
                ciphertext=result.payload.ciphertext,
            ),
            commitment=result.commitment,
        )

    def Unseal(self, request: seal_pb2.UnsealRequest, context: grpc.ServicerContext):
        if not request.HasField("unlock_at") or not request.HasField("payload"):
            context.abort(
                grpc.StatusCode.INVALID_ARGUMENT, "unlock_at et payload sont obligatoires."
            )
        payload = SealedPayload(
            key_id=request.payload.key_id,
            nonce=request.payload.nonce,
            ciphertext=request.payload.ciphertext,
        )
        try:
            result = self._sealer.unseal(
                request.capsule_id, request.unlock_at.ToDatetime(tzinfo=UTC), payload
            )
        except InvalidSealRequest as error:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(error))
        except StillSealed as error:
            LOGGER.info("Ouverture refusée pour %s : encore scellée", request.capsule_id)
            context.abort(grpc.StatusCode.FAILED_PRECONDITION, str(error))
        except UnknownKey as error:
            context.abort(grpc.StatusCode.NOT_FOUND, str(error))
        except TamperedPayload as error:
            LOGGER.warning("Intégrité compromise pour la capsule %s", request.capsule_id)
            context.abort(grpc.StatusCode.DATA_LOSS, str(error))

        LOGGER.info("Capsule %s ouverte", request.capsule_id)
        return seal_pb2.UnsealResponse(plaintext=result.plaintext, salt=result.salt)


def build_server(
    sealer: TimeLockSealer, address: str, max_workers: int = 8
) -> tuple[grpc.Server, int, health.HealthServicer]:
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=max_workers),
        options=[
            ("grpc.max_receive_message_length", MAX_MESSAGE_SIZE),
            ("grpc.max_send_message_length", MAX_MESSAGE_SIZE),
        ],
    )
    seal_pb2_grpc.add_SealServiceServicer_to_server(SealServicer(sealer), server)

    health_servicer = health.HealthServicer()
    health_pb2_grpc.add_HealthServicer_to_server(health_servicer, server)
    for service in ("", SERVICE_NAME):
        health_servicer.set(service, health_pb2.HealthCheckResponse.SERVING)

    # Pas de TLS ici, c'est le mTLS d'Istio qui chiffre dans le cluster.
    port = server.add_insecure_port(address)
    return server, port, health_servicer


def serve() -> None:
    logging.basicConfig(
        level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s"
    )
    port = int(os.environ.get("SEAL_GRPC_PORT", DEFAULT_PORT))
    sealer = TimeLockSealer(load_master_key())
    server, bound_port, health_servicer = build_server(sealer, f"0.0.0.0:{port}")

    def shutdown(signum: int, _frame: object) -> None:
        LOGGER.info("Signal %s reçu : arrêt progressif", signum)
        health_servicer.enter_graceful_shutdown()
        server.stop(SHUTDOWN_GRACE_SECONDS)

    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)

    server.start()
    LOGGER.info("seal-service à l'écoute sur le port %s", bound_port)
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
