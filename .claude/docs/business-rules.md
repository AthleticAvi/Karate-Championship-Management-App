# Business Rules

## Player Colors
| Color | Label |
|---|---|
| AKA | RED |
| AO | BLUE |

## Point Types
| Name | Points |
|---|---|
| YUKO | 1 |
| WAZA-ARI | 2 |
| IPPON | 3 |

## Winning Point Threshold
- The default winning threshold is **8 points**.
- This is a **trigger condition, not a cap** — a player can exceed it. Example: a player at 7 points scores an IPPON (3 pts) and finishes at 10 points. The game ends because the threshold was crossed, not because the player stopped at 8.
- The threshold must be **configurable per championship or rule update** without requiring code changes. It must be loaded from config, not hardcoded.

## Foul Progression
Fouls escalate in the following order:

1. **CHUI** — warning (up to 3)
2. **HANSOKU CHUI** — penalty warning
3. **HANSOKU** — penalty point awarded to opponent
4. **SHIKKAKU** — disqualification

3 CHUI infractions trigger escalation to HANSOKU CHUI.

Accumulating 4 fouls ends the game.

## Game Ending Conditions
A game ends when any of the following occur:
- A player crosses the **winning point threshold** (default: 8 points)
- A player accumulates **4 fouls**
- **Time expires**
- A player is **disqualified**
- A player does not show up (**KIKEN** — no-show forfeit)

## Winner Determination
Winner determination logic must be **dynamic and extensible** — new winning scenarios must be injectable without large code changes.

Default rules (in priority order):
1. First player to cross the winning point threshold wins
2. If time expires with equal scores — winner is determined by **SENSHU** (first player to score)
3. Disqualification results in an automatic win for the opponent

**Manual override:** Manual winner assignment must be supported with a **reason field**, for edge cases where the referee overrides the system result.

## Timer — Time Extension
The timer must support adding time via UI buttons:
- **+10 seconds**
- **+30 seconds**

This covers cases where time was wasted and the timer was not stopped in time. Human error accommodation is a requirement, not an edge case.

## Game Duration Options
| Duration | Seconds |
|---|---|
| Short | 90s |
| Standard | 120s (default) |
| Long | 180s |

Duration is loaded from `config.properties`, not hardcoded.

## Game States
| State | Meaning |
|---|---|
| QUEUED | Game created, not yet started |
| RUNNING | Game actively in progress |
| PAUSED | Game temporarily halted |
| FINISHED | Game over, result determined |

## Game State Transitions

Valid transitions:

| From | To |
|---|---|
| QUEUED | RUNNING |
| RUNNING | PAUSED |
| PAUSED | RUNNING |
| RUNNING | FINISHED |
| PAUSED | FINISHED |

**FLAG — PAUSED → FINISHED not explicitly implemented.**
As of the current codebase, `endGame` in `KumiteGameService` performs no state validation before transitioning to `FINISHED`. It calls `timer.stop()` and sets the state regardless of whether the game is `RUNNING` or `PAUSED`. PAUSED → FINISHED works by accident, not by design — there is no guard enforcing valid transitions at all. State transition guards must be added when the Game Orchestrator is built. See also `.claude/docs/known-issues.md`.
