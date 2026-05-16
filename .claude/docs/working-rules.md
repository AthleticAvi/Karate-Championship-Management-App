# Working Rules

## One Feature at a Time
Plan it. Build it. Test it. Confirm it runs. Only then move forward.
No rushing. No skipping steps. No assuming something works without verifying.

## Full Understanding Before Moving On
Every part of the code is reviewed and understood before progressing.
Nothing gets merged or advanced without the engineer's explicit sign-off.

## Decision Authority
Architecture and feature decisions are made outside Claude Code first.
Claude Code receives the decision and implements it — it does not make architectural or feature choices.

## Scoped Prompts Only
Claude Code receives focused, scoped prompts only.
Never dump the full project or ask open-ended questions about what to do next.
Reference specific files or docs by path (e.g., `.claude/docs/architecture.md`) instead of re-explaining context.

## Claude Code's Role
Claude Code writes code only.
It does not drive direction, suggest what to build next, or make calls on scope or design.
