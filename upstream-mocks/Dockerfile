# Build context: the repository root (docker compose and CI pass it).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Maven reads every module POM of the reactor, even when building one module.
COPY upstream-mocks/pom.xml upstream-mocks/
COPY aggregator/pom.xml aggregator/
COPY quality-gates/pom.xml quality-gates/
COPY upstream-mocks/src upstream-mocks/src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl upstream-mocks -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home app
WORKDIR /app
COPY --from=build /workspace/upstream-mocks/target/upstream-mocks-*.jar app.jar
USER app
EXPOSE 8081
HEALTHCHECK --interval=5s --timeout=3s --start-period=30s --retries=12 \
  CMD curl -fsS http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
