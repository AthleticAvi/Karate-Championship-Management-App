# web

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [contract] Response contract
- [access] Who may call what
- [identity] Who did it
- [wire-formats] Wire formats, and why
- [mutations] Mutations return the new state
- [validation] Validation at the boundary
- [cors] Cross-origin calls
- [conditional-reads] Two views of a game, and their tag
- [countdown] Keeping time between changes
- [lists] Lists: pages, filters and order
- [description] API description
- [shapes] Request and response records
- [mappers] Mappers
- [errors] Error contract
- [error-facts] Error facts

## [purpose] Purpose

The inbound gateway. Accepts HTTP, delegates to a service, maps the result, returns. No logic.

This package holds what every feature's API shares: the version prefix, the role table, CORS, the
API description and the error contract. Each feature keeps its controller, its request and response
records and its mapper in its own `web` package: `kumitegame.web`, `athlete.web`, `referee.web`
and `account.web`.

## [owns] Owns

In the feature packages:

- `KumiteGameController` at `/api/v1/kumite-games`: creates and lists games, serves the public view
  and the full view of one, and records each action on one through a sub-resource that reads as a
  thing: `settings`, `scores`, `penalties`, `clock-actions`, `senshu-records`, `assignments`,
  `amendments`, `results`, `status-changes`. See `[conditional-reads]` for the two views and
  `[lists]` for the list.
- `AthleteController` at `/api/v1/athletes`: creates, lists, reads, updates and deletes athletes.
- `RefereeController` at `/api/v1/referees`: creates, lists, reads, renames and deletes referees.
- `AccountController` at `/api/v1/accounts`: creates and removes accounts. Hashes the password here,
  because the encoder is a Spring Security type.

In this package:

- `ApiAccess`: the role table. See `[access]`.
- `ApiVersionConfiguration`: `PREFIX`, the one place the version is named. It adds the prefix to
  every feature controller through the framework's path matching, so a controller maps only its own
  resource. It names one controller per feature, so a new feature's controller is added there.
  Nothing else moves: the actuator, the error path and the API description stay where they are.
- `ApiCorsConfiguration`: which browsers may call the API. See `[cors]`.
- `ApiDescriptionConfiguration`: what the generated description cannot find. See `[description]`.
- `ApiMediaTypeConfiguration`: keeps the API on JSON. See `[description]`.
- `GlobalExceptionHandler`: the inbound gateway for failures. See `[errors]`.

## [may-call] May call

A feature's `web` package calls its own `service`, and the `domain` types its mapper reads and its
records carry. Never `data` or `rules`.

Three narrow additions: `config` for `CorsProperties` and the side colours in
`KumiteGameProperties`, the header name in `filters`, and `domain` enums for the values a request
or response carries. `security` depends on this package, never the
reverse. `config` never depends back on this package, because
services depend on `config`, so that would be a cycle. That is why the CORS wiring sits here.

## [contract] Response contract

`Location` is built from `PREFIX` and stays a relative path: see `[server]` in the config README.
It lives here and not in `config` because it is a fact about HTTP routes, which the controllers need
for `Location`.

A request on an old path, or on any other version, is refused before a controller runs: it has no
row in the role table, so it answers 401, or 403 with credentials.

Persistence types do not cross the HTTP boundary in either direction. `KumiteGameMapper`,
`AthleteMapper` and `RefereeMapper` are the only places a domain type becomes a response, and none
sees a document: each feature keeps its stored form in `data`.

