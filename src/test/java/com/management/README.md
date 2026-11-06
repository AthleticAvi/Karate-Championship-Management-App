# Tests

## Index

- [purpose] Purpose
- [suites] Suffixes decide the suite
- [layout] Layout
- [accounts] Accounts in tests
- [docs-tools] Docs tools

## [purpose] Purpose

Proves the product behaves. How to test is in `context/workflow/testing/`. This file holds what is
specific to this suite.

## [suites] Suffixes decide the suite

Declared in `pom.xml`.

| Suffix | Suite | Phase | Command |
|---|---|---|---|
| `*Test`, `*Tests` | unit and slice | `test` | `mvn test` |
| `*IT` | integration and end-to-end | `integration-test` | `mvn verify` |

Naming an integration test `*Test` runs it in the fast suite with no container behind it. Docker
must be running for `mvn verify`: integration tests start their own MongoDB through Testcontainers.

## [layout] Layout

| Package | Holds |
|---|---|
| `kumitegametests` | Behaviour, controller slice and integration tests. `SecurityRulesSliceTest` holds the role table as decided, `SecurityIT` proves credentials over HTTP, `FirstAdminIT` starts the application with and without an Admin |
| `models`, `rules`, `enums`, `filters`, `config` | Unit tests for those packages |
| `testsupport` | Base classes, builders and fakes: `IntegrationTestBase`, which also supplies the weak MongoDB containers `DatastoreRefusalIT` needs and starts and calls an application by hand, `WebSliceTestBase`, `TestClock`, `KumiteGameBuilder`, `PlayerBuilder`, `FakeRepositories`, `InMemoryMongo` |
| `architecture` | `ArchitectureTest`, the layer rules |
| `docs` | Index generator, commit message check, and the tests that hold the docs to the scaffold |

## [accounts] Accounts in tests

Security is never off. A slice goes through the real chain, and a test that writes names who is
asking with `@WithMockUser`. An integration test signs in with `asAdmin()`, `asScorekeeper()` or
`adminCredentials()`. The password is made up per run, and `IntegrationTestBase` writes both accounts
back after each drop, with a low cost hash, so hundreds of requests stay cheap. An application
started by hand gets the same Admin through `startApplication`.

Logging restarts with each application started by hand, which removes any appender a test added
before the start. Such a test reads `CapturedOutput` instead, or it passes with nothing to check.

## [docs-tools] Docs tools

`docs/IndexGenerator.java` and `docs/CommitMessageCheck.java` are single-file programs that also run
with `java`, so each must stay one file with only JDK imports.

The reference check treats any backticked `.md` path as a committed file, and it reads the working
tree. A git-ignored file therefore passes locally and fails in CI, so write ignored files as plain
text.
