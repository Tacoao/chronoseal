"""Sonde de santé pour Docker Compose (code 0 si le service répond SERVING)."""

from __future__ import annotations

import os
import sys

import grpc
from grpc_health.v1 import health_pb2, health_pb2_grpc

from app.server import DEFAULT_PORT


def main() -> int:
    port = int(os.environ.get("SEAL_GRPC_PORT", DEFAULT_PORT))
    with grpc.insecure_channel(f"127.0.0.1:{port}") as channel:
        stub = health_pb2_grpc.HealthStub(channel)
        try:
            response = stub.Check(health_pb2.HealthCheckRequest(service=""), timeout=2)
        except grpc.RpcError:
            return 1
    return 0 if response.status == health_pb2.HealthCheckResponse.SERVING else 1


if __name__ == "__main__":
    sys.exit(main())
