FROM gradle:9-jdk21 AS build

WORKDIR /workspace

COPY shared/common-domain ./shared/common-domain
COPY services/found-item-service ./services/found-item-service

WORKDIR /workspace/services/found-item-service

RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/services/found-item-service/build/libs/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]