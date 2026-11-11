# domain

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [model] The game: entries, everything else derived
- [vocabulary] Vocabulary
- [status] Status and what each status accepts
- [clock] The clock
- [warnings] Warnings
- [retries] Entry ids and retries
- [stored] Stored data
- [source] Where the rules come from
- [deviations] Known deviations

## [purpose] Purpose

The domain types and vocabulary of every feature, and the rules of a Kumite game. Holds invariants
that must not be bypassed. This README covers the `domain` package of each feature. No domain type
is a stored document: see `[pattern]` in `src/main/java/com/management/referee/README.md`.

Officials decide, the system records (WKF Kumite Competition Rules 2026.01, Articles 15.3, 15.6). An
action is **refused** only when the data would contradict itself. A rule breach the system can see
is **applied with a warning**. A judgement call is **recorded as given**. Only the scorekeeper ends
a game, by recording a result.

## [owns] Owns

- Aggregates: `KumiteGame` in `kumitegame.domain`, `Athlete` in `athlete.domain`, `Referee` in
  `referee.domain`, `Account` in `account.domain`.
- `kumitegame.domain`:
  - The entries `Score`, `Penalty`, `Assignment`, `ClockAction`, `Amendment`, `Result`, each with an
    `EntryMeta`, composed rather than inherited. `Settings`, the rule values a game runs under.
  - Business inputs `NewGame`, `NewSettings`, `NewScore`, `NewPenalty`, `NewClockAction`,
    `NewAssignment`, `NewAmendment`, `NewResult`: what the client sent, mapped by the gateway.
  - Derived values `GameState`, `SideState`, `ClockState`, `GameWarning`, folded by `GameFold`.
  - The rules: `EntryKind`, the status matrix, and one class per warning, `ClockTolerance`,
    `LateScore`, `PenaltyLadder` and `FormatCombination`, each reading the settings snapshot.
  - `GameRead`, the game with the athletes and referees it names, and `Recording`, what a mutation
    answers.
- The exception types each feature throws. `GlobalExceptionHandler` maps them: see `[errors]` in
  `src/main/java/com/management/web/README.md`.

## [may-call] May call

Other `domain` types: `kumitegame.domain` uses `athlete.domain` and `referee.domain`. Never a
`service`, `data` or `web` package, and no Spring Data type: strict rules in `ArchitectureTest`.

## [model] The game: entries, everything else derived

A game stores only what happened: `format`, `medalGame`, `extraGame`, `settings`, each side's
`scores` and `penalties`, `assignments`, `clockActions`, `amendments` and `results`. Every entry
carries `meta`: the client's `id`, an `order` counted across the whole game, `recordedAt`,
`recordedBy` and the `raisedWarnings`. Read by `order`, the lists tell the game.

**Everything else is derived** by `GameFold`, folding every list in order: the status, the main
clock and each side's medical timer, the exchange counter, the athlete on each side, the panel, the
totals, the IPPON, WAZA_ARI and CHUI counts, and the current result. Nothing derived is stored, so
it can never disagree with the entries.

**The fold never throws.** Validity is checked when an entry is appended. Stored history always
loads: an entry this build cannot fold as written, such as a second CANCEL of one target or a reused
id, is skipped and reported as `HISTORY_INCONSISTENT` in `GameState.warnings`.

- **Athletes and panel are assignment entries.** The game is created with an athlete on each side
  and an optional panel, of any size. Changes after the start are recorded with a warning.
- **Points come from the settings map,** never from an entry or the code.
- **The exchange** is the number of main-clock starts. A score or penalty takes the current one,
  or an earlier one the client names, as a video review may. Empty before the start.
- **A penalty's level is recorded as given,** never derived from a count (Article 10.3). In
  elimination it gives the opponent no points.
- **Amendments.** CANCEL takes a score or penalty back: it stays on record, marked cancelled, and
  leaves the totals and counts. ACKNOWLEDGE records that the scorekeeper saw an entry's warnings.
  Refused: a CANCEL of anything but a score or penalty, a second CANCEL of one target, a target
  that does not exist. To undo a mistaken CANCEL, record the entry again.
- **Results are recorded as given.** Only a WIN names a winner. A later result supersedes the
  current one, which stays in `results`.
- **The panel applies as given.** Adding a referee already on it changes the role, and removing
  one not on it changes nothing. One athlete on both sides is refused.

## [vocabulary] Vocabulary

WKF Kumite terms are authoritative. `RED`, `BLUE`, Player, Foul, Points, Match and Bout are not used.

| Concept | Values |
|---|---|
| Side | AKA, AO. Each side's colour is configuration, mapped at the gateway (Article 2.2) |
| Format | ELIMINATION, ROUND_ROBIN, plus `medalGame` and `extraGame` |
| Score level | IPPON, WAZA_ARI, YUKO. The rulebook mostly writes WAZA ARI |
| Score source | JUDGES, VIDEO_REVIEW |
| Penalty level | CHUI, HANSOKU_CHUI, HANSOKU, SHIKKAKU |
| Infraction | The 17 behaviours of Article 9.1.1, optional |
| Clock action | START, PAUSE, RESTART, ADD_TIME, SET_TIME, TIME_UP, on MAIN or a side's MEDICAL timer |
| Panel role | REFEREE, JUDGE, KANSA, VIDEO_REVIEW_JUDGE (Article 4.1) |
| Outcome | WIN, DRAW, NO_RESULT |
| Result reason | LEAD, POINTS, SENSHU, IPPON, WAZA_ARI, HANTEI, HIKIWAKE, HANSOKU, SHIKKAKU, KIKEN, DOUBLE_DISQUALIFICATION |
| Status | NOT_STARTED, FIGHTING, TIME_UP, DECIDED |
| Account roles | SCOREKEEPER, ADMIN |

