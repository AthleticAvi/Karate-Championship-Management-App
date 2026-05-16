# Design Patterns

## Strategy Pattern — Point Scoring
- **Interface:** `PointStrategy`
- **Implementations:** `IpponStrategy`, `WazariStrategy`, `YokoStrategy`
- **Carrier:** `PointsType` enum — each enum constant holds its corresponding strategy instance
- Points are calculated by invoking the strategy carried by the enum, not by switch/if logic

## Repository Pattern
- All data access goes through Spring Data `MongoRepository` interfaces
- No direct MongoDB driver calls in service or controller layers

## Service Layer
- All business logic lives in service classes
- Controllers do not contain business logic — they delegate entirely to services

## MVC / REST Controllers
- Standard Spring `@RestController` pattern
- Controllers map HTTP verbs and paths to service calls and return DTOs

## DTO Pattern
- **Inbound:** `*RequestDTO` — defines the shape of data accepted from API consumers
- **Outbound:** `*ResponseDTO` — defines the shape of data returned to API consumers
- Domain models are never exposed directly via the API

## Global Exception Handler
- Implemented with `@ControllerAdvice`
- Catches custom and standard exceptions and returns consistent error response shapes

## Implicit State Pattern
- `GameState` enum models the game lifecycle state machine
- States: `QUEUED` → `RUNNING` ⇄ `PAUSED` → `FINISHED`
- State transition guards are not yet enforced in the service layer — `endGame` performs no state validation before transitioning to `FINISHED`. Guards must be added when the Game Orchestrator is built. See `.claude/docs/known-issues.md`.

## Value Objects
- `Points` — represents a scored point with its type and associated player
- `Foul` — represents a foul event with its type and associated player

## Utility / Static Helper
- `KumiteGameManagementUtils` — stateless static methods for game management calculations and logic shared across services

## Externalized Configuration
- `GameConfig` — Spring `@Configuration` class that loads game settings
- `config.properties` — external properties file for game duration and configurable rule values
- Timer durations (90s / 120s / 180s) are read from config, not hardcoded

## Lazy Dependency Injection — Circular Dependency Workaround
- `PlayerService` and `KumiteGameService` have a mutual dependency
- Broken via `GameHelperService` acting as an intermediary, with `@Lazy` injection
- This is a known workaround, not a permanent architectural solution — see `.claude/docs/known-issues.md`
