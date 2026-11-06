# Karate Championship Management

The system of record for a Kumite bout: fighters, the clock, every point and foul, and the result,
with the rules of the sport encoded.

This file is a router. Pick up context before anything else, and make no assumptions.

**Start with `context/workflow/README.md`.** It covers how to classify work, modes, how to look
things up, and where knowledge belongs. Generic how-to lives there. Project facts live in the domain
READMEs below.

## Route by domain

| Need | Read |
|------|------|
| Package map, layers, frozen violations, what is not built | `src/main/java/com/management/README.md` |
| API, response contract, wire formats | `src/main/java/com/management/controllers/README.md` |
| Correlation IDs, request logging | `src/main/java/com/management/filters/README.md` |
| Errors and status codes | `src/main/java/com/management/exceptions/README.md` |
| Rules engine, how a match ends | `src/main/java/com/management/rules/README.md` |
| Scoring across aggregates, concurrency | `src/main/java/com/management/services/README.md` |
| Domain types, timer, versioned documents | `src/main/java/com/management/models/README.md` |
| Vocabulary, state transitions, known deviations | `src/main/java/com/management/enums/README.md` |
| Rule values and configuration | `src/main/java/com/management/config/README.md` |
| MongoDB setup | `src/main/java/com/management/repositories/README.md` |
| Test suites and layout | `src/test/java/com/management/README.md` |
| Quality gates, baselines, hooks, MCP | `config/README.md` |
| Contributor rules | `CONTRIBUTING.md` |
| Business rules and where they come from | `[source]` in `src/main/java/com/management/rules/README.md` |

Files use `## [tag] Title` sections and a generated `## Index`. Grep the tag and read that section:
`grep -n "^## \[" <file>`.

## Layers

| Responsibility | Packages |
|-----------|----------|
| Gateways | `controllers`, `filters`, `dto`, `mappers`, `exceptions` handler |
| Business | `services`, `rules`, `models`, `enums`, `exceptions` types |
| Data | `repositories`, `models/converters` |
| Wiring | `config`, `KumiteGameStarter` |

`Controller -> Service -> Repository -> MongoDB`. Two aggregates, **KumiteGame** and **Player**.
Services depend one way: `KumiteGameService -> PlayerService`. `ArchitectureTest` enforces this.

## Stack

Java **25** everywhere (`pom.xml`, `.java-version`, local JDK, CI). Enforcer fails any other JDK. Spring Boot **4.1.2**, Maven, embedded Tomcat.
Jackson 3, JUnit 6, Testcontainers 2, MongoDB driver 5.8. MongoDB 8.0 in Docker, a one-member replica set. springdoc
3.2 for the API description, which brings Jackson 2 beside Jackson 3. Frontend not started.

## Commands

MongoDB must be running in Docker to start the application. Docker must be running for `mvn verify`.

```
mvn spring-boot:run                  run the application
mvn verify                           THE GATE: every check and both test suites
mvn test                             fast suite only, no integration tests
mvn test -Dtest=ClassName#method     one unit or slice test
mvn verify -Dit.test=ClassName       one integration test, after the fast suite
mvn spotless:apply                   fix formatting
java src/test/java/com/management/docs/IndexGenerator.java    regenerate indexes after editing docs
```

## Hard rules

1. **Reuse first, framework first.** If Spring Boot or the JDK already does it, use it. A
   hand-rolled equivalent is rejected regardless of quality. See `[reuse]` in
   `context/workflow/design/principles.md`.
2. **Every finding goes to the engineer**, not to a file and not to the tracker. Bugs, missing
   features, refactors, enhancements: surface them and stop. The engineer decides on an issue.
3. **No session artefacts.** No plan, handoff, next-session prompt or notes files. Anything that
   outlives a session is a GitHub issue. Markdown is for domain READMEs, `context/workflow/` and
   root docs only.
4. **Architecture and scope decisions are made by the engineer**, outside Claude Code. Implement
   the decision; do not make it.
5. **One thing at a time**, confirmed working and signed off before the next starts.
6. **Design deliberately.** Answer the design check in `context/workflow/delivery/planning.md`.
   SOLID, KISS, reuse and patterns are in `context/workflow/design/`.
7. **Gateways, business and data never mix.** See `context/workflow/design/layers.md`.
8. **`context/workflow/` stays generic:** project agnostic everywhere, and stack agnostic outside
   its technology folders. A test checks for known terms.
9. **Record knowledge last.** Before every PR, ask what a future agent would not know.

## Never

- Hardcode durations, point thresholds or rule values. All come from config.
- Expose domain models through the API. Always use DTOs.
- Put business logic in a controller.
- Reintroduce a service cycle, a `@Lazy` injection, or a second stored copy of a fighter.
- Start a new feature until the current one is built, tested and signed off.
