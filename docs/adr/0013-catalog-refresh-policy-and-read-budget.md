# ADR-0013: Catalog refresh policy and Firestore read budget

- **Status:** Accepted
- **Date:** 2026-10-14
- **Amends:** ADR-0007 (cost and scale numbers)

## Context
Search and get by id read an in-memory copy of the whole catalog (ADR-0007). Every load of that copy reads **every** movie document, and the free tier allows 50,000 reads per day. The refresh policy therefore decides most of the Firestore bill. It also decides what users see when Firestore is slow or a document is corrupt (ADR-0010).

Constraints:
- Cloud Run (`--max-instances 1`, `min-instances 0`) throttles CPU between requests, stops an idle instance after about 15 minutes, and briefly runs two instances during a rollout.
- A failed load is not free: a load that fails on a corrupt document has usually been billed for every document it read.
- The catalog must never go blank because a refresh failed, and the refresh logic must be unit-testable without sleeping.

## Options considered
1. **Scheduled background refresh** (`@Scheduled`): reloads even when nobody uses the service, so an idle instance costs reads; on Cloud Run the throttled background CPU can stall it.
2. **Lazy refresh on access** (chosen): the first request after the interval reloads on its own thread. An idle instance costs no reads.
3. **Retry a failed refresh quickly** (for example once a minute): recovers sooner, but at 20 movies a permanently failing load would cost about 28,800 reads a day, and much more with a larger catalog.
4. **`min-instances 1`**: makes the number of loads predictable (no cold starts), but an always-on instance is not free.

## Decision
`MovieCatalog` (service package, `app.catalog.refresh-interval`, default `60m`):
- **Initial load** in the constructor. If it fails, startup fails (usually a missing database or permission that no retry fixes). The interval must be positive; zero would reload on every request.
- **Lazy reload on access:** `movies()` and `findById()` reload first when the clock has reached `nextLoadAttemptAt`. No scheduler.
- **One reload at a time:** a non-reentrant `AtomicBoolean` guard, checked again after taking it, so a request that arrives while another reloads (or just finished) serves the current copy instead of starting a second full read.
- **Timing:** `nextLoadAttemptAt` = start of the attempt + interval, set on **every** attempt, successful or not.
- **Failure:** on `MovieStorageException` the old snapshot keeps being served, a warning is logged, and the next attempt waits a **full interval**.
- **Merge:** movies included while a load was running are merged into the fresh snapshot (keyed by id), so a create that lands during a reload is not lost.
- **`include(Movie)`**, called by `MovieService` after a successful create, replaces an entry with the same id (never duplicates) and does not move the next reload. It is named `include`, not `add`: SpotBugs treats `add*`/`load*`/`set*`/`put*` methods as mutators and would then flag the injected collaborators (EI_EXPOSE_REP2). Renaming avoided a suppression (ADR-0008).
- The default interval stays **60 minutes** (user decision).

**Read budget:** reads/day = (cold starts + refreshes during an instance's life + failed attempts) × max(movies, 1).
- Refreshes are at most 24 a day at 60 minutes and zero while idle; failed attempts are gated by the same interval.
- With `min-instances 0`, sparse traffic can cause 50-100 cold starts a day, and rollouts briefly run two instances. A realistic worst case is about **100 loads a day**.
- So the safe ceiling is about **500 movies** (not ~1,000 as ADR-0007 first estimated). 20 movies cost at most about 2,000 reads a day (4 % of the quota).
- Seeding 20 movies costs 20 writes, once.

## Consequences
- An idle service costs no reads, and a failing store or corrupt document costs at most one load per interval instead of one per minute.
- After a failure the catalog can stay stale for up to one more interval (60 minutes by default), and a corrupt document is noticed only through the warning log. Fix it in the Firestore console; the next attempt picks it up.
- The request that triggers a reload waits for it (up to the 20-second store timeout, ADR-0010); other requests are not blocked.
- A second instance or a manual console edit is visible only after that instance's next reload (ADR-0007).
- Above about 500 movies, or with much more cold-start traffic, revisit this: a longer interval, `min-instances 1` (costs money), or a different storage/search approach (ADR-0007 upgrade path).
- The behavior is pinned by `MovieCatalogTest` with a controllable test clock (`MutableClock`), without threads or sleeps.
