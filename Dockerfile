FROM gradle:8.1.1-jdk17 AS build

USER root

ARG NODE_VERSION=v18.20.8

# xz for the node tarball, ca-certificates so npm can reach the registry over TLS.
RUN apt-get update \
    && apt-get install -y --no-install-recommends xz-utils ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL "https://nodejs.org/dist/${NODE_VERSION}/node-${NODE_VERSION}-linux-x64.tar.xz" \
    | tar -xJ -C /usr/local --strip-components=1 \
    && node --version && npm --version

WORKDIR /src
COPY server ./server
COPY client ./client

# The Gradle build calls `npm run build` without an install step of its own.
RUN cd client && npm ci --no-audit --no-fund

# selfhostJar runs the client's npm build, bundles the Linux natives, and carries no
# release.txt, so the server comes up in Local mode on plain HTTP port 80 with no
# https redirect. A proxy terminates TLS.
RUN cd server && ./gradlew --no-daemon --no-configuration-cache --stacktrace selfhostJar

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /src/server/build/libs/*-selfhost.jar /app/crashy.jar

EXPOSE 80
ENTRYPOINT ["java", "-Duser.home=/data", "-jar", "/app/crashy.jar"]
