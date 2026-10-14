# ADR-0010: Storage errors, bounded waits and document mapping

- **Status:** Accepted
- **Date:** 2026-10-13
- **Updated:** 2026-10-14 (slice 06: catalog gap after a timed-out create; cost of a corrupt document)

## Context
The Firestore adapter (`FirestoreMovieRepository`) has no automated tests and no emulator (ADR-0003). It must still give the rest of the application clear outcomes: a duplicate must become `409 Conflict` and never a `500` (ADR-0006), and no Firestore, gax or gRPC type may leave the `persistence` package. Firestore calls return futures that can wait forever, and the documents read back are not checked by `MovieRules` (ADR-0009), so a corrupt document could turn into a wrong movie without anyone noticing. Decisions marked *[architect]* came from the architect review of the slice plan.

## Options considered
1. **Let Firestore exceptions propagate** and map them in the error handler: no translation code, but the web layer would import Firestore/gax types and the duplicate check would be untestable.
2. **Annotated Firestore POJOs** (`@DocumentId`, `toObject`): less mapping code, but the mapping can only be tested with Firestore classes, and the client converts types silently.
3. **Translate failures into service exceptions and map documents with a plain field map** (chosen).

## Decision
- **Service exceptions** *[architect]*: Firestore failures are translated into `DuplicateMovieException` when `ALREADY_EXISTS` appears anywhere in the cause chain, and into `MovieStorageException` otherwise. Both live in the `service` package, so the later error handler never imports `persistence`. The cause-chain check lives in the package-private `FirestoreErrors.isAlreadyExists`, which is unit tested (`FirestoreErrorsTest`), because it is the one decision that separates `409` from `500`.
- **Bounded waits** *[architect]*: every Firestore call waits at most 20 seconds. On timeout the future is cancelled and `MovieStorageException` is thrown. A timed-out create has an **unknown outcome**: the write may still commit, and a retry then gets `DuplicateMovieException` (409). An interrupt restores the thread's interrupt flag and also becomes `MovieStorageException`.
- **Plain field map**: movies are stored as a `Map<String, Object>` built and read by `MovieDocument`, which uses no Firestore types, so the whole storage format is tested without a database (`MovieDocumentTest`). `createdAt` (a server timestamp, a Firestore type) is added by the repository and stays out of the domain; reading ignores it.
- **Strict reading** *[architect: strict genre and person checks]*: optional parts (director, actors, synopsis) may be missing; everything else must be present with the right type. Only `Long` or `Integer` values in the `int` range become numbers; a `Double`, even a whole one such as `1986.0`, is rejected rather than converted. Each error names the field path (for example `actors[1].lastName`) and the document id. The repository wraps the error in `MovieStorageException`, so one corrupt document stops the whole catalog load. This is intended: fail loudly instead of serving a partial or wrong catalog.
- **Testing boundary**: `FirestoreMovieRepository` and `FirestoreConfig` stay untested (ADR-0003, ADR-0008). The real duplicate path is verified by the Postman collection after deploy.

## Consequences
- The service and the web layer handle two meaningful exceptions and never see storage types; a different store only has to throw the same two.
- A request or startup never hangs on Firestore for more than about 20 seconds per call, at the price that a client may get a storage error (planned as `500`) for a movie that was in fact stored.
- Adding a movie field means updating `MovieDocument` (both directions) and its tests by hand.
- A single bad document, for example one edited by hand in the Firestore console, makes the catalog load fail until it is fixed in the console. The error message names the document id and field to fix. Each failed load attempt can still be billed for all N documents it read, so `MovieCatalog` waits a full refresh interval before trying again and keeps serving the previous copy (ADR-0013).
- A timed-out create that was in fact stored is not added to the in-memory catalog (the catalog is updated only after a confirmed write). The client's retry gets `409` with the `movieId`, but the movie appears in search, and in the planned `GET /api/v1/movies/{id}`, only after the next catalog reload, so a lookup by that id can answer `404` for up to one refresh interval.

## Update 2026-10-14 (documentation audit, slices 04 and 07)
The statements above that were written as plans are now implemented: a `MovieStorageException` is answered as `500` with a detail saying a retry is safe (`ApiExceptionHandler`, ADR-0004), and `GET /api/v1/movies/{id}` exists and reads the catalog. The consequence about a timed-out create still holds: such a movie is visible to lookup and search only after the next catalog reload, so a lookup of its id can answer `404` until then.