| Response type | Endpoint | Shape |
|---|---|---|
| `KumiteGameResponse` | `GET /api/v1/kumite-games/{gameId}/full-view`, and `current` in a game's 409. `GET /api/v1/kumite-games/{gameId}` sends the public view of it: every field but `recordedBy` and `raisedWarnings` in each `meta`, `warnings`, and every note in free text. See `[conditional-reads]` | `id`, `format`, `medalGame`, `extraGame`, `settings`, `status`, `exchange`, `mainClock`, `aka`, `ao`, `panel`, `assignments`, `clockActions`, `senshuClosed`, `senshuRecords`, `amendments`, `results`, `currentResult`, `suggestedResult`, `statusChanges`, `warnings`, `serverTime`. Each side carries `senshu`, true for the holder, `hansokuCount`, `shikkakuCount` and `disqualified`, and `effectiveScore`. `currentResult` is empty after a reopen of a DECIDED game, and on a VOIDED one. `suggestedResult`, and `suggested` on each result, is empty when the rules point to nothing. A suggested HANTEI is a WIN with an empty `winner`: the officials' vote names it, unlike a recorded WIN |
| `RecordedResponse` | every game mutation | `game`, the `KumiteGameResponse`, and `raisedWarnings` |
| `PagedModel` of `KumiteGameSummaryResponse` | `GET /api/v1/kumite-games` | `content`, the summaries, and `page`. A summary is `id`, `status`, `aka` and `ao`, each `colour` and `athlete`, and `currentResult`: `outcome`, `winner`, `reason`, `bothDisqualified`. See `[lists]` |
| `PagedModel` of `AthleteResponse`, of `RefereeResponse` | `GET /api/v1/athletes`, `GET /api/v1/referees` | `content` and `page`: see `[lists]` |
| `AthleteResponse` | `/api/v1/athletes`, and `current` in an update's 409 | `id`, `name`, `weightKg` |
| `RefereeResponse` | `/api/v1/referees`, and `current` in a rename's 409 | `id`, `name` |
| `AccountResponse` | `POST /api/v1/accounts` | `username`, `role`. Never a password or a hash |

## [access] Who may call what

`ApiAccess` is the one statement of the role table. The filter chain builds its rules from it, and
the description reads it, so the two cannot disagree. It sits here because the description may only
live in the web packages and `security` already depends on it, so the reverse would be a cycle.

| Level | Endpoints under `/api/v1` |
|---|---|
| Public | `GET /athletes`, `GET /athletes/{athleteId}`, `GET /referees`, `GET /referees/{refereeId}`, `GET /kumite-games`, `GET /kumite-games/{gameId}` |
| Scorekeeper and Admin | `POST /kumite-games`, `GET /kumite-games/{gameId}/full-view`, `PUT /kumite-games/{gameId}/settings`, `POST /kumite-games/{gameId}/` `scores`, `penalties`, `clock-actions`, `senshu-records`, `assignments`, `amendments`, `results`, `status-changes` |
| Admin | `POST /athletes`, `PUT /athletes/{athleteId}`, `DELETE /athletes/{athleteId}`, `POST /referees`, `PUT /referees/{refereeId}`, `DELETE /referees/{refereeId}`, `POST /accounts`, `DELETE /accounts/{username}` |

Any Scorekeeper may act on any game: there is no assignment of scorekeepers. `status-changes` admits
both roles because the role a move needs depends on the game's status: reopening an APPROVED game
and voiding are an Admin's, and the game refuses them to a Scorekeeper with a 403. See `[status]`
in `src/main/java/com/management/kumitegame/domain/README.md`. Anyone may read a game's public
view; its full view, with who recorded what, the warnings and the notes, is a Scorekeeper's or an
Admin's.
Anyone may list athletes, referees and games, the last as summaries that hold nothing the public view
leaves out.
Athletes follow the levels `/players` had,
and the referees': listed for the owner to confirm. A new endpoint needs a row here,
and until it has one it is refused to everyone. `SecurityRulesSliceTest` writes the table out and
compares it with the real mappings, so a new endpoint fails the build until someone decides. What
each answer means is in the security README.

## [identity] Who did it

A controller that records who acted takes `java.security.Principal` and passes `getName()` down as a
plain string: every game mutation, which stores it as the entry's `recordedBy`. A status change also
passes the role, as `ActingRole`, from `HttpServletRequest.isUserInRole`: the servlet API, which
Spring Security answers, and which the description leaves out like `Principal`. The role is found
by the account role's name, which `ActingRoleTest` ties, because the game feature may not import the
account's type. No request has room
for it, and a body that still sends one is not refused: the field is not read. `Principal` is the
JDK's type, so the description does not list it as a parameter and no Spring Security type comes
near the signature.

## [wire-formats] Wire formats, and why

