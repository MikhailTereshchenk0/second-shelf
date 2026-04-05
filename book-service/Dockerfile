FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace
COPY . .

RUN mvn -pl book-service -am clean package -DskipTests \
    && cp book-service/target/book-service-*.jar /workspace/book-service.jar


FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=builder /workspace/book-service.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]