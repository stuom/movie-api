# AI log 06: In-memory catalog and seed loader

- **Date:** 2026-10-14
- **Goal:** add the in-memory `MovieCatalog` that search and get by id will read (ADR-0007), and the `MovieSeedLoader` that adds the sample movies on first start (ADR-0006), with no API change.
- **Agents and skills used:** architect (Opus) for the plan review, unit-test-writer (Sonnet) for the tests before the implementation, code-reviewer (Opus) for the diff, doc-writer for the docs. Skills: firestore-persistence, write-tests, document-change.

## Key prompts
1. > Plan mode, think hard. Two related things. First, the in-memory copy of the whole catalogue that searching will run against: it loads at startup, is refreshed after a movie is added, reloads itself once it gets stale, and — importantly — keeps serving the previous copy if a refresh fails rather than going blank. Make the clock injectable so all of that is testable without sleeping.
   > Second, loading the sample movies at startup when the catalogue is empty and the feature is switched on, pushing each one through the same create path so they get the same validation, trimming and duplicate handling as anything arriving over HTTP. Anything rejected is logged and skipped, never silently dropped.
   > architect agent on opus reviews the plan, with attention to how many reads a refresh costs us on the free tier. Then unit-test-writer agent on sonnet covers the refresh behaviour with a fixed clock and the loader with a stubbed service.
   > Separately, read the sample file and give me a table of the entries you think are wrong — typos, implausible release years. Do not correct a single one; I decide.

## Plan proposed
Planned in plan mode and approved by the user:
- **`MovieCatalog`** (`@Component`, `final`): immutable snapshot keyed by id; initial load in the constructor, so a failed first load fails startup; lazy reload on access when the injected `Clock` reaches the next attempt time (no scheduler: an idle instance costs no reads, and Cloud Run throttles background CPU); one reload at a time; a failed reload keeps the old snapshot.
- **`MovieService`** calls the catalog after a successful create, not after an invalid, duplicate or failed one.
- **`MovieSeedLoader`** (`ApplicationRunner`, only when `app.seed.enabled=true` and the catalog is empty): every entry through `MovieService.createMovie`; rejected entries logged and skipped.
- **Configuration:** `app.catalog.refresh-interval=60m`, `app.seed.enabled=true`.

Architect (Opus) verdict: **Approve with changes**. Required changes, all folded into the plan:
- after a failed reload, wait a **full interval** before the next attempt instead of retrying once a minute: a failed load (for example on a corrupt document) can be billed for every document it read, so a 1-minute retry could cost about 28,800 reads a day at 20 movies;
- a **corrected cost model**: reads/day = (cold starts + refreshes + failed attempts) × max(movies, 1). With `min-instances 0`, sparse traffic causes 50-100 cold starts a day, so the safe ceiling drops from about 1,000 to about **500 movies**;
- a **storage failure stops seeding** instead of trying every entry: 20 × 20-second timeouts would exceed Cloud Run's 240-second startup limit;
- the seed tests use the **production `ObjectMapper`** (`@JsonTest` + `@Import(JacksonConfig.class)`), because the skip behavior depends on its strictness (ADR-0012);
- the refresh interval must be **positive** (zero would read the whole collection on every request).

Adopted suggestions: a non-reentrant `AtomicBoolean` reload guard (instead of `ReentrantLock.tryLock`, which would let the same thread reload again from inside a load); an explicit merge of movies included during a reload, keyed by id; documentation notes on the limitations (partial seed not resumed, early requests may see a partly seeded catalog).

The decisions are recorded in the new ADR-0013 and in updates to ADR-0006, 0007, 0010 and 0012.

## Red and green
- **Red:** the unit-test-writer wrote `MovieCatalogTest`, `MovieSeedLoaderTest`, the `MovieServiceTest` additions and the `MutableClock` test helper against skeletons; `./gradlew test` failed for the expected reasons.
- **Green:** implementation, then corrections found by the gate:
  - a `NullPointerException` from a debug log that read a value the mock returned as `null`; the log was removed;
  - Checkstyle `AbbreviationAsWordInName` findings fixed by renaming;
  - SpotBugs `EI_EXPOSE_REP2`, fixed in code instead of suppressed (ADR-0008): the catalog method is called `include` instead of `add` (SpotBugs treats `add*`/`load*`/`set*`/`put*` methods as mutators and then flags the injected collaborators), the next reload time is an `AtomicReference<Instant>`, and the seed loader keeps an `ObjectReader` instead of the mapper.

## Review findings
code-reviewer (Opus). No Must fix. The three deviations from the plan (`include`, `AtomicReference<Instant>`, `ObjectReader`) were judged acceptable.

Should fix, all applied:
- **Two reloads could run back to back:** the staleness check ran only before taking the reload flag. It is now repeated after taking it.
- **Wrong reason for `final` in the Javadoc:** it is final because the constructor may throw (SpotBugs CT_CONSTRUCTOR_THROW), not for safe publication. Fixed.
- **Skip tests did not check which entries were created:** they now assert the created names.
- **Storage failure only tested on the first entry:** a test now fails on the second entry and checks that the third is never attempted.
- **Empty seed file untested:** test added.
- **Next reload time taken from the start of the attempt was not pinned:** a test now advances the clock inside `findAll`.

Nits applied: the static import for `atLeast`, `shouldXWhenY` test names, the nested test class renamed to `Including`, and the 256 KB document limit noted in ADR-0012.

## Suspected seed-data errors
Reported to the user as a table; **none were corrected**, the seed file is unchanged (AGENTS.md: the user decides).
- **Likely errors:**
  - Avengers: Endgame 2018, probably 2019;
  - 2001: A Space Odyssey 1986, probably 1968;
  - "Downtown Abbey", probably "Downton Abbey";
  - director "Enger", probably "Engler";
  - "Grey", probably "Gray";
  - genre "Familty";
  - Catherine Mullen, probably Kathryn Mullen;
  - Nicholas "Dullea", probably Nicholas Hoult;
  - "Karkukoski", probably "Karukoski".
- **Lower confidence:** co-directors missing; the Were-Rabbit movie not under its full title; Raiders of the Lost Ark with `ageLimit` 0.

## Human decisions
- The default refresh interval stays **60 minutes** (the architect raised the trade-off; the user decided).
- Which seed entries to correct, if any, is left to the user.

## Outcome
- `./gradlew check` green: 282 tests. No quality gate was loosened, and no SpotBugs suppression was added.
- Commit: see git log.

## Lessons
- SpotBugs decides what a "mutator" is by method name. Before adding a suppression, check whether a name such as `add` or `load` triggers `EI_EXPOSE_REP2`.
- On a pay-per-read store, a failed load is not free. Retry policies must be gated by cost, not only by latency.
- Count cold starts in a read budget: with scale-to-zero they can outnumber scheduled refreshes.