A strict whole-body assertion in `KumiteGameControllerSliceTest` pins these. It is the only thing
between this contract and a silent shift under a dependency upgrade.

- **Durations are whole milliseconds, in fields named `...Ms`.** An integer has one reading and
  drives a countdown directly. `remainingSeconds` is gone.
- **Instants are ISO-8601 strings in UTC,** with millisecond precision: `recordedAt` on every
  entry, and `serverTime`, the server's time when it answered.
- **A clock is `running` and `remainingMs`,** the time left at `serverTime`. A screen counts down
  from there, and starts again only from an answer with a new tag: see `[countdown]`.
- **A side is `aka` or `ao`.** Its `colour` comes from configuration, so changing a colour changes
  no code. Rules and storage know only the side.
- **An athlete or referee a game names is `{id, name, known}`.** One deleted in the accepted race
  reads `"name": null, "known": false`, never a 500.
- **Every entry carries `meta`:** `id`, `order`, `recordedAt`, `recordedBy`, `raisedWarnings`. A
  score, penalty or SENSHU record also says whether it was `cancelled`.
- **SENSHU is `senshu` on each side and `senshuClosed` on the game,** derived on every read. Both
  sides are never true.
- **A disqualification is `hansokuCount`, `shikkakuCount` and `disqualified` on each side,** derived
  on every read and never stored, in both views. The counts are the penalties standing after
  CANCELs, and `disqualified` is true while either is above zero, so a screen never replays the
  penalties. They come from penalties only: a result by SHIKKAKU recorded with no penalty entry, or
  a result's `bothDisqualified`, leaves them as they are.
- **Enums serialise as their names**, uppercase, exactly as declared. An absent value is `null`.

## [mutations] Mutations return the new state

Every game mutation answers `RecordedResponse`: the game as it now stands and the warnings the
action raised, so a screen never needs a follow-up read. The game carries its `version` in every
answer, both views and the 409 included: a screen sends it back as `expectedVersion` on setting the
time, a result, a settings change or a status change. See `[writes]` in the service README. A
create answers 201 with a `Location`; every other mutation 200, a retried one included, which
answers the warnings stored with the first.
`PUT /api/v1/athletes/{id}` and `PUT /api/v1/referees/{id}` answer the updated record.
`DELETE /api/v1/athletes/{id}`, `DELETE /api/v1/referees/{id}` and `DELETE /api/v1/accounts/{username}`
answer 204 with no body. `POST /api/v1/accounts` answers 201 with a `Location`, though no `GET`
serves it: nothing reads an account back, and the path is what `DELETE` takes.

## [validation] Validation at the boundary

Request DTOs are records with Jakarta Bean Validation constraints, triggered by `@Valid` on
`@RequestBody` parameters: required fields, sizes, and a duration of zero or less. Rules that span
fields, such as a WIN naming a winner or a medical timer naming its side, are domain validation and
answer 400 as `InvalidGameRequestException`.

**A missing `expectedVersion` answers in two shapes.** A settings change, a result and a status
change require it as a field, so its absence answers with the `errors` map. A clock action needs it
only for SET_TIME, and refuses it on every other action: a rule across fields, so the service holds
it, as the paragraph above says, and it answers with a `detail`. Like a missing field, it is
checked before the game is read, so an unknown game still answers 400 here, not 404.

Failures map to the table in `[errors]`.

## [cors] Cross-origin calls

`cors.allowed-origins` decides who may call: see `[cors]` in the config README. Everything else is
fixed here, because it follows from what the API is, and no deployment tunes it.

| Rule | Value | Why |
|---|---|---|
| Path | `/api/v1/*` only | The actuator and the description answer as if CORS did not exist |
| Methods | `GET`, `POST`, `PUT`, `DELETE` | The ones the API uses. A cross-origin `HEAD` is refused too, though the mapping would serve it |
| Request headers | `Authorization`, `Content-Type`, `If-None-Match`, `X-Correlation-ID` | Credentials, JSON, a game's tag sent back by code, and the ID a caller may send |
| Exposed headers | `X-Correlation-ID`, `Location`, `ETag`, `Date` | A browser hides every other header from code, except a short list that holds `Cache-Control`. A create answers with `Location`, so a client needs it to find what it made. A poller reads a game's tag, and `Date` is the only server time a 304 carries |
| Credentials | off | There are no cookies or sessions. `Authorization` is set by the caller, and this flag is not what allows it |
| Max age | not set | A browser repeats a preflight after about 5 seconds. A poller that lets the browser's cache send the tag needs none: see `[countdown]` |

