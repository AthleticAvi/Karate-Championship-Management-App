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
| How long startup waits for an answer | 5s | `datastore.startup-wait` |

`w=majority` is fixed in code: it is a guarantee, not a tunable. The driver API for the timeout is
marked alpha, and `wtimeoutMS`, the alternative, is deprecated.

| The datastore | At startup | After startup |
|---|---|---|
| Is a replica set member | Starts | Runs |
| Is standalone | Refuses, exit 1, no port opened | Context closes, on the first answer only |
| Was never initiated | Refuses, exit 1, no port opened | Runs, and the driver refuses writes |
| Does not answer | Starts after the wait, health DOWN | Checked on the first answer |

A context closed after startup ends the process with exit code 0, not 1.

## [profiles] Profiles

`dev` is the default, and `test` is applied by the shared test base classes. See `README.md` at the
repo root.
