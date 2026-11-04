# repositories

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [facts] Project facts
- [backup] Backup and restore
- [pending] Pending on hosted MongoDB
- [known-gaps] Known gaps against the ACID rules

## [purpose] Purpose

The data layer. One Spring Data repository per aggregate root.

## [owns] Owns

`KumiteGameRepository`, `PlayerRepository`.

## [may-call] May call

`models` only. Only `services` calls this package.

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
  TLS on.
- `MONGO_DB` wins over a database named in `MONGO_URI`. A hosted URI usually names none, and without
  this the driver would write to `test`.
- **The database password never reaches a log, the username and hosts do.** The driver prints both
  at INFO when the client starts, with the password hidden. `DatastoreConnectionIT` proves the
  password stays out on a failed connection, rejected credentials and a malformed URI. A
  `proxyPassword` in the URI is the exception: the driver prints it in clear, so never use one.
- **`/actuator/health` shows the username and hosts to anyone while the login or connection fails,**
  with the password hidden, because `show-details` is `always`. #80 closes this, and it must land
  before a hosted database has a public address in front of it.
- The hosts in `MONGO_URI` only pick the first server to ask. The driver then uses the member
  addresses stored in the replica set, so they must be reachable from the application: publishing
  the container on another port needs a matching member address in `rs.initiate`.
- A wrong password starts the application with health DOWN (503), and every request that needs the
  datastore fails with a 500.
- This package is `@NullUnmarked` on purpose. On a `@NullMarked` package, Spring Data adds a runtime
  null check to every repository. Today that only adds an interceptor, because both interfaces are
  empty and `findById` returns `Optional`. It matters once a custom finder exists: one that finds
  nothing would throw `EmptyResultDataAccessException`, which `GlobalExceptionHandler` does not
  handle.

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

**Take the dump with no bout running.** Each collection is read at a different moment, so a dump
taken during scoring can hold a game and a fighter that disagree, and `--oplog`, which would prevent
it, is refused on hosted free clusters. Anything written after the dump is lost on restore.

## [pending] Pending on hosted MongoDB

Proven locally, on a replica set with a login, and not yet on a hosted cluster. Do these when the
first Atlas cluster exists:

- Connect the same build by setting `MONGO_URI` alone, with TLS through `mongodb+srv`.
- Run the two commands in `[backup]` once against it, and restore the result.

## [known-gaps] Known gaps against the ACID rules

The rules are in `context/workflow/design/acid.md`. This code predates them. Each row is known debt,
not a regression: new code meets the rules, and no gap grows.

| Rule | Today | Fixed by |
|---|---|---|
| `[atomicity]` | A point saves on `Player`, then SENSHU or the result saves on `KumiteGame`: two writes | Milestone: Structure and engine |
| `[consistency]` | `addPoint` checks the match, then writes a different document | Milestone: Structure and engine |
| `[idempotency]` | A retried `add-point` scores twice | Milestone: Lifecycle and frontend |