An origin not on the list gets a 403 before anything runs, on a preflight and on a real request. The
body is the framework's `Invalid CORS request`, with no content type: not a problem detail, and no
browser code can read it. An error answered to an allowed origin carries the CORS headers, so a
client can read the problem detail.

**The server compares `Origin` with its own address,** so a request from that address is not
cross-origin and passes whatever the list says. Behind a TLS proxy with forwarded headers off, the
address it sees is not the public one, so a page on the same public origin counts as cross-origin
and needs listing.

**Why a filter and not the MVC mapping.** It answers ahead of Spring Security, covers answers given
before a handler is chosen such as a 401, and sits inside the correlation filter, so a refusal has
an ID. The filter ends every preflight it sees, even with no configuration for the path, which is
why its servlet mapping is narrower than `/*`.

**For Spring Security:** this filter runs ahead of the chain, so a preflight never reaches
authentication, and an answer from the chain to an allowed origin still carries the CORS headers.
The chain's own CORS filter is turned off: Spring Security adds it by itself on finding exactly one
`UrlBasedCorsConfigurationSource`, and it would refuse a preflight on any path outside the source,
such as the actuator. Keep this the one such bean all the same, and keep the registration named
`apiCorsFilter`: Spring Security looks up a bean named `corsFilter` and expects a plain `CorsFilter`.
A preflight on the actuator is plain `OPTIONS` there, so it gets the 401 of any unlisted method.

## [conditional-reads] Two views of a game, and their tag

| View | Path | Who | Leaves out |
|---|---|---|---|
| Public | `GET /api/v1/kumite-games/{gameId}` | Anyone | `recordedBy` and `raisedWarnings` in every `meta`, `warnings`, and the notes: `reason` on each SENSHU record, amendment and status change, and `reasonDetail` on each result, `currentResult` included |
| Full | `GET /api/v1/kumite-games/{gameId}/full-view` | Scorekeeper and Admin | Nothing |

**Notes are the scorekeeper's.** A note in free text can hold a medical detail or why a game was
voided, and a public answer may sit in a browser or a shared cache, so the public view leaves every
one out. The enums stay, `pauseReason` and a result's `reason` among them: a scoreboard shows them.
A new free-text field in `KumiteGameResponse` needs the same marking.

**Two paths, so a cache never mixes them.** The full view sits under the game as a sub-resource;
the game's own path is the public one.

**One record, two JSON views.** `KumiteGameResponse` serves both. A field marked
`@JsonView(GameViews.Scorekeeper.class)` is left out by the public handler, which names
`GameViews.Public`. A handler that names no view writes every field, so the full view, every
mutation and the game in a 409 need no marking. Jackson 3 leaves out every unmarked field under a
view by default, so `spring.jackson.mapper.default-view-inclusion=true` keeps them: without it the
public view is `{}`, and the strict slice test fails.

**Both reads carry a weak `ETag`.** The handler decides it before the game is folded or the body
built, with the framework's `WebRequest.checkNotModified`; a current `If-None-Match` answers 304
with no body and the tag. It is a digest over:

- the view, so a tag from one view never answers 304 on the other;
- the application's start, because a redeploy can change what a stored game reads as, such as the
  side colours or the suggested result. A restart costs each caller one full answer;
- the game document's version;
- the version of each athlete and referee the game names, by id, from `VersionedGameRead`. A rename
  changes it, and so does one deleted in the accepted race, which drops out.

Each version is read with what it describes, so the tag never pairs an old name with a new version.
The tag is weak because `serverTime`, and a running clock's `remainingMs`, differ between two
answers at the same versions. The 304 carries no `serverTime`: the caller keeps the answer it holds,
with that answer's own time, and `Date` gives the server's time to the second.

