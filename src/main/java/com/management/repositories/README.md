# repositories

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [facts] Project facts
- [known-gaps] Known gaps against the ACID rules

## [purpose] Purpose

The data layer. One Spring Data repository per aggregate root.

## [owns] Owns

`KumiteGameRepository`, `PlayerRepository`.

## [may-call] May call

`models` only. Only `services` calls this package.

## [facts] Project facts

- MongoDB 7 in Docker at `localhost:27017`, database `kumitedb`, standalone. **Not a replica set**,
  so multi-document transactions are unavailable.
- Connection settings bind through **`spring.mongodb.*`**, not `spring.data.mongodb.*`. Boot 4 split
  that namespace into driver level and repository level. Both resolve, so the wrong one fails
  silently by falling back to the default host.
- Environment variables: `MONGO_HOST`, `MONGO_PORT`, `MONGO_DB`.

## [known-gaps] Known gaps against the ACID rules

The rules are in `context/workflow/design/acid.md`. This code predates them. Each row is known debt,
not a regression: new code meets the rules, and no gap grows.

| Rule | Today | Fixed by |
|---|---|---|
| `[atomicity]` | A point saves on `Player`, then SENSHU or the result saves on `KumiteGame`: two writes | Milestone: Structure and engine |
| `[consistency]` | `addPoint` checks the match, then writes a different document | Milestone: Structure and engine |
| `[durability]` | No `w=majority` and no bound on the wait. Settings are host and port only | #122 |
| `[same-guarantees]` | Local and tests run standalone, on `mongo:7`, with no startup check | #122 |
| `[idempotency]` | A retried `add-point` scores twice | Milestone: Lifecycle and frontend |
