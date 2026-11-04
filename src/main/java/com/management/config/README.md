# config

## Index

- [purpose] Purpose
- [owns] Owns
- [facts] Rule values
- [datastore] Datastore guarantees
- [profiles] Profiles

## [purpose] Purpose

Typed settings, validated at startup.

## [owns] Owns

`GameProperties`: a `@ConfigurationProperties("game")` record that binds every rule value from
`game.*` in `application.properties`.

`DatastoreProperties`, `DatastoreConfiguration` and `DatastoreGuaranteeCheck`: see `[datastore]`.

## [facts] Rule values

Never hardcode these. All come from configuration.

| Setting | Default | Key |
|---|---|---|
| Game duration | 120s, optional 90s and 180s | `game.default-duration`, `game.optional-durations` |
| Winning score | 8, a trigger and never a cap | `game.winning-points` |
| Foul limit | 4, ends the match against the offender | `game.fouls-ending-match` |
| Clock increments | 10s and 30s | `game.clock-increments` |

## [datastore] Datastore guarantees

| Setting | Default | Key |
|---|---|---|
| Longest one operation may take, retries included | 10s | `datastore.operation-timeout` |
| Longest opening one connection or a heartbeat may take | 10s | `datastore.connect-timeout` |
| Longest an operation may wait for a usable server | 5s | `datastore.server-selection-timeout` |
| How long startup waits for an answer | 5s | `datastore.startup-wait` |

`w=majority`, primary reads and retryable writes are fixed in code: they are guarantees, not
tunables. The driver API for the timeout is
marked alpha, and `wtimeoutMS`, the alternative, is deprecated.

There is no socket timeout setting: with an operation timeout set, the driver ignores one. A zero
value for any timeout stops startup: the driver reads it as no limit, or for server selection as
failing at once. The driver derives its heartbeat timeouts from the connect timeout, so lowering it
makes a slow hosted member look lost.

| The datastore | At startup | After startup |
|---|---|---|
| Is a replica set member | Starts | Runs |
| Is a member the client cannot use: wrong set name, or an unreachable member address | Starts, health DOWN, no write lands | The same |
| Is standalone | Refuses, exit 1, no port opened | Context closes, on the first answer only |
| Was never initiated | Refuses, exit 1, no port opened | Runs, and the driver refuses writes |
| Does not answer | Starts after the wait, health DOWN | Checked on the first answer |

A context closed after startup ends the process with exit code 0, not 1. Only the first answer is
checked, so a datastore swapped for a weaker one later goes unnoticed. With `replicaSet` set, the
driver will not use a standalone at all. On a direct connection, a write already waiting for a
server can land in the moment before the context closes.

## [profiles] Profiles

`dev` is the default, and `test` is applied by the shared test base classes. See `README.md` at the
repo root.
