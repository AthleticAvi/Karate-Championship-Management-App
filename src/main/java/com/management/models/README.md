# models

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [timer] The timer
- [versioning] Versioned documents

## [purpose] Purpose

The domain types, and for now the MongoDB documents too. Holds invariants that must not be
bypassed. `KumiteGame` and `Player` mixing domain and storage is a frozen layer violation: see
`[boundaries]` in `README.md` at the package root.

## [owns] Owns

- Aggregates: `KumiteGame`, `Player`, `Account`.
- Values: `Points`, `Foul`, `Referee`, `RefereeOverride`, `ClockAdjustment`, `MatchOutcome`.
- `GameWithFighters`: the composed read of both aggregates, which `KumiteGameMapper` consumes.
- `NewOverride` carries the operator and the referee apart: the signed-in account that recorded the
  call, and the referee who made it, who may hold no account. `NewClockAdjustment.addedBy` is the
  signed-in account. Both arrive as plain strings.
- `NewMatch`, `NewOverride`, `NewClockAdjustment`, `NewAccount`: business inputs, mapped from
  requests by the gateway.
- `GameTimer`: the match clock.
- `strategies/`: the `PointStrategy` family. `addPoint(Points)` and `removePoint(Points)` mutate the
  score in place. No method returns a value.
- `converters/`: stored-form conversion. See its README.

## [may-call] May call

`enums`, `exceptions`. Never `services`, `repositories`, `controllers`, `dto`.

## [timer] The timer

`GameTimer` is never persisted. `KumiteGame.timer(Clock)` builds a new one on every call from two
persisted values, `remainingTime` **and** `startTime`. Both matter: a timer rebuilt without
`startTime` treats `pause()` as a no-op and silently freezes the clock.

The caller passes the `Clock`, so `models` stays free of Spring and a loaded document needs no
setup. Only `KumiteGameService` and `TimeExpiredCondition` hold the bean. Two rules follow: a change
made to a timer reaches the match only when it is copied back to the fields, and a caller that also
clears `startTime` reads the timer first. The clock clamps at zero, never negative.

`startTime`, `ClockAdjustment.addedAt` and `RefereeOverride.decidedAt` are `Instant`, stored as BSON
dates, so no instance's zone or daylight saving reaches them.

The stored values are the whole clock, so any instance rebuilds it, and one left running through a
restart has counted the whole time. Instances do have to agree on the time: elapsed time is measured
between two wall clocks, so a difference between hosts moves the clock by that much. A reader behind
the writer counts nothing until it catches up, because elapsed time is never negative.

## [versioning] Versioned documents

`KumiteGame` and `Player` carry `@Version private long`, so a stale save raises
`OptimisticLockingFailureException` instead of silently losing an update.

**Documents written before the version field existed cannot be re-saved.** Spring Data reads an
absent version as 0, treats the entity as new, and the next `save` fails with a duplicate key on the
existing `_id`. No backfill is provided, and pre-#49 documents (embedded fighters, no `playerIds`)
are not migrated, because there is no production data. **Drop the dev database** instead.

**Overrides stored before accounts existed are not migrated either.** They hold a `decidedBy` name
that a caller typed, and nothing says who that was. They still read, with `operator` and `referee`
as `null`, and `decidedBy` is dropped the next time that match is saved. There is no production data,
so the same answer applies: drop the dev database if the old name matters. A clock adjustment keeps
its field, `addedBy`, so an old one still holds what a caller typed. `OverrideAuditIT` proves an old
override reads, and that a second override of it keeps what the rules engine decided.

`Account` has no version field, because an account is only ever inserted or deleted, never updated.
Its username is the document id, in a field named `id`, which the mapping stores as the id with no
annotation. That keeps `Account` free of persistence annotations, and so out of the frozen rule.
