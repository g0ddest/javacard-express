# Simulator server image (published by the release workflow as ghcr.io/<owner>/jcx-simulator:<version>).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src src
# Release version recorded in the jar manifest (the release workflow passes it; pom.xml has the default).
ARG JCX_VERSION=
RUN mvn package -B -q -DskipTests ${JCX_VERSION:+-Drevision=$JCX_VERSION}

FROM eclipse-temurin:25-jre
WORKDIR /app
# Exact path (pom.xml sets finalName=jcx-simulator): a COPY source that does not exist fails the build.
# The previous wildcard "jcx-simulator-*[!s].jar" matched nothing under BuildKit, so every published image was empty.
COPY --from=build /app/target/jcx-simulator.jar /app/server.jar
# The server runs the class files its clients send: never as root (65534 = nobody).
USER 65534:65534
# Listen on all interfaces of the container so that the published port reaches the server (outside a container the
# server listens on the loopback interface by default). Optional: JCX_TOKEN (access token clients must present first)
# and JCX_MAX_SESSIONS (concurrent sessions, default 16).
ENV JCX_BIND_ADDRESS=0.0.0.0
EXPOSE 9876
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
