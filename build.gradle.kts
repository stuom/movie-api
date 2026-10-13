import org.springframework.boot.gradle.plugin.SpringBootPlugin

// Movie API build. Versions are declared inline on purpose (ADR-0001); the Spring Boot BOM supplies
// the versions of the Spring libraries. Quality gates are described in ADR-0008.

plugins {
    java
    checkstyle
    jacoco
    id("org.springframework.boot") version "3.5.16"
    id("com.diffplug.spotless") version "8.10.4"
    id("com.github.spotbugs") version "6.5.13"
}

group = "com.pehmo"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Gradle's native platform instead of the io.spring.dependency-management plugin: the plugin applies
    // the BOM to every configuration, including SpotBugs' own classpath, and downgrades libraries SpotBugs needs.
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ---------------------------------------------------------------------------
// Packaging: a fixed jar name so the Dockerfile can copy it without knowing the version.
// ---------------------------------------------------------------------------
tasks.bootJar {
    archiveFileName = "app.jar"
}
tasks.jar {
    enabled = false // the "-plain" jar is not needed for the container
}

// ---------------------------------------------------------------------------
// Quality gates. Every gate fails the build; never loosen one to get a green build.
// ---------------------------------------------------------------------------

// Compiler: any warning is an error ("no new warnings" in the definition of done).
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-Werror"))
}

// Formatting: google-java-format owns the layout of every Java file.
spotless {
    java {
        // 1.36.1 is the newest version Spotless 8.10.4 supports; 1.37.0 fails inside the Spotless step.
        googleJavaFormat("1.36.1")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Linting: naming, imports, Javadoc and empty catch blocks (config/checkstyle/checkstyle.xml).
checkstyle {
    toolVersion = "14.3.0"
    maxWarnings = 0
    isIgnoreFailures = false
}

// Bug detection on main and test classes.
spotbugs {
    toolVersion = "4.10.5"
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    ignoreFailures = false
}
tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
}

// Coverage: 80% line coverage on main code. Bootstrap and the thin Firestore adapter classes are
// excluded because they have no automated tests by design (ADR-0003, ADR-0008).
jacoco {
    toolVersion = "0.8.15"
}
val coverageExclusions =
    listOf(
        "**/MovieApiApplication.class",
        "**/FirestoreMovieRepository*.class",
        "**/FirestoreConfig*.class",
    )
val coveredClasses = sourceSets.main.get().output.classesDirs.asFileTree.matching { exclude(coverageExclusions) }

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(coveredClasses)
}
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(coveredClasses)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
    // JaCoCo silently skips the rule when there is no execution data. Fail instead whenever there is
    // main code under the rule but no test ran (no tests yet, or tests no longer discovered).
    doLast {
        val hasCoveredClasses = !coveredClasses.isEmpty
        val hasExecutionData = tasks.jacocoTestCoverageVerification.get().executionData.files.any { it.exists() }
        if (hasCoveredClasses && !hasExecutionData) {
            throw GradleException("Main code is subject to the coverage rule, but no test ran: add unit tests (ADR-0008).")
        }
    }
}
