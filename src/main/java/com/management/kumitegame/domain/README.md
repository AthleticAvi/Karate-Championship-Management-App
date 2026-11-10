# domain

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [timer] The timer
- [versioning] Versioned documents
- [vocabulary] Vocabulary
- [transitions] Valid state transitions
- [deviations] Known vocabulary deviations

## [purpose] Purpose

The domain types and vocabulary of every feature, and for now the MongoDB documents too. Holds
invariants that must not be bypassed. This README covers the `domain` package of each feature.
`KumiteGame` and `Player` mixing domain and storage is a frozen layer violation: see `[boundaries]`
in `src/main/java/com/management/README.md`.

## [owns] Owns

- Aggregates: `KumiteGame` in `kumitegame.domain`, `Player` in `athlete.domain`, `Referee` in
  `referee.domain`, `Account` in `account.domain`. `Referee` is the one with no stored form in it:
  see `src/main/java/com/management/referee/README.md`.
- Values: `Points` and `Foul` in `athlete.domain`. `Referee` (a name embedded in a match, not the
  aggregate), `RefereeOverride`, `ClockAdjustment` and `MatchOutcome` in `kumitegame.domain`.
- `GameWithFighters`: the composed read of both aggregates, which `KumiteGameMapper` consumes.
- `NewOverride` carries the operator and the referee apart: the signed-in account that recorded the
  call, and the referee who made it, who may hold no account. `NewClockAdjustment.addedBy` is the
  signed-in account. Both arrive as plain strings.
- `NewMatch`, `NewOverride`, `NewClockAdjustment`, `NewAccount`: business inputs, mapped from
  requests by the gateway.
- `GameTimer`: the match clock.
- The vocabulary: see `[vocabulary]`.
- The exception types each feature throws. `GlobalExceptionHandler` maps them: see `[errors]` in
  `src/main/java/com/management/web/README.md`.
- `athlete.domain.strategies`: the `PointStrategy` family. `addPoint(Points)` and
  `removePoint(Points)` mutate the score in place. No method returns a value.
- Stored-form conversion is data layer code: see
  `src/main/java/com/management/kumitegame/data/converters/README.md`.

## [may-call] May call

Other `domain` types: `kumitegame.domain` uses `athlete.domain`. Never a `service`, `data`, `rules`
or `web` package. Strict rules hold this for `service` and `web`; `data` and `rules` are held only by
the cycle rule. The one known edge into `data`: `KumiteGame` names its stored-winner converter in its
mapping, part of the frozen persistence mapping, and the cycle rule ignores it by name.

## [timer] The timer

`GameTimer` is never persisted. `KumiteGame.timer(Clock)` builds a new one on every call from two
persisted values, `remainingTime` **and** `startTime`. Both matter: a timer rebuilt without
`startTime` treats `pause()` as a no-op and silently freezes the clock.

The caller passes the `Clock`, so the domain stays free of Spring and a loaded document needs no
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

**The type hint follows the package.** Spring Data stores each document's fully qualified class name
in `_class`, so moving a document class changes it on every later write, and a collection then holds
both names. A document with a name that no longer loads still reads: Spring Data falls back to the
declared type. Anything that relies on `_class`, such as a type restriction on a query, misses the old
ones. Documents written before the move into feature packages keep the old names.

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

## [vocabulary] Vocabulary

The domain vocabulary. WKF Kumite terms are authoritative. Colours, game states and end reasons sit
in `kumitegame.domain`, scoring and foul stages in `athlete.domain`, and roles in `account.domain`.

| Concept | Values |
|---|---|
| Player colours | AKA = RED, AO = BLUE. Exactly one of each per game |
| Scoring | IPPON = 3, WAZA-ARI = 2, YUKO = 1 |
| Game states | QUEUED, RUNNING, PAUSED, FINISHED |
| Foul stages | CHUI1, CHUI2, CHUI3, HANSOKU_CHUI, HANSOKU, SHIKKAKU, derived from the count |
| Match end reasons | POINTS_THRESHOLD, FOUL_LIMIT, TIME_EXPIRED, DISQUALIFICATION, KIKEN, REFEREE_OVERRIDE |
| Account roles | SCOREKEEPER, ADMIN |

`PointsType` carries its `PointStrategy` instance.

## [transitions] Valid state transitions

Anything not in this table is illegal.

| From | To |
|---|---|
| QUEUED | RUNNING |
| RUNNING | PAUSED, FINISHED |
| PAUSED | RUNNING, FINISHED |

The table lives as data on `GameState` (`canTransitionTo`). Each lifecycle method in
`KumiteGameService` names its allowed starting states (start: QUEUED; pause: RUNNING; resume:
PAUSED; end: RUNNING or PAUSED). The verbs are narrower than the state machine, since only a paused
match can be *resumed*. An illegal call surfaces as HTTP 409 via `IllegalStateTransitionException`.

## [deviations] Known vocabulary deviations

- `PointsType.WAZARI` is missing the hyphen of the WKF's **WAZA-ARI**. It is public API surface:
  clients send `pointType=WAZARI`. It is the last spelling out of step with the rulebook.
- `PlayerColor` has only `RED` and `BLUE`. The AKA and AO names appear nowhere in code.
- `FoulTypes` is derived from the count (`FoulTypes.forCount`). With the default limit of 4 the
  match ends at `HANSOKU_CHUI`, so `HANSOKU`'s point award and `SHIKKAKU`'s disqualification are
  only reached if the limit is raised. "CHUI up to 3, then HANSOKU CHUI" and "4 fouls ends the
  match" do not both fit one progression. Both are configuration rather than picked between in
  code. **Worth an explicit decision.**
