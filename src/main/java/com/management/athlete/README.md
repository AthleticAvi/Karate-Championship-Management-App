# athlete

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [writes] Writes

## [purpose] Purpose

An athlete, kept once and outside any game: `id`, `name` and, when known, `weightKg`. A game holds
the athlete's id and looks the name up when it is read. It replaces the old `Player`, which also
held scores and fouls: those live on the game now, so a score never writes an athlete.

It follows the referee's split of domain type, stored document and mapper: see `[pattern]` in
`src/main/java/com/management/referee/README.md`. The collection is `athlete`.

## [owns] Owns

| Layer | Types |
|---|---|
| `web` | `AthleteController` at `/api/v1/athletes`, `AthleteRequest`, `AthleteResponse`, `AthleteMapper` |
| `service` | `AthleteService`, `AthleteReferences` |
| `domain` | `Athlete`, `AthleteNotFoundException`, `AthleteChangedException`, `AthleteInUseException` |
| `data` | `AthleteDocument`, `AthleteRepository`, `AthleteDocumentMapper` |

## [may-call] May call

No other feature. Whether a game names an athlete comes through `AthleteReferences`, which this
feature owns and `kumitegame.service.GameReferences` implements: the game feature may use this one,
never the reverse.

## [writes] Writes

| Operation | Atomic unit | Invariants | Guard | Acknowledgement |
|---|---|---|---|---|
| Create, update | One document | Domain type | Version | `w=majority` |
| Delete | One document | No game names it, read first | None | `w=majority` |

**An update is not retried.** It sets the name and weight, so a retry would overwrite what the
caller never saw. An update that loses to another write answers 409 with the athlete as it now
stands, in `current`. Values already stored write nothing.

**Deleting an athlete a game names is refused** with 409. Any assignment counts, a removed one
included. A game created between the check and the delete is the accepted residual race: it reads
the athlete as unknown.

Who may call each endpoint follows the levels `/api/v1/players` had: a read is public, everything
else is Admin.
