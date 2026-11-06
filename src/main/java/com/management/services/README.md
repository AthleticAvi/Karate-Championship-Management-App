# services

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [cross-aggregate] How scoring works across aggregates
- [dependencies] Dependencies are one way
- [concurrency] Concurrency
- [creation] A match is validated before anything is written

## [purpose] Purpose

Orchestration. Owns every operation that spans the match and fighter aggregates, and decides what
happens next.

## [owns] Owns

- `KumiteGameService`: match creation, lifecycle, scoring, fouls, disqualification, KIKEN, override
  and clock extension. Resolves colour to fighter id and calls down into `PlayerService`.
- `PlayerService`: a leaf that mutates and saves a `Player`.
- `MatchStateWriter`: applies a write-once change to a match, retrying a losing write.
- `AccountService`: a leaf that reads and creates accounts. A password reaches it already hashed.

## [may-call] May call

`repositories`, `rules`, `models`, `enums`, `config`, `exceptions`. Never `controllers` or `mappers`.
Never `dto`: the gateway maps a request first, and a strict rule enforces it.

## [cross-aggregate] How scoring works across aggregates

Recording a point is exactly one document write. Reads compose the match and the fighters into
`GameWithFighters`, which `KumiteGameMapper` consumes. After every point, foul and disqualification
`KumiteGameService` calls the rules engine, then applies and persists the answer: see
`rules/README.md`.

## [dependencies] Dependencies are one way

`KumiteGameService -> PlayerService`, acyclically. The old mutual dependency, the
`GameHelperService` pass-through and the `@Lazy` workaround are gone. Keep it that way: see
`context/workflow/design/service-interaction.md`.

## [concurrency] Concurrency

Both documents are versioned: see `[versioning]` in `models/README.md`. The scoring mutations in
`PlayerService` are `@Retryable` (Spring Framework core resilience, enabled by
`@EnableResilientMethods` on the application class): re-read, re-apply, bounded attempts. Exhaustion
surfaces as HTTP 409.

`MatchStateWriter` is a separate bean, not a private method, because `@Retryable` is applied by a
proxy and a self-call would bypass it. First scorer and result are write-once facts, so a retry
that finds them already recorded changes nothing.

## [creation] A match is validated before anything is written

Exactly one RED and one BLUE is checked here at creation. A duplicate colour used to persist both
fighters and the half-formed match, then fail in the mapper as a 500 with no id returned. That was
unrecoverable, with no transaction to roll back.
