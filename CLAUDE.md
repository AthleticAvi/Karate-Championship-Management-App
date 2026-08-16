# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Hard Rules

1. **Reuse first, framework first.** If Spring Boot or the JDK already does it, use it. A hand-rolled equivalent of a framework feature is rejected regardless of quality. Establish what the framework provides *before* writing a mechanism.
2. **Every finding goes to the engineer, not to a file and not to the tracker.** Bugs, missing features, refactors, enhancements — surface them and stop. The engineer decides whether it becomes a GitHub issue.
3. **No new markdown files in the project.** Work items live as GitHub issues; reusable knowledge lives in `workflow/`. Nothing else.
4. **Architecture and scope decisions are made by the engineer**, outside Claude Code. Implement the decision; do not make it.
5. **One thing at a time**, confirmed working and signed off before the next starts.

## workflow/

`workflow/` is the project-agnostic engineering knowledge base: how this team builds Spring Boot services, not what this service does. It contains no project names, classes, files, or issue numbers, and everything in it is intended to transfer to the next project unchanged.

Read only what the task needs — each file is written to be found by grep and read alone. Every file opens with an `Applies when:` line naming its triggers.

| Folder | Contents |
|---|---|
| `workflow/standards/` | Rules that apply to all code — `java.md`, `spring-boot.md`, `enforcement.md` |
| `workflow/patterns/` | One file per pattern: how to build a given thing correctly, with the framework-first check, do/don't, and verification |

Consult it before designing, and record in it anything learned the hard way — generalised, with no project references.

## Domain Rules

Settled rules. Anything not listed here is either unbuilt (tracked as a GitHub issue) or undecided — ask, do not invent.

**Vocabulary** — WKF Kumite terms are authoritative.

| Concept | Values |
|---|---|
| Player colours | AKA = RED, AO = BLUE. Exactly one of each per game. |
| Scoring | IPPON = 3, WAZA-ARI = 2, YUKO = 1 |
| Game durations | 90s / **120s default** / 180s, loaded from config |
| Game states | QUEUED, RUNNING, PAUSED, FINISHED |

**Valid state transitions.** Anything not in this table is illegal.

| From | To |
|---|---|
| QUEUED | RUNNING |
| RUNNING | PAUSED, FINISHED |
| PAUSED | RUNNING, FINISHED |

The table is enforced. Each lifecycle method in `KumiteGameService` names its allowed starting states (start: QUEUED; pause: RUNNING; resume: PAUSED; end: RUNNING or PAUSED — the verbs are narrower than the state machine, since only a paused match can be *resumed*), and an illegal call surfaces as HTTP 409 via `IllegalStateTransitionException`. The full transition table also lives as data on `GameState` (`canTransitionTo`) for the planned Game Orchestrator.

**Known vocabulary deviations in code** (each has an issue):
- `PointsType.WAZARI` is missing the hyphen of the WKF's **WAZA-ARI**. It is public API surface — clients send `pointType=WAZARI`. #102 renamed `YOKO` to `YUKO` on the same grounds, so this is the last spelling left out of step with the rulebook.
- `PlayerColor` has only `RED` / `BLUE`; the AKA/AO names appear nowhere in code.
- `FoulTypes` (`CHUI1, CHUI2, CHUI3, HANSOKU_CHUI, HANSOKU, SHIKKAKU`) is declared but **never referenced**. Fouls are currently a plain counter with no progression.
- The project wiki calls the in-progress state `STARTED`; the code says `RUNNING`. **The code is authoritative.**

## Stack

- Java: **21** everywhere — `<java.version>` in `pom.xml`, the local JDK, and the CI toolchain. Do not let these diverge; the build carries JDK-sensitive compiler flags for Error Prone, and a divergence makes them untestable locally.
- Spring Boot **4.1.0**, Maven, embedded Tomcat. Jackson 3, JUnit 6, Testcontainers 2, MongoDB driver 5.8 all arrive with this BOM.
- MongoDB 7 in Docker — `localhost:27017`, database `kumitedb`, standalone (**not** a replica set, so multi-document transactions are unavailable)
- Tooling: IntelliJ IDEA, Postman, MongoDB Compass
- **`mvn verify` is the quality gate** — it runs every check below plus both test suites. `mvn test` runs only the fast suite and does **not** run integration tests.
- Docker must be running for `mvn verify` — integration tests start their own MongoDB via Testcontainers.
- Frontend: not started

