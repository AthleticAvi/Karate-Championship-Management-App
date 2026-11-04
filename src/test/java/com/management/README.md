# Tests

## Index

- [purpose] Purpose
- [suites] Suffixes decide the suite
- [layout] Layout
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
| `kumitegametests` | Behaviour, controller slice and integration tests |
| `models`, `rules`, `enums` | Unit tests for those packages |
| `testsupport` | Base classes, builders and fakes: `IntegrationTestBase`, `WebSliceTestBase`, `KumiteGameBuilder`, `PlayerBuilder`, `FakeRepositories`, `InMemoryMongo` |
| `architecture` | `ArchitectureTest`, the layer rules |
| `docs` | Index generator, commit message check, and the tests that hold the docs to the scaffold |

## [docs-tools] Docs tools

`docs/IndexGenerator.java` and `docs/CommitMessageCheck.java` are single-file programs that also run
with `java`, so each must stay one file with only JDK imports.
