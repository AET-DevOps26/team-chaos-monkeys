FROM gradle:9-jdk21 AS build

WORKDIR /workspace

COPY services/matching-service ./services/matching-service

WORKDIR /workspace/services/matching-service

RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/services/matching-service/build/libs/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]