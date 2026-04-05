FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace
COPY . .

RUN mvn -pl book-service -am clean package -DskipTests \
    && cp book-service/target/book-service-*.jar /workspace/book-service.jar


FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /workspace/book-service.jar app.jar

EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=10 \
  CMD curl -fsS http://localhost:8082/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]