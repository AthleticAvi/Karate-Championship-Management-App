# services

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [cross-aggregate] How scoring works across aggregates
- [dependencies] Dependencies are one way
- [concurrency] Concurrency
- [creation] A match is validated before anything is written
- [last-admin] At least one Admin always remains

## [purpose] Purpose

Orchestration. Owns every operation that spans the match and fighter aggregates, and decides what
happens next.

## [owns] Owns

- `KumiteGameService`: match creation, lifecycle, scoring, fouls, disqualification, KIKEN, override
  and clock extension. Resolves colour to fighter id and calls down into `PlayerService`.
- `PlayerService`: a leaf that mutates and saves a `Player`.
- `MatchStateWriter`: applies a write-once change to a match, retrying a losing write.
- `AccountService`: a leaf that reads, creates and deletes accounts. A password reaches it already
  hashed.
- `AccountRemoval`: the one transaction in this application. See `[last-admin]`.

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

## [last-admin] At least one Admin always remains

The only rule here that spans documents: deleting an Admin depends on the others. Accounts are one
document each, so it needs a transaction, the only one in this application.

**A transaction alone does not hold it.** Two Admins deleting each other write different documents,
so a snapshot shows each of them two Admins, nothing conflicts, and none is left. `AccountRemoval`
writes to every Admin first, to a `writeGuard` field nothing reads, so the two collide. The loser
fails with a write conflict and runs again, and then sees the other's deletion.

| Property | Answer |
|---|---|
| Atomicity | One transaction: the guard write and the delete |
| Consistency | `AccountRemoval` counts Admins inside the transaction |
| Isolation | The guard write makes concurrent removals conflict |
| Durability | The commit, at `w=majority` |

`AccountService.delete` retries outside the transaction, on the `TransientTransactionError` label,
because the framework retries nothing and `@Retryable` on the same method would run inside it. The
exception type cannot decide this: see `[facts]` in the exceptions README. The transaction manager
retries a commit with an unknown result, and only the commit, then fails as a 500. Deleting a
Scorekeeper takes the same path with no guard, because no rule depends on one.

`AccountApiIT` runs two Admins deleting each other for several rounds, and needs the manager bean:
without one a transactional method runs with no transaction and gives no warning.