**A 304 costs the reads of the versions and nothing more.** `getVersioned` reads the game and the
athletes and referees it names, each with its version, and folds the history only when the handler
asks for the read, which it does after the tag check. Nothing is read again for the fold, so a body
always matches its tag.

**Cache policy.** The public view answers `Cache-Control: no-cache`, on its 200 and its 304: a
browser keeps it and asks with the tag before showing it again. It is the same for every caller, so
a shared cache may keep it too, though only an answer to a request without credentials: a shared
cache stores nothing asked for with `Authorization` unless the answer says `public`. The full view
sets none, so Spring Security's `no-cache, no-store, max-age=0, must-revalidate` stands on its 200,
and nothing keeps who recorded what. Spring Security
writes nothing on a 304, which is why the public view sets its own policy on both answers: a cache
takes a 304's headers in place of those it held. Every other answer, an error on the public path
included, keeps Spring Security's `no-store`.

## [countdown] Keeping time between changes

The server never ticks: a clock is `remainingMs` at `serverTime`, and an unchanged game answers 304.
A screen polling the public view keeps its countdown right this way:

1. **Anchor each new answer once, by its age.** An answer may be older than its arrival: after a
   reload, a new tab or a second screen, the browser hands code the body it kept, which can be
   minutes old. Its age is `Date` minus `serverTime`, never below zero. Anchor it at its arrival
   less that age, on a monotonic clock such as `performance.now()`.
2. **Count down by the time elapsed since the anchor:** show `remainingMs` less that time while the
   clock runs, and `remainingMs` while it is paused. Read the elapsed time on each frame; never
   count timer ticks, which a browser slows in a hidden tab.
3. **An unchanged tag changes nothing.** Keep the anchor. Never anchor that answer again at a later
   arrival, or the countdown jumps back by the time since the first.
4. **A new tag replaces the answer.** Anchor it by rule 1.
5. **`Date` is the server's time on every answer, a 304 included,** to the second, so an age is
   right to within a second. `serverTime` is the same to the millisecond, on a full answer only. A
   proxy in front of the server may send its own `Date`.

**Two ways to poll, the same rule.**

- **Let the browser's cache send the tag:** a plain `fetch` with the default cache mode. The browser
  sends `If-None-Match` itself, takes the 304, and hands code a 200 with the body it kept: the same
  `serverTime` and `remainingMs` as the first time, with the 304's headers, so the same `ETag` and a
  new `Date`. That is why a screen compares the tag before it believes the body. No preflight: a tag
  the browser sends does not count.
- **Send the tag from code:** `If-None-Match` set on the request. The browser then leaves its cache
  out, and code sees the 304 itself, with no body. The header is not one a browser sends freely, so
  each such poll from another origin needs a preflight, repeated about every 5 seconds: see
  `[cors]`.

A one-off check in headless Chrome, from another origin, showed both: three cached polls each read
200 with the first `serverTime` and a later `Date` while the server logged 304s, and a poll with the
tag set by code read 304 with `Date`.

## [lists] Lists: pages, filters and order

| List | Filters | Order |
|---|---|---|
| `GET /api/v1/athletes`, `GET /api/v1/referees` | `name`: any part of it, in any case, matched as written | By name, then id |
| `GET /api/v1/kumite-games` | `status`, repeated for any of several; `athleteId`, on either side now | Newest first, then id |

**One page shape, the framework's.** Each answers Spring Data's `PagedModel`: `content`, and `page`
with `size`, `number`, `totalElements` and `totalPages`. The caller sends `page`, counted from 0, and
`size`. A size past the most allowed is cut to it, not refused: see `[server]` in the config README.
A page past the end is empty, with the totals, however far past: a page that starts beyond what
MongoDB can skip, an `int`, is answered empty without asking it, because the skip would wrap and
answer the first page. A blank `name` or `athleteId` filters nothing, and spaces around either are dropped.

**The order is the server's.** A caller's `sort` is read by the framework and then dropped, so a
page never shifts with what another caller asked, and the description leaves it out. Names sort by
code point, so `anna` comes after `Zoe`.

