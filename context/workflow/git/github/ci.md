# CI

Applies when: editing a CI workflow, deciding what CI runs, pinning actions
Status: enforced

## Index

- [scope] Scope
- [actions] Workflow rules

## [scope] Scope

- Every gate that runs locally also runs in CI. Local hooks are a convenience, not a gate.
- CI runs one documented command, so a local run and a CI run mean the same thing.
- CI never needs real data, secrets or special hardware. If a check does, it is a local test.
- Pull requests from forks get no secrets. Never use `pull_request_target` to work around that:
  it hands secrets to unreviewed code.

## [actions] Workflow rules

- Pin third-party actions to a full commit SHA, with the version in a comment.
- Set `permissions: contents: read` unless a job needs more.
- Set `timeout-minutes` and cancel superseded runs.
- Install from the lock file strictly, so a stale lock file fails the build.