## Commands

MongoDB must be running in Docker before starting the application.

```bash
# Run the application
mvn spring-boot:run

# Build
mvn clean install

# THE GATE — runs both suites. Requires Docker.
mvn verify

# Fast suite only (unit + slice). Does NOT run integration tests.
mvn test

# Run a single test class
mvn test -Dtest=KumiteGameReloadTest

# Run a single test method
mvn test -Dtest=KumiteGameReloadTest#startGame_setsTheGameRunning

# Select a single integration test (the fast suite still runs first)
mvn verify -Dit.test=ActuatorHealthIT
```

**Test suffixes decide which suite a test runs in**, and the mapping is declared explicitly in `pom.xml`:

| Suffix | Suite | Phase | Command |
|---|---|---|---|
| `*Test`, `*Tests` | unit and slice | `test` | `mvn test` |
| `*IT` | integration and end-to-end | `integration-test` | `mvn verify` |

Naming an integration test `*Test` runs it in the fast suite with no container behind it. See `workflow/patterns/testing-strategy.md`.

### Quality gates — the tool registry

Three engines, three non-overlapping jobs. All run inside `mvn verify`; gate order follows `workflow/standards/enforcement.md`. **No two tools have authority over the same category** — if a finding is reported twice, that is a bug in this setup, not thoroughness.

| Tool | Owns | Configured in | Mode | Baseline |
|---|---|---|---|---|
| **Spotless** + google-java-format | **Layout** — indentation, wrapping, import order, whitespace | `pom.xml` → `spotless-maven-plugin` | **Enforced.** Build fails; `mvn spotless:apply` fixes | 0 |
| **Checkstyle** (published) | **Conventions** — naming, structure, braces, star imports | `config/checkstyle/google-checks-vendored.xml` | Ratchet | **17** |
| **Checkstyle** (project) | **`java.md`'s own rules** — logger naming, `printStackTrace`, `Optional` misuse | `config/checkstyle/project-standards.xml` | Ratchet | **3** |
| **Error Prone + NullAway** | **Correctness** — null analysis, API misuse, time/locale bugs | `pom.xml` → `maven-compiler-plugin` `compilerArgs` + `annotationProcessorPaths` | Report only | **45** across main and test (26 NullAway) |
| **javac** | Compiler warnings | `pom.xml` → `-Xlint:all,-serial` | Report only | 0 |
| **JaCoCo** | **Coverage** — merged across both suites | `pom.xml` → `jacoco-maven-plugin` | **Enforced floor** | LINE **80%**, BRANCH **64%** |

```bash
mvn spotless:apply    # fix formatting — run this if the build fails on layout
mvn verify            # everything, including the coverage report
```

**Coverage** is measured across *both* suites: an agent runs under surefire and another under failsafe, the two execution files are merged, and one report is written to `target/site/jacoco/`. Open `index.html` from there, or download the `coverage-report` artifact from any CI run. A number from one suite alone is misleading — most of this codebase is only reachable through integration tests.

The floor is enforced and **ratchets upward only**: raise it in `pom.xml` as coverage rises, never lower it. It is a floor, not a target — `workflow/patterns/testing-strategy.md` lists what must *not* be tested, and coverage bought that way is a worse suite with a better number. #37 raised it from LINE 37% / BRANCH 15% by covering every controller endpoint and every branch of the exception handler — the error paths were the untested branches. The #32 review fixes took it further, to a measured 81% / 67%, by covering the legacy-winner converter, the write-path colour guard and the non-leaking `IllegalArgumentException` handler. What remains uncovered is mostly the game lifecycle methods, which have no endpoints yet.

