# Orchestrated run

Applies when: told to work a milestone or a parent issue autonomously, orchestrating several issues, handing issues to sub-agents, merging agent PRs, the run issue
Status: enforced

## Index

- [role] The orchestrator delivers nothing itself
- [order] Order
- [hand-off] The hand-off prompt
- [merge-gate] The merge gate
- [merge] Merge and move main
- [keep-going] Nothing blocks the run
- [run-issue] The run issue
- [verify] Verify

## [role] The orchestrator delivers nothing itself

- It reads the milestone or parent issue and the issues under it. It writes no code and reads
  no more of the codebase than a merge gate needs.
- It hands each issue to a fresh sub-agent, checks the result, merges it, and moves on.
- It keeps its own context small: issue numbers, PR numbers, outcomes.
- **The owner's instruction to run is the grant to merge,** for the PRs of this run only. It
  replaces "stop after one PR" in `delivery/agent-runs/unattended.md` and "an agent never merges"
  in the tracker rules, for this run.

## [order] Order

- Order the issues by their dependencies ("Depends on #N"), not by number. Break ties by the
  lowest number.
- Skip issues that are closed, taken by someone else, or are the run issue itself.
- Open the run issue before the first hand-off: see `[run-issue]`.
- Before each hand-off, local `main` matches the remote and the working tree is clean.

## [hand-off] The hand-off prompt

Start a fresh sub-agent per issue, never a copy of the orchestrator's own context, and send this
text with the numbers filled in. Nothing else, so every sub-agent starts the same way:

```
Work issue #<N> unattended, inside orchestrated run #<R>.
Read CLAUDE.md first, then pick up context through its router: only the domain READMEs and
workflow files this issue needs, nothing else. Classify the work. Follow
delivery/agent-runs/unattended.md and delivery/flow.md in the workflow, from the issue to an
open PR. Do not merge. Report back: the PR number, whether it is a draft, and what you
recorded in run issue #<R>.
```

## [merge-gate] The merge gate

Check each of these yourself. Never merge on the sub-agent's report alone.

- The PR is not a draft, targets `main`, and closes its issue.
- Every CI check passed.
- The branch contains the current `main`.
- The body follows the PR description rules; the evidence comment exists and lists every review
  finding with what was done about it.
- Nothing in the PR does what `[forbidden]` in `delivery/agent-runs/unattended.md` forbids.

A failed gate goes to a new sub-agent with the PR number and the failed checks. After two failed
attempts, leave the PR unmerged, record it in the run issue, and carry on.

## [merge] Merge and move main

1. Merge with a merge commit. Never squash, rebase or fast forward.
2. Delete the branch on the remote and locally.
3. Update local `main` to the remote.
4. Confirm the issue is closed. If not, close it with a link to the PR.
5. If `main` fails its checks after the merge, the next hand-off is the repair, on its own branch.

## [keep-going] Nothing blocks the run

- An issue left with a draft or unmerged PR stays open. Its dependents are skipped. Both are
  recorded in the run issue. Independent issues carry on.
- The run ends when no runnable issue is left.

## [run-issue] The run issue

One issue per run, opened under the same milestone, or linked from the parent issue. It is the
owner's review point, not a ledger. It is the agreed channel for findings in this run, in place
of the PR body alone.

Record only:

- Deviations from an issue or its plan, and why.
- Decisions forced by circumstances, with the option taken and the reason.
- Blockers and how they were overcome, or why they were not.
- Issues skipped or left unmerged, and why.
- Findings outside an issue's scope.

Never record routine progress, a per-issue status line, or what the PRs already show. Sub-agents
add their own entries as comments. At the end, the orchestrator adds one closing comment: PRs
merged, issues left open and why. The owner closes the run issue.

## [verify] Verify

- Every merged PR passed every gate check, checked by the orchestrator.
- `main` holds merge commits only, and no merged branch remains.
- Every issue closed by the run has a merged PR.
- The run issue holds only the five kinds of entry above.
