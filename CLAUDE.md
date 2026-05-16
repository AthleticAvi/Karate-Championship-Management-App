# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Detailed Documentation

All reference material lives in `.claude/docs/`. Read these before making changes:
- `.claude/docs/architecture.md` — package structure and class responsibilities
- `.claude/docs/patterns.md` — every design pattern in use, including known workarounds
- `.claude/docs/business-rules.md` — Kumite scoring rules, game states, and valid transitions
- `.claude/docs/known-issues.md` — active issues that affect what can safely be built
- `.claude/docs/stack.md` — full technology stack and tooling
- `.claude/docs/working-rules.md` — how this project is developed; read before doing anything

## Commands

MongoDB must be running in Docker before starting the application.

```bash
# Run the application
mvn spring-boot:run

# Build
mvn clean install

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=KumiteGameTimerTest

# Run a single test method
mvn test -Dtest=KumiteGameTimerTest#testStartGame
```

## Architecture

4-layer: `Controller → Service → Repository → MongoDB`

There are two aggregate roots with their own controller/service/repository stack: **KumiteGame** and **Player**.

**How scoring works across aggregates:** Points and fouls are stored on `Player` (persisted via `PlayerRepository`). When a point or foul is recorded, `PlayerService` mutates and saves the `Player`, then calls `GameHelperService.updateKumiteGame()` to re-sync the player snapshot embedded inside `KumiteGame`. The `KumiteGameController` routes these mutations to `PlayerService`, not `KumiteGameService`.

**GameTimer is `@Transient`** — it is never persisted to MongoDB. `KumiteGame.initializeTimer()` must be called on every `startGame` and `resumeGame` to reconstruct it from `remainingTime`. Any feature touching the timer must account for this.

**Circular dependency:** `KumiteGameService` and `PlayerService` mutually depend on each other. `GameHelperService` breaks this cycle as a delegating intermediary, injected with `@Lazy` in both services. This is a workaround — see `.claude/docs/known-issues.md` before touching any of these three services.

**Game lifecycle endpoints do not exist yet.** `startGame`, `pauseGame`, `resumeGame`, and `endGame` are implemented in `KumiteGameService` but have no controller mappings. They are intentionally withheld pending timer implementation.

## Key Wiring to Know

- `KumiteGameController` at `/api/kumitegame` — owns game creation, retrieval, point/foul mutations, and winner assignment
- `PlayerController` at `/api/players` — owns player CRUD
- `PointsType` enum carries its `PointStrategy` instance — point value is resolved by calling `pointsType.getStrategy().calculatePoints()`
- `GameConfig` reads from `src/main/resources/config.properties` — game durations are loaded there, not hardcoded
- MongoDB connection is configured via environment variables (`MONGO_HOST`, `MONGO_PORT`, `MONGO_DB`), defaulting to `localhost:27017/kumitedb`

## What Is Not Built Yet

- **Timer implementation** — designed, not written
- **Game lifecycle endpoints** — `startGame`, `pauseGame`, `resumeGame`, `endGame` have no controller mappings
- **Game Orchestrator** — evaluates game ending conditions after every point/foul, determines winner, updates state
- **Spring Security and JWT** — not started
- **Frontend** — not started

## What To Never Do

- Do not hardcode game durations, point thresholds, or rule values — all must come from config
- Do not expose domain models directly via the API — always use DTOs
- Do not add business logic to controllers — delegate to services
- Do not build on top of the `@Lazy` circular dependency workaround as if it is stable
- Do not make architectural or feature decisions — those are made outside Claude Code first
- Do not start a new feature until the current one is confirmed built, tested, and signed off
