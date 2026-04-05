FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace
COPY . .

RUN mvn -pl user-service -am clean package -DskipTests \
    && cp user-service/target/user-service-*.jar /workspace/user-service.jar


FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=builder /workspace/user-service.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]