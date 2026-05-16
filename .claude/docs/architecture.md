# Architecture

## Layered Architecture

```
Controller → Service → Repository → MongoDB
```

4-layer architecture following standard Spring Boot conventions.

## Package Structure

Base package: `com.management`

| Package | Purpose |
|---|---|
| `controllers` | REST controllers — handle HTTP requests and delegate to services |
| `dto` | Data Transfer Objects — separate inbound request and outbound response shapes |
| `enums` | Enumerations — GameState, PointsType, PlayerColor, and others |
| `exceptions` | Custom exception classes and global exception handler |
| `kumitegame` | Spring Boot application entry point — contains KumiteGameStarter (@SpringBootApplication) |
| `models` | Domain entities persisted to MongoDB |
| `models/strategies` | Strategy implementations for point scoring |
| `repositories` | Spring Data MongoDB repository interfaces |
| `services` | Business logic layer — orchestrates between controllers and repositories |
| `util` | Static helper utilities |

## Class Responsibilities

### Controllers
- Receive HTTP requests, validate input via DTOs, delegate to services, return responses

### Services
- `KumiteGameService` — core game lifecycle management (create, state transitions, scoring)
- `PlayerService` — player registration and lookup
- `GameHelperService` — intermediary service introduced to break circular dependency between `PlayerService` and `KumiteGameService`

### Repositories
- MongoDB repository interfaces extending `MongoRepository` — one per aggregate root

### Models
- Domain entities mapped to MongoDB documents
- `GameTimer` — timer structure (implementation pending)
- `models/strategies` — point strategy implementations attached to domain logic

### DTOs
- Inbound: `*RequestDTO` — shapes accepted from API consumers
- Outbound: `*ResponseDTO` — shapes returned to API consumers

### Enums
- `GameState` — QUEUED / RUNNING / PAUSED / FINISHED
- `PointsType` — carries the strategy instance for each point type
- `PlayerColor` — AKA (RED) / AO (BLUE)

### Exceptions
- Custom exception classes per error domain
- `@ControllerAdvice` global exception handler

### Entry Point
- `KumiteGameStarter` — @SpringBootApplication entry point, bootstraps the Spring context

### util
- Static helper classes shared across the application
- `KumiteGameManagementUtils` — static utility methods for game management

### Config
- `GameConfig` — loads externalized configuration
- `config.properties` — game duration and rule configuration values
