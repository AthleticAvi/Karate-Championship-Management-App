# Coding standards

Applies when: writing or reviewing any code, in any language; naming; functions; types; comments; dead code; errors
Status: enforced
Scope: every language. Each language adds its own file under `languages/`.

## Index

- [one-style] One style
- [naming] Naming
- [functions] Functions
- [types] Types
- [errors] Errors
- [config] Configuration
- [comments] Comments
- [dependencies] Dependencies
- [verify] Verify

## [one-style] One style

- The repo reads as if one person wrote it. Match the code around you.
- Layout belongs to the formatter. Never argue with it, never hand-format around it.
- Where a tool can decide, a tool decides: see `tooling/static-analysis.md`.

## [naming] Naming

- Names say what, not how: `loadOrders`, not `processData`.
- Functions are verbs, types are nouns, booleans read as questions.
- Use the vocabulary of the domain, spelled the way the domain spells it.
- Put units in the name when they are not obvious: `durationSeconds`, `heightPx`.
- No abbreviations beyond well-known ones (`id`, `url`).
- No `utils`, `helpers`, `common` or `manager`. Name the thing the code does.

## [functions] Functions

- One job, one level of abstraction.
- Pure where possible. Push I/O to the edges, so logic tests without files or processes.
- Pass what a function needs, not a whole configuration or context object.
- Return early. Deep nesting hides the main path.
- Few parameters. Several that travel together are a type waiting to be named.

## [types] Types

- Model the domain. A named type beats a string or a bare map at any boundary.
- Make illegal states unrepresentable where the language allows it.
- Immutable by default. Mutation is a claim that something needs to change.
- No sentinel strings for absent state. Use the language's absence, or an enumeration.

## [errors] Errors

- Fail early at boundaries, with a message that says what to fix.
- Never swallow an error into a degraded default. Handle it or let it propagate.
- Never use exceptions as control flow.
- Use specific error types. The type carries the meaning, not the message.

## [config] Configuration

- Rule values, thresholds, durations and paths live in configuration, never in code.
- Parse configuration once at the edge, into typed objects.
- Secrets come from the environment only.

## [comments] Comments

- Comments explain why, never what. If the what needs a comment, rename something.
- Delete dead code. Never comment it out: version control remembers.
- No leftover debug output.
- A comment may cite the official source of a rule, by that source's own section number, so anyone
  can open it. Never reference a local or private copy, or say where one lives.

## [dependencies] Dependencies

- Prefer the framework, then the standard library, then what is already installed.
- Check the licence before adding one, for everything it brings in.
- A dependency can change behaviour just by being present, such as a data format the framework then accepts on its own. Compare the running application before and after, not only the dependency list.
- Follow the official docs of a library. No clever workaround without a recorded reason.

## [verify] Verify

- The formatter and analysers pass with no new findings.
- A reader understands each name without opening its definition.
- No dead code, debug output or hardcoded rule value in the diff.
