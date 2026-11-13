# domain

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [model] The game: entries, everything else derived
- [vocabulary] Vocabulary
- [status] Status and what each status accepts
- [clock] The clock
- [senshu] SENSHU
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
a game, by recording a result, and only an Admin annuls one.

## [owns] Owns

- Aggregates: `KumiteGame` in `kumitegame.domain`, `Athlete` in `athlete.domain`, `Referee` in
  `referee.domain`, `Account` in `account.domain`.
- `kumitegame.domain`:
  - The entries `Score`, `Penalty`, `Assignment`, `ClockAction`, `SenshuRecord`, `Amendment`,
    `Result`, `StatusChange`, each with an `EntryMeta`, composed rather than inherited. `Settings`, the rule values a game runs under.
  - Business inputs `NewGame`, `NewSettings`, `NewScore`, `NewPenalty`, `NewClockAction`,
    `NewSenshuRecord`, `NewAssignment`, `NewAmendment`, `NewResult`, `NewStatusChange`: what the
    client sent, mapped by the gateway. `ActingRole`, the role the signed-in account acts under.
  - Derived values `GameState`, `SideState`, `ClockState`, `SenshuState`, `GameWarning`, folded by
    `GameFold`. `SenshuFold` replays the SENSHU records. `EffectiveScores` derives each side's
    `EffectiveScore`.
  - The suggested result: `ResultRules`, the port the `kumitegame.rules` chain implements,
    `Standing`, what it reads, and `SuggestedResult`, what it answers, which also decides whether a
    result agrees with it. See `src/main/java/com/management/kumitegame/rules/README.md`.
  - The rules: `EntryKind`, the status matrix, and one class per warning, `ClockTolerance`,
    `LateScore`, `PenaltyLadder`, `SenshuWarnings` and `FormatCombination`, each reading the
    settings snapshot.
  - `GameRead`, the game with the athletes and referees it names and the stored version it was read
    at, and `Recording`, what a mutation answers. `KumiteGame` never sees the version.
- The exception types each feature throws. `GlobalExceptionHandler` maps them: see `[errors]` in
  `src/main/java/com/management/web/README.md`.

## [may-call] May call

Other `domain` types: `kumitegame.domain` uses `athlete.domain` and `referee.domain`. Never a
`service`, `rules`, `data` or `web` package, and no Spring Data type: strict rules in
`ArchitectureTest`.

The suggestion comes from `kumitegame.rules` through `ResultRules`, a port the domain owns. A game
is built with it, through `KumiteGame.create` or the data mapper, so its fold includes the
suggestion and no action needs it passed in. The port must answer for any standing. The fold guards
it all the same: when it throws, the game has no suggestion and warns `SUGGESTION_UNAVAILABLE`, and
the failure is logged once per fold with the game id and the rules. Reads, scores and writes go on.

## [model] The game: entries, everything else derived

A game stores only what happened: `format`, `medalGame`, `extraGame`, `settings`, each side's
`scores` and `penalties`, `assignments`, `clockActions`, `senshuRecords`, `amendments`, `results`
and `statusChanges`. Every entry
carries `meta`: the client's `id`, an `order` counted across the whole game, `recordedAt`,
`recordedBy` and the `raisedWarnings`. Read by `order`, the lists tell the game.

**Everything else is derived** by `GameFold`, folding every list in order: the status, the main
clock and each side's medical timer, the exchange counter, the athlete on each side, the panel, the
totals, the IPPON, WAZA_ARI and CHUI counts, the HANSOKU and SHIKKAKU counts and whether each side
is disqualified, who holds SENSHU and whether it is closed, and the current result. Nothing derived is stored, so
it can never disagree with the entries.

**The fold never throws.** Validity is checked when an entry is appended. Stored history always
loads: an entry this build cannot fold as written, such as a second CANCEL of one target or a reused
id, is skipped and reported as `HISTORY_INCONSISTENT` in `GameState.warnings`. Result rules that
fail leave the game with no suggestion: see `[may-call]`.

- **Athletes and panel are assignment entries.** The game is created with an athlete on each side
  and an optional panel, of any size. Changes after the start are recorded with a warning.
- **Points come from the settings map,** never from an entry or the code. Totals are folded with
  the game's current settings, so a settings change before the start also prices a score recorded
  before the start, which already warned. Such a change is applied, never refused, so a wrong
  setting can always be fixed, but it warns `SETTINGS_CHANGE_AFTER_ENTRIES` once any entry but an
  assignment exists. No settings history is kept.
- **The exchange** is the number of main-clock starts. A score or penalty takes the current one,
  or an earlier one the client names, as a video review may. Empty before the start. One that has
  not happened yet, later than the current one or any before the start, is refused. Stored history
  that names one still loads, counted as written.
