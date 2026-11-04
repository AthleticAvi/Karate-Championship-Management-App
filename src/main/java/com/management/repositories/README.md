# repositories

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [facts] Project facts
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
- `w=majority` and the operation timeout are set in `config`, not in a connection string, so no
  environment can weaken them. The application refuses a reachable standalone: see the config
  README.
- **Tests connect directly, local connects by discovery.** The Testcontainers URL names no
  `replicaSet`, so tests never exercise member discovery. Local does, through
  `MONGO_REPLICA_SET`. Both give the same transaction and write concern behaviour.
- **`mongo:8.0` refuses to start on Linux kernel 6.19 and newer** (SERVER-121912, a startup crash).
  CI is pinned to `ubuntu-22.04` for this, and a Docker host that moves to such a kernel hits it too.
- A one-member replica set has no failover, so neither local nor tests exercise one.
- Connection settings bind through **`spring.mongodb.*`**, not `spring.data.mongodb.*`. Boot 4 split
  that namespace into driver level and repository level. Both resolve, so the wrong one fails
  silently by falling back to the default host.
- Environment variables: `MONGO_HOST`, `MONGO_PORT`, `MONGO_DB`, `MONGO_REPLICA_SET`.
- `MONGO_HOST` and `MONGO_PORT` only pick the first server to ask. The driver then uses the member
  addresses stored in the replica set, so they must be reachable from the application: publishing
  the container on another port needs a matching member address in `rs.initiate`.
- This package is `@NullUnmarked` on purpose. On a `@NullMarked` package, Spring Data adds a runtime
  null check to every repository. Today that only adds an interceptor, because both interfaces are
  empty and `findById` returns `Optional`. It matters once a custom finder exists: one that finds
  nothing would throw `EmptyResultDataAccessException`, which `GlobalExceptionHandler` does not
  handle.

## [known-gaps] Known gaps against the ACID rules

The rules are in `context/workflow/design/acid.md`. This code predates them. Each row is known debt,
not a regression: new code meets the rules, and no gap grows.

| Rule | Today | Fixed by |
|---|---|---|
| `[atomicity]` | A point saves on `Player`, then SENSHU or the result saves on `KumiteGame`: two writes | Milestone: Structure and engine |
| `[consistency]` | `addPoint` checks the match, then writes a different document | Milestone: Structure and engine |
| `[idempotency]` | A retried `add-point` scores twice | Milestone: Lifecycle and frontend |
