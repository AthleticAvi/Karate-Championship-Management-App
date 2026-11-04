# config

## Index

- [purpose] Purpose
- [owns] Owns
- [facts] Rule values
- [profiles] Profiles

## [purpose] Purpose

Typed settings, validated at startup.

## [owns] Owns

`GameProperties`: a `@ConfigurationProperties("game")` record that binds every rule value from
`game.*` in `application.properties`.

## [facts] Rule values

Never hardcode these. All come from configuration.

| Setting | Default | Key |
|---|---|---|
| Game duration | 120s, optional 90s and 180s | `game.default-duration`, `game.optional-durations` |
| Winning score | 8, a trigger and never a cap | `game.winning-points` |
| Foul limit | 4, ends the match against the offender | `game.fouls-ending-match` |
| Clock increments | 10s and 30s | `game.clock-increments` |

## [profiles] Profiles

`dev` is the default, and `test` is applied by the shared test base classes. See `README.md` at the
repo root.
