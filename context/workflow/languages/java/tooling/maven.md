# Maven

Applies when: running a build, Maven phases, surefire, failsafe, test suffixes, git hooks from the build, single-file Java tools
Status: enforced
Builds on: `tooling/static-analysis.md`

## Index

- [gate] One command is the gate
- [suites] Suffixes select the suite
- [hooks] Hooks from the build
- [single-file-tools] Repo tools as single-file programs
- [verify] Verify

## [gate] One command is the gate

- `mvn verify` runs every check and both test suites. `mvn test` does not run integration tests.
- Report what actually ran. A green `mvn test` is not a green gate.

## [suites] Suffixes select the suite

- `*Test` and `*Tests` run under surefire. `*IT` runs under failsafe.
- Declare the mapping in the POM rather than inheriting tool defaults.
- **Select several classes with commas:** `-Dtest=ATest,BTest`, `-Dit.test=AIT,BIT`. A `+` joins
  methods of one class (`ATest#one+two`), so `ATest#one+BTest` runs `ATest` alone and the other
  class is skipped without a word. A proof that names two classes this way proves only the first.

## [hooks] Hooks from the build

- `git-build-hook-maven-plugin` sets `core.hooksPath` to a versioned folder on the first build.
- Pin the folder to LF in `.gitattributes`. A CRLF in the shebang breaks the hook on Windows.
- Prefer pre-push to pre-commit for formatters. A formatter in pre-commit has to stage files
  mid-commit, which silently commits unstaged edits in those files.

## [single-file-tools] Repo tools as single-file programs

- A tool with no dependencies can live under `src/test/java` and run with
  `java path/to/Tool.java`, using the JDK the project already needs.
- Living in test sources gets it compiled, formatted, analysed and tested with everything else.
- Keep it to one file with only JDK imports, or the launcher cannot run it.

## [verify] Verify

- `mvn verify` from a clean checkout matches CI.
- A test named with the wrong suffix lands in the wrong suite, so check new test names.
