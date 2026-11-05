# Application

## Index

- [purpose] Purpose
- [layers] Layers
- [aggregates] Aggregates
- [boundaries] Layer boundaries
- [not-built] Not built yet

## [purpose] Purpose

The system of record for a Kumite bout: fighters, the clock, every point and foul, and the result.
This README maps the packages. Each package has its own README.

## [layers] Layers

| Responsibility | Packages | Job |
|---|---|---|
| Gateways | `controllers`, `filters`, `dto`, `mappers`, `exceptions` handler | HTTP in and out. Translate, then delegate |
| Business | `services`, `rules`, `models`, `enums`, `exceptions` types | Orchestration, rules, domain types |
| Data | `repositories`, `models/converters` | MongoDB access and stored-form conversion |
| Wiring | `config`, `KumiteGameStarter` | Typed settings and application start |

Flow: `Filter -> Controller -> Service -> Repository -> MongoDB`. There are no outbound gateways yet.

`KumiteGameStarter` sits at the root of the package tree. Component scanning, repository discovery
and test slice configuration all derive from its location, so no `scanBasePackages` or
`@EnableMongoRepositories` arguments are needed.

## [aggregates] Aggregates

Two aggregate roots, each with a controller, service and repository: **KumiteGame** and **Player**.

`KumiteGame` stores fighter **ids** keyed by colour (`playerIds`). Names, points and fouls live
only on `Player` documents: one fact, one owner, no embedded snapshot to re-sync.

## [boundaries] Layer boundaries

`src/test/java/com/management/architecture/ArchitectureTest.java` enforces the layers inside
`mvn verify`. Rules the code passes are strict. These known violations are frozen in
`config/archunit/frozen`: tolerated, but new ones fail the build.

| Frozen rule | Today |
|---|---|
| Business does not know API shapes | `KumiteGameService` and `PlayerService` import request types from `dto` |
| Domain types carry no persistence mapping | `KumiteGame` and `Player` are also Spring Data documents |
| No utility package | `util.KumiteGameManagementUtils` |

Separating domain and persistence types is an architecture decision, not yet taken.

The store only changes on purpose. A fixed violation fails the build until you run
`mvn test -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreUpdate=true` and commit
the store.

One cycle is accepted by name, not frozen: `enums.PointsType` attaches strategies from
`models.strategies`, and `models` uses `enums`. Any other cycle fails the build.

`exceptions` holds two responsibilities: the types business code throws, and
`GlobalExceptionHandler`, the gateway. Both stay in one package for now.

## [not-built] Not built yet

- **Timer lifecycle wiring:** `GameTimer` exists but is not integrated into lifecycle methods. No
  WebSocket push. No strategy for recalculating `remainingTime` on point or foul events.
- **Game lifecycle endpoints:** `startGame`, `pauseGame`, `resumeGame` and `endGame` exist in
  `KumiteGameService` with no controller mappings, withheld pending the timer work.
- **Clock-driven expiry:** expiry is observed on the next scoring event, not pushed. A match left
  untouched past its duration stays open until something asks.
- **Spring Security and JWT:** not started.
- **Frontend:** not started.
