FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace
COPY . .

RUN mvn -pl exchange-service -am clean package -DskipTests \
    && cp exchange-service/target/exchange-service-*.jar /workspace/exchange-service.jar


FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /workspace/exchange-service.jar app.jar

EXPOSE 8083

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=10 \
  CMD curl -fsS http://localhost:8083/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]