# Workflow

How to work and how to build. Generic knowledge, project agnostic: project facts live in the
domain READMEs, which the project's `CLAUDE.md` routes to.

## Index

- [start] Start here
- [modes] Modes
- [work-types] Work types
- [scaffold] The scaffold
- [layering] General first, then specific
- [lookup] How to look something up
- [refer] How to refer to knowledge
- [add] How to add knowledge
- [record] What belongs where

## Catalogue

- `databases/mongodb/design/persistence.md`: [document-design] [acid] [object-construction-on-read] [excluded-fields] [dates] [identifiers] [optimistic-locking] [transactions] [write-and-read-concern] [schema-versioning] [indexes] [auditing] [local-setup-and-initialisation] [verify]
- `delivery/agent-runs/attended.md`: [plan] [decisions] [findings] [progress]
- `delivery/agent-runs/orchestrated.md`: [role] [order] [hand-off] [merge-gate] [merge] [keep-going] [resume] [run-issue] [verify]
- `delivery/agent-runs/unattended.md`: [start] [plan] [bar] [forbidden] [decisions] [stuck] [record] [findings] [finish]
- `delivery/bug.md`: [differs]
- `delivery/change.md`: [differs]
- `delivery/chore.md`: [differs]
- `delivery/documenting.md`: [differs]
- `delivery/enhancement.md`: [differs]
- `delivery/feature.md`: [differs]
- `delivery/flow.md`: [context] [plan] [execute] [tests] [self-review] [commit] [reviews] [record] [finalise] [pr] [after-merge] [findings] [artefacts]
- `delivery/learning.md`: [find] [answer]
- `delivery/planning.md`: [plan] [design-check] [challenge]
- `design/acid.md`: [contract] [atomicity] [consistency] [isolation] [durability] [same-guarantees] [enforcement] [verify]
- `design/concurrency-consistency.md`: [default-failure-mode-is] [one-fact-one-owner] [version-writes] [write-last] [idempotency] [verify] [sources]
- `design/layers.md`: [responsibilities] [gateways] [business] [data] [dependencies] [ownership] [readmes] [add-layer] [external-tools] [verify]
- `design/logging.md`: [rules] [levels] [correlation] [structured-output] [never-log] [log-lines-are-an] [verify]
- `design/patterns.md`: [choose] [table] [smells] [verify]
- `design/principles.md`: [use] [single-responsibility] [open-closed] [liskov] [interface-segregation] [dependency-inversion] [kiss] [reuse] [one-owner] [conflicts] [verify]
- `design/service-exposure.md`: [boundary-rule] [controllers] [status-codes] [response-shape] [serialisation-format] [urls] [conditional-reads] [description] [verify] [sources]
- `design/service-interaction.md`: [circular-dependencies] [where-logic-belongs] [transaction-and-consistency-boundary] [cross-aggregate-reads] [verify]
- `design/timers.md`: [no-tick] [client-time] [display] [units] [verify] [sources]
- `git/branches.md`: [naming] [flow]
- `git/commits.md`: [message] [enforcement] [units] [identity] [privacy]
- `git/github/ci.md`: [scope] [actions]
- `git/github/issues-and-prs.md`: [assignee] [issues] [prs] [pr-body]
- `git/github/repo-settings.md`: [main]
- `languages/java/standards/standards.md`: [rules] [do] [dont] [encoding] [naming] [third-party-code-generation] [verify] [sources]
- `languages/java/testing/unit.md`: [rules] [final-field-trap] [do] [dont] [testing-time-dependent-logic] [verify]
- `languages/java/tooling/maven.md`: [gate] [suites] [hooks] [single-file-tools] [verify]
- `languages/java/tooling/static-analysis.md`: [owners] [spotless] [checkstyle] [error-prone] [jacoco] [archunit] [verify]
- `quality/self-review.md`: [scope] [design] [code] [tests] [safety] [docs]
- `standards/standards.md`: [one-style] [naming] [functions] [types] [errors] [config] [comments] [dependencies] [verify]
- `testing/test-proof.md`: [proves-behaviour] [no-fitting]
- `testing/testing.md`: [layer-selection] [rules] [round-trip-fidelity] [what-not-to-test] [naming-and-shape] [fixtures] [suite-performance] [verify] [sources]
- `tooling/static-analysis.md`: [principle] [gate-order] [adoption-sequence] [required-build-settings] [what-each-analyser-is] [one-job-per-tool] [vendor-the-ruleset] [suppression-policy] [architecture-rules] [ci] [review-only-rules] [verify]
- `web-frameworks/spring-boot/design/auth.md`: [shape] [rules] [401-versus-403] [management-endpoints] [cors] [testing] [verify]
- `web-frameworks/spring-boot/design/configuration.md`: [rules] [profiles] [request-limits] [secrets] [precedence] [local-development] [verify] [sources]
- `web-frameworks/spring-boot/design/error-handling.md`: [response-format] [rules] [messages] [extension-fields] [verify] [sources]
- `web-frameworks/spring-boot/design/layers.md`: [mapping] [contract] [stereotypes] [verify]
- `web-frameworks/spring-boot/design/patterns.md`: [framework-provides] [add-a-rule] [enum-strategy] [verify]
- `web-frameworks/spring-boot/design/service-exposure.md`: [conditional-reads] [json-views] [verify] [sources]
- `web-frameworks/spring-boot/design/validation.md`: [two-kinds-two-places] [input-validation] [what-constraints-cannot-express] [domain-invariants] [failure-reporting] [trust-boundary] [verify] [sources]
- `web-frameworks/spring-boot/standards/standards.md`: [framework-first] [rules] [do] [dont] [api-description] [package-layout] [verify]
- `web-frameworks/spring-boot/testing/integration.md`: [purpose] [real-infrastructure-never-a] [rules] [concurrency-scenarios] [what-belongs-at-this] [verify]
- `web-frameworks/spring-boot/testing/slice.md`: [what-a-slice-is] [web-slice] [rules] [persistence-slice] [dont] [verify]