**A game list carries summaries, not games.** Anyone may list games, so a summary holds no entry:
nothing in it names who recorded what, raises a warning or carries free text, such as a result's
`reasonDetail`. Read the game for the rest, and its full view for the notes.

**A game list filters on what the entries fold to.** The status and the athlete on each side are
never stored, so the service reads and folds every game that may match, then cuts the page: see
`[facts]` in `src/main/java/com/management/kumitegame/data/README.md`. A game whose athlete was
replaced lists under the one on the side now, not the one replaced. A status that does not exist
answers 400.

**A game this build cannot read is left out of every list,** with a warning naming it, such as one a
newer build stored before a rollback. Reading that game alone still fails. A document Spring Data
cannot build at all, such as one with no `version`, fails before the mapper and still answers 500 to
every list.

## [description] API description

Generated from the code by springdoc, at `/v3/api-docs` and `/v3/api-docs.yaml`, under `dev` only:
see `[cors]` in the config README for where settings live. There is no copy in the repo. No UI is
included: the description is what a client needs, and any viewer can open it.

**Off in `prod`.** It would hand anyone a map of every write, and a client team can read it from
`dev`, where it needs no login. It is a property, so no beans are created while it is off. The chain
leaves its paths open in every profile, so turning it on in `prod` makes it public there too.

**The generator cannot see runtime statuses.** On its own it lists no failure at all, and a 200 for
every success. So:

- `@ApiResponse` states the 201 and 204 on the methods that answer them. A new one needs the same.
- `ApiDescriptionConfiguration` adds failures to every operation by rule, as problem details:

| Applies to | Status |
|---|---|
| Every operation | 500 |
| Anything but `GET` and `DELETE` | 400 |
| A path parameter | 404 |
| Every operation | 401 |
| A row at Admin level | 403 |
| A path parameter, on anything but `GET` | 409 |
| A request body | 413, 415 |

`GET /api/v1/kumite-games` states its own 400, for a status that does not exist, and so its 200.
`POST /api/v1/accounts` states its own 409, because a body of its own can collide: the rules give a
409 only to a path parameter. `PUT /api/v1/referees/{refereeId}`, `PUT /api/v1/athletes/{athleteId}`,
and a game's `settings`, `clock-actions` and `results` state their 409 to say that `current` holds the
record as it now stands, in words only: the shared problem schema cannot list it, because a 400 does
not carry it. A game's four absolute actions, `status-changes` included, also say that a stale
`expectedVersion` answers it; athletes and referees take none. Stating any status stops the
generator adding the 200, so that is stated too. The 409 on deleting an athlete or referee comes
from the rules: a game names it.

A game's `status-changes` states its 403 and its 409 too: its row is at Scorekeeper level, and the
game refuses an Admin's move from a Scorekeeper.

Both views of a game state their 200 and 304, each with the `ETag` header, and the 304 with no
content. The public view states its `Cache-Control` on both. The public view's handler names a JSON view, so the generator writes a second schema for
every record it reaches, named after the view: `KumiteGameResponse_Public`, `EntryMetaResponse_Public`
and the rest. Those lack the fields the view leaves out, and `ApiDescriptionIT` compares them with
a real public answer.

`ApiDescriptionIT` pins these rules, and compares 400, 401, 404 and the success codes with real
answers. A 401 is on public reads too, because wrong credentials are refused there. A 403 is on Admin
rows only, because a Scorekeeper row admits both roles an account can hold. Every row but a public
one states the `basicAuth` scheme. The access rows come from `ApiAccess`.

A 409 from a concurrent update cannot be provoked on demand, and a 500 needs a fault, so
those rest on reading `GlobalExceptionHandler`. A 409 from a stale `expectedVersion` can be:
`KumiteGameApiIT` checks its `current` per absolute action. A 405 belongs to no operation, and the
chain answers an unlisted method first anyway, and a CORS refusal is never visible to code, so neither is listed. Nor is a 406: no mapping declares what it produces, so it is
decided while writing the answer, after a write has already happened, and for a malformed `Accept` it
has no body. It is not a failure a client can treat as refused.

