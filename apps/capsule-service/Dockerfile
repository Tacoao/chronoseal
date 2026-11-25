# docker build --build-context proto=../../proto -t capsule-service .

FROM gradle:8.14-jdk17@sha256:4adc63f5657468850c2012dc27efba47a25a61fab5a4993f4ff3bf8f295a751c AS builder
WORKDIR /workspace/apps/capsule-service
COPY --from=proto . /workspace/proto
COPY . .
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy@sha256:aa6381384f8db99762fa7d0d3c2b5de58874e5f12c96df4c13a1455376ae0ed1
RUN groupadd --system --gid 10001 chronoseal \
    && useradd --system --uid 10001 --gid chronoseal --no-create-home chronoseal
WORKDIR /app
COPY --from=builder /workspace/apps/capsule-service/build/libs/capsule-service.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
