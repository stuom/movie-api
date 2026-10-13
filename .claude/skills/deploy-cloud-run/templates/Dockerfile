# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the Spring Boot jar.
# Tests and linters are not run here; `./gradlew deployCloudRun` runs `check` before uploading.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy build definition first so the dependency download layer is cached between deployments.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src/ src/
RUN ./gradlew --no-daemon bootJar

# ---- Runtime stage: JRE only, non-root user.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 appuser
WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar app.jar
USER appuser

# Container-aware JVM sizing: use up to 75% of the memory limit, single-threaded GC for a small instance.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
