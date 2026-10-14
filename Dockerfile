# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the Spring Boot jar.
# Tests and linters are not run here: `./gradlew deployCloudRun` runs `check` before uploading.
# No separate dependency layer: Cloud Run source deploys build in a fresh environment and do not reuse
# Docker layers, so it would only add a step.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

# ---- Runtime stage: JRE only, non-root user.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 appuser
WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar app.jar
USER appuser

# Container-aware JVM sizing. The heap needs little (a few hundred movies), so 60% of the 512Mi limit
# leaves room for metaspace, thread stacks and the Firestore client's gRPC/Netty buffers. Serial GC
# suits a single small instance. On OutOfMemoryError the JVM exits, so Cloud Run starts a fresh
# instance instead of keeping a broken one.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
