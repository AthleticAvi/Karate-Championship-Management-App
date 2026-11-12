# config

## Index

- [purpose] Purpose
- [owns] Owns
- [facts] Rule values
- [datastore] Datastore guarantees
- [auditing] Created and modified instants
- [profiles] Profiles
- [server] Server limits
- [cors] Allowed origins and the API description
- [accounts] The first Admin
- [actuator] Actuator
- [logging] Logging

## [purpose] Purpose

Typed settings, validated at startup.

## [owns] Owns

`KumiteGameProperties`: a `@ConfigurationProperties("kumite-game")` record that binds every rule
value a new game copies, and the colour each side is shown in. See `[facts]`.

`DatastoreProperties`, `DatastoreConfiguration`, `DatastoreGuaranteeCheck` and `DatastoreAccepted`:
see `[datastore]`.

`StartupSettingsCheck`: see `[profiles]`.

`CorsProperties`: see `[cors]`.

`FirstAdminProperties`: see `[accounts]`.

`AuditingConfiguration`: see `[auditing]`.

`TimeConfiguration`: the one `Clock` bean. It is UTC, and ticks in whole milliseconds because that
is what a BSON date keeps, so a value in memory equals the value a reload returns.

## [facts] Rule values

Never hardcode these. All come from configuration, under `kumite-game.rules`, cited from WKF Kumite
Competition Rules 2026.01. Each is validated at startup: a bad or missing value stops the
application, and `KumiteGamePropertiesTest` proves it.

**A game copies these when it is created and keeps its copy.** Rules read only the copy, so a value
changed and redeployed reaches new games only, and a running or finished game never changes its
rules. A game's copy may change through its settings resource, before it starts only.

| Setting | Default | Key | Article |
|---|---|---|---|
| Duration | 120s; offered 90s, 120s, 180s, and any other positive one | `duration`, `durations-offered` | 5.1, 5.2 |
| Point values | IPPON 3, WAZA_ARI 2, YUKO 1 | `point-values.ippon`, `.waza-ari`, `.yuko` | 8 |
| Lead to win | 8 | `lead-to-win` | 7.7, 12.2.1 |
| Final window | 15s | `final-window` | 12.2.8, 12.2.10 |
| Late-score window | 1.5s after the clock reached zero | `late-score-window` | 8.10 |
| CHUI limit | 3 | `chui-limit` | 10.3 |
| Round-robin forfeit points | 4 | `round-robin-forfeit-points` | 10.7.1 |
| Add-time steps | 10s, 20s, 30s, and any other positive one | `add-time-steps` | |
| Medical time | 3m | `medical-time` | 11.2.3 |
| Time-shown tolerance | 1s | `time-shown-tolerance` | |
| Rulebook version | WKF 2026.01 | `rulebook-version` | |

| Setting | Default | Key |
|---|---|---|
| Colour each side is shown in | AKA red, AO blue (Article 2.2) | `kumite-game.side-colours.aka`, `.ao` |

A map key such as `waza-ari` binds to `WAZA_ARI`: the binder matches an enum name ignoring case,
hyphens and underscores. A duration is a positive `java.time.Duration` such as `120s`, kept in a
game as whole milliseconds.

## [datastore] Datastore guarantees

| Setting | Default | Key |
|---|---|---|
| Longest one operation may take, retries included | 10s | `datastore.operation-timeout` |
| Longest opening one connection or a heartbeat may take | 10s | `datastore.connect-timeout` |
| Longest an operation may wait for a usable server | 5s | `datastore.server-selection-timeout` |
| How long startup waits for an answer | 5s | `datastore.startup-wait` |

`CommitRetryingTransactionManager` and `TransientTransactionRetry` serve the one transaction: see
`[last-admin]` in the service README.

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

A datastore that passes is announced with `DatastoreAccepted`, at startup or at its first answer, so
what needs it runs after the check. A listener that throws `IllegalStateException` stops the
application the same way a refusal does.

A context closed after startup ends the process with exit code 0, not 1. Only the first answer is
checked, so a datastore swapped for a weaker one later goes unnoticed. With `replicaSet` set, the
driver will not use a standalone at all. On a direct connection, a write already waiting for a
server can land in the moment before the context closes.

## [auditing] Created and modified instants