**How to change each one**

- **Formatting rules** — not configurable by design. google-java-format has no options; that is why it was chosen.
- **Published conventions** — edit the vendored ruleset. Its header explains what was removed from the upstream `google_checks.xml` and why, and how to re-vendor after a Checkstyle upgrade.
- **Project rules** — edit `project-standards.xml`. Every module there cites the `java.md` rule it mechanises. New rule in `java.md` → new module here.
- **Correctness** — Error Prone checks are toggled with `-Xep:CheckName:OFF|WARN|ERROR` in the compiler args.
- **Suppressions** — `config/checkstyle/suppressions.xml`, one entry per reason. A suppression with no reason gets rejected in review.

**The pre-push hook.** `hooks/pre-push` runs `spotless:check` before anything leaves the machine and refuses the push if formatting is off, naming `mvn spotless:apply` as the fix. It installs itself: the build sets `core.hooksPath` to the versioned `hooks/` directory, so it arrives on the first `mvn` run rather than needing per-clone setup.

It is a **convenience, not a gate** — bypassable with `git push --no-verify`, and absent until someone runs a build. CI remains the thing that actually protects the repository. Only the formatter runs there; analyser findings are not auto-fixable, so a hook could only block on them. To stop using hooks: `git config --unset core.hooksPath`.

**It reports rather than fixes, and that is why it is on push.** A pre-commit hook that formats has to put the result back into the commit, and the only way to do that is to stage files mid-commit. `git add <path>` stages the *whole working-tree* version, so the previous hook silently committed unstaged edits sitting in an already-staged file — and reformatted files the commit was not about. A pre-push hook has no such job. It also runs once per push rather than once per commit, so local commits stay cheap. `.gitattributes` pins `hooks/**` to LF, because `core.autocrlf` on a Windows clone would otherwise put a carriage return in the shebang and break the hook on the machines most likely to run it.

**The ratchet.** `maxAllowedViolations` is set to the current baseline, so the build fails if the count *rises*. Lower it as findings are fixed; it never goes up. Error Prone has no count ratchet in javac, so it stays report-only until its findings are cleared and NullAway can move to `ERROR`. Note its count depends on the command: `mvn compile` sees main sources only, `mvn verify` also compiles tests.

**Deliberately not used: SonarQube.** It would duplicate most of the above — SonarSource has itself deprecated 158 Checkstyle/PMD rules as redundant with SonarJava. More decisively, its value here would be the PR gate, and GitHub withholds secrets from `pull_request` runs originating in forks. Most PRs on this repo come from forks, so Sonar would silently skip them. The usual workaround is `pull_request_target`, which hands secrets to fork-authored code — see the warning in `.github/workflows/ci.yml`. Revisit only if contribution stops coming through forks.

- Main class: `com.management.KumiteGameStarter`, at the root of the package tree — component scanning, repository discovery and the test slices' configuration search all derive from its location, so no `scanBasePackages` or `@EnableMongoRepositories` arguments are needed
- Spring Boot 4.1.0, Java 21 (pinned via `<java.version>` in `pom.xml`; CI provisions the same)
- Windows dev setup: see `karate-app-dev-setup-windows.pdf` in the repo root.

## MCP Tooling

**Context7 is the only MCP needed for Spring Boot development on this project.** Use it to fetch current docs for Spring Boot 4.1, Spring Data MongoDB, Jakarta Validation, Spring Security, and any other library/framework before relying on training knowledge.

**MongoDB MCP is intentionally NOT connected.** MongoDB Compass covers the rare moments live data inspection is needed during current-phase backend work. Reconsider connecting MongoDB MCP when any of these become true:
- Debugging a real data-corruption incident (e.g. after the dual-write/snapshot fix lands)
- Writing or verifying a data migration (e.g. backfilling `@Version` fields for optimistic locking)
- Optimizing queries / designing indexes for the Championship layer
- Building Testcontainers integration tests that need mid-test collection inspection

Do not propose adding other MCPs (JetBrains, Postman, Docker, etc.) unless the user asks.

