# Layers and ownership

Applies when: deciding where code belongs, adding a layer or domain, mixing business and data, gateways, dependency direction, code ownership
Status: enforced

## Index

- [responsibilities] Three responsibilities, never mixed
- [gateways] Gateways
- [business] Business
- [data] Data
- [dependencies] Dependency direction
- [ownership] Ownership
- [readmes] Domain READMEs
- [add-layer] Adding a layer or domain
- [external-tools] Wrapping external tools
- [verify] Verify

## [responsibilities] Three responsibilities, never mixed

Every project separates these, whatever it calls them:

| Responsibility | Job | Never |
|---|---|---|
| Gateways | Everything crossing the boundary, in or out | Decide anything |
| Business | Rules, decisions, orchestration | Know about transport or storage |
| Data | Store and retrieve | Hold a rule |

## [gateways] Gateways

- Inbound: request handlers, command line entry points, message consumers.
- Outbound: clients for other services, external tools, processes.
- A gateway translates between outside shapes and inside shapes, then delegates.
- Input shape checking happens here. Whether an operation is legal does not.
- Outside types stop here. Each outside system sits behind one adapter.

## [business] Business

- Holds every rule and decision, and is where the domain vocabulary lives.
- Invariants live on the domain type, so no caller can bypass them.
- Depends on abstractions it owns, not on the outside world.
- Testable with no framework, no network and no storage.

## [data] Data

- Reads and writes, and owns how things are stored.
- A rule found in a query or a storage type belongs in business.
- Storage shapes never reach a gateway.

## [dependencies] Dependency direction

- Dependencies point towards business. Business depends on neither gateways nor data details.
- Layers talk through a contract. A layer never does another's job: it requests the service.
- No cycles, between layers or between domains.
- An architecture test enforces this, not review. See `tooling/static-analysis.md`.

## [ownership] Ownership

- Each domain and layer owns its job and its facts, stated in its README.
- One fact, one owner: see `[one-owner]` in `design/principles.md`.
- Changing what a domain owns means changing that domain, not reaching in from outside.
- When two domains seem to own something, the boundary is wrong. Raise it.

## [readmes] Domain READMEs

Every domain or layer has a README, so an agent learns what is there without reading the code.
A folder with many files doing many things gets a nested one.

| Section | Holds |
|---|---|
| `[purpose]` | Its one job |
| `[owns]` | The facts and files it owns |
| `[contract]` | What it offers, and what it requires |
| `[may-call]` | The layers it may depend on |
| `[facts]` | Project facts an agent would not guess |

Project facts live here, never in the workflow.

## [add-layer] Adding a layer or domain

1. Create it with a README.
2. Declare it in the architecture test.
3. Add it to the router's layer table.

No existing layer changes. If one has to, the contract is wrong: fix that first.

## [external-tools] Wrapping external tools

- Each external tool or process sits behind one adapter.
- Tools in other runtimes run as subprocesses behind that adapter.
- The rest of the code never builds a command line or a request.

## [verify] Verify

- The architecture test passes with no new frozen violations.
- A business class can be built and tested with no container.
- No storage or transport type appears in a business signature.
