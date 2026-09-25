# ---------------------------------------------------------------------------
# Session 04 — image for gateway.
#
# The build context is the REPOSITORY ROOT, not this folder, because this is a Maven
# multi-module project: the build needs the parent pom and the common-events module.
# Compose passes the right context; to build it by hand from the root:
#
#     docker build -f gateway/Dockerfile -t orderflow/gateway:dev .
#
# Two stages: a full JDK to build, a JRE to run (~230 MB instead of ~780 MB).
# Instructions are ordered from least to most frequently changed, so editing a Java
# file reuses the cached dependency layer and the rebuild takes seconds.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 1. POMs only: this layer survives every source change.
COPY pom.xml .
COPY common-events/pom.xml common-events/
COPY catalog-service/pom.xml catalog-service/
COPY order-service/pom.xml order-service/
COPY payment-service/pom.xml payment-service/
COPY notification-service/pom.xml notification-service/
COPY gateway/pom.xml gateway/
RUN mvn -B -q -pl gateway -am dependency:go-offline

# 2. Sources of this module and of what it depends on.
COPY common-events/src common-events/src
COPY gateway/src gateway/src
RUN mvn -B -q -pl gateway -am clean package -DskipTests

# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build --chown=app:app /build/gateway/target/*.jar app.jar

# Never root: it costs nothing and removes one layer of attack.
USER app
EXPOSE 8080

# MaxRAMPercentage, never a fixed -Xmx equal to the container limit: the JVM also needs
# metaspace, thread stacks and direct buffers. That mistake is the classic exit code 137.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

HEALTHCHECK --interval=15s --timeout=3s --start-period=50s --retries=3 \
  CMD wget -q -O- http://localhost:8080/actuator/health/liveness || exit 1

# exec form + exec: java becomes PID 1 and receives SIGTERM, so graceful shutdown works.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
