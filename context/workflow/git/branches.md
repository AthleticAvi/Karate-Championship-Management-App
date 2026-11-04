# Branches

Applies when: naming a branch, starting work, branch flow
Status: enforced

## Index

- [naming] Naming
- [flow] Flow

## [naming] Naming

`<type>/<issue-number>-<task-name>`, hyphens between words: `feature/12-user-login`.

Types: `feature`, `bug`, `change`, `enhancement`, `docs`, `chore`.

The issue comes first, because the branch name needs its number.

## [flow] Flow

1. Create or pick the issue.
2. Branch from an up-to-date `main`.
3. All work happens on the branch. Never commit to `main`.
4. Open a PR into `main`.
5. Once merged, update local `main`.
6. Delete the local branch.
7. Read the next issue and start again.