## [status] Status and what each status accepts

| From | To | Entry that causes it |
|---|---|---|
| NOT_STARTED | FIGHTING | the first main-clock START |
| FIGHTING | TIME_UP | a main-clock TIME_UP |
| TIME_UP | FIGHTING | a main-clock ADD_TIME, RESTART or SET_TIME |
| any | DECIDED | a result |

Only the main clock moves the status, and nothing moves it out of DECIDED: the clock never ends a
game. Anything not in the table changes no status, such as START in TIME_UP, TIME_UP before the
start, or any medical timer action.

`EntryKind` holds which actions each status accepts:

| Entry | NOT_STARTED | FIGHTING | TIME_UP | DECIDED |
|---|---|---|---|---|
| score | warn | yes | yes, warn after the late-score window | warn |
| penalty | yes | yes | yes | yes |
| clock action | yes | yes | yes | warn |
| amendment | yes | yes | yes | yes |
| assignment | yes | warn | warn | warn |
| settings change | yes | refuse | refuse | refuse |
| result | yes for KIKEN, SHIKKAKU; warn otherwise | yes | yes | warn, and supersedes |

`KumiteGameTest` proves every cell.

## [clock] The clock

The server never ticks: the clocks are folded from the clock actions. A `ClockState` holds whether
it runs, the time left, and the instant that was true. All times are whole milliseconds.

| Action | Runs after | Time left after | Exchange |
|---|---|---|---|
| START | yes | unchanged | +1, main clock only |
| PAUSE | no | the time shown | unchanged |
| RESTART | no | the duration, or the medical time | unchanged |
| ADD_TIME | unchanged | plus the amount | unchanged |
| SET_TIME | no | the amount | unchanged |
| TIME_UP | no | 0 | unchanged |

- **The time shown is the fact.** Every clock action carries what the screen showed, and the server
  warns when it is further from the derived clock than the tolerance.
- **TIME_UP is the screen's report of zero.** A second one changes nothing. Time put back after
  zero returns the game to FIGHTING.
- A PAUSE of a clock already paused keeps the time it had.
- The medical timer runs per side (Article 11.3) and never touches the status.

## [warnings] Warnings

A warning is applied, stored on the entry and returned. It is never confirmed first.

| Warning | When | Article |
|---|---|---|
| `TIME_SHOWN_OUT_OF_TOLERANCE` | A clock action's time shown is outside the tolerance | |
| `CLOCK_REMAINING_OUT_OF_TOLERANCE` | A score's or penalty's clock remaining is outside it | |
| `SCORE_BEFORE_START`, `SCORE_AFTER_RESULT` | The status matrix | |
| `SCORE_AFTER_LATE_SCORE_WINDOW` | A score in TIME_UP later than the window after the zero | 8.10 |
| `CHUI_OVER_LIMIT` | A CHUI past the limit; a cancelled one does not count | 10.3 |
| `HANSOKU_WITHOUT_HANSOKU_CHUI` | HANSOKU on a side with no HANSOKU_CHUI | 10.3 |
| `CLOCK_ACTION_AFTER_RESULT`, `ASSIGNMENT_AFTER_START`, `RESULT_BEFORE_START`, `RESULT_WHILE_DECIDED` | The status matrix | |
| `FORMAT_COMBINATION` | A round-robin medal game, or an elimination extra game. Derived, because creation is not an entry | Appendix 6 |
| `HISTORY_INCONSISTENT` | Stored history the fold skipped | |

## [retries] Entry ids and retries

Every entry carries the id the client made for it, and the client sends the same id when it
retries. The check runs per game, inside the versioned save:

- Same id, same request: nothing is applied, and the stored warnings answer.
- Same id, any other request: refused.
- "The same request" compares only what the client sent, plus `recordedBy`. Order, instant and
  warnings differ on a retry by design. An exchange the client left out was filled in by the server,
  so it is not compared.

## [stored] Stored data

- **The data was dropped.** Games and players stored before this model were deleted with no
  migration: there was no production data. Drop the local `kumiteGame` and `player` collections.
- **After the December championship there is real data.** From then on, every change to a stored
  shape needs a migration, or a mapper that reads both shapes. See `[schema-versioning]` in
  `context/workflow/databases/mongodb/design/persistence.md`.
- **The type hint follows the package.** Spring Data stores each document's class name in `_class`,
  so moving a document class changes it on every later write. A document whose name no longer loads
  still reads, through the declared type.
- `Account` has no version field, because an account is only ever inserted or deleted.

## [source] Where the rules come from

The WKF Kumite Competition Rules are authoritative, currently version 2026.01. They are published
at https://www.wkf.net/documents. Each game stores the version it ran under in its settings.

A local, indexed reference of the rulebook sits outside the repo. Its location is in CLAUDE.local.md
at the repo root, which is not committed. Grep its tags and indexes and take what the task needs.
Without it, read the official rulebook.

Comments cite the rulebook itself, by version and article, never the local reference or where it
lives: `WKF Kumite Competition Rules 2026.01, Article 7.7`. Cite articles and not page numbers, which
are unreliable in the source.

To change a rule value, change configuration and redeploy: a game keeps the settings it was
created with. New behaviour is a new rule class, not a change to the entries.

## [deviations] Known deviations

- **A DRAW in elimination or an extra game is recorded without a warning** (Articles 12.2.5, 12.2.4).
  The suggested result, still to come, warns when a recorded result differs from it.
