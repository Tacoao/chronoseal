# docker build --build-context proto=../../proto -t capsule-service .

FROM gradle:8.14-jdk17@sha256:a9ead09d1462d8c4ae77554f63200a0088c687e2edb628071537bb84ceaf71fe AS builder
WORKDIR /workspace/apps/capsule-service
COPY --from=proto . /workspace/proto
COPY . .
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy@sha256:c6a5dd70fb24f4e07ef88cbda8efb613a5a649a1af071a41de8eaabd1ee6b5da
RUN groupadd --system --gid 10001 chronoseal \
    && useradd --system --uid 10001 --gid chronoseal --no-create-home chronoseal
WORKDIR /app
COPY --from=builder /workspace/apps/capsule-service/build/libs/capsule-service.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
