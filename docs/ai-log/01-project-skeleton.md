# AI log 01: Project skeleton with quality gates

- **Date:** 2026-10-13
- **Goal:** An empty Spring Boot service that builds, starts and enforces every quality gate in AGENTS.md, with no features.
- **Agents and skills used:** architect (Sonnet) for the plan review, code-reviewer for the diff. Skills: none needed beyond AGENTS.md (no endpoint yet).

## Key prompts
1. > Plan mode. First slice, no features at all: I want an empty Spring Boot service that builds, starts and enforces every quality gate AGENTS.md asks for. Versions go straight into the build file, we are not using a version catalog. Set up formatting, linting, bug detection and coverage so that a failing gate actually blocks the build, and exclude the two thin Firestore classes from the coverage rule as AGENTS.md says. Use only the dependencies already agreed in the ADRs; ask me before adding anything else. Have the architect agent on sonnet look over the plan, then show it to me and wait. Once I approve, build it and run the full gate. Tell me honestly what passes and what doesn't.
2. Clarifying question from the AI: an empty service has only `MovieApiApplication`, which fails the 80% rule unless a test starts Spring. The user chose to **exclude the bootstrap class from coverage**.

## Plan proposed
- Gradle 9.4.1 wrapper (with checksum), Java 21 toolchain, Spring Boot 3.5.16, only `spring-boot-starter-web` and the test starter.
- Validation, springdoc and Firestore are deferred to the slices that use them.
- Gates wired into `check`: compiler `-Werror`, Spotless (google-java-format), Checkstyle, SpotBugs, JaCoCo 80% lines.
- Each gate proven to block with a temporary violation.

The architect said "Approve with changes". All 8 required changes were applied:
- `bootJar` produces `app.jar` for the Dockerfile.
- `HideUtilityClassConstructor` and `MissingSwitchDefault` dropped.
- `JavadocMethod` no longer forces tags.
- The JaCoCo version is pinned.
- The write-tests skill is updated with the new exclusion.
- The wrapper bootstrap is explicit.
- Each gate is proven in isolation.

Suggestions applied:
- One shared exclusion filter for both the report and the verification task.
- A trimmed Checkstyle rule set.
- No unrequested `gradle.properties` or graceful-shutdown settings.
- The "fix, don't suppress" stance.
- README guidance for finding the JDK.

## Deviations found while building
- **`io.spring.dependency-management` replaced by `platform(SpringBootPlugin.BOM_COORDINATES)`.**
  The plugin applied Boot's BOM to SpotBugs' tool classpath and downgraded `commons-lang3` to 3.17.0. SpotBugs 4.10.5 then failed with `NoClassDefFoundError`.
  The change removes a plugin; it adds nothing.
- **google-java-format pinned to 1.36.1.** 1.37.0, the newest version, throws `InvocationTargetException` inside Spotless 8.10.4. 1.36.1 is the version Spotless supports.
- **`.gitattributes` forces LF.** Spotless otherwise expected CRLF on Windows, and the container build runs on Linux.

## Review findings
code-reviewer had no "Must fix" items. "Should fix" items:
1. Missing AI log. This file fixes it.
2. The coverage gap depended on someone remembering it. Fixed: `check` now fails when main code falls under the rule but no test ran. This was proven with an untested probe class.
3. Two hook scripts are stored with CRLF and conflict with `.gitattributes`. They are renormalized in a separate chore commit.

Nits applied: ADR-0008 now explains `this-escape`/`serial` under `-Werror` and the strict abbreviation rule.
Nits left as they are: the redundant Spotless steps (harmless). The README Swagger and deploy links, and the `Dockerfile`/`gradle.properties`, belong to the deploy slice.

## Human decisions
- Exclude `MovieApiApplication` from coverage, instead of adding a `@SpringBootTest`, which would break the unit-tests-only rule.

## Outcome
- `./gradlew clean check` is green. `test` is NO-SOURCE (there are no tests), and JaCoCo is SKIPPED with no covered classes.
- Proven to block, each in isolation:
  - `spotlessCheck` (format)
  - `checkstyleMain` (`MissingJavadocMethod`)
  - `spotbugsMain` (`RV_RETURN_VALUE_IGNORED`)
  - `compileJava` (rawtypes with `-Werror`)
  - `jacocoTestCoverageVerification` (0.40 < 0.80)
  - `check` (untested class, no tests)
- A fully tested probe passed. The probe files are deleted.
- Startup: `app.jar` started in about 1.4 s on `$PORT=18573` and answered `GET /` with Spring's default 404 JSON. (Errors are not yet `ProblemDetail`; that comes with the error-handling slice.)

## Lessons
- The PostToolUse hook runs `spotlessApply` after every Edit/Write. Deliberately badly formatted probe files must be written via the shell, or the hook "fixes" them.
- Check new tool versions against the Spotless plugin before pinning them.
