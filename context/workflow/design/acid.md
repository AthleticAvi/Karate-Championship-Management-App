# ACID

Applies when: storing data, changing state, all or nothing, atomicity, consistency, isolation, durability, transaction, write acknowledgement, crash, partial write, datastore settings, stale read
Status: enforced

## Index

- [contract] Every state change states its guarantee
- [atomicity] Atomicity: one operation, one atomic unit
- [consistency] Consistency: invariants hold at every commit
- [isolation] Isolation: no decision on stale state
- [durability] Durability: success means it survives
- [same-guarantees] Every environment gives the same guarantees
- [enforcement] How these rules are enforced
- [verify] Verify

## [contract] Every state change states its guarantee

ACID is a property of an operation, not of a product. An engine may give it for one record, across
records, or only when configured to. The persistence file for the datastore says where its boundary
sits and which settings hold it.

Every operation that changes state answers these in the plan:

| Property | Question | A valid answer |
|---|---|---|
| Atomicity | What is the one unit that lands whole or not at all? | Names one record, or one transaction |
| Consistency | Which invariants must hold, and what checks them? | Names the domain type or the engine constraint |
| Isolation | What can another caller change between my read and my write? | Names the version check or the transaction level |
| Durability | When the caller hears success, what survives a crash? | Names the acknowledgement setting |

"The engine handles it" is not an answer. Unanswered means the design is not finished.

One line per operation is enough, and operations with the same answers can share one:

```
<operation>: <atomic unit>; <what checks the invariants>; <the guard>; <acknowledgement setting>
```

## [atomicity] Atomicity: one operation, one atomic unit

- One business operation commits in exactly one atomic unit of the engine.
- Size the aggregate to that unit: what must change together is stored together.
- Prefer one record over a transaction. A transaction costs more and needs more of the deployment.
- Two writes in sequence are not atomic, in any order and however close together.
- A compensating delete is not atomicity. It narrows the window and can fail too: use it only where
  no atomic unit is possible, and record the residual risk. See `[write-last]` in
  `design/concurrency-consistency.md`.

## [consistency] Consistency: invariants hold at every commit

- Invariants live on the domain type, so no caller can bypass them. See `[business]` in
  `design/layers.md`.
- A state that fails an invariant is never written, not even briefly.
- What the engine can enforce, the engine enforces: uniqueness is a constraint, not a read first.
- A check and the write it guards belong to the same atomic unit. A check made on another record,
  or before a separate write, proves nothing by the time the write lands.

## [isolation] Isolation: no decision on stale state

- Know the engine's default isolation and write it down. It is often weaker than people assume.
- A write that depends on a read is guarded: see `[version-writes]` in
  `design/concurrency-consistency.md`.
- A value composed from several reads may not be from one point in time. Do not decide on it unless
  the write is guarded against every record it read.
- Never hold a transaction open across a request, a user action or a remote call.

## [durability] Durability: success means it survives

- Report success only after the engine's durable acknowledgement. Never fire and forget.
- Set the acknowledgement level explicitly, in configuration, for every environment. A default
  changes with topology and version, and nothing reports it.
- A timeout is an unknown outcome, not a failure. The write may have landed, so a caller must be able
  to repeat it safely: see `[idempotency]` in `design/concurrency-consistency.md`.
- Durability covers a crash. It does not cover a bad delete or a lost disk: those need backups, and a
  backup counts only once a restore has been tested.

## [same-guarantees] Every environment gives the same guarantees

- Local, test and production offer the same transaction support and the same acknowledgement level.
- An environment that is weaker or stronger than production hides defects from the tests run on it.
- Where one must differ, write down how and why, and what that leaves untested.
- The application refuses to start when a reachable datastore cannot give the guarantees it was
  built on. Failing at startup beats losing data quietly in the middle of an operation.
- An unreachable datastore is an outage, not a weak datastore. Start, report unhealthy, serve no
  state changes, and run the check once it answers.

## [enforcement] How these rules are enforced

| Gate | What it does |
|---|---|
| Plan | `[design-check]` in `delivery/planning.md` requires the `[contract]` answers |
| Self-review | `[design]` in `quality/self-review.md` checks each state change against them |
| Tests | Every item in `[verify]` is a test, not a review note |
| Architecture test | Where a rule can be expressed as structure, it is. Known violations are frozen until fixed |
| Startup | The application checks the datastore's guarantees before serving requests |
| Known gaps | Existing code that breaks a rule is listed in its domain README, with the issue fixing it |

A rule that a test can check gets a test. Review is for what code cannot decide.

A listed gap is known debt, not a regression. New code meets the rules, and no gap grows.

## [verify] Verify

- Forcing a failure at each step of a state change leaves nothing partly applied.
- Two concurrent operations on the same record both hold their invariants. Use a latch.
- A write based on stale state is refused, not applied.
- Repeating an operation after a simulated timeout does not apply it twice.
- The application refuses to start against a reachable datastore that lacks a required guarantee.
- Against an unreachable datastore it starts and reports unhealthy.
- The configured acknowledgement level is what the running connection actually uses.
