# Contributing

Thanks for your interest. This is a personal project built to a deliberate standard, and contributions are welcome when they meet it. This document tells you exactly what that means so you don't waste your time.

Read it before opening a pull request. Most rejections happen because it wasn't read.

## Before you write any code

1. **Find an open issue.** Every change starts from one. If you want to work on something that isn't an issue, open one and describe it — do not open a pull request for unplanned work.
2. **Comment on the issue and ask for it.** Say briefly how you intend to approach it.
3. **Wait for the maintainer to confirm the issue is yours.** GitHub's assignee field cannot be used for people outside the repository, so a comment from the maintainer saying the issue is yours **is** the assignment. Not every request is granted, and issues are sometimes reserved.

   **A pull request opened without that confirmation will be closed, however good the work is.** This is not about the quality of your change. Several people asking for the same issue and two of them writing the same patch wastes their evening and mine, and the only way to prevent it is for claims to be settled before work starts.
4. **One issue at a time, and one open pull request at a time.** Finish or withdraw from your current issue before claiming another.

If you go quiet for a week without an update, the issue is released so someone else can take it. Just say so if you need more time — that's always fine.

**Triage happens at least once a week.** Issue claims, review comments and pull requests are picked up in that window, often sooner. This is a side project, so if you have heard nothing after a week, it is fine to comment again and nudge.

## What gets merged

A pull request is merged when all of these hold:

- It resolves exactly one issue.
- The diff does what the description says it does — no more, no less.
- The change is covered by tests that would fail without it.
- `mvn verify` passes.
- It follows the standards in [`workflow/`](workflow/).
- It touches no file that the issue does not require.

## What gets declined

Being direct about this saves everyone time:

- **Cosmetic-only changes.** Whitespace, typo-only edits, comment reflows, and reformatting are not accepted as standalone contributions. This is not a repository for padding a contribution graph.
- **A description that does not match the diff.** If the pull request claims work that isn't in the changed files, it is closed without further review.
- **Unrelated changes bundled in.** Reformatting a file you happened to open, upgrading a dependency you happened to notice, renaming something you'd have named differently. Raise it as an issue instead.
- **Work on an issue that was not confirmed as yours**, including one already claimed by someone else.
- **Large unsolicited rewrites.** An architectural change is a conversation before it is a pull request.
- **Anything with no tests**, where the change is testable.

Declining a pull request is not a judgement of you. It usually means the change didn't fit the plan, and the plan is not always visible from outside.

## Standards

Engineering standards live in [`workflow/`](workflow/) and apply to every change:

- [`workflow/standards/java.md`](workflow/standards/java.md) — language-level rules
- [`workflow/standards/spring-boot.md`](workflow/standards/spring-boot.md) — framework-level rules
- [`workflow/patterns/`](workflow/patterns/) — one file per pattern: testing, persistence, service exposure, validation, error handling, configuration, logging, concurrency, auth

Each file opens with an `Applies when:` line. Read the ones your change touches — you are not expected to read all of them.

The overriding rule: **reuse first, framework first.** If Spring Boot or the JDK already does something, use it rather than writing an equivalent. A hand-rolled version of a framework feature is declined regardless of how well it is written.

## Local setup

Requirements: **JDK 17** (the build targets 17; a newer JDK may be installed as long as the build target is unchanged), Maven, Docker.

```bash
# MongoDB must be running before the application starts
docker run -d -p 27017:27017 --name kumite-mongo mongo:7

# Build and run the test suite
mvn verify

# Run the application
mvn spring-boot:run

# Run a single test class
mvn test -Dtest=SomeTestClass
```

The current test suite does not require MongoDB — it is unit-level. Do not add a test that depends on a database running on your machine. Tests that need a database must provision it themselves with Testcontainers; see [`workflow/patterns/testing-integration.md`](workflow/patterns/testing-integration.md).

## Making a change

1. **Fork** this repository and clone your fork.
2. **Branch off `main`**, named for what it does: `fix/...`, `refactor/...`, `feature/...`, `chore/...`.
3. Make your change, with tests.
4. Run `mvn verify` and make sure it passes.
5. Push to your fork and open a pull request against `main`.

Keep your branch focused. If you discover a second problem while working, open an issue for it rather than fixing it in the same pull request.

## Pull request requirements

Your description must state:

- **Which issue it resolves** — use `Closes #123`.
- **What changed**, accurately and specifically.
- **How you verified it** — the command you ran and what happened. If you could not run something, say so plainly. "Tests attempted" is not a result.

Do not describe work you did not do. This is the fastest way to have a pull request closed and to not be assigned another issue.

Continuous integration runs on every pull request. A red build will not be reviewed until it is green.

## On AI assistance

Using an AI assistant is fine — this project is largely developed with one. Submitting output you have not read, run, and understood is not.

You are responsible for every line in your pull request. If you cannot explain why a change is correct, it is not ready. In review you may be asked to explain a specific decision; "the tool wrote it" is not an answer.

## Review

Reviews are done by the maintainer, within a week and usually sooner. This is a side project, so a delay is not disinterest.

Expect comments. Being asked to change something is normal and is not criticism. If you disagree with a review comment, say so and explain why; that conversation is welcome.

If you stop responding on an open pull request, it will eventually be closed. You can always reopen it.

## Reporting a bug

Open an issue with: what you did, what you expected, what happened, and the versions involved. A failing test is the best possible bug report.

Do not report a suspected security vulnerability in a public issue. Contact the maintainer directly instead.
