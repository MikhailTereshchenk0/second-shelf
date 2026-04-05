FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace
COPY . .

RUN mvn -pl auth-service -am clean package -DskipTests \
    && cp auth-service/target/auth-service-*.jar /workspace/auth-service.jar


FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=builder /workspace/auth-service.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]