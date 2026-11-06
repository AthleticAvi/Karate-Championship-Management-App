# dto

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [facts] Project facts

## [purpose] Purpose

The shapes of the API: request and response records. They carry values and no logic.

## [owns] Owns

- Requests: `KumiteGameRequestDTO`, `PlayerDTO`, `PlayerRequestDTO`, `ClockAdjustmentRequest`,
  `WinnerOverrideRequest`.
- Responses: `KumiteGameResponse`, `PlayerSummary`, `PlayerResponse`.

## [may-call] May call

`enums` only.

## [facts] Project facts

- The response contract and wire formats are in `controllers/README.md`.
- The `DTO` suffix counts as an abbreviation under the vendored Checkstyle ruleset. The remaining
  request types are part of the published ratchet baseline.
- `@Schema` here feeds the API description and nothing else: see `[description]` in
  `controllers/README.md`.
- `services` imports the request types. This is a frozen layer violation: see `[boundaries]` in
  `README.md` at the package root.
