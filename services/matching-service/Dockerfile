FROM gradle:9-jdk21 AS build

WORKDIR /workspace

COPY gradle ./gradle
COPY config/checkstyle ./config/checkstyle
COPY shared/common-domain ./shared/common-domain
COPY shared/photo-storage ./shared/photo-storage
COPY shared/domain-events ./shared/domain-events
COPY shared/genai-client ./shared/genai-client
COPY shared/magic-link ./shared/magic-link
COPY api ./api
COPY services/matching-service ./services/matching-service

WORKDIR /workspace/services/matching-service

RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jdk-alpine AS jre

RUN "$JAVA_HOME/bin/jlink" \
      --add-modules java.base,java.compiler,java.logging,java.naming,java.sql,java.management,java.instrument,java.security.jgss,java.xml,java.desktop,java.net.http,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.management,jdk.management.agent,jdk.jdwp.agent,jdk.zipfs,jdk.naming.dns,jdk.httpserver \
      --strip-debug --no-man-pages --no-header-files --compress=zip-9 \
      --output /opt/jre

FROM alpine:3.21

RUN apk add --no-cache tzdata \
 && adduser -D -u 10001 appuser

ENV JAVA_HOME=/opt/jre PATH="/opt/jre/bin:$PATH"

COPY --from=jre /opt/jre /opt/jre

WORKDIR /app

COPY --from=build /workspace/services/matching-service/build/libs/*.jar app.jar

EXPOSE 8084

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