## Architecture

4-layer: `Controller → Service → Repository → MongoDB`

There are two aggregate roots with their own controller/service/repository stack: **KumiteGame** and **Player**.

**How scoring works across aggregates:** Points and fouls are stored on `Player` (persisted via `PlayerRepository`). When a point or foul is recorded, `PlayerService` mutates and saves the `Player`, then calls `GameHelperService.updateKumiteGame()` to re-sync the player snapshot embedded inside `KumiteGame`. The `KumiteGameController` routes these mutations to `PlayerService`, not `KumiteGameService`.

**GameTimer is `@Transient`** — it is never persisted to MongoDB. `KumiteGame.getTimer()` rebuilds it on demand from the two persisted values, `remainingTime` **and** `startTime` — both, because a timer rebuilt without its `startTime` treats `pause()` as a no-op and silently freezes the clock. No caller has to (or can) initialise the timer explicitly; the one rule is to read the timer before mutating the fields it rebuilds from. The clock clamps at zero, never negative.

**Circular dependency:** `KumiteGameService` and `PlayerService` mutually depend on each other. `GameHelperService` breaks this cycle as a delegating intermediary, injected via a `@Lazy` constructor parameter in both services. This is a workaround, not a pattern — #53 retires it; see `workflow/patterns/service-interaction.md` before touching any of these three services.

**Game lifecycle endpoints do not exist yet.** `startGame`, `pauseGame`, `resumeGame`, and `endGame` are implemented in `KumiteGameService` but have no controller mappings. They are intentionally withheld pending timer implementation.

## Key Wiring to Know

- `KumiteGameController` at `/api/kumitegame` — owns game creation, retrieval, point/foul mutations, and winner assignment
- `PlayerController` at `/api/players` — owns player CRUD
- `PointsType` enum carries its `PointStrategy` instance — `PointStrategy` declares `addPoint(Points)` / `removePoint(Points)`, which mutate the score in place. There is no method that returns a value.
- `GameProperties` (a `@ConfigurationProperties("game")` record, validated at startup) binds game durations from `game.*` in `application.properties` — durations are configuration, not code
- MongoDB connection is configured via environment variables (`MONGO_HOST`, `MONGO_PORT`, `MONGO_DB`), defaulting to `localhost:27017/kumitedb`. They bind through **`spring.mongodb.*`**, not `spring.data.mongodb.*` — Boot 4 split that namespace into driver-level (`spring.mongodb`) and repository-level (`spring.data.mongodb`). Both still resolve, so using the wrong one fails silently by falling back to the default host.

## The API Response Contract

Settled in Epic #32. Persistence types do not cross the HTTP boundary in either direction; `KumiteGameMapper` and `PlayerMapper` are the only places a document becomes a response.

| Response type | Endpoint | Shape |
|---|---|---|
| `KumiteGameResponse` | everything under `/api/kumitegame` | `id`, `gameState`, `remainingSeconds`, `red`, `blue`, `referees`, `winner` |
| `PlayerSummary` | nested as `red` / `blue` | `id`, `name`, `points`, `fouls` |
| `PlayerResponse` | `/api/players` | `id`, `name`, `points`, `fouls` |

**The wire formats, and why.** Every one of these is pinned by a strict whole-body assertion in `KumiteGameControllerSliceTest` — change one and that test fails, which is the only thing standing between this contract and a silent shift under a dependency upgrade.

- **The clock is `remainingSeconds`, a whole number of seconds.** Not a `Duration`, which Jackson can render as `PT1M27S` or as a decimal depending on configuration nobody on this project has set. An integer has one reading, needs no `spring.jackson.*` property, and drives a countdown directly.
- **No timestamp is exposed at all.** `startTime` is internal bookkeeping for computing elapsed time; a client holding `remainingSeconds` has no use for it. Omitting a field is the cheapest way to avoid specifying its format. If a timestamp is ever genuinely needed, it goes out as an ISO-8601 UTC string — decided here so the question is not reopened.
- **`winner` is a `PlayerColor` or `null`.** Never a sentence, never a placeholder string. The client renders the wording.
- **Points and fouls are bare integers.** The `Points` and `Foul` wrappers exist so scoring strategies have something to mutate in place; that is an implementation detail of scoring.
- **Referees are names.** A `List<String>`, not a list of objects, until a referee has more than a name.
- **Enums serialise as their names**, uppercase, exactly as declared.

