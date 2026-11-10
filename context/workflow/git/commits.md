# Commits

Applies when: writing a commit message, commit identity, what must not be committed
Status: enforced

## Index

- [message] Messages
- [enforcement] Enforcement
- [units] Logical units
- [identity] Identity
- [privacy] Before committing

## [message] Messages

- Start with an action verb: `Add`, `Remove`, `Fix`, `Document`, `Append`, `Test`, and so on.
- Short and to the point, 72 characters at most: `Add login form`.
- Any reader understands it without the diff. Professional and concise. No essays, no filler.
- Body only when the why is not obvious, and then two lines at most.
- Never list files or restate the diff. A reader scanning the one-line log understands the
  history from the subjects alone.

## [enforcement] Enforcement

A `commit-msg` hook checks the verb, the subject and the body length, where the repo has one.
CI runs the same check on every commit in a PR, so a skipped or missing hook does not get past
it. CI also refuses autosquash commits, which local hooks allow. Add a verb to its list when a real action is missing.

## [units] Logical units

- Commit work in logical units where possible, not everything in one go.
- Each commit is one change a reader can follow on its own.

## [identity] Identity

- Use the GitHub noreply address, set per repo, so no personal email is published.

## [privacy] Before committing

- Nothing personal: names, local paths, emails. This holds for private repos too.
- `.gitignore` cannot remove what is already committed. Scan before the first push, because
  cleaning history afterwards means a rewrite.
- Never skip hooks.
