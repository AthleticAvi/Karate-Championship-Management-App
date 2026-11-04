# Service Interaction

Applies when: two services need each other, circular dependency, orchestration, layering, where logic belongs, transaction boundary
Status: enforced
Framework-first: the container resolves dependencies. If it cannot, the dependency graph is wrong: do not reach for a mechanism that makes the container tolerate it.

## Index

## [circular-dependencies] Circular dependencies

A cycle between two services is a design defect, not a wiring inconvenience. The container's refusal to construct it is correct and useful information.

**Never resolve a cycle by:**

- enabling circular-reference support: this hides every future cycle too;
- lazy injection: this defers construction so the failure moves from startup to first use, and leaves the cycle in place;
- inserting a pass-through intermediary that delegates in both directions: this renames the cycle without breaking it, and adds a layer that must be read through to understand either side.

A cycle nearly always means responsibility is split across the wrong boundary: each service holds part of an operation that belongs to neither.

**Resolve it by moving the shared operation up.** Introduce a component that owns the operation spanning both, and have it call downward into each. Both former participants become leaves that no longer know about each other.

```
// cycle: each service reaches into the other
ServiceA <-> ServiceB

// resolved: a coordinator owns what spanned them
Coordinator -> ServiceA
            -> ServiceB
```

Alternatives when a coordinator is not warranted: extract the shared logic into a stateless collaborator both depend on; or invert one direction with an event, so the upstream side publishes and does not know who reacts.

**If a temporary lazy injection is unavoidable** to land a change, it carries a named owner and a linked removal task, and it never spreads to a third participant. It is scaffolding with a demolition date, not a pattern.

## [where-logic-belongs] Where logic belongs

- **Domain invariants** (rules that are always true of a thing regardless of who asks) live on the domain type, not in a service. A state machine's legal transitions are a domain invariant; enforce them where the state lives so no caller can bypass them.
- **Orchestration** (sequencing several domain operations, resolving what happens next) lives in an application service or a dedicated coordinator.
- **Input shape checking** lives at the boundary, in the gateway layer. See `design/layers.md`.

A service that is a chain of calls to other services with no decisions of its own is a pass-through: delete it and let the caller call directly.

## [transaction-and-consistency-boundary] Transaction and consistency boundary

One operation, one consistency boundary, one aggregate. An operation that must atomically change two aggregates is a signal that the boundary is drawn in the wrong place. See `design/concurrency-consistency.md`.

The boundary belongs at the application service, not the web layer and not the domain type. Starting it in the web layer holds it open across serialisation; starting it in a domain type makes the domain depend on infrastructure.

## [cross-aggregate-reads] Cross-aggregate reads

Composing a response from several aggregates is fine and is a read concern. Do it in the application service or a dedicated read model, not by embedding one aggregate inside another for convenience: that trades a cheap read for a consistency problem.

## [verify] Verify

- The dependency graph is acyclic with no lazy injection and no circular-reference support enabled.
- Every service can be constructed with `new` and test doubles, with no framework present.
- No service exists whose methods only forward to another service.
- Illegal state transitions are rejected by the domain type, provably, from a unit test with no container.