**Mutations return the new state.** The four scoring endpoints answer with the updated `KumiteGameResponse` rather than a confirmation sentence, so a scoreboard never needs a follow-up read. `DELETE /api/players/{id}` answers 204 with no body.

**Errors are RFC 9457 problem details** — `application/problem+json`, carrying `status`, `title` and `detail`. `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`, so the exceptions the framework itself raises are mapped too rather than falling through to the catch-all as 500s.

| Failure | Status | Exception | Detail |
|---|---|---|---|
| Request fails Bean Validation | 400 | `MethodArgumentNotValidException` (framework) | generic, plus an `errors` extension property mapping each failing field path to its message |
| Not a colour (`color=purple`) | 400 | `InvalidPlayerColorException` | the message |
| Not a point type | 400 | `PointTypeNotFoundException` | the message |
| A request this app validated and rejected | 400 | `InvalidGameRequestException` | the message |
| Any other illegal argument, incl. `NumberFormatException` | 400 | `IllegalArgumentException` | **generic**, message logged |
| A lifecycle call the match's state forbids | 409 | `IllegalStateTransitionException` | the message |
| No such match | 404 | `GameNotFoundException` | the message |
| No such fighter, or a real colour this match does not field | 404 | `PlayerNotFoundException` | the message |
| Anything else | 500 | catch-all | **generic**, message logged |

The two colour failures are deliberately different types: *not a colour* is the caller's mistake (400), *a colour this match does not have* is a missing resource (404).

**Requests are validated at the boundary.** The request DTOs are records carrying Jakarta Bean Validation constraints, triggered by `@Valid` on the `@RequestBody` parameters; `spring-boot-starter-validation` supplies the engine. `gameDuration` is an `Integer` (seconds) — non-numeric input dies at binding as a 400, never as a hand-parse failure. What constraints cannot see — the one-RED-one-BLUE rule — stays domain validation in `KumiteGameService`.

**Only messages this project wrote are returned.** `InvalidGameRequestException` marks validation whose wording is meant for the caller, so its detail is echoed. A bare `IllegalArgumentException` can come from anywhere beneath the controller — Spring Data's *"The given id must not be null"*, an enum conversion failing on a legacy document — so it still answers 400 as #36 requires, but with a fixed detail and the real message in the log. Echoing it blamed the caller for a server fault and leaked internal text through the same handler that the catch-all is careful not to.

**A match is validated before anything is written.** Exactly one RED and one BLUE is checked in `KumiteGameService` at creation, not just in `KumiteGameMapper` on the way out. A duplicate colour used to persist both fighters and the half-formed match, then fail out of the mapper as a 500 with no id returned — unrecoverable, since a standalone MongoDB gives no transaction to roll back. The mapper keeps its own check: this one guards what enters the database, that one guards what leaves it.

**Documents written before the `winner` field became a `PlayerColor` are still readable.** `LegacyWinnerConverter`, bound to that one property with `@ValueConverter`, reads the old display sentence (`"RED player: Kenji"` → `RED`, `"Pending game ending"` → `null`) and always writes the enum name, so each document migrates itself on its next save. Without it `Enum.valueOf` fails and the whole match becomes unloadable. `LegacyWinnerIT` inserts raw pre-change BSON to prove it, because every other test writes its fixtures through the current mapping and so can never see the old form. Delete the converter once no legacy documents remain.

## What Is Not Built Yet

- **Timer lifecycle wiring** — `GameTimer` class exists with basic structure and a test, but is not yet integrated into game lifecycle methods. WebSocket push to frontend not implemented. Persistence strategy for `remainingTime` recalculation on point/foul events not implemented.
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
