# controllers

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [contract] Response contract
- [wire-formats] Wire formats, and why
- [mutations] Mutations return the new state
- [validation] Validation at the boundary

## [purpose] Purpose

The inbound gateway. Accepts HTTP, delegates to a service, maps the result, returns. No logic.

## [owns] Owns

- `KumiteGameController` at `/api/v1/kumitegame`: match creation, retrieval, points, fouls, winner
  assignment, disqualification, KIKEN, referee override and clock extension.
- `PlayerController` at `/api/v1/players`: player CRUD.
- `ApiVersionConfiguration`: `PREFIX`, the one place the version is named. It adds the prefix to
  every controller in this package through the framework's path matching, so a controller maps only
  its own resource. Nothing else moves: the actuator and the error path stay where they are.

## [may-call] May call

`services`, `mappers`, `dto`. Never `repositories` or `rules`.

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