- **A penalty's level is recorded as given,** never derived from a count (Article 10.3). In
  elimination it gives the opponent no points.
- **Amendments.** CANCEL takes a score, penalty or SENSHU record back: it stays on record, marked
  cancelled, and leaves the totals and counts, or SENSHU as `[senshu]` says. ACKNOWLEDGE records
  that the scorekeeper saw an entry's warnings. Refused: a CANCEL of anything else, a second CANCEL
  of one target, a target that does not exist. To undo a mistaken CANCEL, record the entry again.
- **Results are recorded as given.** Only a WIN names a winner. A later result supersedes the
  current one, which stays in `results`. So does a reopen of a DECIDED game, and a void.
- **The suggestion is derived, and kept on each result.** `GameState.suggested` is what the rules
  point to now, none on a VOIDED game. Each result stores `suggested`, the suggestion it was
  recorded against, so later corrections never change what the scorekeeper saw.
- **The effective score is derived from the result that stands.** In round robin, a WIN by
  HANSOKU, KIKEN or SHIKKAKU gives the loser 0, and the winner the higher of
  `roundRobinForfeitPoints` and what it scored, the points added as YUKO; a double disqualification
  gives both 0 (Articles 10.7.1, 6.2, 12.3.2). A HANSOKU with no result yet changes nothing.
  Elimination keeps what was scored. No score entry is ever added.
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
| Infraction | The 17 behaviours of Article 9.1.1, optional. Avoiding combat as Article 12.2.8 lists it: JOGAI, AVOIDING_COMBAT, CLINCHING, GRABBING_WITH_BOTH_HANDS, GRABBING_WITH_ONE_HAND |
| SENSHU record | AWARDED, WITHDRAWN |
| Clock action | START, PAUSE, RESTART, ADD_TIME, SET_TIME, TIME_UP, on MAIN or a side's MEDICAL timer |
| Panel role | REFEREE, JUDGE, KANSA, VIDEO_REVIEW_JUDGE (Article 4.1) |
| Outcome | WIN, DRAW, NO_RESULT |
| Result reason | LEAD, POINTS, SENSHU, IPPON, WAZA_ARI, HANTEI, HIKIWAKE, HANSOKU, SHIKKAKU, KIKEN, DOUBLE_DISQUALIFICATION |
| Status | NOT_STARTED, FIGHTING, TIME_UP, DECIDED, APPROVED, VOIDED |
| Status change | APPROVE, REOPEN, VOID |
| Account roles | SCOREKEEPER, ADMIN |

## [status] Status and what each status accepts

| Status | Means |
|---|---|
| NOT_STARTED | Created; the settings may change |
| FIGHTING | The first START given |
| TIME_UP | The clock reached zero, waiting for officials |
| DECIDED | A result recorded |
| APPROVED | The result signed off. Final |
| VOIDED | Annulled. Final, and the record stays |

| From | To | Entry that causes it |
|---|---|---|
| NOT_STARTED | FIGHTING | the first main-clock START |
| FIGHTING | TIME_UP | a main-clock TIME_UP |
| TIME_UP | FIGHTING | a main-clock ADD_TIME, RESTART or SET_TIME |
| NOT_STARTED, FIGHTING, TIME_UP | DECIDED | a result |
| DECIDED | APPROVED | APPROVE |
| DECIDED | the status before the result | REOPEN, which supersedes the result |
| APPROVED | DECIDED | REOPEN by an Admin, with a reason (Articles 13.4, 10.4.17) |
| any but VOIDED | VOIDED | VOID by an Admin, with a reason (Article 13.4) |

Only the main clock and the status changes move the status: the clock never ends a game. Anything
not in the table changes no status, such as START in TIME_UP, TIME_UP before the start, a result
while DECIDED, or any medical timer action.

- **The status before the result** is where the clock has taken it. A clock action while DECIDED
  is recorded with a warning and leaves the game DECIDED, but moves the status a reopen returns
  to. A game decided by KIKEN before the start, then started, reopens to FIGHTING, so its rules
  can no longer change.
- **A reopen of an APPROVED game keeps its result.** DECIDED means a result stands, so it returns
  to DECIDED with the same one. The scorekeeper then records a corrected result, which warns
  `RESULT_WHILE_DECIDED` as any second result does, or reopens again to before the result.
- **A void annuls the result.** It stays in `results`, and `currentResult` is empty.
- **A final game stops its clocks.** APPROVED and VOIDED take no clock action, so the main clock
  and both medical timers stop, with the time they had, at the approve or the void. An Admin's
  reopen leaves them stopped.
- **A final game refuses first.** Every other check on an action, such as a WIN with no winner or
  a CANCEL of something missing, comes after, so the caller is never sent to fix what stays
  refused.
