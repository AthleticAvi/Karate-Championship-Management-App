# Issues and PRs

Applies when: opening an issue or PR, assignee, PR scope, PR description
Status: enforced

## Index

- [assignee] Assignee
- [issues] Issues
- [prs] Pull requests
- [pr-body] PR description

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
- Fill in the template, following `[pr-body]`. Tick a box only after doing the step.
- No personal data in titles, bodies or comments.
- The owner reviews and merges. An agent never merges without being told to.

## [pr-body] PR description

Write for a reader with two minutes. They read the summary, scan the lists, and know what changed
and what they must do.

1. **Summary first.** 3 to 5 sentences at most: what the PR did and what it delivers. Plain words,
   clear with no other context.
2. **Then short bulleted lists, one per concern or delivered item.** For example changes,
   decisions for the owner, findings, not proven. Up to 5 bullets a list, one line each. Drop a
   topic with nothing to say.
3. **No blocks of text.** The summary is the only paragraph. Anything longer than a one-line
   bullet is cut or split. The evidence comment and run-issue entries follow the same rule:
   headings and bullets a person can scan, never an essay.
4. **Say each thing once.** A point appears in one place. Never restate the summary in a list.
5. **Keep only what the reader needs.** That is what changes their review or needs their decision.
   Leave out what the diff, the commits or CI already show.
6. **Evidence stays out of the body.** The body gets one line: the command run and what happened.
   Further proof, the plan and full findings go in one PR comment, linked from that line. No long
   tables in the body. Anything that must outlive the PR is an issue.
7. **Read it as the owner before posting.** Cut any line they would skip. No filler, no padding,
   no claims without a result behind them.
