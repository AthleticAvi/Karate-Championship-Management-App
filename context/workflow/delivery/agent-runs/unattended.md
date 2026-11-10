# Unattended mode

Applies when: unattended mode, owner away, forbidden actions, when stuck, finishing a run, working inside an orchestrated run
Status: enforced

## Index

- [start] Before starting
- [plan] Plan
- [bar] The bar
- [forbidden] Never do these
- [decisions] Other decisions
- [stuck] When stuck
- [record] Where things are recorded
- [findings] Review findings
- [finish] Finish

## [start] Before starting

- Read `CLAUDE.md`, then only the domain READMEs and workflow files the issue needs.
- An issue without a clear "Done when" is not started. Comment on the issue saying what is
  missing, record it as `[record]` says inside an orchestrated run, and report back.

## [plan] Plan

Write the plan and go ahead. Answer the design check in `delivery/planning.md`, and have a
sub-agent challenge the plan when that file calls for it. Put the plan in the evidence comment:
see the PR description rules in the tracker folder.

## [bar] The bar

Every change meets these, read from the files the router names for the stack:

- Framework first and reuse first: `[reuse]` in `design/principles.md`.
- SOLID and KISS: `design/principles.md`. Patterns only where they solve a named problem:
  `design/patterns.md`.
- Layers never mix: `design/layers.md`.
- Every change to stored state states its guarantee: `design/acid.md`.
- The language and framework standards, and the scaffold: `[layering]` in the workflow README.
- Tests that prove behaviour: `testing/`.
- Review with `/code-review high` and `/security-review`, fix the findings, then open the PR.

## [forbidden] Never do these

- Anything against a hard rule in `CLAUDE.md`.
- A licence decision or a new dependency.
- A change to behaviour the project protects.
- Anything destructive or hard to reverse.

When the work needs one, do not stop. Take an alternative that avoids it. If there is none,
leave that part out, finish the rest, and record what was left out and why.

## [decisions] Other decisions

Take the recommended option and record it in the PR, with the reason. A plan challenge that
ends in disagreement is decided the same way: take the option that keeps the hard rules and
protected behaviour, and record the disagreement.

## [stuck] When stuck

After 3 failed attempts at the same problem, open a draft PR with what was tried and what
blocks it, record it, and report back.

## [record] Where things are recorded

- Inside an orchestrated run: deviations, forced decisions, blockers and how they were overcome,
  and findings outside the task go to the run issue as a comment. See `[run-issue]` in
  `delivery/agent-runs/orchestrated.md`. Only what the owner needs to review.
- Otherwise: in the PR body.

## [findings] Review findings

Fix the review findings on this change, and list each in the evidence comment with what was done
about it. Findings outside the task are recorded as `[record]` says.

## [finish] Finish

Stop after one PR and report back. Never merge: the orchestrator merges inside an orchestrated
run, the owner otherwise. The next issue branches from the updated `main`.
