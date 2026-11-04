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

- Aggregates: `KumiteGame`, `Player`.
- Values: `Points`, `Foul`, `Referee`, `RefereeOverride`, `ClockAdjustment`, `MatchOutcome`.
- `GameWithFighters`: the composed read of both aggregates, which `KumiteGameMapper` consumes.
- `GameTimer`: the match clock.
- `strategies/`: the `PointStrategy` family. `addPoint(Points)` and `removePoint(Points)` mutate the
  score in place. No method returns a value.
- `converters/`: stored-form conversion. See its README.

## [may-call] May call

`enums`, `exceptions`. Never `services`, `repositories`, `controllers`, `dto`.

## [timer] The timer

`GameTimer` is `@Transient`, so it is never persisted. `KumiteGame.getTimer()` rebuilds it on demand
from two persisted values, `remainingTime` **and** `startTime`. Both matter: a timer rebuilt without
`startTime` treats `pause()` as a no-op and silently freezes the clock.

No caller has to initialise the timer. The one rule: read the timer before mutating the fields it
rebuilds from. The clock clamps at zero, never negative.

## [versioning] Versioned documents

Both documents carry `@Version private long`, so a stale save raises
`OptimisticLockingFailureException` instead of silently losing an update.

**Documents written before the version field existed cannot be re-saved.** Spring Data reads an
absent version as 0, treats the entity as new, and the next `save` fails with a duplicate key on the
existing `_id`. No backfill is provided, and pre-#49 documents (embedded fighters, no `playerIds`)
are not migrated, because there is no production data. **Drop the dev database** instead.
