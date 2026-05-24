# syntax=docker/dockerfile:1
# Build context is the repository root (the module inherits the parent POM).

# --- build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
# Copy every module's POM so the Maven reactor can read the parent's
# <modules>; only auth-server's sources are copied, so only it gets built.
COPY auth-server/pom.xml auth-server/pom.xml
COPY resource-api/pom.xml resource-api/pom.xml
COPY auth-server/src auth-server/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -pl auth-server -am -DskipTests clean package

# --- runtime stage ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system spring \
 && useradd --system --gid spring spring
USER spring
COPY --from=build /workspace/auth-server/target/auth-server.jar app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
