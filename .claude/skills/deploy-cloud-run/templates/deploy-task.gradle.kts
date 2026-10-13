// ---------------------------------------------------------------------------
// Append to build.gradle.kts.
// One-line deployment:  ./gradlew deployCloudRun
// Settings come from gradle.properties. Prerequisite: one-time gcloud setup (see docs/deployment.md).
// ---------------------------------------------------------------------------

// Fixed jar name so the Dockerfile can copy it without guessing the version.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}
tasks.named<Jar>("jar") {
    enabled = false // skip the "-plain" jar, it is not needed for the container
}

val gcpProjectId = providers.gradleProperty("gcpProjectId").get()
val gcpRegion = providers.gradleProperty("gcpRegion").get()
val cloudRunService = providers.gradleProperty("cloudRunService").get()
val cloudRunServiceAccount = providers.gradleProperty("cloudRunServiceAccount").get()

// On Windows the gcloud launcher is gcloud.cmd.
val gcloudExecutable =
    if (System.getProperty("os.name").lowercase().contains("win")) "gcloud.cmd" else "gcloud"

tasks.register<Exec>("deployCloudRun") {
    group = "deployment"
    description = "Runs all checks, then builds from source with Cloud Build and deploys to Cloud Run."

    // Only a green build may be released.
    dependsOn("check")

    workingDir = rootDir
    commandLine(
        gcloudExecutable, "run", "deploy", cloudRunService,
        "--source", ".",
        "--project", gcpProjectId,
        "--region", gcpRegion,
        "--service-account", cloudRunServiceAccount, // least-privilege identity, see docs/deployment.md
        "--allow-unauthenticated",
        "--min-instances", "0",   // scale to zero when idle: no idle cost
        "--max-instances", "1",   // keep at 1: one in-memory catalog (ADR-0007), also caps cost
        "--memory", "512Mi",
        "--cpu", "1",
        "--quiet",                // accept defaults such as creating the Artifact Registry repo
    )
}
