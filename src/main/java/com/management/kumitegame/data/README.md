# data

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [facts] Project facts
- [backup] Backup and restore
- [pending] Pending on hosted MongoDB
- [known-gaps] Known gaps against the ACID rules

## [purpose] Purpose

The data layer of each feature, and the MongoDB setup they share. One Spring Data repository per
aggregate root, in its feature's `data` package.

## [owns] Owns

Each feature stores a document apart from its domain type, with a mapper beside it: see
`[pattern]` in `src/main/java/com/management/referee/README.md`.

| Feature | Repository | Document and mapper |
|---|---|---|
| `kumitegame` | `KumiteGameRepository` | `KumiteGameDocument`, `KumiteGameDocumentMapper` |
| `athlete` | `AthleteRepository` | `AthleteDocument`, `AthleteDocumentMapper` |
| `referee` | `RefereeRepository` | `RefereeDocument`, `RefereeDocumentMapper` |
| `account` | `AccountRepository` | `Account` is stored as it is: it carries no Spring Data type |

The athlete and referee repositories and documents extend `IdentityRepository` and
`IdentityDocument` in the shared `data` package: see `src/main/java/com/management/data/README.md`.

`KumiteGameDocument` stores enum values as plain strings and durations as whole milliseconds, so a
name this build no longer knows reaches the mapper, the one place an old shape is absorbed. Its
entries are nested records of its own, never the domain records, so a domain rename cannot change
the stored shape unseen.

**A nested property named `id` is stored as `_id`.** Spring Data maps it so in an embedded
document too, so an entry's id is stored as `meta.entryId`. `RepresentationCharacterizationTest`
pins it.

## [may-call] May call

`domain` types only. Only a `service` package calls a repository.

## [facts] Project facts

- MongoDB 8.0 in Docker at `localhost:27017`, database `kumitedb`, a one-member replica set named
  `rs0`, published to `127.0.0.1` only. Tests run a replica set on the same image.
- `w=majority`, primary reads, retryable writes and every timeout are set in `config`, not in a
  connection string, so no environment can weaken them, and the same option in `MONGO_URI` is
  ignored. The application refuses a reachable standalone: see the config README.
- **Tests connect directly, local connects by discovery.** The Testcontainers URL names no
  `replicaSet`, so tests never exercise member discovery. Local does, through
  `replicaSet` in the default `MONGO_URI`. Both give the same transaction and write concern
  behaviour.
- **`mongo:8.0` refuses to start on Linux kernel 6.19 and newer** (SERVER-121912, a startup crash).
  CI is pinned to `ubuntu-22.04` for this, and a Docker host that moves to such a kernel hits it too.
- A one-member replica set has no failover, so neither local nor tests exercise one.
- Connection settings bind through **`spring.mongodb.*`**, not `spring.data.mongodb.*`. Boot 4 split
  that namespace into driver level and repository level. Both resolve, so the wrong one fails
  silently by falling back to the default host.
- Environment variables: `MONGO_URI` and `MONGO_DB`. The same build reaches local and hosted
  MongoDB by changing them alone. Credentials and TLS arrive in `MONGO_URI`, and `mongodb+srv` turns
  TLS on. Only the `dev` profile defaults `MONGO_URI`: see `[profiles]` in the config README.
- `AccountRepository.touchEveryAccountWithRole` writes a `writeGuard` field that `Account` does not
  map, so transactions collide: see `[last-admin]` in
  `src/main/java/com/management/kumitegame/service/README.md`.
- `MONGO_DB` wins over a database named in `MONGO_URI`. A hosted URI usually names none, and without
  this the driver would write to `test`.
- **The database password never reaches a log.** `DatastoreConnectionIT` proves it on a failed
  connection, rejected credentials and a malformed URI. A `proxyPassword` in the URI is the
  exception: the driver prints it in clear, so never use one.
- **The username stays out of the logs until a login fails.** The driver's start line is off, through
  `logging.level.org.mongodb.driver.client`. A rejected login still logs it, with the password
  hidden, in the health check's warning. Hosts appear in the driver's cluster logs.
- `/actuator/health` needs an Admin. Its detail names the username, hosts and disk paths, so the
  public probes show the status alone: see `[actuator]` in the config README.
- The hosts in `MONGO_URI` only pick the first server to ask. The driver then uses the member
  addresses stored in the replica set, so they must be reachable from the application: publishing
  the container on another port needs a matching member address in `rs.initiate`.
- A wrong password starts the application with readiness DOWN (503), and every request that needs
  the datastore fails with a 500, including one with credentials, because they cannot be checked.
- Collections: `kumiteGame`, `athlete`, `referee` and `account`, each named in `@Document`
  because the classes end in `Document`. Every document but an account carries `version`,
  `schemaVersion`, `createdAt` and `modifiedAt`. No index beyond `_id`.
- **Every list scans its collection, and no index is declared for one.** A name search,
  `findByNameContainingIgnoreCase` on `athlete` and `referee`, matches anywhere in the name in any
  case, which no index can serve. A game list reads every game, or every game that ever named the
  athlete through `findByAssignmentsAthleteId`, because the status and the sides are folded and
  never stored. At one championship's load, tens to hundreds of small documents, a scan is enough.
  Measure before adding one.
- **A derived `Containing` query is safe from the caller's text.** Spring Data quotes it before
  building the pattern, so a `.` or a `(` is matched as written. A hand-written `$regex` query would
  not be: `RefereeApiIT` fails on one.
- **Deleting an athlete or referee a game names is refused** by
  `existsByAssignmentsAthleteId` and `existsByAssignmentsRefereeId`, a collection scan of
  `kumiteGame`, accepted at one championship's load until a measurement says otherwise.
- Each feature's `data` package, and the shared `data` package, is `@NullUnmarked` on purpose. On a `@NullMarked` package, Spring
  Data adds a runtime null check to every repository. The custom methods today return a count or a
  boolean, and `findById` returns `Optional`, so it would only add an interceptor. A finder that
  returns one document would matter: finding nothing would throw `EmptyResultDataAccessException`,
  which `GlobalExceptionHandler` does not handle.

## [backup] Backup and restore

`mongodump` and `mongorestore`, because they work on any plan and any host. The `mongo:8.0` image
ships both. Keep the URI out of shell history and out of any file under version control. Name the
database the application uses: a wrong name exits 0 and writes an empty archive, so check the
collection count in the output.

```bash
mongodump --uri="$MONGO_URI" --db="${MONGO_DB:-kumitedb}" --archive=kumitedb.archive --gzip
mongorestore --uri="$MONGO_URI" --archive=kumitedb.archive --gzip --drop
```

`--drop` replaces each collection in the archive, so check the target first. A restore writes back
to the database named in the archive. A database user with `readWrite` on it can run both.

**Take the dump with no game running.** Each collection is read at a different moment, so a dump
taken during a game can hold a game that names an athlete the dump missed, and `--oplog`, which would prevent
it, is refused on hosted free clusters. Anything written after the dump is lost on restore.

## [pending] Pending on hosted MongoDB

Proven locally, on a replica set with a login, and not yet on a hosted cluster. Do these when the
first Atlas cluster exists:

- Connect the same build by setting `MONGO_URI` alone, with TLS through `mongodb+srv`.
- Run the two commands in `[backup]` once against it, and restore the result.

## [known-gaps] Known gaps against the ACID rules

The rules are in `context/workflow/design/acid.md`. No gap is known today: every action on a game is
one versioned write to one document, and an entry carries the id a retry is recognised by.
