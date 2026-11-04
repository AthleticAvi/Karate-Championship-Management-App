# config

## Index

- [purpose] Purpose
- [owns] Owns
- [facts] Rule values
- [datastore] Datastore guarantees
- [profiles] Profiles
- [server] Server limits

## [purpose] Purpose

Typed settings, validated at startup.

## [owns] Owns

`GameProperties`: a `@ConfigurationProperties("game")` record that binds every rule value from
`game.*` in `application.properties`.

`DatastoreProperties`, `DatastoreConfiguration` and `DatastoreGuaranteeCheck`: see `[datastore]`.

`StartupSettingsCheck`: see `[profiles]`.

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

No profile applies by itself. `mvn spring-boot:run` sets `dev`, a deployment sets `prod`, and the
shared test base classes set `test`, which has no file. See `README.md` at the repo root.

`spring.mongodb.uri` is the one real difference, so it sits in the profile files and not the base:
`dev` defaults `MONGO_URI` to the local replica set, and `prod` has no default.

`StartupSettingsCheck` runs from `KumiteGameStarter.main`, before any bean, so test contexts do not
pass through it.

| Started with | Result |
|---|---|
| No profile | Refuses, exit 1, says what to set |
| `prod`, no `MONGO_URI` | Refuses, exit 1, names `MONGO_URI` |
| `prod`, empty `MONGO_URI` | Refuses, exit 1, the driver reports an invalid connection string |
| A profile with no file, such as a typo | Refuses, exit 1, names `spring.mongodb.uri` |

The check exists because Boot stops neither of the first two: with no profile it activates one named
`default`, and the binder leaves an unresolved `${MONGO_URI}` as text. Reading the key through the
`Environment` resolves it strictly.

## [server] Server limits

All in `application.properties`, the same in every profile. Boot binds them, so there is no record
here.

| Setting | Value | Key | Why |
|---|---|---|---|
| Largest JSON body | 65536 bytes | `spring.jackson.factory.constraints.read.max-document-length` | The largest real body is a few KB. Exact, and holds for chunked bodies |
| Largest request line and headers | 8KB | `server.max-http-request-header-size` | Tomcat's own default, chosen on purpose. Revisit with JWTs |
| Largest form body | 64KB | `server.tomcat.max-http-form-post-size` | No endpoint takes a form. Matches the JSON limit |
| Refused body read and discarded | 2MB | `server.tomcat.max-swallow-size` | Past this the connection closes, and the caller may not see the answer |
| Silence allowed during a request | 20s | `server.tomcat.connection-timeout` | Also ends a stalled body, with a 408 |
| Silence allowed between requests | 60s | `server.tomcat.keep-alive-timeout` | Longer than a proxy is likely to hold an idle connection |
| Shutdown | graceful | `server.shutdown` | Boot's default, stated on purpose |
| Longest wait on shutdown | 20s | `spring.lifecycle.timeout-per-shutdown-phase` | Twice the datastore operation timeout, under a usual 30s kill |
| Form filter | off | `spring.mvc.formcontent.filter.enabled` | It reads a form `PUT` whole, with no limit |
| Multipart | off | `spring.servlet.multipart.enabled` | No endpoint takes an upload |

**Tomcat has no limit on a JSON body.** Its size settings cover forms and headers only. The JSON
parser enforces the limit as it reads, so a body not read as JSON is not covered: the form and
multipart readers stay off for that reason, and a new `String` or `byte[]` `@RequestBody` would
need its own limit.

On shutdown, a request in flight finishes and a stalled one ends with the connection timeout. Both
were seen on the packaged jar, by sending SIGTERM to it in a container.

### Forwarded headers

| Environment variable | Default | Key |
|---|---|---|
| `FORWARD_HEADERS_STRATEGY` | `none` | `server.forward-headers-strategy` |
| `TRUSTED_PROXIES` | empty | `server.tomcat.remoteip.internal-proxies` |

The default ignores forwarded headers. To trust a proxy, set the strategy to `native` and name the
proxy's addresses, as CIDR ranges or a regular expression. With `native` alone nothing is trusted,
where Boot's own default would trust every private address. Never use `framework`: it believes the
headers from any caller.

What the hosting proxy sends is unknown until the hosting check, so the values stay unset.

`Location` is a relative path, so it is right behind a proxy that keeps the path, whatever these
settings are. Absolute URLs built from the request depend on them.
