# Design principles

Applies when: designing anything, planning a change, reviewing a diff, SOLID, KISS, reuse, adding an abstraction, duplicated code
Status: enforced

## Index

- [use] How to use this file
- [single-responsibility] Single responsibility
- [open-closed] Open/closed
- [liskov] Liskov substitution
- [interface-segregation] Interface segregation
- [dependency-inversion] Dependency inversion
- [kiss] KISS
- [reuse] Reuse first, framework first
- [one-owner] One fact, one owner
- [conflicts] When principles conflict
- [verify] Verify

## [use] How to use this file

Each principle is a decision, not a slogan. Read its `When` line and act on the rest:

- **When:** the moment this principle applies.
- **Apply:** what to do.
- **Violated if:** the signal in a diff that it was broken.
- **Not when:** where applying it does harm.

Check them at two points: the design check when planning (`delivery/planning.md`) and the design
section of `quality/self-review.md`.

## [single-responsibility] Single responsibility

- **When:** adding code to an existing class, module or function.
- **Apply:** give each unit one reason to change. If the description needs "and", split it.
- **Violated if:** two unrelated requests would edit the same unit, or a unit mixes layers.
- **Not when:** splitting would produce fragments that only ever change together.

## [open-closed] Open/closed

- **When:** adding a new variant of something that already varies.
- **Apply:** add a class, adapter or definition. No existing branch changes.
- **Violated if:** a new case edits a switch or an if chain.
- **Not when:** only one variant exists. See `[kiss]`.

## [liskov] Liskov substitution

- **When:** implementing an interface or extending a type.
- **Apply:** honour the whole contract, including errors and edge cases.
- **Violated if:** a caller checks the concrete type, or an implementation throws "not supported".
- **Not when:** it always applies. If it cannot hold, the types are not related: compose instead.

## [interface-segregation] Interface segregation

- **When:** defining what a caller depends on.
- **Apply:** keep interfaces small, shaped by the caller rather than the implementation.
- **Violated if:** an implementation stubs methods it does not need, or a caller sees methods it
  never calls.
- **Not when:** one caller and one implementation exist. Then no interface is needed at all.

## [dependency-inversion] Dependency inversion

- **When:** business code needs storage, a network, a clock or any other outside thing.
- **Apply:** depend on an abstraction the business side owns. The wiring supplies the
  implementation.
- **Violated if:** a rule cannot be tested without real infrastructure, or code builds its own
  collaborators.
- **Not when:** the collaborator is a stable value type with no behaviour worth replacing.

## [kiss] KISS

- **When:** choosing between designs, or about to add an abstraction.
- **Apply:** pick the simplest design that meets a requirement that exists today. Add an
  abstraction when a second implementation exists or is planned, not for a hypothetical one.
- **Violated if:** a reader needs several files to follow one simple behaviour, or an interface
  has one implementation and no plan for another.
- **Not when:** the simple option breaks a hard rule or a layer boundary.

## [reuse] Reuse first, framework first

- **When:** about to write any mechanism.
- **Apply:** look in this order: the framework, the language, this repo, an installed dependency.
  Write new code only when all four come up empty, and say so in the plan.
- **Violated if:** the diff hand-rolls something the framework provides, however well it is
  written. The framework keeps maintaining its version. Nobody maintains yours.
- **Not when:** two pieces of code look alike but change for different reasons. Three similar lines
  beat a premature shared abstraction.

## [one-owner] One fact, one owner

- **When:** storing, caching or copying a value.
- **Apply:** every fact has one authoritative location. Everything else references it.
- **Violated if:** two writes must agree. See `design/concurrency-consistency.md`.
- **Not when:** a copy is a deliberate read model that can be rebuilt from the owner.

## [conflicts] When principles conflict

1. Hard rules and layer boundaries win.
2. Then KISS: the principles serve change that is real or planned, not imagined.
3. If two options stay close, raise it with the owner, with a recommendation.

## [verify] Verify

- The plan answers the design check.
- No `Violated if` line matches the diff.
- Every new abstraction names its second implementation.
