# Service Exposure

Applies when: adding an endpoint, choosing a status code, designing a response body, URL naming, API versioning, serialisation format, ETag, If-None-Match, If-Match, 304, polling, caching
Status: enforced
Framework-first: content negotiation, status mapping, serialisation and binding are provided. Assembling responses by hand is a defect.

## Index

- [boundary-rule] The boundary rule
- [controllers] Controllers
- [status-codes] Status codes
- [response-shape] Response shape
- [serialisation-format] Serialisation format
- [urls] URLs
- [conditional-reads] Conditional reads: ETag and 304
- [description] Generated description
- [verify] Verify
- [sources] Sources

## [boundary-rule] The boundary rule

Persistence types never cross the HTTP boundary, in either direction. Requests bind to a request type; responses are built from a response type. No exceptions, including "it happens to have the right fields today".

Returning a persistence type means every field ever added to it joins the public contract silently, with no decision and no review. That includes fields added for internal bookkeeping, audit, and orchestration state. The cost is paid later by whoever has to remove one.

The mapping lives in the service layer or a dedicated mapper, never inline in a controller.

## [controllers] Controllers

Accept, delegate, return. A controller that branches on domain state, maps, or calls persistence has absorbed logic that belongs a layer down. See `design/service-interaction.md`.

Endpoints exist because a client needs them. An endpoint that exposes an internal maintenance operation (re-syncing a cached copy, forcing a recalculation) is API surface with no business meaning, and it lets a client drive the system into a state nobody designed. Delete it and fix the reason it existed.

## [status-codes] Status codes

| Situation | Status |
|---|---|
| Read succeeded | 200 |
| Created, with `Location` header | 201 |
| Accepted for async processing | 202 |
| Succeeded, nothing to return | 204 |
| Malformed syntax, failed validation, unparseable value | 400 |
| No credentials, or bad credentials | 401 |
| Authenticated but not permitted | 403 |
| The addressed resource does not exist | 404 |
| The resource exists but is not in a state permitting this | 409 |
| Semantically invalid but syntactically fine | 422 |
| Unhandled fault | 500 |

Two distinctions that are routinely got wrong:

- **An unrecognised enum value in a parameter is 400, not 404.** The caller sent something invalid; nothing is missing. 404 sends them hunting for a resource that was never referenced.
- **An illegal state transition is 409, not 400.** The request is well-formed and the resource exists; the resource is simply not in a state that allows the operation. The distinction tells a client whether to fix the request or re-read the resource.

Reporting a client error as 500 is worse than either: it says the server is broken and may page someone.

## [response-shape] Response shape

- **Model the domain vocabulary**, not positional placeholders. Named roles that mirror the domain language beat `item1` / `item2`, which map onto nothing and force every consumer to learn a private convention.
- **Include what changes.** A response for a live resource that omits its state, its progress, and its result cannot describe the resource while it is in use.
- **Represent absence with absence.** A `null` field, an omitted field, or an enum, never a sentence like `"pending"` in a value field, which forces string comparison on every consumer.
- **Expose the identifier deliberately** as a named field.

## [serialisation-format] Serialisation format

Fix the representation of every temporal and numeric type explicitly, and assert it in a test. Durations, timestamps and decimals each have several plausible encodings, and the default can shift under a dependency upgrade with no code change and nothing failing.

The cheapest way to remove the question is to expose the primitive the client needs: a whole number of seconds rather than a duration object, an instant in a fixed textual form rather than a structured date. A field whose format cannot be misread does not need a policy.

Omit a field entirely rather than specify a format nobody consumes.

## [urls] URLs

- Plural nouns for collections, identifiers for members: `/api/widgets`, `/api/widgets/{id}`.
- Verbs live in the HTTP method, not the path. Where an operation genuinely is not CRUD, express it as a sub-resource that reads as a thing rather than a command.
- Filtering, sorting and pagination are query parameters, never path segments.
- Version at the base path from the first public release. Retrofitting a version once clients exist is a migration, not a change.

## [conditional-reads] Conditional reads: ETag and 304

- **Use an entity tag on reads that many clients poll.** The server sends `ETag`; the client
  sends it back in `If-None-Match`; an unchanged resource answers `304 Not Modified` with no body.
- **The tag must change whenever any part of the representation changes,** including data composed
  in from other records at read time. A tag built from one record's version alone serves a stale
  304 after a change to a record it embeds. Build it from every version the response depends on.
- **Decide the tag before building the body** where possible. A tag hashed from the finished body
  saves bandwidth, not work. Defer whatever is derived from the records too, not only the
  serialisation: read the versions with the records, hand back the derivation unbuilt, and build
  it only after the tag check. Reading again for the body would let the body and the tag differ.
- **Preconditions on writes (`If-Match`, answering 412) guard against lost updates** from a client
  editing a stale view. They also refuse a client's own second action sent before the first
  answer arrived. Use them where a person edits a resource at leisure, not on a stream of quick
  commands; protect those with a version check on the server and an idempotency key (see
  `[idempotency]` in `design/concurrency-consistency.md`).
- **A polled countdown needs the server's time.** Send the server's current time in the response,
  so the client computes its offset and its display does not drift.

## [description] Generated description

- Generate the description from the code. A copy kept by hand drifts, and nothing fails when it does.
- A generator lists what it can see. Read the output: failures and statuses set at runtime are the usual gaps, and the result still looks complete.
- Compare it with the real routes and real answers in a test, in both directions, so an endpoint added later cannot go missing.
- Serve it only where it is needed. It is a map of every operation, so it waits for the operations to be protected.

## [verify] Verify

- No controller signature references a persistence type.
- At least one test per response asserts an internal field is absent.
- Every status code in the table above that the endpoint can produce has a test.
- Temporal and numeric formats are asserted against a literal body, not an object.
- A read with a current `If-None-Match` answers 304; a change to any record the response composes
  in changes the tag.

## [sources] Sources

- RFC 9110: HTTP semantics; the normative definition of every status code above. Conditional
  requests: `ETag` (8.8.3), `If-Match` and `If-None-Match` (13.1.1, 13.1.2), 304 (15.4.5).
- The boundary rule is the API-contract / persistence-model separation described as the Data Transfer Object pattern in *Patterns of Enterprise Application Architecture* (Fowler), and as the adapter boundary in hexagonal architecture.
