# Maven

Applies when: running a build, two builds at once, a coverage check failing with green tests, Maven phases, surefire, failsafe, test suffixes, git hooks from the build, single-file Java tools, a port still held after stopping a run
Status: enforced
Builds on: `tooling/static-analysis.md`

## Index

- [gate] One command is the gate
- [suites] Suffixes select the suite
- [hooks] Hooks from the build
- [single-file-tools] Repo tools as single-file programs
- [run] Stopping an application Maven started
- [verify] Verify

## [gate] One command is the gate

- `mvn verify` runs every check and both test suites. `mvn test` does not run integration tests.
- Report what actually ran. A green `mvn test` is not a green gate.
- **One build per checkout at a time.** Two builds share `target/`: a second run, even one test,
  rewrites the coverage data the first is still collecting, and the first then fails its coverage
  check with numbers far below normal and every test green. Run the other build in its own clone.

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

## [run] Stopping an application Maven started

- A run goal can start the application in a JVM of its own. Killing the Maven process, as a
  background task stop does on Windows, can leave that JVM running: the port stays held and the
  next start fails with the port in use, or a later check talks to the old build.
- After stopping it, check that nothing listens on the port, and stop the leftover Java process
  if one does.

## [verify] Verify

- `mvn verify` from a clean checkout matches CI.
- A test named with the wrong suffix lands in the wrong suite, so check new test names.
