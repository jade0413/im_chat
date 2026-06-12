# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY im-proto ./im-proto
COPY im-server ./im-server

RUN --mount=type=cache,target=/root/.m2 \
    cd im-server && \
    mvn -q -pl im-bootstrap -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates && \
    rm -rf /var/lib/apt/lists/* && \
    useradd --system --create-home --shell /usr/sbin/nologin im

COPY --from=build /workspace/im-server/im-bootstrap/target/im-bootstrap-0.1.0-SNAPSHOT.jar /app/im-server.jar

USER im
EXPOSE 8081 9091
HEALTHCHECK --interval=15s --timeout=5s --retries=10 \
  CMD curl -fsS http://127.0.0.1:8081/actuator/health/readiness >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/im-server.jar"]
