# rules

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [contract] Contract
- [add-rule] Adding a rule
- [source] Where the rules come from
- [facts] Settled rules

## [purpose] Purpose

The rules engine: decides whether a match is over, why, and who won. It is the component older
docs call the Game Orchestrator.

## [owns] Owns

- `MatchOutcomeEvaluator`: holds two injected lists and produces a `MatchOutcome`.
- `conditions/`: `MatchEndCondition` beans. Is the match over, and why.
- `winners/`: `WinnerRule` beans, sorted by `@Order`. First rule to answer wins.

## [may-call] May call

`domain`, `config`. Never a `data` or `web` package.

## [contract] Contract

`KumiteGameService` calls the evaluator after every point, foul and disqualification, then applies
and persists the answer. A match also finishes this way, without any lifecycle call.

## [add-rule] Adding a rule

Add a class. No existing rule changes.

## [source] Where the rules come from

The WKF Kumite Competition Rules are authoritative, currently version 2026.01. They are published
at https://www.wkf.net/documents.

A local, indexed reference of the rulebook sits outside the repo. Its location is in CLAUDE.local.md
at the repo root, which is not committed. Grep its tags and indexes and take what the task needs.
Without it, read the official rulebook.

Comments cite the rulebook itself, by version and article, never the local reference or where it
lives: `WKF Kumite Competition Rules 2026.01, Article 7.7`. Cite articles and not page numbers, which
are unreliable in the source.

## [facts] Settled rules

- Winner priority: disqualification, then higher score, then SENSHU, then draw.
- **SENSHU is captured as it happens** (first fighter to score, recorded on the match), because it
  cannot be reconstructed later.
- Ending is idempotent: the first condition to fire owns the result.
- A finished match cannot be scored against.
- Winning score is a trigger, never a cap: crossing 8 with an IPPON from 7 finishes at 10.