The same class marks every field of a response as required, because a response always carries them,
and adds `null` to the values of a nullable enum, which the generator leaves out.

**Not in the description:** values that come from configuration, such as the side colours; the `X-Correlation-ID` a caller may send;
that a text field must not be blank, because `@Size` renders `minLength: 0` and `@Size(min = 1)` would
add a second message to the `errors` body. Required response fields follow references into lists,
maps and nullable references, so nested response records are held to the same rule.

**Documentation annotations stay in the web packages, never in a mapper.** `ArchitectureTest` fails on
any other class using them.

**Jackson 2 is here for this.** springdoc reads types through swagger-core, which needs Jackson 2 and
its YAML format. Spring MVC still uses Jackson 3 for every request and response. The framework adds
a YAML converter on its own once the format is present, in every profile, so `ApiMediaTypeConfiguration`
removes it: left in, a YAML body gets past the JSON size limit. The jar cannot simply be excluded,
because springdoc will not start without it.

## [shapes] Request and response records

Each feature's `web` package holds the shapes of its part of the API: records that carry values and
no logic.

- `kumitegame.web`: `KumiteGameSummaryResponse` with its nested records, a game in a list.
  `KumiteGameRequest` with `PanelMemberRequest`, `SettingsRequest`,
  `ScoreRequest`, `PenaltyRequest`, `ClockActionRequest`, `SenshuRecordRequest`,
  `AssignmentRequest`, `AmendmentRequest`,
  `ResultRequest`, `StatusChangeRequest`, `RecordedResponse`, and `KumiteGameResponse` with its nested records.
- `athlete.web`: `AthleteRequest`, the body of a create and an update, and `AthleteResponse`.
- `referee.web`: `RefereeRequest`, the body of a create and a rename, and `RefereeResponse`.
- `account.web`: `AccountRequest`, `AccountResponse`.

They may use `domain` enums, and `Account.USERNAME_PATTERN`, so the rule for a username has one home.

- Name records `*Request` and `*Response`. A `DTO` suffix fails Checkstyle, as an abbreviation.
- Every entry request carries `id`, made by the client and sent again on a retry.
- `SettingsRequest`, `ResultRequest` and `StatusChangeRequest` require `expectedVersion`;
  `ClockActionRequest` takes it for SET_TIME only. `KumiteGameResponse` carries `version`.
- `@Schema` on them feeds the API description and nothing else: see `[description]`.
- Requests stop at the gateway. No `service` package imports them.
- `AccountRequest` holds a password in clear, so its `toString` leaves it out. A password within the
  character limit can still pass the 72 bytes the hash takes: the encoder refuses it, and the handler
  for an illegal argument answers 400.

## [mappers] Mappers

Each feature's mapper turns domain types into API responses, and requests into domain input. A
mapper reads `domain` types and its feature's records, never a `service`, `data` or `rules` package.

- `KumiteGameMapper`: `GameRead` to `KumiteGameResponse` and to `KumiteGameSummaryResponse`, `Recording` to `RecordedResponse`, each
  request to its `New*` input. Takes the side colours as an argument: it holds no configuration.
- `AthleteMapper`: `Athlete` to `AthleteResponse`. `GlobalExceptionHandler` uses it for `current`.
- `RefereeMapper`: `Referee` to `RefereeResponse`. `GlobalExceptionHandler` uses it for `current`.
- `AccountMapper`: `AccountRequest` and a hash to `NewAccount`, `Account` to `AccountResponse`.

## [errors] Error contract

`GlobalExceptionHandler` is the one place a failure becomes an HTTP response. It extends
`ResponseEntityExceptionHandler`, so framework exceptions map too rather than falling through to the
catch-all as 500s. The types it maps sit in the `domain` package of the feature that throws them,
one exception type per meaning. Those an athlete and a referee share extend one base each in the
shared `domain` package, and one handler answers that base.

Errors are RFC 9457 problem details: `application/problem+json`, with `status`, `title`, `detail`
and `instance`. `type` is not sent, because no problem has a page to point at.

