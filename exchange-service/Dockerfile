FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace
COPY . .

RUN mvn -pl exchange-service -am clean package -DskipTests \
    && cp exchange-service/target/exchange-service-*.jar /workspace/exchange-service.jar


FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=builder /workspace/exchange-service.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]