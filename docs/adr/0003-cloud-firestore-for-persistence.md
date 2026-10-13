# ADR-0003: Cloud Firestore for persistence

- **Status:** Accepted, amended by [ADR-0007](0007-in-memory-search-over-firestore-catalog.md) (Firestore is used as a durable store only)
- **Date:** 2026-10-12
- **Updated:** 2026-10-13

## Context
Data must survive restarts, and the service must run on the Google Cloud free tier. Cloud Run containers have an ephemeral filesystem and scale to zero, so an embedded database (H2) loses data. The persistence choice must stay replaceable because other developers will extend the service.

## Options considered
1. **H2 embedded (in-memory or file):** simplest, but data is lost on restart or scale-down on Cloud Run, and instances do not share state. Rejected for a service that should look production-grade.
2. **Cloud SQL (PostgreSQL):** relational and familiar, and would suit rich search, but a managed instance is billed while it exists and does not fit a free-tier goal.
3. **External free-tier Postgres (non-Google):** adds a second vendor and network and credential handling.
4. **Cloud Firestore, Native mode:** serverless, scales to zero cost, IAM-based auth through Application Default Credentials (no passwords), and has a free quota (1 GiB storage, 50,000 reads, 20,000 writes and 20,000 deletes per day, for the project's one free database).

Client choice for Firestore: Spring Data Firestore only offers reactive repositories, which conflicts with Spring MVC, so the plain `google-cloud-firestore` Java client is used behind our own `MovieRepository` interface.

## Decision
Use Cloud Firestore (Native mode) in the `(default)` database, accessed with the official Java client through a `MovieRepository` interface implemented by `FirestoreMovieRepository`. Firestore only stores and returns movies (create, read one, read all); searching is done in memory (ADR-0007). Unit tests mock the repository interface. The runtime service account has only `roles/datastore.user`.

## Consequences
- Data is persistent; no credentials to manage.
- Free quota is per day, so catalog loads must stay cheap: roughly one read per movie per load (ADR-0007 gives the size guideline).
- Only the `(default)` database gets the free tier; named databases do not.
- The database location is permanent once created.
- Each movie, including its actors and director, is a single document (ADR-0006).
- Because there are no filtered queries, no composite indexes are needed.
- To keep the project simple there is no emulator and no integration-test suite. The Firestore adapter has no automated tests, so it is kept thin (create, get, list all) and verified by the smoke test after deploy. Add emulator-based tests if the storage logic grows.
- Switching to another store later means writing a new `MovieRepository` implementation; the service and API stay unchanged.
