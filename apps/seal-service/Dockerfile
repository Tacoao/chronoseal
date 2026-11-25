# docker build --build-context proto=../../proto -t seal-service .

FROM python:3.12-slim AS builder
WORKDIR /build
COPY requirements.txt requirements-build.txt ./
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt \
    && pip install --no-cache-dir -r requirements-build.txt
COPY --from=proto . /proto
RUN mkdir gen && python -m grpc_tools.protoc --proto_path=/proto \
    --python_out=gen --grpc_python_out=gen chronoseal/seal/v1/seal.proto

FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app/gen
RUN groupadd --system --gid 10001 chronoseal \
    && useradd --system --uid 10001 --gid chronoseal --no-create-home chronoseal
COPY --from=builder /install /usr/local
WORKDIR /app
COPY --from=builder /build/gen ./gen
COPY app ./app
USER 10001
EXPOSE 50051
CMD ["python", "-m", "app.server"]
