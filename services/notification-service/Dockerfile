FROM gradle:9-jdk21 AS build

WORKDIR /workspace

COPY services/notification-service ./services/notification-service

WORKDIR /workspace/services/notification-service

RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/services/notification-service/build/libs/*.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]