# Repo settings

Applies when: configuring a repo, protecting main, merge settings
Status: enforced

## Index

- [main] Protecting main

## [main] Protecting main

- No force pushes to `main`, and no deleting it.
- Require a PR and the CI check to merge, and apply the rule to admins too.
- With one maintainer, require no approvals: nobody else can give them.
- Merge commits only, no fast forwards. Disable squash and rebase merges in the repo.
- Delete branches on merge.
- GitHub Free does not enforce branch protection on private repos: the API answers
  "Upgrade to GitHub Pro or make this repository public". There, the rule holds by
  convention. Turn protection on when the repo goes public or the plan changes.
