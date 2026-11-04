# rules

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [contract] Contract
- [add-rule] Adding a rule
- [facts] Settled rules

## [purpose] Purpose

The rules engine: decides whether a match is over, why, and who won. It is the component older
docs call the Game Orchestrator.

## [owns] Owns

- `MatchOutcomeEvaluator`: holds two injected lists and produces a `MatchOutcome`.
- `conditions/`: `MatchEndCondition` beans. Is the match over, and why.
- `winners/`: `WinnerRule` beans, sorted by `@Order`. First rule to answer wins.

## [may-call] May call

`models`, `enums`, `config`. Never `repositories`, `controllers` or `dto`.

## [contract] Contract

`KumiteGameService` calls the evaluator after every point, foul and disqualification, then applies
and persists the answer. A match also finishes this way, without any lifecycle call.

## [add-rule] Adding a rule

Add a class. No existing rule changes.

## [facts] Settled rules

- Winner priority: disqualification, then higher score, then SENSHU, then draw.
- **SENSHU is captured as it happens** (first fighter to score, recorded on the match), because it
  cannot be reconstructed later.
- Ending is idempotent: the first condition to fire owns the result.
- A finished match cannot be scored against.
- Winning score is a trigger, never a cap: crossing 8 with an IPPON from 7 finishes at 10.
