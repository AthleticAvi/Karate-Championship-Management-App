# dto

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [facts] Project facts

## [purpose] Purpose

The shapes of the API: request and response records. They carry values and no logic.

## [owns] Owns

- Requests: `KumiteGameRequest`, `MatchFighterRequest`, `PlayerRequest`, `ClockAdjustmentRequest`,
  `WinnerOverrideRequest`, `AccountRequest`.
- Responses: `KumiteGameResponse`, `PlayerSummary`, `PlayerResponse`, `AccountResponse`.

## [may-call] May call

`enums`, and `Account.USERNAME_PATTERN` in `models`, so the rule for a username has one home.

## [facts] Project facts

- The response contract and wire formats are in `controllers/README.md`.
- Name records `*Request` and `*Response`. A `DTO` suffix fails Checkstyle, as an abbreviation.
- `@Schema` here feeds the API description and nothing else: see `[description]` in
  `controllers/README.md`.
- Requests stop at the gateway. `services` never imports this package.
- `AccountRequest` holds a password in clear, so its `toString` leaves it out. A password within the
  character limit can still pass the 72 bytes the hash takes: the encoder refuses it, and the handler
  for an illegal argument answers 400.