| Failure | Status | Exception | Detail |
|---|---|---|---|
| Request fails Bean Validation | 400 | `MethodArgumentNotValidException` (framework) | generic, plus an `errors` extension property mapping each failing field path to its message |
| A query value that does not convert, such as a status that does not exist | 400 | `TypeMismatchException` (framework) | fixed, naming the parameter and never the value sent |
| A request this app validated and rejected | 400 | `InvalidGameRequestException` | the message |
| Any other illegal argument, incl. `NumberFormatException` | 400 | `IllegalArgumentException` | **generic**, message logged |
| Missing or wrong credentials | 401 | `AuthenticationException` (framework) | fixed, the same for every cause |
| Valid credentials without the role | 403 | `AccessDeniedException` (framework) | fixed |
| A move the game keeps for an Admin, from a Scorekeeper: reopening an APPROVED game, voiding | 403 | `ActionForbiddenException` | the message |
| Body past the JSON size limit | 413 | `HttpMessageNotReadableException` (framework) | generic |
| A username already taken | 409 | `AccountAlreadyExistsException` | the message |
| Removing the last Admin | 409 | `LastAdminException` | the message |
| A write that lost to another through every retry | 409 | `OptimisticLockingFailureException`, `DataIntegrityViolationException` (framework) | generic |
| An athlete update or a referee rename that lost to another write | 409 | `IdentityChangedException`: `AthleteChangedException`, `RefereeChangedException` | fixed, plus a `current` extension property holding the athlete or referee as it now stands. Not retried. A kind with no response shape in the handler answers 500 |
| Setting the time, a result, a settings change, or an approve, reopen or void sent with a stale `expectedVersion`, or that lost to another write | 409 | `GameChangedException` | fixed, plus `current`, the game as it now stands, with its `version`. Not retried |
| An action the data would contradict: an entry id reused for another action, a CANCEL of something missing or cancelled, a settings change after the start, one athlete on both sides, a referee already on the panel, a full panel, an exchange that has not happened | 409 | `ActionRefusedException` | the message |
| Deleting an athlete or referee a game names | 409 | `IdentityInUseException`: `AthleteInUseException`, `RefereeInUseException` | the message |
| No such game | 404 | `GameNotFoundException` | the message |
| No such account | 404 | `AccountNotFoundException` | the message |
| No such athlete or referee | 404 | `IdentityNotFoundException`: `AthleteNotFoundException`, `RefereeNotFoundException` | the message |
| Accounts could not be read while checking credentials | 500 | `AuthenticationServiceException` (framework) | **generic**, message logged |
| Anything else | 500 | catch-all | **generic**, message logged |

## [error-facts] Error facts

- The 413 is told apart by the wording of the Jackson message, because every parser limit raises the
  same type. `RequestLimitsIT` fails if an upgrade changes it. The other limits, such as nesting
  depth, stay 400.
- **Tomcat refuses some requests before Spring runs,** so they are not problem details. Headers past
  the limit answer 400 as `text/html`, and a silent connection answers 408. No part of this
  application sees them, so they carry no correlation ID and leave no request line.
- **A lost write conflict arrives as a data integrity violation,** because the mapping layer sorts
  that code with the duplicate keys. A duplicate key on a create is caught in `AccountService`.
- **A 401 and a 403 start in the security filter chain, outside Spring MVC.** `SecurityFailureResponder`
  hands them to this handler through the framework's exception resolver, so they get the same form,
  `instance` and content type as every other failure. A 401 sends no challenge: see the security
  README.
- **Spring Security's firewall refuses some requests before the chain,** such as a path with a `;`.
  They answer 400 through the error dispatch, with a correlation ID, and are not problem details.
- **A CORS refusal is not a problem detail.** It is the framework's 403, answered before Spring MVC:
  see `[cors]`.
- An athlete or referee that a new game or assignment names but that does not exist answers 400 as
  `InvalidGameRequestException`, not 404: the resource addressed is the game.
- **Only messages this project wrote are returned.** `InvalidGameRequestException` marks wording
  meant for the caller, so its detail is echoed. A bare `IllegalArgumentException` can come from
  anywhere, such as Spring Data's "The given id must not be null", so it answers 400 with a fixed
  detail and the real message in the log.