`AuditingConfiguration` turns on Spring Data auditing, and its `DateTimeProvider` reads the `Clock`
bean, so a stored instant is UTC in whole milliseconds and a test that replaces the clock fixes it.
It is a class of its own, not an annotation on `KumiteGameStarter`, so a web slice does not start it.
Only a type with `@CreatedDate` or `@LastModifiedDate` is touched: `KumiteGameDocument`,
`AthleteDocument` and `RefereeDocument`.

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
| `prod` or `dev`, empty `MONGO_URI` | Refuses, exit 1, names `MONGO_URI` |
| A profile with no file, such as a typo | Refuses, exit 1, names the profile |
| `prod`, no Admin stored, no `ADMIN_USERNAME` or `ADMIN_PASSWORD` | Refuses, exit 1, names both: see `[accounts]` |

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
| Silence allowed between requests | 75s | `server.tomcat.keep-alive-timeout` | Above the 60s idle timeout common in proxies, so a proxy never reuses a connection closing here |
| Shutdown | graceful | `server.shutdown` | Boot's default, stated on purpose |
| Longest wait on shutdown | 20s | `spring.lifecycle.timeout-per-shutdown-phase` | Room for a normal request and a slow datastore operation, under a usual 30s kill. A request retrying through an outage can outlast it |
| Form filter | off | `spring.mvc.formcontent.filter.enabled` | It reads a form `PUT` whole, with no limit |
| Multipart | off | `spring.servlet.multipart.enabled` | No endpoint takes an upload |
| Items in a list page, unless asked | 20 | `spring.data.web.pageable.default-page-size` | Boot's default, stated on purpose |
| Most items in a list page | 100 | `spring.data.web.pageable.max-page-size` | A larger size asked for is cut to this, not refused. Boot's default is 2000 |

**Tomcat has no limit on a JSON body.** Its size settings cover forms and headers only. The JSON
parser enforces the limit as it reads, so a body not read as JSON is not covered: the form and
multipart readers stay off for that reason, and a new `String` or `byte[]` `@RequestBody` would
need its own limit. For the same reason the YAML converter is removed: see `[description]` in the
web README.

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

## [cors] Allowed origins and the API description

| Setting | Default | Key | Differs |
|---|---|---|---|
| Origins that may call the API | none | `cors.allowed-origins`, from `CORS_ALLOWED_ORIGINS` | `dev` allows `http://localhost:5173` |
| Serve the API description | off | `springdoc.api-docs.enabled` | `dev` turns it on |

`prod` allows no origin until a real one exists: set `CORS_ALLOWED_ORIGINS` on the deployment, comma
separated. Each value is one exact origin, as a browser sends it. A wildcard, a path, a trailing slash
or a scheme's default port stops startup.

The rest of the CORS rules and the reasons for the description settings are in `[cors]` and
`[description]` in the web README.

## [accounts] The first Admin

| Environment variable | Default | Key |
|---|---|---|
| `ADMIN_USERNAME` | none | `accounts.first-admin.username` |
| `ADMIN_PASSWORD` | none | `accounts.first-admin.password` |
| None | `true`, and `false` in `dev` | `accounts.first-admin.required` |

Both are used only when no Admin exists, and neither has a default in any profile. Blank means not
set: the placeholders give an empty value on purpose, because a placeholder with no default reaches
the record as its own text, which would become a password.

| No Admin stored, and | Result |
|---|---|
| Both set | Creates the Admin and starts |
| Either missing, `required` true | Refuses, exit 1, names both |
| Either missing, `required` false | Starts with a warning. Nobody can sign in |
| The username held by a Scorekeeper | Refuses, exit 1, names `ADMIN_USERNAME` |
| A username with anything but letters, digits, `.`, `_` or `-` | Refuses, naming the key. A colon could never sign in through Basic |
| A password too long to hash | Refuses, naming `ADMIN_PASSWORD`. The hash takes 72 bytes |

**Keep `${` out of `ADMIN_PASSWORD`.** The framework resolves placeholders inside a value, so
`${x:y}` in a password becomes `y` with no error, and that is what gets hashed. Environment values
are read this way however they are bound.

The test profile has no file, so it gets `required` true and its values from the tests, which make up
a password per run. `FirstAdminProperties.toString` leaves the password out, and the password carries
no constraint, because a failed constraint prints its value.

## [actuator] Actuator

| Setting | Value | Key |
|---|---|---|
| Exposed over HTTP | `health`, `info`. Confirmed: nothing else is needed | `management.endpoints.web.exposure.include` |
| Health detail | Admin only | `management.endpoint.health.show-details`, `management.endpoint.health.roles` |
| Readiness | Also needs MongoDB | `management.endpoint.health.group.readiness.include` |

Who may read what is in the security README. Boot already serves the liveness and readiness groups,
so only the readiness contents are set here. Readiness includes MongoDB because every request needs
it, and liveness does not, because an outage must not get the process restarted.

**Actuator stays on the main port.** Decided on purpose: the two public probes need no second port,
everything else needs an Admin, and a platform that polls one port needs nothing extra.

**In an outage, detail is out of reach.** Reading `/actuator/health` needs an Admin login and a login
needs the datastore, so it answers 500 then. A public readiness probe still answers 503, with the
status alone.

## [logging] Logging

| Profile | Console output | Key |
|---|---|---|
| `prod` | Elastic Common Schema JSON, one object per line | `logging.structured.format.console` |
| `dev`, `test` | Boot's text format | `logging.pattern.correlation` adds the ID and fields |

The MDC and the `addKeyValue` fields become top-level JSON fields with no code: `correlationId`,
`method`, `route`, `status` and `durationMs`. ECS was chosen because Boot documents it first and the
hosted log service is not chosen yet. Changing it is this one property.

Logging is global to the JVM, so a test that boots under `prod` turns the console to JSON for
whatever runs after it. Assert on log events, not console text, except in the test about the format.
