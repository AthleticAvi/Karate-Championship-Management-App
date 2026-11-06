# dto

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [facts] Project facts

## [purpose] Purpose

The shapes of the API: request and response records. They carry values and no logic.

## [owns] Owns

- Requests: `KumiteGameRequest`, `FighterRequest`, `PlayerRequest`, `ClockAdjustmentRequest`,
  `WinnerOverrideRequest`.
- Responses: `KumiteGameResponse`, `PlayerSummary`, `PlayerResponse`.

## [may-call] May call

`enums` only.

## [facts] Project facts

- The response contract and wire formats are in `controllers/README.md`.
- Name records `*Request` and `*Response`. A `DTO` suffix fails Checkstyle, as an abbreviation.
- `@Schema` here feeds the API description and nothing else: see `[description]` in
  `controllers/README.md`.
- `services` imports the request types. This is a frozen layer violation: see `[boundaries]` in
  `README.md` at the package root.
