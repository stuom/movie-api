// Append to build.gradle.kts. The fixed jar name (app.jar) that the Dockerfile copies is set by
// tasks.bootJar { archiveFileName = "app.jar" }.
// ---------------------------------------------------------------------------
// Deployment: ./gradlew deployCloudRun (docs/deployment.md, ADR-0014). Settings live in gradle.properties.
// The keys are checked when the task runs, never while configuring, so a missing key cannot break `check`.
// ---------------------------------------------------------------------------
tasks.register<Exec>("deployCloudRun") {
    group = "deployment"
    description = "Runs all checks, then builds the source with Cloud Build and deploys it to Cloud Run."
    // Only a green build may be released.
    dependsOn(tasks.check)
    workingDir = rootDir
    // Task-local values, so doFirst does not capture the build script (configuration cache).
    val deploySettings =
        listOf("gcpProjectId", "gcpRegion", "cloudRunService", "cloudRunServiceAccount")
            .associateWith { providers.gradleProperty(it) }
    // On Windows the gcloud launcher is gcloud.cmd.
    val gcloud = if (System.getProperty("os.name").lowercase().contains("win")) "gcloud.cmd" else "gcloud"
    doFirst {
        val missing = deploySettings.filterValues { it.orNull.isNullOrBlank() }.keys
        if (missing.isNotEmpty()) {
            throw GradleException("Missing deployment settings in gradle.properties: ${missing.joinToString()}")
        }
        val values = deploySettings.mapValues { it.value.get() }
        commandLine(
            gcloud, "run", "deploy", values.getValue("cloudRunService"),
            // Uploads the working tree, not the last commit: commit before deploying.
            "--source", ".",
            "--project", values.getValue("gcpProjectId"),
            "--region", values.getValue("gcpRegion"),
            // Least-privilege identity: only roles/datastore.user, no key files.
            "--service-account", values.getValue("cloudRunServiceAccount"),
            "--allow-unauthenticated",
            // Scale to zero when idle: no idle cost.
            "--min-instances", "0",
            // Keep at 1: one in-memory catalog (ADR-0007); also caps cost.
            "--max-instances", "1",
            "--memory", "512Mi",
            "--cpu", "1",
            // Extra CPU while the JVM and Spring start: shorter cold starts.
            "--cpu-boost",
            // Accept defaults such as creating the Artifact Registry repository.
            "--quiet",
        )
    }
}
