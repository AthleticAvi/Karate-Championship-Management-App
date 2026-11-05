# controllers

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [contract] Response contract
- [wire-formats] Wire formats, and why
- [mutations] Mutations return the new state
- [validation] Validation at the boundary
- [cors] Cross-origin calls
- [description] API description

## [purpose] Purpose

The inbound gateway. Accepts HTTP, delegates to a service, maps the result, returns. No logic.

## [owns] Owns

- `KumiteGameController` at `/api/v1/kumitegame`: match creation, retrieval, points, fouls, winner
  assignment, disqualification, KIKEN, referee override and clock extension.
- `PlayerController` at `/api/v1/players`: player CRUD.
- `ApiVersionConfiguration`: `PREFIX`, the one place the version is named. It adds the prefix to
  every controller in this package through the framework's path matching, so a controller maps only
  its own resource. Nothing else moves: the actuator, the error path and the API description stay
  where they are.
- `ApiCorsConfiguration`: which browsers may call the API. See `[cors]`.
- `ApiDescriptionConfiguration`: what the generated description cannot find. See `[description]`.
- `ApiMediaTypeConfiguration`: keeps the API on JSON. See `[description]`.

## [may-call] May call

`services`, `mappers`, `dto`. Never `repositories` or `rules`.

Three narrow additions: `config` for `CorsProperties`, the header name in `filters`, and `enums` for
the values a documented parameter takes. `config` never depends back on this package, because
services depend on `config`, so that would be a cycle. That is why the CORS wiring sits here.

## [contract] Response contract

`Location` is built from `PREFIX` and stays a relative path: see `[server]` in the config README.
It lives here and not in `config` because it is a fact about HTTP routes, which the controllers need
for `Location`.

A request on an old path, or on any other version, answers 404 before a controller runs.

Persistence types do not cross the HTTP boundary in either direction. `KumiteGameMapper` and
`PlayerMapper` are the only places a document becomes a response.

| Response type | Endpoint | Shape |
|---|---|---|
| `KumiteGameResponse` | everything under `/api/v1/kumitegame` | `id`, `gameState`, `remainingSeconds`, `red`, `blue`, `referees`, `winner`, `endReason`, `overridden` |
| `PlayerSummary` | nested as `red` / `blue` | `id`, `name`, `points`, `fouls` |
| `PlayerResponse` | `/api/v1/players` | `id`, `name`, `points`, `fouls` |

## [wire-formats] Wire formats, and why

A strict whole-body assertion in `KumiteGameControllerSliceTest` pins each of these. It is the only
thing between this contract and a silent shift under a dependency upgrade.

- **The clock is `remainingSeconds`, a whole number.** Not a `Duration`, which Jackson can render as
  `PT1M27S` or a decimal depending on configuration nobody here has set. An integer has one reading
  and drives a countdown directly.
- **No timestamp is exposed.** `startTime` is internal bookkeeping. If a timestamp is ever needed,
  it goes out as an ISO-8601 UTC string: decided, so the question is not reopened.
- **`winner` is a `PlayerColor` or `null`.** Never a sentence or placeholder. The client renders
  the wording.
- **Points and fouls are bare integers.** The `Points` and `Foul` wrappers exist so strategies have
  something to mutate: an implementation detail.
- **Referees are names.** A `List<String>`, until a referee has more than a name.
- **Enums serialise as their names**, uppercase, exactly as declared.

## [mutations] Mutations return the new state

Scoring, disqualification, KIKEN, override and add-time endpoints answer with the updated
`KumiteGameResponse`, so a scoreboard never needs a follow-up read, including when the mutation
finished the match. `DELETE /api/v1/players/{id}` answers 204 with no body.

## [validation] Validation at the boundary

Request DTOs are records with Jakarta Bean Validation constraints, triggered by `@Valid` on
`@RequestBody` parameters. `gameDuration` is an `Integer` of seconds, so non-numeric input dies at
binding as a 400. The one-RED-one-BLUE rule is domain validation: see `services/README.md`.

Failures map to the table in `exceptions/README.md`.

## [cors] Cross-origin calls

`cors.allowed-origins` decides who may call: see `[cors]` in the config README. Everything else is
fixed here, because it follows from what the API is, and no deployment tunes it.

