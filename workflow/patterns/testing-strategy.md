# Testing Strategy

Applies when: choosing a test type, structuring a suite, naming tests, deciding what not to test, diagnosing a slow suite
Status: enforced
Framework-first: the framework supplies the harness for every layer below. Never build a bespoke test base class before checking which slice annotation already does it.

## Layer selection

Pick the cheapest layer that can fail for the reason you care about.

| Layer | Loads | Use for | Cost |
|---|---|---|---|
| Unit | nothing | logic, branching, calculation, state machines | microseconds |
| Slice | one layer of the container | serialisation, routing, status codes, query mapping | ~1s per context |
| Integration | full container + real infrastructure | wiring, transactions, cross-component flows | seconds |

Majority unit, a meaningful band of slice, a deliberate handful of integration. An inverted shape — where most confidence comes from full-context tests — produces a suite too slow to run on every change, which means it stops being run on every change.

## Rules

- **A test asserts behaviour, not implementation.** If renaming a private method or reordering internal calls breaks a test, the test is coupled to structure and will block the refactors it was supposed to protect.
- **One reason to fail.** A test that can fail for three reasons reports a symptom, not a cause.
- **Deterministic or deleted.** No dependence on wall-clock time, ambient timezone, locale, map iteration order, network, or leftover state. A flaky test is worse than no test: it trains the team to ignore red.
- **Never `sleep`.** Await a condition with a bounded timeout, or inject the clock. A sleep is either too short (flaky) or too long (slow), usually both across machines.
- **Time is a dependency.** Anything that reads the current instant takes a clock abstraction so a test can control it. Code that calls a static "now" cannot be tested for elapsed-time behaviour without sleeping.
- **The fake must be able to fail the way production fails.** A test double that preserves state production would lose proves nothing. See *Round-trip fidelity* below.
- **New defect, new test first.** A fix without a test that fails before it is a fix that can silently regress.
- **Assert the serialised form at the boundary**, not just the object. Object-level equality passes while the wire contract changes underneath.

## Round-trip fidelity

The most common false-green: a persistence double that returns the same in-memory instance that was saved. Anything not actually persisted — transient fields, values populated only by the mapping layer, defaults applied on read — survives in the test and vanishes in production.

A persistence double must return an object that does not share identity with the one stored. Serialise and deserialise, deep-copy, or use real infrastructure. If a field is excluded from persistence, a test must prove the code still works when that field comes back absent.

This class of defect is invisible to every assertion in the test. It is only caught by the fake's design.

## What not to test

- Framework behaviour. Trust that the container injects and that the mapping layer maps; test your use of them, not them.
- Getters, setters, and generated members.
- Configuration values, as opposed to the behaviour that depends on them.
- Private methods directly. If one is complex enough to need its own test, it wants to be a separate type.

## Naming and shape

`methodUnderTest_condition_expectedOutcome`. The failure line should describe the defect without opening the file.

Arrange, act, assert — separated by blank lines, in that order, once. A test with three act phases is three tests.

## Fixtures

Build test data with named builders exposing intent-revealing defaults, so each test states only the field it cares about. A test that sets eleven fields to reach one assertion hides which field matters.

Shared mutable fixtures across tests reintroduce order dependence. Build per test; the cost is irrelevant next to the debugging cost of coupling.

## Suite performance

Each distinct context configuration is built and cached separately. Varying configuration per test class — different properties, different mocked beans, different active profiles — multiplies context builds and dominates suite time. Converge on a small number of configurations and reuse them.

Before optimising anything else, count how many distinct contexts the suite builds.

## Verify

- Suite passes from a clean checkout with no ordering dependence, and passes when run in reverse order.
- Reverting any recent fix turns exactly one test red.
- Removing a persisted field's storage makes at least one test fail.
- Context build count is known and justified.
