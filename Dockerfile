FROM gradle:8.1.1-jdk17 AS build

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends xz-utils ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY server ./server
COPY client ./client

# selfhostJar = linux natives, no release/beta marker, so the server comes up in
# Local mode on plain HTTP port 80 with no https redirect (a proxy terminates TLS).
RUN cd server && ./gradlew --no-daemon --no-configuration-cache --stacktrace selfhostJar

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /src/server/build/libs/*-selfhost.jar /app/crashy.jar

EXPOSE 80
ENTRYPOINT ["java", "-Duser.home=/data", "-jar", "/app/crashy.jar"]
