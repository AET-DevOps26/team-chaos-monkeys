FROM gradle:9-jdk21 AS build

WORKDIR /workspace

COPY shared/common-domain ./shared/common-domain
COPY shared/photo-storage ./shared/photo-storage
COPY shared/domain-events ./shared/domain-events
COPY services/lost-item-service ./services/lost-item-service

WORKDIR /workspace/services/lost-item-service

RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/services/lost-item-service/build/libs/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
