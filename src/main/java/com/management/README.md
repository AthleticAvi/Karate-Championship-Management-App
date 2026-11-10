# Application

## Index

- [purpose] Purpose
- [layers] Layers
- [aggregates] Aggregates
- [boundaries] Layer boundaries
- [not-built] Not built yet

## [purpose] Purpose

The system of record for a Kumite bout: fighters, the clock, every point and foul, and the result.
This README maps the packages. Each shared package has its own README, and each layer has one in
`kumitegame` that covers that layer in every feature.

## [layers] Layers

The code is grouped by feature, `kumitegame`, `athlete` and `account`, and each feature is split by
layer. Shared code sits in its own top-level packages.

| Responsibility | Packages | Job |
|---|---|---|
| Gateways | `<feature>.web`, `web`, `filters`, `security` | HTTP in and out. Translate, then delegate |
| Business | `<feature>.service`, `kumitegame.rules`, `<feature>.domain` | Orchestration, rules, domain types |
| Data | `<feature>.data`, `kumitegame.data.converters` | MongoDB access and stored-form conversion |
| Wiring | `config`, `KumiteGameStarter` | Typed settings and application start |

| Package | Holds | README |
|---|---|---|
| `<feature>.web` | The feature's controller, request and response records, and mapper | `web/README.md` |
| `web` | What every feature's API shares: version prefix, role table, CORS, API description, `GlobalExceptionHandler` | `web/README.md` |
| `<feature>.service` | Services | `kumitegame/service/README.md` |
| `kumitegame.rules` | The rules engine | `kumitegame/rules/README.md` |
| `<feature>.domain` | Domain types, the vocabulary, and the exception types the feature throws | `kumitegame/domain/README.md` |
| `<feature>.data` | Repositories, and in `kumitegame` the stored-form converters | `kumitegame/data/README.md` |
| `filters`, `security`, `config`, `util` | Shared, each with its own README | |

`athlete` holds today's `Player` types under their current names. A referee is a name inside the
match, so there is no `referee` package.

`[package-layout]` in `context/workflow/web-frameworks/spring-boot/standards/standards.md` names
`web`, `domain` and `data` only. `service` and `rules` are kept as layers of their own, because the
Spring Boot layer contract does not let domain code call persistence, and the services do.

Flow: `Filter -> Security -> Controller -> Service -> Repository -> MongoDB`. There are no outbound
gateways yet.

`KumiteGameStarter` sits at the root of the package tree. Component scanning, repository discovery
and test slice configuration all derive from its location, so no `scanBasePackages` or
`@EnableMongoRepositories` arguments are needed.

## [aggregates] Aggregates

Three aggregate roots, one per feature: **KumiteGame** in `kumitegame`, **Player** in `athlete`,
**Account** in `account`. Each has a controller, service and repository. Nothing ties an account to a
match or a fighter. `kumitegame` may use `athlete`; no other feature uses another, and
`ArchitectureTest` enforces it.

`KumiteGame` stores fighter **ids** keyed by colour (`playerIds`). Names, points and fouls live
only on `Player` documents: one fact, one owner, no embedded snapshot to re-sync.

## [boundaries] Layer boundaries

`src/test/java/com/management/architecture/ArchitectureTest.java` enforces the layers inside
`mvn verify`. Rules the code passes are strict. These known violations are frozen in
`config/archunit/frozen`: tolerated, but new ones fail the build.

| Frozen rule | Today |
|---|---|
| Domain types carry no persistence mapping | `kumitegame.domain.KumiteGame` and `athlete.domain.Player` are also Spring Data documents |
| No utility package | `util.KumiteGameManagementUtils` |

Separating domain and persistence types is an architecture decision, not yet taken.

The store only changes on purpose. A fixed violation fails the build until you run
`mvn test -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreUpdate=true` and commit
the store.

Documentation types from the API description library stay in the web packages. A strict rule fails
on any other package using them. Spring Security types stay in `security` and the web packages the
same way.

The cycle rule treats each layer as one slice across the features, as the layer packages were before
the move, so the shared `web` and each feature's `web` are one slice: `ApiAccess` names each
controller's path, and each controller names the version prefix. One cycle is accepted by name, not
frozen: `KumiteGame` names `LegacyWinnerConverter` in its mapping, and the converter reads
`PlayerColor`. It is part of the frozen persistence mapping. Any other cycle fails the build.

Exception types sit in the `domain` package of the feature that throws them, and
`GlobalExceptionHandler`, the gateway, in `web`.

## [not-built] Not built yet

- **Timer lifecycle wiring:** `GameTimer` exists but is not integrated into lifecycle methods. No
  WebSocket push. No strategy for recalculating `remainingTime` on point or foul events.
- **Game lifecycle endpoints:** `startGame`, `pauseGame`, `resumeGame` and `endGame` exist in
  `KumiteGameService` with no controller mappings, withheld pending the timer work.
- **Clock-driven expiry:** expiry is observed on the next scoring event, not pushed. A match left
  untouched past its duration stays open until something asks.
- **Frontend:** not started.
