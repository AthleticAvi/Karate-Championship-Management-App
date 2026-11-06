# mappers

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [facts] Project facts

## [purpose] Purpose

Turns domain types into API responses, and a create request into domain input. The only place a
document becomes a response.

## [owns] Owns

- `KumiteGameMapper`: consumes `GameWithFighters`, produces `KumiteGameResponse`. Also turns
  `KumiteGameRequest` into `NewMatch`.
- `PlayerMapper`: `Player` to `PlayerResponse`.

## [may-call] May call

`models`, `dto`, `enums`. Never `services`, `repositories` or `rules`.

## [facts] Project facts

`KumiteGameMapper` keeps its own one-RED-one-BLUE check. `KumiteGameService` guards what enters the
database, and the mapper guards what leaves it.
