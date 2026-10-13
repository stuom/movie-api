# ADR-0008: Quality gates and build verification

- **Status:** Accepted
- **Date:** 2026-10-13

## Context
Tests and documentation matter more than the number of features, and other developers will extend the service.
The rules in AGENTS.md (formatting, naming, Javadoc, no swallowed exceptions, coverage) only hold if the build enforces them.
A gate that only reports, or that is not wired into `./gradlew check`, is ignored sooner or later.
`deployCloudRun` depends on `check`, so the gates also guard every release.

## Options considered
1. **Reports only** (gates run but `ignoreFailures = true`): no friction, but violations pile up.
2. **Blocking gates in `check`, rules limited to what AGENTS.md asks for**: every violation fails the build, and the rule set stays small enough that nobody is tempted to suppress it.
3. **Blocking gates with a full style guide** (for example `google_checks.xml`): more rules, but many overlap or conflict with google-java-format, and they create noise.

## Decision
Option 2. Every gate below fails `./gradlew check`:

| Gate | Tool and setting | Enforces |
|---|---|---|
| Compiler | `-Xlint:all,-processing -Werror` | "No new warnings" from the definition of done |
| Format | Spotless with google-java-format 1.36.1 | One layout everywhere; `./gradlew spotlessApply` fixes it |
| Lint | Checkstyle 14.3.0, `maxWarnings = 0`, `config/checkstyle/checkstyle.xml` | Naming, no abbreviations, no star or unused imports, Javadoc on public types and methods, no empty catch blocks |
| Bugs | SpotBugs 4.10.5, effort MAX, confidence MEDIUM, main and test | Likely defects (ignored return values, exposed mutable state, ...) |
| Coverage | JaCoCo 0.8.15, LINE covered ratio >= 0.80 | Main code is exercised by unit tests |

Further rules:
- **Layout belongs to google-java-format alone.** Checkstyle has no indentation, line-length, brace or import-order rules.
- **Tags are not forced.** `JavadocMethod` allows missing `@param`/`@return` tags, so Javadoc explains *why* instead of repeating signatures.
- **Javadoc checks are suppressed in `src/test`.** Test method names document the behavior.
- **Coverage exclusions:** `MovieApiApplication` (bootstrap only, would need a full Spring context to test), `FirestoreMovieRepository` and `FirestoreConfig` (thin adapter, no automated tests by design, ADR-0003).
  One shared filter feeds both the verification task and the report, so the gate and the report always agree.
- **Fix, don't suppress.** There is no SpotBugs exclude filter. Expected findings such as `EI_EXPOSE_REP` on records holding lists are fixed with `List.copyOf` in the compact constructor, which also matches the immutability rule.
- **Java 21 toolchain without the foojay resolver plugin.** Gradle finds a local Java 21 JDK, either from `JAVA_HOME` or from `org.gradle.java.installations.paths` in the user's `~/.gradle/gradle.properties`. The container build uses `eclipse-temurin:21-jdk`.
- **Spring Boot BOM as a Gradle `platform(...)`, not the `io.spring.dependency-management` plugin.**
  The plugin applies the BOM to every configuration, including SpotBugs' own tool classpath. There it downgraded `commons-lang3` to 3.17.0, and SpotBugs 4.10.5 then crashed with `NoClassDefFoundError: org/apache/commons/lang3/Strings`.
  The platform applies only to the application configurations.

## Consequences
- A green `check` means the code is formatted, linted, analyzed, tested and covered. Each gate was proven to block with a deliberate violation in slice 01.
- **No silent skip:** JaCoCo skips its rule when there is no execution data. So `check` also fails when main code falls under the coverage rule but no test ran (no tests yet, or tests no longer discovered).
  The skeleton passes only because its single class, `MovieApiApplication`, is excluded.
- Tool versions are pinned in `build.gradle.kts`. Upgrades are deliberate.
  google-java-format must stay within what the Spotless plugin supports: 1.37.0 fails inside Spotless 8.10.4.
- `-Xlint:all` includes `this-escape` and `serial`. Calling an overridable method from a constructor, or a `Serializable` class (for example an exception) without `serialVersionUID`, fails compilation. This is deliberate: fix the code.
- `AbbreviationAsWordInName` allows no run of capitals (`parseUrl`, not `parseURL`; `MovieDto`, not `MovieDTO`), matching the "no abbreviations" naming rule. Static final constants are not affected.
- Adding a Checkstyle rule or a coverage exclusion is a change to this ADR, not a quiet edit.