## [start] Start here

1. **Classify** the work with `[work-types]`.
2. **Find your mode.** The prompt states attended, unattended, or an autonomous run of a milestone
   or parent issue (orchestrated). If it does not, you are attended.
3. **Read context:** the domain READMEs the task touches, then the files this README points to.
4. **Think and plan,** then act as your mode says.

Concise. No fluff, no slop, no em dashes. Deterministic checks first: use AI judgment only where
code cannot decide.

## [modes] Modes

| Mode | Owner is | Read |
|---|---|---|
| Attended | Present and answering | `delivery/agent-runs/attended.md` |
| Unattended | Away | `delivery/agent-runs/unattended.md` |
| Orchestrated | Away, a milestone or parent issue to run | `delivery/agent-runs/orchestrated.md` |

## [work-types] Work types

| Work | What it is | Read |
|---|---|---|
| Feature | A new capability | `delivery/feature.md` |
| Bug | Something behaves wrongly | `delivery/bug.md` |
| Change | Existing behaviour changed on purpose | `delivery/change.md` |
| Enhancement | The same behaviour, done better | `delivery/enhancement.md` |
| Documenting | Docs only, no behaviour change | `delivery/documenting.md` |
| Chore | Tooling, dependencies, CI, repo upkeep | `delivery/chore.md` |
| Learning | Answering a question, read-only | `delivery/learning.md` |

All delivery except learning follows `delivery/flow.md`.

## [scaffold] The scaffold

Concerns sit at the top and apply to every stack:

| Folder | Holds |
|---|---|
| `delivery/` | How work moves from issue to merge |
| `standards/` | Coding standards for all languages |
| `design/` | Principles, patterns, layers, system design |
| `testing/` | What to test and what proves a test |
| `tooling/` | Formatters, linters, static checkers |
| `quality/` | Checking your own work |
| `git/` | Commits, branches, GitHub |

Technologies sit in a category folder: `<category>/<technology>/<concern>/<topic>.md`.

| Category | Holds |
|---|---|
| `languages/` | Programming languages |
| `web-frameworks/` | Web frameworks |
| `databases/` | Datastores |

## [layering] General first, then specific

1. A top-level concern applies to every stack. A technology adds only what is specific to it.
2. A folder under a technology must match a top-level concern. A test enforces this.
3. Read general to specific: the concern, then the language, then the framework.
4. A specific file never repeats its parent. It opens with `Builds on:`, naming the parent.
5. Specific wins for its stack. A file that breaks a general rule says so and gives the reason.
6. When a second stack needs the same rule, it moves up to the top level.

## [lookup] How to look something up

1. Find the file in the catalogue by path or tag, or grep the `Applies when:` lines.
2. List its sections: `grep -n "^## \[" <file>`.
3. Read only the sections the task needs.

Example, a controller test in a web framework:

1. `testing/testing.md`: what to test.
2. `languages/<language>/testing/`: test doubles and naming.
3. `web-frameworks/<framework>/testing/`: how to write it.

## [refer] How to refer to knowledge

- Paths are relative to this folder: `design/layers.md`.
- Point to a section with its tag: `[kiss]` in `design/principles.md`.
- Generic files never name a technology file. Say what to look for instead.
- Code comments and project docs use the full path from the repo root.

## [add] How to add knowledge

1. Copy `TEMPLATE.md` to the right folder. One file per topic.
2. Tags are lowercase with hyphens, unique within a file. Every section is tagged.
3. Keep it generic. No project facts anywhere, and only technology folders name a stack.
4. Write rules and traps, not tutorials. State the symptom, so a grep finds it.
5. New technology: add `<category>/<technology>/`, with only the concern folders it needs.
6. New category: add it to `[scaffold]` and to the scaffold test.
7. Run the index generator the router lists. Never edit an index by hand.

## [record] What belongs where

| Knowledge | Goes in |
|---|---|
| A generic how-to or a trap learned the hard way | This folder |
| A fact about this project | The domain README |
| Work that outlives a session | A GitHub issue |
| A plan, handoff or next-session prompt | Nowhere: see `[artefacts]` in `delivery/flow.md` |
