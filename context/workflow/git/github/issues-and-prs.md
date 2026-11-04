# Issues and PRs

Applies when: opening an issue or PR, assignee, PR scope
Status: enforced

## Index

- [assignee] Assignee
- [issues] Issues
- [prs] Pull requests

## [assignee] Assignee

Every issue and PR is assigned to the person who opened it, unless they say otherwise.

```
gh issue create --assignee @me
gh pr create --assignee @me
```

Include this rule in every new scaffold.

## [issues] Issues

- One outcome per issue, using the repo template.
- State what done means, so it can be checked.
- Issues hold anything that outlives a session. No plan or handoff files.
- Only the owner decides that something becomes an issue. An agent surfaces it and stops.

## [prs] Pull requests

- One issue per PR. `Closes #<number>` in the body.
- Small enough to review in one sitting. Separate moving code from changing behaviour.
- Fill in the template. Tick a box only after doing the step.
- No personal data in titles, bodies or comments.
- The owner reviews and merges. An agent never merges without being told to.
