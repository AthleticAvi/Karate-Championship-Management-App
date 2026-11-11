# Application

## Index

- [purpose] Purpose
- [layers] Layers
- [aggregates] Aggregates
- [boundaries] Layer boundaries
- [not-built] Not built yet

## [purpose] Purpose

The system of record for a Kumite game: athletes, the clock, every score and penalty, and the
result. This README maps the packages. Each shared package has its own README, and each layer has
one in `kumitegame` that covers that layer in every feature.

## [layers] Layers

The code is grouped by feature, `kumitegame`, `athlete`, `referee` and `account`, and each feature is
split by layer. Shared code sits in its own top-level packages.

| Responsibility | Packages | Job |
|---|---|---|
| Gateways | `<feature>.web`, `web`, `filters`, `security` | HTTP in and out. Translate, then delegate |
| Business | `<feature>.service`, `<feature>.domain` | Orchestration, rules, domain types |
| Data | `<feature>.data` | MongoDB access, the stored documents and their mappers |
| Wiring | `config`, `KumiteGameStarter` | Typed settings and application start |

| Package | Holds | README |
|---|---|---|
| `<feature>.web` | The feature's controller, request and response records, and mapper | `web/README.md` |
| `web` | What every feature's API shares: version prefix, role table, CORS, API description, `GlobalExceptionHandler` | `web/README.md` |
| `<feature>.service` | Services | `kumitegame/service/README.md` |
| `<feature>.domain` | Domain types, the vocabulary, the game's rules, and the exception types the feature throws | `kumitegame/domain/README.md` |
| `<feature>.data` | Repositories, stored documents and their mappers | `kumitegame/data/README.md` |
| `athlete` | The athlete aggregate | `athlete/README.md` |
| `referee` | The referee aggregate, and the domain, document and mapper split every feature follows | `referee/README.md` |
| `filters`, `security`, `config` | Shared, each with its own README | |

The game's rules sit in `kumitegame.domain`: the aggregate refuses what is impossible, and a
rule class per warning reads the game's settings. There is no `rules` package until the system
suggests results.

`[package-layout]` in `context/workflow/web-frameworks/spring-boot/standards/standards.md` names
`web`, `domain` and `data` only. `service` is kept as a layer of its own, because the Spring Boot
layer contract does not let domain code call persistence, and the services do.

Flow: `Filter -> Security -> Controller -> Service -> Repository -> MongoDB`. There are no outbound
gateways yet.

`KumiteGameStarter` sits at the root of the package tree. Component scanning, repository discovery
and test slice configuration all derive from its location, so no `scanBasePackages` or
`@EnableMongoRepositories` arguments are needed.

## [aggregates] Aggregates

Four aggregate roots, one per feature: **KumiteGame** in `kumitegame`, **Athlete** in `athlete`,
**Referee** in `referee`, **Account** in `account`. Each has a controller, service and repository.
Nothing ties an account to a game, except the name of the account that recorded each entry.

A game holds athlete and referee **ids**, in its assignment entries. Names are looked up when the
game is read, never copied: one fact, one owner. Scores and penalties live on the game alone, so
every action is one write to one document.

`kumitegame` may use `athlete` and `referee`; no other feature uses another, and `ArchitectureTest`
enforces it. Refusing to delete an athlete or referee a game names needs the games, so the athlete
and referee services each own an interface, `AthleteReferences` and `RefereeReferences`, and
`kumitegame.service.GameReferences` implements both: the dependency is inverted, not reversed.

## [boundaries] Layer boundaries

`src/test/java/com/management/architecture/ArchitectureTest.java` enforces the layers inside
`mvn verify`. Every rule is strict: no rule is frozen and `config/archunit/frozen` holds no
violation. A rule the code cannot yet pass may be frozen there, its known violations tolerated and
new ones failing the build.

Every feature's `domain` holds no Spring Data type: each feature stores a document of its own,
mapped in its `data` package. See `[pattern]` in `referee/README.md`.

The store only changes on purpose. A fixed violation fails the build until you run
`mvn test -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreUpdate=true` and commit
the store.

Documentation types from the API description library stay in the web packages, never in a mapper. A
strict rule fails on any other class using them. Spring Security types stay in `security`, the
controllers and the shared `web` the same way.

The cycle rule gives each layer of each feature a slice, such as `kumitegame.domain`, and each shared
package one. No cycle is accepted. A feature is any top-level package split into layers, so a new
one falls under the feature rule without editing the test.

Exception types sit in the `domain` package of the feature that throws them, and
`GlobalExceptionHandler`, the gateway, in `web`.

## [not-built] Not built yet

- **SENSHU records, sign-off (APPROVE, REOPEN, VOID), the suggested result and the public view:**
  later issues of the Structure and engine milestone.
- **The round-robin effective score** after a forfeit: the setting exists, nothing reads it yet.
- **ETag on reads:** planned, not built.
- **Push to screens:** a screen reads the game after each action. No WebSocket or Server-Sent Events.
- **Frontend:** not started.
