# Delivery flow

Applies when: any delivery, the steps from issue to merge, reviews, recording knowledge, findings outside the task
Status: enforced

## Index

- [context] Pick up context
- [plan] Think and plan
- [execute] Execute
- [tests] Test
- [self-review] Self-review
- [commit] Commit before reviewing
- [reviews] Reviews
- [record] Record knowledge
- [finalise] Finalise
- [pr] Push and open the PR
- [after-merge] After the merge
- [findings] Findings outside the task
- [artefacts] No session artefacts

## [context] Pick up context

No assumptions. Read in this order, and only what the task needs:

1. The issue.
2. The README of each domain it touches, through the router.
3. The business rules reference, when the task touches business rules and the router names one.
   Grep its index and read only the sections the task needs.
4. The workflow for the work type, then the how-to files for the tools involved.

## [plan] Think and plan

Follow `delivery/planning.md`, then act as the mode file says.

## [execute] Execute

1. Branch from an up-to-date `main`: see `git/branches.md`.
2. Keep to the agreed scope. Anything else is a finding: see `[findings]`.
3. Commit in logical units: see `git/commits.md`.

## [tests] Test

1. Write tests: see the testing workflow for the stack.
2. Prove each test fails when its behaviour breaks: see `testing/test-proof.md`.
3. A new gate or guard is proven only when it has failed on a real violation.
4. Run the test and static check commands the router lists.
5. Run local regression tests if the change touches the code they protect.
6. See it working in the real product, on a small input first.

Report failures as they are. A failed or skipped step is never done.

## [self-review] Self-review

Run `quality/self-review.md` and fix what it finds.

## [commit] Commit before reviewing

Commit locally first: `/security-review` reads committed work. Do not push yet.

`/security-review` needs `origin/HEAD`. A repo created locally and pushed has none, so run
`git remote set-head origin main` once.

## [reviews] Reviews

Run `/code-review` and `/security-review`, then handle the findings as the mode file says.

## [record] Record knowledge

Last step before the PR, every time. What did this work learn the hard way that a future
agent would not know?

- Generic how-to or trap: `context/workflow/`.
- Project fact: the README of its domain.
- Nothing new: say so in the PR.

## [finalise] Finalise

Commit the review fixes and recorded knowledge, then re-run the tests and static checks.

## [pr] Push and open the PR

Push, then open the PR assigned to its author with the template filled in: see
`git/github/issues-and-prs.md`. Only the owner merges, or an agent the owner told to merge
that PR.

## [after-merge] After the merge

Update local `main` and delete the local branch.

## [findings] Findings outside the task

Bugs, missing features, refactors and enhancements found along the way go to the owner. Do not
fix them, write them to a file or open an issue. The owner decides whether one becomes an issue.

## [artefacts] No session artefacts

Never write a plan, handoff, next-session prompt or notes file. Plans live in the conversation
or the PR. Anything that outlives a session is a GitHub issue, so nothing goes stale.
