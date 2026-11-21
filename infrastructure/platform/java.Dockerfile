# syntax=docker/dockerfile:1
FROM eclipse-temurin:25.0.5.1_2-jdk-noble@sha256:c7eef3dfeb81468533ea48c67bddaa8c7ca916454259e4d0d734d12bcff8e125 AS build

WORKDIR /workspace
COPY --chmod=0755 mvnw ./
COPY .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/
COPY pom.xml ./
COPY backend/management/pom.xml backend/management/
COPY backend/records/pom.xml backend/records/
COPY infrastructure/api-gateway/pom.xml infrastructure/api-gateway/
COPY station/auxiliary/pom.xml station/auxiliary/

ARG MODULE
COPY ${MODULE}/src/ ${MODULE}/src/
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -ntp -pl "$MODULE" -am package \
    && cp "$MODULE"/target/*-SNAPSHOT.jar /app.jar

FROM eclipse-temurin:25.0.5.1_2-jre-noble@sha256:2ec98aa522c472deadfd2b7c5b39bca680e33ef522a53a72f10789bdf89a6bb5 AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --home-dir /app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /app.jar ./app.jar
USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS auxiliary
USER root
RUN install -d -m 0700 -o 10001 -g 10001 /var/lib/station
USER 10001:10001
