# Enforcement

Applies when: adding a quality gate, wiring CI, deciding whether a rule is checkable, triaging analyser output
Status: enforced

## Principle

A rule that only a reviewer enforces decays. Every rule in `standards/` is either mechanically checked or explicitly marked as review-only. When a defect class is found twice, the second fix includes the check that prevents the third.

## Gate order

Gates run cheapest-first so the slowest gate never runs against code the fastest one rejects.

| Order | Gate | Catches |
|---|---|---|
| 1 | Compiler, warnings enabled | type errors, deprecation, unchecked operations |
| 2 | Formatter check | layout drift |
| 3 | Static analysis — style | naming, visibility, structure, forbidden calls |
| 4 | Static analysis — correctness | null dereference, resource leak, ignored return |
| 5 | Unit and slice tests | behaviour |
| 6 | Integration tests | wiring and real infrastructure |

## Adoption sequence

Turning a gate on hard against an existing codebase produces a wall of findings, and a build that always fails gets disabled rather than fixed. Adopt in three steps:

1. **Report only.** Add the analyser, let the build succeed, record the finding count as the baseline.
2. **Ratchet.** Fail the build if the count rises above the baseline. New code is clean immediately; existing findings are burned down in batches.
3. **Enforce.** Once the count reaches zero, fail on any finding.

Never skip to step 3 on an existing codebase. Never stop at step 1 — an unenforced report is noise.

## Required build settings

- Source and resource encoding pinned to UTF-8 explicitly. Never inherit the platform default.
- Parameter names retained in bytecode. Framework mapping layers resolve constructor arguments by name; without the flag, object construction from persisted data fails at runtime with no compile-time signal.
- Compiler warnings enabled. Deprecation and unchecked warnings suppressed only at the narrowest possible scope, with a comment giving the reason.

## What each analyser is for

- **Style analyser** — visibility, naming, forbidden calls, file structure. Configure from a published ruleset and suppress what genuinely does not fit; do not author a ruleset from scratch. Suppressions live in one file with a reason per entry.
- **Correctness analyser** — null dereference, ignored return values, resource leaks, equality mistakes. Its null analysis is the highest-value part; treat those findings as defects rather than style.

## One job per tool

**No two tools may hold authority over the same category.** Analyser suites overlap heavily by default — formatters and style analysers both police layout, style and correctness analysers both police forbidden calls — and running them on stock configuration means one defect produces two findings, in two baselines, fixable in two places.

That is not extra safety. It is a maintenance cost that grows with every tool, and it trains people to skim findings rather than read them.

So when adopting a tool, name the category it owns and **remove that category from every other tool**, in the other tool's ruleset rather than in a suppression file. A useful default division:

| Category | Owner |
|---|---|
| Layout | the formatter, alone |
| Naming, structure, forbidden calls | the style analyser |
| Null analysis, API misuse, correctness | the correctness analyser |
| Coverage, duplication, history | the aggregation platform, if one is used |

Prefer a formatter with no options. Formatting is the category where configurability buys nothing and costs recurring argument.

**Adopting an aggregation platform is a replacement decision, not an addition.** These products are built to subsume standalone style and correctness analysers, and their vendors say so. Adopting one alongside the tools it replaces is the overlap problem at its worst. Decide which stack owns the rules, and delete the other.

**Check where the gate can actually run before adopting it.** A hosted analyser needs a credential, and platforms deliberately withhold credentials from builds triggered by outside contributors' pull requests. If contribution arrives that way, a hosted gate cannot see the changes that most need reviewing, and the common workaround — the trigger that does expose the credential — hands it to unreviewed code. Rule the tool out rather than working around it.

## Vendor the ruleset

A published ruleset referenced by name usually ships inside the analyser's own distribution, so upgrading the analyser silently changes which rules run. Where a build ratchets on a violation count, that turns a tool upgrade into an unexplained baseline movement.

Copy the ruleset into the repository and reference the copy. Upgrades then arrive as a reviewable diff, and any deviation from the published standard sits in one file with the reason next to it.

## Suppression policy

A suppression is a decision, so it carries a reason. Blanket file-level or project-level suppression of a rule is equivalent to deleting the rule — do that deliberately in the ruleset instead, so it is visible in one place.

## CI

Every gate that runs locally runs in CI on every change. A gate that runs only locally does not exist. CI runs against a clean checkout so nothing passes because of stale local state.

## Review-only rules

These cannot be mechanically checked and remain reviewer responsibility:

- Framework-first: whether an existing framework feature was overlooked.
- Whether a name describes what the thing does.
- Whether a test asserts behaviour or restates the implementation.
- Whether an abstraction earns itself.

## Verify

- A deliberately introduced violation of each enforced rule fails the build.
- The analyser baseline is recorded and monotonically decreasing.
- CI and local runs produce the same result on the same commit.
