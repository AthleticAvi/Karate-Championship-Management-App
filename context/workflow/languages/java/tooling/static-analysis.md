# Java static analysis

Applies when: configuring a Java formatter, linter or analyser, Spotless, Checkstyle, Error Prone, NullAway, JaCoCo, ArchUnit, a build failing on layout
Status: enforced
Builds on: `tooling/static-analysis.md`

## Index

- [owners] What each tool owns
- [spotless] Spotless
- [checkstyle] Checkstyle
- [error-prone] Error Prone and NullAway
- [jacoco] JaCoCo
- [archunit] ArchUnit
- [verify] Verify

## [owners] What each tool owns

| Tool | Owns |
|---|---|
| Spotless with google-java-format | Layout |
| Checkstyle, published ruleset | Naming, structure, braces |
| Checkstyle, project ruleset | The project's own language rules |
| Error Prone and NullAway | Correctness, null analysis, API misuse |
| javac `-Xlint` | Compiler warnings |
| JaCoCo | Coverage |
| ArchUnit | Layer boundaries and cycles |

## [spotless] Spotless

- google-java-format has no options. That is why it is chosen.
- It reflows Javadoc too, so a longer path in a comment can fail `spotless:check`.
- Fix with `mvn spotless:apply`, never by hand.

## [checkstyle] Checkstyle

- Vendor the ruleset: see `[vendor-ruleset]` in `tooling/static-analysis.md`.
- Ratchet with `maxAllowedViolations`, set to the current count. It only goes down.
- Set `includeTestSourceDirectory`, so tests hold to the same rules.

## [error-prone] Error Prone and NullAway

- javac has no count ratchet, so findings stay report-only until cleared, then move to `ERROR`.
- The count depends on the command: `compile` sees main sources, `verify` also sees tests.
- Needs JDK-sensitive compiler flags, so the local JDK and CI must match.

## [jacoco] JaCoCo

- Run an agent under both test suites and merge the execution files. One suite alone misleads.
- The floor only rises. It is a floor, not a target.

## [archunit] ArchUnit

- Use the core `archunit` artifact with plain `@Test` methods. It avoids depending on the JUnit
  engine integration across JUnit major versions.
- Wrap rules an existing codebase already breaks in `FreezingArchRule`: recorded violations pass,
  new ones fail, and fixed ones leave the store. This is the ratchet for architecture.
- Keep rules the code already passes unfrozen, so they stay strict.
- Commit the store, and set `freeze.store.default.allowStoreCreation=false`, so a missing store
  fails instead of silently re-recording everything.
- The default matcher ignores line numbers, so ordinary edits do not count as new violations.

## [verify] Verify

- A deliberate violation of each tool's category fails the build or appears in its report.
- No category appears in two tools' reports.
