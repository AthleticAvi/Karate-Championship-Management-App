# Planning

Applies when: writing a plan, design check, challenging a plan
Status: enforced

## Index

- [plan] Writing a plan
- [design-check] Design check
- [challenge] Challenging a plan

## [plan] Writing a plan

A plan states:

- What changes and what stays the same.
- Which layers and files it touches.
- How it will be tested, and what proves it worked.
- Decisions taken, with the reason.
- Open questions.
- The answers to `[design-check]`.

Keep it as short as the work allows.

## [design-check] Design check

Answer these in the plan, in a line each:

1. **Owner:** which layer and domain own this? See `design/layers.md`.
2. **Reuse:** what already does this: the framework, the language, this repo? See `[reuse]` in
   `design/principles.md`.
3. **Abstraction:** does any new abstraction have a second implementation, real or planned?
4. **Pattern:** if a pattern is used, which problem does it solve? See `design/patterns.md`.

## [challenge] Challenging a plan

Have another agent challenge the plan when it includes any of these:

- A change to a contract between layers.
- A new dependency.
- A migration or a large move of code.
- Low confidence in the approach.

Give that agent the plan and the context behind it, and ask what is wrong or missing. Two
rounds at most. Keep what holds up, and record what changed in the plan.

If the two still disagree, attended work takes it to the owner. Unattended work stops.
