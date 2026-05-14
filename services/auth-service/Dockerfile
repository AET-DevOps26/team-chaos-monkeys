FROM gradle:9-jdk21 AS build

WORKDIR /workspace

COPY services/auth-service ./services/auth-service

WORKDIR /workspace/services/auth-service

RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/services/auth-service/build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]