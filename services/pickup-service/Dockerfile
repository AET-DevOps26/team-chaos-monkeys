FROM gradle:9-jdk21 AS build

WORKDIR /workspace

COPY shared/domain-events ./shared/domain-events
COPY shared/magic-link ./shared/magic-link
COPY services/pickup-service ./services/pickup-service

WORKDIR /workspace/services/pickup-service

RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jdk-alpine AS jre

RUN "$JAVA_HOME/bin/jlink" \
      --add-modules java.base,java.compiler,java.logging,java.naming,java.sql,java.management,java.instrument,java.security.jgss,java.xml,java.desktop,java.net.http,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.management,jdk.management.agent,jdk.jdwp.agent,jdk.zipfs,jdk.naming.dns,jdk.httpserver \
      --strip-debug --no-man-pages --no-header-files --compress=zip-9 \
      --output /opt/jre

FROM alpine:3.21

RUN apk add --no-cache tzdata

ENV JAVA_HOME=/opt/jre PATH="/opt/jre/bin:$PATH"

COPY --from=jre /opt/jre /opt/jre

WORKDIR /app

COPY --from=build /workspace/services/pickup-service/build/libs/*.jar app.jar

EXPOSE 8087

ENTRYPOINT ["java", "-jar", "app.jar"]