- **An Admin may do whatever a Scorekeeper may,** so an Admin may approve, and reopen a DECIDED
  game, with no reason. A reason is needed only for an Admin's own moves.
- **The role is a plain value.** The gateway passes `ActingRole`, named as the account roles, the
  way it passes `recordedBy`. The role table admits both roles to the endpoint, and the game
  refuses an Admin's move from a Scorekeeper with `ActionForbiddenException`, a 403. The status is
  checked first, so a move the status refuses is a 409 whoever asks.
- **Approve, reopen and void are absolute:** not retried, so a lost race answers 409 with the game
  as it now stands. An entry id sent again still replays.

`EntryKind` holds which actions each status accepts:

| Entry | NOT_STARTED | FIGHTING | TIME_UP | DECIDED | APPROVED | VOIDED |
|---|---|---|---|---|---|---|
| score | warn | yes | yes, warn after the late-score window | warn | refuse | refuse |
| penalty | yes | yes | yes | yes | refuse | refuse |
| clock action | yes | yes | yes | warn | refuse | refuse |
| SENSHU record | warn | yes | yes | warn | refuse | refuse |
| amendment | yes | yes | yes | yes | refuse | refuse |
| assignment | yes | warn | warn | warn | refuse | refuse |
| settings change | yes; warn after any entry but an assignment | refuse | refuse | refuse | refuse | refuse |
| result | yes for KIKEN, SHIKKAKU; warn otherwise | yes | yes | warn, and supersedes | refuse | refuse |
| APPROVE | refuse | refuse | refuse | yes | refuse | refuse |
| REOPEN | refuse | refuse | refuse | yes | Admin, reason | refuse |
| VOID | Admin, reason | Admin, reason | Admin, reason | Admin, reason | Admin, reason | refuse |

`KumiteGameTest` proves every cell. Stored history with an entry after APPROVED or VOIDED, or a
status change that does not fit where it lands, still loads: the entry is skipped as
`HISTORY_INCONSISTENT`.

## [clock] The clock

The server never ticks: the clocks are folded from the clock actions. A `ClockState` holds whether
it runs, the time left, and the instant that was true. All times are whole milliseconds.

| Action | Runs after | Time left after | Exchange |
|---|---|---|---|
| START | yes | unchanged | +1, main clock only, unless it was running |
| PAUSE | no | the time shown | unchanged |
| RESTART | no | the duration, or the medical time | unchanged |
| ADD_TIME | unchanged | plus the amount | unchanged |
| SET_TIME | no | the amount | unchanged |
| TIME_UP | no | 0 | unchanged |

- **The time shown is the fact.** Every clock action carries what the screen showed, and the server
  warns when it is further from the derived clock than the tolerance.
- **TIME_UP is the screen's report of zero.** A second one changes nothing. Time put back after
  zero returns the game to FIGHTING, but only if the clock then has time left: SET_TIME to 0 stays
  TIME_UP.
- A START on a clock already running, such as a double tap, opens no new exchange.
- A PAUSE of a clock already paused keeps the time it had.
- The medical timer runs per side (Article 11.3) and never touches the status.

## [senshu] SENSHU

Officials decide who gets SENSHU and when it is withdrawn, and the scorekeeper records it as given
(Article 12.2). The system never awards or withdraws it on its own, and never decides which warning
level forfeits it (Articles 10.4.16, 12.2.8 differ): the referee withdraws it, and that is recorded.

- **Recorded:** `senshuRecords`, each `{ side, kind: AWARDED | WITHDRAWN, reason?, exchange?,
  clockRemaining?, meta }`, appended and retried like any entry. The exchange is filled in, or
  refused when it has not happened, as for a score.
- **Derived, never stored:** `GameState.senshu`, the holder, one side or none, so both can never
  hold it, and whether SENSHU is closed. `SenshuFold` replays the standing records in order:
  AWARDED gives it to its side; WITHDRAWN takes it from its side, and closes it when it came in the
  final window (Article 12.2.10). A WITHDRAWN from a side that does not hold it changes nothing.
- **The final window** is `finalWindowMs` from the settings snapshot, 15 s by default: fewer
  milliseconds left than that. The time left is the clock remaining the entry was sent with, or the
  main clock when it was sent with none. An entry naming an earlier exchange carries that
  exchange's time, so the main clock at the moment it was recorded decides instead.
- **A cancelled record never happened,** which is why replaying the standing ones is enough: a
  cancelled AWARDED takes the holder away and closes nothing, and a cancelled WITHDRAWN gives the
  holder back and undoes the closure it caused.
- **Refused:** an AWARDED while a side holds SENSHU, either side. A CANCEL of a WITHDRAWN while a
  later AWARDED stands, since both would hold it: cancel that award first. Stored history with two
  holders still loads: the second award is skipped as `HISTORY_INCONSISTENT`.
