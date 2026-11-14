# service

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [dependencies] Dependencies are one way
- [writes] Every write a game takes
- [creation] Everything is read before anything is written
- [last-admin] At least one Admin always remains

## [purpose] Purpose

Orchestration: reads what an action needs, applies it through the domain, writes once. This README
covers the `service` package of each feature.

## [owns] Owns

- `KumiteGameService`, in `kumitegame.service`: creates, lists and reads games, and records every
  action on one. Reads the athletes and referees a game names, and composes `GameRead`; a list
  reads them once for the whole page, and filters on what the entries fold to: see `[lists]` in
  the web README. Builds every game with the `ResultRules` bean from `kumitegame.rules`, as
  `KumiteGameWriter` does, so each read and action sees the suggestion. `getVersioned` answers, as `VersionedGameRead`, the version of the game
  and of each athlete and referee it shows, each from the same read as what it describes: the
  gateway builds the game's tag from them. The fold waits until the gateway asks for the read, and
  reads nothing again, so a 304 costs only those reads. See `[conditional-reads]` in the web README.
- `KumiteGameWriter`, in `kumitegame.service`: reads a game, applies an action, saves it against the
  version it read. Appends retry; absolute actions do not. See `[writes]`.
- `GameReferences`, in `kumitegame.service`: answers `AthleteReferences` and `RefereeReferences`,
  whether a game names an athlete or referee.
- `AthleteService`, in `athlete.service`, and `RefereeService`, in `referee.service`: leaves that
  create, read, update and delete one document. A delete asks the references interface first.
- `AccountService`, in `account.service`: a leaf that reads, creates and deletes accounts. A
  password reaches it already hashed.
- `AccountRemoval`, in `account.service`: the one transaction in this application. See
  `[last-admin]`.

## [may-call] May call

Its own feature's `data`, `rules` and `domain`, the `service` and `domain` of a feature it may use,
and `config`. Never a `web` package: the gateway maps a request first, and a strict rule enforces it.

## [dependencies] Dependencies are one way

`KumiteGameService -> AthleteService, RefereeService`, acyclically. The athlete and referee services
never call the game feature: the question they need answered is an interface they own, implemented
in `kumitegame.service`. See `context/workflow/design/service-interaction.md`.

## [writes] Every write a game takes

One game is one document, so every action is one atomic write, with no transaction.

| Operation | Atomic unit | Invariants | Guard | Acknowledgement |
|---|---|---|---|---|
| Score, penalty, clock action but SET_TIME, SENSHU record, assignment, amendment | One `kumiteGame` document | `KumiteGame`: refusals, then warnings | Version, bounded retry | `w=majority` |
| SET_TIME, result, settings change | One `kumiteGame` document | `KumiteGame`: the status matrix | Version, no retry, 409 with the game as it stands | `w=majority` |
| Approve, reopen, void | One `kumiteGame` document | `KumiteGame`: the status move and the role | Version, no retry, 409 with the game as it stands | `w=majority` |
| Create a game | One new `kumiteGame` document | Athletes and referees exist, read first; `KumiteGame`: each referee once, the panel within its limit | Insert | `w=majority` |
| Create, update an athlete or referee | One document | The domain type | Version | `w=majority` |
| Delete an athlete or referee | One document | No game names it, read first | None | `w=majority` |

- **An append retries** on a lost version race, through Spring Framework's own `@Retryable` on
  `KumiteGameWriter.append`: re-read, re-apply, at most ten attempts, then a 409. A retry that
  finds its entry id on record applies nothing, which is what makes it safe. `KumiteGameWriter` is
  its own bean because `@Retryable` works through a proxy, which a self-call would bypass.
- **An absolute action does not retry.** It sets a value the caller chose while looking at what is
  now stale, so the loser answers 409 with the game as it now stands, in `current`.
- **The panel limit rests on the same guard.** An add that loses a race is re-applied to the game
  it re-read, so the limit is checked against the panel as it now stands.
- **A retried entry writes nothing.** The writer skips the save when the domain answers a replay.
- **The residual race with a delete is accepted.** A game created between a delete's check and the
  delete names an athlete that is gone, and reads it as unknown.

`KumiteGameApiIT` proves concurrent appends all land, with a latch, and that a score leaves the
athlete's version alone. `KumiteGameServiceTest` proves an absolute action that loses answers the
current game and is not retried.

## [creation] Everything is read before anything is written

A game is created with one insert, after the athletes and referees it names have been read. A
missing one answers 400 and writes nothing. An assignment naming an athlete or referee reads it
first the same way. The game's id and the ids of its first entries are given by the service, so
the domain type is never without an id: a create is not retried by id, and a retried create makes a
second game.

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
exception type cannot decide this: see `[error-facts]` in the web README. The transaction manager
retries a commit with an unknown result, and only the commit, then fails as a 500. Deleting a
Scorekeeper takes the same path with no guard, because no rule depends on one.

`AccountApiIT` runs two Admins deleting each other for several rounds, and needs the manager bean:
without one a transactional method runs with no transaction and gives no warning.
