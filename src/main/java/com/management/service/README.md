# service

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [facts] Project facts

## [purpose] Purpose

The service steps every named identity shares, such as an athlete or a referee, so a fix to them
is made once.

## [owns] Owns

- `IdentityService`: get, list, `findAll`, `findAllVersioned`, the delete refused while a game
  names it, and the version-checked save. A feature's service extends it with its own create and
  change, and answers its hooks: the mapper, whether a game names it, and its three exceptions.
- `Versioned`: a value with the version it is stored at, read together, for a game's tag.

## [may-call] May call

Shared `data` and `domain`. Never a feature: a feature extends these, never the reverse.

## [facts] Project facts

- **A change is not retried,** and the "nothing to write" check stays in each feature's service,
  because it compares that aggregate's own fields.
- **Whether a game names one** comes through the interface each feature owns, such as
  `AthleteReferences`, which `kumitegame.service.GameReferences` implements.
