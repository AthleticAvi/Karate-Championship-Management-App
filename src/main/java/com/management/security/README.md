# security

## Index

- [purpose] Purpose
- [owns] Owns
- [contract] Contract
- [may-call] May call
- [facts] Project facts

## [purpose] Purpose

The inbound gateway that decides who is calling and whether they may. It authenticates against the
accounts in the datastore and applies the role table. It holds no rule of the sport.

## [owns] Owns

- `SecurityConfiguration`: the filter chain, the password encoder, and the `UserDetailsService` bean.
- `SecurityFailureResponder`: hands a 401 or 403 to `GlobalExceptionHandler`.
- `AccountUserDetailsService`: the adapter between the framework and `AccountService`.
- `FirstAdminSetup`: creates the first Admin from the environment. Every later account comes through
  `POST /api/v1/accounts`. There is no way to change a password: delete and create again.

The role table itself is `ApiAccess` in `web`: see `[access]` in the web README.

## [contract] Contract

HTTP Basic on every request, stateless. No session, no cookie, no CSRF token, no login page, no
logout.

| Caller | Answer |
|---|---|
| No credentials, on a public endpoint | Reaches it |
| No credentials, anywhere else | 401 |
| Wrong credentials, malformed credentials or an unknown account, anywhere | 401, a public read included |
| Valid credentials without the role | 403 |
| Valid credentials on anything the table does not list | 403 |
| Accounts cannot be read, such as in an outage | 500, because nobody knows if the credentials are right |

Both refusals are problem details: see `[errors]` in the web README. The body is the same for every kind of
wrong credential, so it does not say whether an account exists.

**Anything not listed is denied, whoever asks.** The last rule is `denyAll`, so a new endpoint is
inaccessible until it has a row, and even an Admin gets a 403. Unknown paths and unlisted methods
answer this way too, so nobody can map the API by trying paths. `HEAD` and a plain `OPTIONS` are
unlisted: a public read answers `GET` only.

| Path | Who |
|---|---|
| `/actuator/health/liveness`, `/actuator/health/readiness` | Anyone, `GET` only, status alone |
| Everything else the actuator serves | Admin |
| `/v3/api-docs`, `/v3/api-docs.yaml`, under it | Anyone, `GET` only. Only `dev` has anything there, so `prod` answers 404 as it always did |

## [may-call] May call

`account.service`, `account.domain`, `config`, and `web` for `ApiAccess`. Nothing depends on this
package, so it cannot form a cycle. Spring Security types stay in this package, the controllers and
the shared `web`: a strict rule in `ArchitectureTest` fails on any other.

## [facts] Project facts

- **A 401 sends no `WWW-Authenticate: Basic`, on purpose.** This departs from the HTTP rule that a
  401 carries a challenge. The challenge makes a browser show its login prompt, and from then on
  resend the credentials by itself on every request, which is what would make CSRF off unsafe. The
  frontend sends `Authorization` itself. A client that waits for a challenge before sending
  credentials gets none, so it must send them first.
- **The entry point is set on the Basic configurer, and that covers both paths.** It answers wrong
  credentials itself, and it becomes the default for exception handling, which answers none. Set on
  exception handling alone, wrong credentials would still get the challenge.
- **The password hash is made in a gateway,** because `PasswordEncoder` is a Spring Security type.
  Business code gets a plain string, and no hasher interface exists, because it would have one
  implementation.
- **The encoder is the framework's delegating one,** so a stored hash starts with its scheme, such as
  `{bcrypt}`, and the default can move without touching stored accounts.
- **Credentials are checked on every request, with no cache.** A cache would hold a password
  equivalent in memory and keep a deleted account working. The cost was measured on the packaged jar
  on a developer machine under load: a scoring call took 89ms at the median against 13ms for a public
  read.
- **The first Admin waits for `DatastoreAccepted`,** so nothing is written to a datastore about to be
  refused, and a datastore that first answers after startup gets the same check.
- **A datastore that answers but cannot be read skips the check with a warning,** such as one
  refusing the login. Nothing refuses in that case, so a restart is what checks again.
- **`ADMIN_USERNAME` and `ADMIN_PASSWORD` do nothing once an Admin exists.** Changing them later
  changes no password.
- **Spring Security's firewall answers first.** A path with a `;`, an encoded slash or a method that
  is not standard gets a 400 through the error dispatch, which is why that dispatch is permitted. It
  carries a correlation ID and is not a problem detail.
- **In a slice, the rules need importing.** `WebSliceTestBase` imports `SecurityConfiguration`.
  `EndpointRequest` matches nothing there, because a slice has no actuator, so the actuator rules are
  proven in `ActuatorHealthIT`.
