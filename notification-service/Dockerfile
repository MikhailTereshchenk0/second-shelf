FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace
COPY . .

RUN mvn -pl notification-service -am clean package -DskipTests \
    && cp notification-service/target/notification-service-*.jar /workspace/notification-service.jar


FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system appuser \
    && useradd --system --gid appuser --home-dir /app --no-create-home --shell /usr/sbin/nologin appuser

WORKDIR /app
COPY --from=builder --chown=appuser:appuser /workspace/notification-service.jar app.jar

EXPOSE 8084

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=10 \
  CMD curl -fsS http://localhost:8084/actuator/health/readiness || exit 1

USER appuser

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
