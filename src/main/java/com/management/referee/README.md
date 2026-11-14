# referee

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [pattern] Domain, document and mapper
- [writes] Writes

## [purpose] Purpose

A referee, kept once and outside any game, so a game can refer to one by id. The first feature
whose domain type was apart from its stored document; `kumitegame` and `athlete` now follow the same
split.

## [owns] Owns

| Layer | Types |
|---|---|
| `web` | `RefereeController` at `/api/v1/referees`, which also lists and searches them by name, `RefereeRequest`, `RefereeResponse`, `RefereeMapper` |
| `service` | `RefereeService`, extending the shared `IdentityService`, and `RefereeReferences` |
| `domain` | `Referee`, and `RefereeNotFoundException`, `RefereeChangedException` and `RefereeInUseException`, extending the shared bases |
| `data` | `RefereeDocument` and `RefereeRepository`, extending the shared bases, and `RefereeDocumentMapper` |

## [may-call] May call

No other feature. Whether a game names a referee comes through `RefereeReferences`, which this
feature owns and `kumitegame.service.GameReferences` implements. Inside the feature, each layer as
`src/main/java/com/management/README.md` states.

## [pattern] Domain, document and mapper

The pattern to follow when a domain type is parted from its stored form.

**A named identity kept outside any game, such as an athlete or a referee, extends the shared
bases** rather than copying another feature: `IdentityDocument` and `IdentityRepository` in
`src/main/java/com/management/data/README.md`, `IdentityService` and `Versioned` in
`src/main/java/com/management/service/README.md`, and the three exceptions in
`src/main/java/com/management/domain/README.md`. It keeps its own fields, mapper, create and
change, controller, routes and role levels. It adds a case for its response shape to the changed
handler in `GlobalExceptionHandler`: without one, a lost change answers 500, not 409.

- **The domain type holds what the business owns,** with no Spring Data type in it: `Referee` is
  `id` and `name`. A strict rule in `ArchitectureTest` holds every feature's `domain` to that.
- **The document holds the stored shape:** the domain fields, plus `version`, `schemaVersion`, and
  the `createdAt` and `modifiedAt` instants, which `IdentityDocument` holds for a named identity. It knows nothing of the business. Its creator is marked
  `@PersistenceCreator`: see `[object-construction-on-read]` in
  `context/workflow/databases/mongodb/design/persistence.md`. The collection is named in
  `@Document`, because the default would follow the class name.
- **The mapper sits in `data`** and is the one place an old shape is absorbed. It reads every shape
  up to `CURRENT_SCHEMA_VERSION`, refuses a newer one, and every write stores the current shape.
- **A change carries the stored metadata through.** The service reads the document, maps it to the
  domain, applies the change there, and maps the result back onto that document. The save is
  checked against the version that was read. A save replaces the whole document, so the mapper
  must copy `createdAt` from the stored one: auditing sets it only on an insert.
- **An aggregate type never carries the stored version;** a composed read the service builds may
  carry it, when a caller must send it back. `Referee`, `Athlete` and `KumiteGame` never see it.
  `GameRead` carries the game's, so the version a caller sends back on an absolute action reaches
  every answer. A reader that only compares versions, such as a game's tag, gets them beside the
  aggregate from the service, in a `Versioned` record or `VersionedGameRead`.
- **Instants come from auditing,** never set by hand: see `[auditing]` in the config README.
- **A primitive `version` is stored as 1 on insert** and rises by one on each save. A document
  stored without one reads as version 0, so its next save is an insert and fails on the duplicate
  id: not backfilled, as for the other versioned documents. Only this application writes referees.

`RefereeAuditingIT` and `RefereeApiIT` prove the stored shape, and `RefereeDocumentMapperTest`
the round trip.

## [writes] Writes

| Operation | Atomic unit | Invariants | Guard | Acknowledgement |
|---|---|---|---|---|
| Create, rename | One document | Domain type | Version | `w=majority` |
| Delete | One document | No game names it, read first | None | `w=majority` |

**A rename is not retried.** It sets a value, so a retry would overwrite a name the caller never
saw. A rename that loses to another write answers 409 with the referee as it now stands, in a
`current` property of the problem detail. A referee deleted in between answers 404.

**A rename to the name already stored writes nothing** and answers the referee as it is.

**A delete has no version check.** It goes by id in one call, because removing a referee does not
depend on the name, and the count it returns tells a miss apart.

**Deleting a referee a game names is refused** with 409, by a read of the games before the delete.
Any assignment counts, a removed one included, so every game a read can show still finds the name.
A game created between the read and the delete is the accepted residual race: it reads the referee
as unknown.
