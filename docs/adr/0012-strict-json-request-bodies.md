# ADR-0012: Strict JSON request bodies

- **Status:** Accepted
- **Date:** 2026-10-13
- **Updated:** 2026-10-14 (slice 06: the seed loader uses an `ObjectReader` derived from the mapper; after the first live Postman run: `413` for every body over the limit, see Update below)

## Context
`POST /api/v1/movies` is public, unauthenticated and served by a single Cloud Run instance (ADR-0007). By default Jackson is lenient, which hides client mistakes:
- an unknown field such as `"synopsys"` is dropped silently, so data is lost without an error;
- `"1986"` and `1986.0` become the integer `1986`, and `123` becomes the text `"123"`;
- content after the JSON object is ignored, and a repeated key keeps only its last value;
- the body size is not limited. Cloud Run accepts requests up to 32 MiB, and the whole body is read before `MovieRules` (ADR-0009) can reject it, so large bodies could exhaust the heap of the one instance.

A maximal valid movie is about 10,000 characters.

## Options considered
1. **Keep Jackson's defaults** and rely on `MovieRules`: less configuration, but the conversions and dropped fields happen before the rules ever see the value.
2. **Strict settings in `application.properties` only:** simple, but `allow-coercion-of-scalars=false` only stops text from becoming a number. A test showed that `"name": 123` still reached the controller as `"123"`. There is also no property for the document size limit.
3. **Strict properties plus a small Jackson customizer** (chosen).

## Decision
In `application.properties`:
- `spring.jackson.deserialization.fail-on-unknown-properties=true`
- `spring.jackson.deserialization.accept-float-as-int=false`
- `spring.jackson.mapper.allow-coercion-of-scalars=false`
- `spring.jackson.deserialization.fail-on-trailing-tokens=true`
- `spring.jackson.parser.strict-duplicate-detection=true`

`common/JacksonConfig` (a `Jackson2ObjectMapperBuilderCustomizer`) adds:
- a coercion config that makes Jackson fail when an integer, float or boolean is sent where text is expected;
- `StreamReadConstraints.maxDocumentLength` of 256 KB. This came from a code-review "Must fix".

Every such error becomes a `400` problem detail. A body over the limit becomes `413` (ADR-0004).

## Consequences
- Client mistakes get a `400` that names the field instead of being converted or dropped. Sending `id` is rejected as an unknown field.
- These settings configure the application's one `ObjectMapper`, so they apply to **everything that injects it**, not only request bodies:
  - `MovieSeedLoader` reads the seed file as a `JsonNode` array and converts one entry at a time, with an `ObjectReader` derived from this mapper (`readerFor(NewMovie.class)`, which keeps all its settings). One bad entry is then logged and skipped (the AGENTS.md seed rule) instead of failing startup.
  - The 256 KB limit applies to the seed file as well. That is fine for the 20 sample movies (about 9 KB), but would need attention near the ceiling of about 500 movies (ADR-0013). If the seed grows past the limit, the loader needs its own copy of the mapper (`ObjectMapper.copy()`) with a larger limit.
- `@WebMvcTest` leaves out user `@Configuration` classes, so controller tests must `@Import(JacksonConfig.class)` or they test a more lenient mapper than production uses.
- Strictness depends on Jackson settings that are easy to misread, so it is pinned by controller tests: `"1986"`, `1986.0`, `123`, `1.5` and `true` in the wrong place, unknown fields, repeated keys, trailing content, int overflow and an oversized body.

## Update 2026-10-14 (413 for every oversized body)
The first live Postman run showed that a body well over 256 KB got `400` ("The request body is not a valid movie…", with the violation "has the wrong type") instead of `413`. When the limit trips inside a field, Jackson wraps its `StreamConstraintsException` in a `JsonMappingException`, and `ApiExceptionHandler` only looked at the direct cause; the controller test passed because its body was only 208 bytes over the limit, where the limit trips after the last field. The handler now searches the whole cause chain. Jackson uses the same exception type for its other stream limits (number length, nesting depth, name length), which can trip in a small body, so only the document-length limit becomes `413`; Jackson 2 tells them apart only by the message (it names `getMaxDocumentLength`). Controller tests pin a 300,000-character synopsis, 40,000 genres (`413`) and a 1001-digit year (`400`), so a Jackson upgrade that changes the wording fails `check`.
