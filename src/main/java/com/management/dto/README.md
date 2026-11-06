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
- `@Schema` here feeds the API description and nothing else: see `[description]` in
  `controllers/README.md`.
- Requests stop at the gateway. `services` never imports this package.
