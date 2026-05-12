FROM gradle:9.0-jdk21 AS build

WORKDIR /app

COPY . .

RUN gradle clean bootJar --no-daemon

FROM sapmachine:23

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]