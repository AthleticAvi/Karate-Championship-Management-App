# rules

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [chain] The chain per format
- [agree] When a result agrees
- [add-rule] Adding a rule
- [source] Where the rules come from

## [purpose] Purpose

The suggested-result chain: the result the rules of a Kumite game point to, worked out live from
where the game stands. The system suggests; the scorekeeper confirms by recording a result. The
server never records a result itself. This README covers the `rules` package of each feature; only
`kumitegame` has one.

## [owns] Owns

- `SuggestedResultChain`: implements `ResultRules`, the port `kumitegame.domain` owns. Asks each
  `SuggestionRule` in order; the first that answers decides.
- One `SuggestionRule` bean per step, ordered with `@Order`. Each reads only the `Standing` and its
  settings snapshot, never configuration.
- `FormatRules`, the strategy for what differs between the formats: `EliminationRules` and
  `RoundRobinRules`. Only `LevelRule` asks it.
- `Ahead`, which side is ahead on one count.

## [may-call] May call

`domain` only. The domain never calls this package: it asks `ResultRules`, and the services give
each game the bean. Gateways never reach it, and `ArchitectureTest` enforces both.

## [chain] The chain per format

Read top to bottom; the first step that answers decides. Steps marked "time up" answer only when
the game is TIME_UP, or was when its result was recorded and no time was put back.

| Order | Step | Elimination | Round robin | Articles |
|---|---|---|---|---|
| 10 | `DoubleDisqualificationRule` | Both sides with HANSOKU or SHIKKAKU: NO_RESULT, DOUBLE_DISQUALIFICATION. A medal game: POINTS, then SENSHU, then HANTEI, with `bothDisqualified` | Same | 12.2.11, 10.7.1 |
| 20 | `DisqualificationRule` | HANSOKU or SHIKKAKU against one side: the other wins, reason SHIKKAKU if it has one, else HANSOKU | Same | 12.2.1 |
| 30 | `LeadRule` | A lead of at least `leadToWin`: WIN, LEAD, whatever the clock | Same | 7.7, 12.2.1 |
| 40 | `PointsRule` | Time up: more points, POINTS | Same | 12.2.1, Appendix 6 |
| 50 | `SenshuRule` | Time up: the SENSHU holder, SENSHU | Same | 12.2.1, Appendix 6 |
| 60 | `IpponRule` | Time up: more IPPON, IPPON | Same | 12.2.3 a |
| 70 | `WazaAriRule` | Time up: more WAZA_ARI, WAZA_ARI | Same | 12.2.3 b |
| 80 | `LevelRule` | Time up, still level: HANTEI, no winner suggested | HIKIWAKE, a DRAW; HANTEI in an extra game | 12.2.4, 12.2.5, Appendix 6 |

Otherwise there is no suggestion. Article 12.2, Appendix 6 and the planning record agree on this
order. Article 7.9 goes from a tied score straight to HANTEI: it describes only the last step.

- **HANTEI is a decision only.** It is suggested as a WIN with no winner: the officials' vote names
  it, so the chain never does.
- **KIKEN is never suggested.** The scorekeeper records it.
- **What stands now, not what came first.** A SHIKKAKU after a LEAD win moves the suggestion to the
  opponent, and a corrected game warns.
- **A medal game's double disqualification reads the points as they stand,** not frozen at the
  second disqualification (Article 12.2.11 says "at the moment of disqualification").
- **No medal after SHIKKAKU** (Article 12.2.11) is the championship's to apply. The game suggests
  only who won it.

## [agree] When a result agrees

`SuggestedResult` in `kumitegame.domain` decides it, so the result and every correction ask the
same question.

- The outcome, the reason and the double disqualification match, and the winner too, unless the
  suggestion is a HANTEI. A DOUBLE_DISQUALIFICATION counts as both disqualified, flag or not. A
  LEAD suggested and POINTS recorded differ: the reason is part of the result.
- KIKEN always agrees: the chain never suggests it, and a withdrawal decides whatever the score.
- With no suggestion, SHIKKAKU agrees too. Anything else ends a game the rules do not point to an
  end of, such as a DRAW mid-game in elimination.
- A recorded result that does not agree warns `RESULT_DIFFERS_FROM_SUGGESTION`. A correction while
  DECIDED that makes the result standing, which agreed just before it, stop agreeing warns
  `SUGGESTION_DIFFERS_FROM_RESULT`. One that only moves the suggestion of a result that already
  differed raises nothing more. Both are applied and recorded, and nothing reopens.
- Each result keeps `suggested`, the suggestion as it stood when it was recorded, on purpose: later
  corrections change the live one.

## [add-rule] Adding a rule

Add a class: a `@Component` implementing `SuggestionRule`, with an `@Order` between the steps it
sits between. The container sorts the list the chain is given, so no existing class changes. A rule
value it needs is a setting: add it to `Settings` and configuration, never a literal.

Behaviour that differs by format goes in `FormatRules`, never a switch on the format in a step. A
new format adds a `FormatRules` bean; the application refuses to start without one per format.

`SuggestedResultChainTest` builds the chain from the beans, through `TestGames.rules()`, so the
order it proves is the order the application runs.

## [source] Where the rules come from

See `[source]` in `src/main/java/com/management/kumitegame/domain/README.md`. Cite the rulebook by
version and article in comments.
