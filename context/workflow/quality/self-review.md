# Self-review

Applies when: checking your own work before review
Status: enforced

## Index

- [scope] Scope
- [design] Design
- [code] Code
- [tests] Tests
- [safety] Safety
- [docs] Docs

## [scope] Scope

- The diff does what the issue asks, and nothing else.
- Every "Done when" item is met, with evidence.

## [design] Design

- Gateways, business and data stay apart. No layer does another's work: see `design/layers.md`.
- Every principle in `design/principles.md` was checked against its `Violated if` line.
- Nothing was rebuilt that the framework, the language or this repo already provides.
- No abstraction without a second implementation. Any pattern used solves a named problem.
- No tunable value or path hardcoded.

## [code] Code

- One style: see `standards/standards.md`, then the standards for the stack.
- No dead code, no leftover debug output, no commented-out code.
- Names say what things are.

## [tests] Tests

- Each test fails when its behaviour breaks, and that was proven: see `testing/test-proof.md`.
- Tests check real behaviour, not the code's own constants or structure.
- No test was weakened or removed to get to green.

## [safety] Safety

- Nothing personal, and no real user data or anything derived from it, in the diff.
- Outputs stay in the configured paths, tests included.
- No secret in code, config or logs.

## [docs] Docs

- READMEs and workflows match what the code now does.
- Indexes regenerated.
