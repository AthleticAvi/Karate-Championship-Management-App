# referee

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [pattern] Domain, document and mapper
- [writes] Writes

## [purpose] Purpose

A referee, kept once and outside any match, so a match can refer to one by id. The first feature
whose domain type is apart from its stored document: `kumitegame` and `athlete` follow the same
split.

`kumitegame.domain.Referee` is a different type: today's name embedded in a match. It stays until
match assignments replace it.

## [owns] Owns

| Layer | Types |
|---|---|
| `web` | `RefereeController` at `/api/v1/referees`, `RefereeRequest`, `RefereeResponse`, `RefereeMapper` |
| `service` | `RefereeService` |
| `domain` | `Referee`, `RefereeNotFoundException`, `RefereeChangedException` |
| `data` | `RefereeDocument`, `RefereeRepository`, `RefereeDocumentMapper` |

## [may-call] May call

No other feature. Inside the feature, each layer as `src/main/java/com/management/README.md`
states.

## [pattern] Domain, document and mapper

The pattern to follow when a domain type is parted from its stored form.

- **The domain type holds what the business owns,** with no Spring Data type in it: `Referee` is
  `id` and `name`. A strict rule in `ArchitectureTest` holds every feature's `domain` to that, except
  the two still frozen.
- **The document holds the stored shape:** the domain fields, plus `version`, `schemaVersion`, and
  the `createdAt` and `modifiedAt` instants. It knows nothing of the business. Its creator is marked
  `@PersistenceCreator`: see `[object-construction-on-read]` in
  `context/workflow/databases/mongodb/design/persistence.md`. The collection is named in
  `@Document`, because the default would follow the class name.
- **The mapper sits in `data`** and is the one place an old shape is absorbed. It reads every shape
  up to `CURRENT_SCHEMA_VERSION`, refuses a newer one, and every write stores the current shape.
- **A change carries the stored metadata through.** The service reads the document, maps it to the
  domain, applies the change there, and maps the result back onto that document. The domain never
  sees the version, and the save is checked against the version that was read. A save replaces the
  whole document, so the mapper must copy `createdAt` from the stored one: auditing sets it only
  on an insert.
- **Instants come from auditing,** never set by hand: see `[auditing]` in the config README.
- **A primitive `version` is stored as 1 on insert** and rises by one on each save.

`RefereeAuditingIT` and `RefereeApiIT` prove the stored shape, and `RefereeDocumentMapperTest`
the round trip.

## [writes] Writes

| Operation | Atomic unit | Invariants | Guard | Acknowledgement |
|---|---|---|---|---|
| Create, rename | One document | Domain type | Version | `w=majority` |
| Delete | One document | None yet | None | `w=majority` |

**A rename is not retried.** It sets a value, so a retry would overwrite a name the caller never
saw. A rename that loses to another write answers 409 with the referee as it now stands, in a
`current` property of the problem detail. A referee deleted in between answers 404.

**A delete has no version check.** It goes by id, because removing a referee does not depend on
the name. Deleting a referee a match refers to is refused once matches refer to referees by id.
