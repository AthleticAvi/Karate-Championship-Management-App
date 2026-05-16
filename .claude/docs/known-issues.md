# Known Issues

## 1. Circular Dependency — `@Lazy` Workaround
- **What:** `PlayerService` and `KumiteGameService` have a mutual dependency that creates a Spring circular injection issue.
- **Workaround:** `GameHelperService` was introduced as an intermediary, with `@Lazy` injection to break the cycle.
- **Status:** Workaround is in place and functional. It is not a permanent architectural fix.
- **Impact:** Must be explicitly considered and resolved when designing and building the Game Orchestrator. Do not build on top of the `@Lazy` workaround as if it is a stable pattern.
- **Preferred resolution:** Redesign service dependencies so the Game Orchestrator owns cross-cutting game logic, eliminating the need for `GameHelperService` as an intermediary.

## 2. `GameTimer` Not Persisted — `@Transient`
- **What:** `GameTimer` is annotated `@Transient` and is therefore not persisted to MongoDB.
- **Status:** The timer class structure is designed but the timer implementation has not been written yet.
- **Impact:** Any feature depending on timer state across restarts or API calls cannot rely on persisted timer data until this is resolved.

## 3. Game Lifecycle Endpoints Not Exposed
- **What:** `startGame`, `pauseGame`, `resumeGame`, and `endGame` methods exist in `KumiteGameService` but have no corresponding controller endpoints.
- **Status:** Intentionally withheld — endpoints will be added once the timer implementation is complete.
- **Impact:** Game state transitions cannot be triggered via the API until timer work is done.

## 4. Naming Inconsistency — `STARTED` vs `RUNNING`
- **What:** The project wiki documents the in-progress game state as `STARTED`. The codebase uses `RUNNING`.
- **Status:** Unresolved. The codebase value (`RUNNING`) is the authoritative reference.
- **Impact:** Any documentation, external tooling, or future API contracts written against the wiki name will be misaligned with the actual enum value.
