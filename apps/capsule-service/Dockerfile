# Étape de construction : produit le jar exécutable Spring Boot.
FROM gradle:8.14-jdk17 AS builder
WORKDIR /workspace
COPY . .
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system --gid 10001 chronoseal \
    && useradd --system --uid 10001 --gid chronoseal --no-create-home chronoseal
WORKDIR /app
COPY --from=builder /workspace/build/libs/capsule-service.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
