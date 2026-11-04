# CI

Applies when: editing a CI workflow, deciding what CI runs, pinning actions
Status: enforced

## Index

- [scope] Scope
- [actions] Workflow rules

## [scope] Scope

- Every gate that runs locally also runs in CI. Local hooks are a convenience, not a gate.
- One job per concern, so a failure names what broke. Together the jobs equal the one documented
  local command, so a local run and a CI run mean the same thing.
- Require one summary job that needs all the others, not each job. A required check that is renamed
  or removed never reports, and every pull request waits on it forever.
- A summary job must run when a job it needs fails, or it is skipped, and a skipped check counts as
  passing.
- Numbers that span jobs, such as merged coverage, are checked in their own job, from the data the
  others upload.
- CI never needs real data, secrets or special hardware. If a check does, it is a local test.
- Pull requests from forks get no secrets. Never use `pull_request_target` to work around that:
  it hands secrets to unreviewed code.

## [actions] Workflow rules

- Pin third-party actions to a full commit SHA, with the version in a comment.
- Set `permissions: contents: read` unless a job needs more.
- Set `timeout-minutes` and cancel superseded runs.
- Install from the lock file strictly, so a stale lock file fails the build.
