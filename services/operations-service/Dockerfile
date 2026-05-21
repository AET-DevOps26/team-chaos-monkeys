FROM gradle:9-jdk21 AS build

WORKDIR /workspace

COPY services/operations-service ./services/operations-service

WORKDIR /workspace/services/operations-service

RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/services/operations-service/build/libs/*.jar app.jar

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "app.jar"]