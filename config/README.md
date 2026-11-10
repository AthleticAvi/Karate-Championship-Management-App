# Quality gates

## Index

- [purpose] Purpose
- [registry] Tool registry
- [coverage] Coverage
- [change] How to change each tool
- [ratchet] The ratchet
- [hooks] Git hooks
- [ci] CI jobs
- [not-sonar] Why not SonarQube
- [mcp] MCP tooling

## [purpose] Purpose

The tool registry for this repo: what runs, where it is configured, and the current baselines. The
generic rules are in `context/workflow/tooling/static-analysis.md`.

## [registry] Tool registry

All run inside `mvn verify`. No two tools have authority over the same category: a finding reported
twice is a bug in this setup, not thoroughness.

| Tool | Owns | Configured in | Mode | Baseline |
|---|---|---|---|---|
| **Spotless** + google-java-format | Layout | `pom.xml`, `spotless-maven-plugin` | **Enforced.** `mvn spotless:apply` fixes | 0 |
| **Checkstyle** (published) | Conventions: naming, structure, braces, star imports | `config/checkstyle/google-checks-vendored.xml` | **Enforced** | 0 |
| **Checkstyle** (project) | The language standard's own rules: logger naming, `printStackTrace`, `Optional` misuse | `config/checkstyle/project-standards.xml` | Ratchet | **3** |
| **Error Prone + NullAway** | Correctness: null analysis, API misuse, time and locale bugs | `pom.xml`, `maven-compiler-plugin` | Report only | **56** across main and test (39 NullAway) |
| **javac** | Compiler warnings | `pom.xml`, `-Xlint:all,-serial` | Report only | 0 |
| **JaCoCo** | Coverage, merged across both suites | `pom.xml`, `jacoco-maven-plugin` | **Enforced floor** | LINE **90%**, BRANCH **80%** |
| **ArchUnit** | Layer boundaries and cycles | `ArchitectureTest`, `config/archunit/frozen` | Strict, plus frozen ratchet | 2 frozen rules |
| **Docs tests** | Indexes, scaffold, generic workflow, em dashes, Java version in step | `RepositoryDocsTest` | **Enforced** | 0 |
| **Enforcer** | The JDK running the build | `pom.xml`, `maven-enforcer-plugin` | **Enforced**, exact major | n/a |

The Java version is in two files, tied by a test. `java.version` in `pom.xml` drives the build and
the Enforcer rule. `.java-version` is what `actions/setup-java` reads, because that action cannot read
a pom. To move versions, change both. Direct goals such as `mvn spotless:check` skip Enforcer.

## [coverage] Coverage

Measured across both suites: an agent runs under surefire and another under failsafe, the execution
files merge, and one report goes to `target/site/jacoco/`. Download the `coverage-report` artifact
from any CI run, or open `index.html` locally. Most of this codebase is only reachable through
integration tests, so one suite alone misleads.

The floor only rises. Raise it in `pom.xml` as coverage rises, never lower it. What remains
uncovered is mostly the game lifecycle methods, which have no endpoints yet.

## [change] How to change each tool

- **Formatting:** not configurable. google-java-format has no options.
- **Published conventions:** edit the vendored ruleset. Its header explains what was removed from
  upstream and how to re-vendor.
- **Project rules:** edit `project-standards.xml`. Every module cites the standard it mechanises.
- **Correctness:** toggle Error Prone checks with `-Xep:CheckName:OFF|WARN|ERROR` in compiler args.
- **Null marking:** every main package has `@NullMarked` in `package-info.java`, except each
  feature's `data` package, which is `@NullUnmarked`: see
  `src/main/java/com/management/kumitegame/data/README.md`. `util` marks its class instead, because
  the frozen `noUtilityPackage` rule would count a new `package-info` as a violation.
- **Suppressions:** `config/checkstyle/suppressions.xml`, one entry per reason. One entry today: the
  framework's retry interface names `shouldRetry`, which the boolean accessor rule cannot allow.
- **Layer rules:** `ArchitectureTest`. To change the store, see `[boundaries]` in
  `src/main/java/com/management/README.md`.

## [ratchet] The ratchet

`maxAllowedViolations` is the current baseline, so the build fails if the count rises. Lower it as
findings are fixed: it never goes up. Error Prone has no count ratchet in javac, so it stays
report-only until its findings clear and NullAway can move to `ERROR`. Its count depends on the
command: `mvn compile` sees main sources only, `mvn verify` also compiles tests. A run with nothing
to recompile prints no findings, so count after `mvn clean verify`.

## [hooks] Git hooks

The build sets `core.hooksPath` to the versioned `hooks/` folder on the first `mvn` run.

- `hooks/pre-push` runs `spotless:check` and names `mvn spotless:apply` as the fix.
- `hooks/commit-msg` runs the commit message check.

Both are conveniences: CI is what protects the repository. Only the formatter runs on push, because
analyser findings are not auto-fixable. It reports rather than fixes because a formatter in
pre-commit has to stage files mid-commit, which silently commits unstaged edits. `.gitattributes`
pins `hooks/**` to LF. To stop using hooks: `git config --unset core.hooksPath`.

## [ci] CI jobs

One job per concern, in `.github/workflows/ci.yml`. Together they are `mvn verify` plus the commit
message check.

| Job | Runs |
|---|---|
| Static checks | Enforcer, Spotless, Checkstyle, compile with Error Prone |
| Commit messages | The commit message check, on pull requests |
| Unit tests | The `*Test` suite |
| Integration tests | The `*IT` suite |
| Coverage | Merges the data from both suites and checks the floor, after both |
| Build and test | Passes only if the jobs above did |

`Build and test` is the one required status check on `main`, so keep that name. The coverage job
recompiles rather than sharing classes, which works because the same source and JDK give the same
class files. If coverage ever drops to near zero with the suites green, look there first.

## [not-sonar] Why not SonarQube

It would duplicate most of the above, and SonarSource has deprecated 158 Checkstyle and PMD rules as
redundant with SonarJava. Its value here would be the PR gate, but GitHub withholds secrets from
`pull_request` runs from forks, and most PRs here come from forks. The workaround,
`pull_request_target`, hands secrets to fork code: see the warning in `.github/workflows/ci.yml`.
Revisit only if contribution stops coming through forks.

## [mcp] MCP tooling

Context7 is the only MCP needed. Use it for current docs on Spring Boot 4.1, Spring Data MongoDB,
Jakarta Validation and Spring Security before relying on training knowledge.

MongoDB MCP is intentionally not connected: Compass covers live inspection. Reconsider when debugging
real data corruption, writing a data migration, optimising queries for the Championship layer, or
when Testcontainers tests need mid-test inspection. Do not propose other MCPs unless asked.
