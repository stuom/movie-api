# Architecture Decision Records

One short document per significant decision: what was decided, why, and what it costs us.
Add a new ADR instead of rewriting history; mark old ones as superseded.

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-spring-boot-java-gradle.md) | Spring Boot, Java 21 and Gradle | Accepted |
| [0002](0002-single-endpoint-for-list-and-search.md) | One endpoint for list and search | Accepted (amended by 0007) |
| [0003](0003-cloud-firestore-for-persistence.md) | Cloud Firestore for persistence | Accepted (amended by 0007) |
| [0004](0004-error-format-rfc-7807.md) | Errors as RFC 7807 problem details | Accepted |
| [0005](0005-name-prefix-search-and-composite-indexes.md) | Name prefix search and composite indexes | Superseded by 0007 |
| [0006](0006-movie-data-model-and-seed-data.md) | Movie data model and seed data | Accepted |
| [0007](0007-in-memory-search-over-firestore-catalog.md) | In-memory search over a Firestore-backed catalog | Accepted (amended by 0013) |
| [0008](0008-quality-gates-and-build-verification.md) | Quality gates and build verification | Accepted |
| [0009](0009-movie-validation-in-plain-java.md) | Movie validation in plain Java at the service boundary | Accepted |
| [0010](0010-storage-errors-and-document-mapping.md) | Storage errors, bounded waits and document mapping | Accepted |
| [0011](0011-springdoc-openapi.md) | OpenAPI description and Swagger UI with springdoc-openapi | Accepted |
| [0012](0012-strict-json-request-bodies.md) | Strict JSON request bodies | Accepted |
| [0013](0013-catalog-refresh-policy-and-read-budget.md) | Catalog refresh policy and Firestore read budget | Accepted |

Template: `.claude/skills/document-change/templates/adr-template.md`