- **A cancelled score leaves SENSHU as it is.** It warns when the holder has no unopposed score
  left; the officials decide.

`SenshuTest` proves each rule, refusal and warning.

## [warnings] Warnings

A warning is applied, stored on the entry and returned. It is never confirmed first. A settings
change is not an entry, so its warning is only returned.

| Warning | When | Article |
|---|---|---|
| `TIME_SHOWN_OUT_OF_TOLERANCE` | A clock action's time shown is outside the tolerance | |
| `CLOCK_REMAINING_OUT_OF_TOLERANCE` | A score's, penalty's or SENSHU record's clock remaining is outside it. Not checked for an earlier exchange a video review names | |
| `SCORE_BEFORE_START`, `SCORE_AFTER_RESULT` | The status matrix | |
| `SCORE_AFTER_LATE_SCORE_WINDOW` | A score in TIME_UP later than the window after the zero | 8.10 |
| `CHUI_OVER_LIMIT` | A CHUI past the limit; a cancelled one does not count | 10.3 |
| `HANSOKU_WITHOUT_HANSOKU_CHUI` | HANSOKU on a side with no HANSOKU_CHUI | 10.3 |
| `SENSHU_BEFORE_START`, `SENSHU_AFTER_RESULT` | The status matrix | |
| `SENSHU_WHEN_BOTH_SCORED` | An AWARDED when the other side has a standing score in the same exchange, or a score for the holder's opponent in the exchange of the standing AWARDED, as a video review adds | 12.2.2, 12.2.9 |
| `SENSHU_AFTER_CLOSED` | An AWARDED after a withdrawal in the final window closed SENSHU | 12.2.10 |
| `SENSHU_WITHDRAWN_NOT_HELD` | A WITHDRAWN from a side that does not hold SENSHU. Nothing changes | |
| `SENSHU_HOLDER_AVOIDING_COMBAT` | A penalty to the holder, in the final window, that names an avoiding-combat infraction, whatever its level. Not checked without an infraction | 12.2.8, 10.4.16 |
| `SENSHU_HOLDER_WITHOUT_UNOPPOSED_SCORE` | A CANCEL of the holder's score that leaves the holder no standing score in an exchange where the opponent has none. A score before the start belongs to no exchange | 12.2.2 |
| `CLOCK_ACTION_AFTER_RESULT`, `ASSIGNMENT_AFTER_START`, `RESULT_BEFORE_START`, `RESULT_WHILE_DECIDED` | The status matrix | |
| `SETTINGS_CHANGE_AFTER_ENTRIES` | A settings change after any entry but an assignment: those entries are now folded with the new values, and their stored warnings were raised under the old ones. Returned, never stored | |
| `RESULT_DIFFERS_FROM_SUGGESTION` | A result that does not agree with the suggestion. Never KIKEN; with no suggestion, anything but SHIKKAKU | 12.2 |
| `SUGGESTION_DIFFERS_FROM_RESULT` | Any entry while DECIDED after which the result standing, which agreed with the suggestion just before it, no longer agrees. Not raised for a result that already differed. The status stays | 12.2 |
| `FORMAT_COMBINATION` | A round-robin medal game, or an elimination extra game. Derived, because creation is not an entry | Appendix 6 |
| `SUGGESTION_UNAVAILABLE` | The result rules threw while folding, so the game has no suggestion. Derived, never stored: see `[may-call]`. Every other rule treats it as no suggestion: a result recorded meanwhile keeps none, and a correction that takes an agreeing suggestion away this way warns `SUGGESTION_DIFFERS_FROM_RESULT` | |
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

- **A stored value this build cannot read** answers 500, never the caller's 400: the mapper turns
  it into an `IllegalStateException`.
- **The data was dropped.** Games and players stored before this model were deleted with no
  migration: there was no production data. Drop the local `kumiteGame` and `player` collections.
- **After the December championship there is real data.** From then on, every change to a stored
  shape needs a migration, or a mapper that reads both shapes. See `[schema-versioning]` in
  `context/workflow/databases/mongodb/design/persistence.md`.
- **A new field raises the schema version too.** A build that does not know a field ignores it on
  read and drops it on its next save, so after a rollback the data is gone. Raised, the older build
  refuses the game instead. Shape 2 added `senshuRecords`, shape 3 `statusChanges`, and shape 4
  `suggested` on each result; an older game reads with none.
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

- **An AWARDED to a side with no score is recorded without a warning** (Article 12.2.2). A
  scorekeeper may record SENSHU before the score it rests on.
- **The unopposed-score warning looks at any exchange, not only the first.** Article 12.2.2 is
  about the first unopposed score; a holder keeping a later unopposed one does not warn.