| Rule | Value | Why |
|---|---|---|
| Path | `/api/v1/*` only | The actuator and the description answer as if CORS did not exist |
| Methods | `GET`, `POST`, `PUT`, `DELETE` | The ones the API uses. A cross-origin `HEAD` is refused too, though the mapping would serve it |
| Request headers | `Authorization`, `Content-Type`, `X-Correlation-ID` | Credentials, JSON, and the ID a caller may send |
| Exposed headers | `X-Correlation-ID`, `Location` | A browser hides every other header from code. A create answers with `Location`, so a client needs it to find what it made |
| Credentials | off | There are no cookies or sessions. `Authorization` is set by the caller, and this flag is not what allows it |
| Max age | not set | A browser repeats a preflight after about 5 seconds |

An origin not on the list gets a 403 before anything runs, on a preflight and on a real request. The
body is the framework's `Invalid CORS request`, with no content type: not a problem detail, and no
browser code can read it. An error answered to an allowed origin carries the CORS headers, so a
client can read the problem detail.

**The server compares `Origin` with its own address,** so a request from that address is not
cross-origin and passes whatever the list says. Behind a TLS proxy with forwarded headers off, the
address it sees is not the public one, so a page on the same public origin counts as cross-origin
and needs listing.

**Why a filter and not the MVC mapping.** It answers ahead of Spring Security, covers answers given
before a handler is chosen such as a 405, and sits inside the correlation filter, so a refusal has
an ID. The filter ends every preflight it sees, even with no configuration for the path, which is
why its servlet mapping is narrower than `/*`.

**For Spring Security:** `http.cors()` configures itself only from a
`UrlBasedCorsConfigurationSource`, and this is the one bean of that kind. A second
`CorsConfigurationSource` turns that off. Spring Security also looks up a bean named `corsFilter`
and expects a plain `CorsFilter`, so the registration here is `apiCorsFilter`. Its order is pinned
ahead of the security chain's -100, so a preflight never reaches authentication.

## [description] API description

Generated from the code by springdoc, at `/v3/api-docs` and `/v3/api-docs.yaml`, under `dev` only:
see `[cors]` in the config README for where settings live. There is no copy in the repo. No UI is
included: the description is what a client needs, and any viewer can open it.

**Off in `prod`.** With no authentication yet, it would hand anyone a map of every write, and a
client team can read it from `dev`. Turn it on when the writes are protected, if a client then
needs it there. It is a property, so no beans are created while it is off.

**The generator cannot see runtime statuses.** On its own it lists no failure at all, and a 200 for
every success. So:

- `@ApiResponse` states the 201 and 204 on the methods that answer them. A new one needs the same.
- `ApiDescriptionConfiguration` adds failures to every operation by rule, as problem details:

| Applies to | Status |
|---|---|
| Every operation | 500 |
| Anything but `GET` and `DELETE` | 400 |
| A path parameter | 404 |
| A path parameter, on anything but `GET` | 409 |
| A request body | 413, 415 |

`ApiDescriptionIT` pins these rules, and compares 400, 404 and the success codes with real answers.
A 409 on `DELETE` or `update-winner` only comes from a concurrent update, and a 500 needs a fault, so
those rest on reading `GlobalExceptionHandler`. A 405 belongs to no operation and a CORS refusal is never
visible to code, so neither is listed. Nor is a 406: no mapping declares what it produces, so it is
decided while writing the answer, after a write has already happened, and for a malformed `Accept` it
has no body. It is not a failure a client can treat as refused.

The same class marks every field of a response as required, because a response always carries them,
and adds `null` to the values of a nullable enum, which the generator leaves out.

**Not in the description:** values that come from configuration, such as the allowed durations; the
exact fighter count, which `@Size` on a map does not reach; the `X-Correlation-ID` a caller may send;
that a text field must not be blank, because `@Size` renders `minLength: 0` and `@Size(min = 1)` would
add a second message to the `errors` body. A colour lists `RED` and `BLUE`, though lower case is also
accepted. Required response fields follow direct references, so a list of a response type would need
that walk extended.

**Documentation annotations stay in `controllers` and `dto`.** `ArchitectureTest` fails on any other
package using them.

**Jackson 2 is here for this.** springdoc reads types through swagger-core, which needs Jackson 2 and
its YAML format. Spring MVC still uses Jackson 3 for every request and response. The framework adds
a YAML converter on its own once the format is present, in every profile, so `ApiMediaTypeConfiguration`
removes it: left in, a YAML body gets past the JSON size limit. The jar cannot simply be excluded,
because springdoc will not start without it.
