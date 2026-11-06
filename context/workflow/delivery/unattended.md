# Unattended mode

Applies when: unattended mode, owner away, stop rules, when stuck, finishing a run
Status: enforced

## Index

- [start] Before starting
- [plan] Plan
- [stop] Stop rules
- [decisions] Other decisions
- [stuck] When stuck
- [findings] Review findings
- [finish] Finish

## [start] Before starting

Refuse an issue without a clear "Done when". Comment on the issue saying what is missing, then
stop.

## [plan] Plan

Write the plan and go ahead. Challenge it when `delivery/planning.md` calls for it. Put the plan
in the evidence comment: see `[pr-body]` in `git/github/issues-and-prs.md`.

## [stop] Stop rules

Stop and report when the work needs any of these:

- Anything against a hard rule in `CLAUDE.md`.
- A licence decision or a new dependency.
- A change to behaviour the project protects.
- Anything destructive or hard to reverse.
- A plan challenge that ends in disagreement.

## [decisions] Other decisions

Take the recommended option and record it in the PR, with the reason.

## [stuck] When stuck

After 3 failed attempts at the same problem, stop. Open a draft PR with what was tried and what
blocks it.

## [findings] Review findings

Fix the review findings on this change, and list each in the evidence comment with what was done
about it. Findings outside the task are listed in the PR body for the owner: see `[findings]` in
`delivery/flow.md`.

## [finish] Finish

Stop after one PR. The next issue waits for the merge, so it can branch from `main`.
