# MongoDB Persistence

Applies when: defining a document, mapping fields, object construction on read, indexes, transactions, local database setup, versioning, ACID, write concern, read concern, journaling, replica set, standalone, retryable writes, durability
Status: enforced
Framework-first: the repository abstraction, mapping layer, auditing, index declaration and optimistic locking are all provided. Direct driver calls in a service are a defect.
Builds on: `design/acid.md`, `design/concurrency-consistency.md`

## Index

- [document-design] Document design
- [acid] How MongoDB meets ACID
- [object-construction-on-read] Object construction on read
- [excluded-fields] Excluded fields
- [identifiers] Identifiers
- [optimistic-locking] Optimistic locking
- [transactions] Transactions
- [write-and-read-concern] Write and read concern
- [indexes] Indexes
- [auditing] Auditing
- [local-setup-and-initialisation] Local setup and initialisation
- [verify] Verify

## [document-design] Document design

- **One document type per aggregate root.** A repository exists per aggregate root, not per class.
- **Store references, not copies.** Embedding a copy of another aggregate creates two sources of truth that must be written together. See `design/concurrency-consistency.md`. Embed only value objects that have no independent identity and are always read with their parent.
- **Documents are not API types.** They never appear in a controller signature. See `design/service-exposure.md`.
- **What must change together lives in one document.** A single document is the only atomic unit that needs no transaction. Moving a fact into the document that owns it is not a copy: a copy is the same fact stored twice.
- **Bound what you embed.** A document is capped at 16 MB, so an array that grows without limit does not belong inside one.

## [acid] How MongoDB meets ACID

The rules are in `design/acid.md`. This is where they meet the engine.

| Property | What the engine gives | What it does not |
|---|---|---|
| Atomicity | A write to one document is atomic, even across many fields | A write spanning documents is not atomic, even one command such as `updateMany`, unless it runs in a transaction |
| Consistency | Unique indexes | No schema by default: invariants live in the domain type |
| Isolation | A reader never sees a half-written document | The default is read uncommitted. Across documents, other operations interleave and reads are not point in time |
| Durability | `w: majority` with journaling | A write acknowledged only in memory can be lost: see `[write-and-read-concern]` |

## [object-construction-on-read] Object construction on read

The mapping layer must be able to rebuild an instance from a stored document. It resolves a creator in a fixed order: a no-argument constructor if present, otherwise a single constructor, otherwise one explicitly annotated as the persistence creator. Fields not covered by the chosen creator are populated afterwards.

Two failure modes follow, both silent:

- **A single constructor that also assigns defaults runs those assignments on every read.** They are then overwritten by the stored values, but only for fields the mapping layer knows about. Any field assigned in the constructor with no stored counterpart is reset on every load. Nothing reports this.
- **Constructor-argument matching is by parameter name**, which requires the compiler to retain parameter names. Without that build setting, construction fails at runtime with no compile-time signal. See `tooling/static-analysis.md`.

**Rule:** every document type declares its creator explicitly: either a no-argument constructor, or a constructor annotated as the persistence creator. Never rely on "there happens to be only one constructor". Keep construction of a *new* aggregate separate from reconstitution of a stored one; a factory method expresses the first, the creator expresses the second.

## [excluded-fields] Excluded fields

A field marked transient is never written and never populated on read. It is `null` on every loaded instance.

Any object holding a transient field must reconstruct it before use, from state that *is* persisted, on every path that touches it. Reconstructing from partial state is worse than leaving it null: it produces an object that looks valid and behaves wrongly. If a transient field derives from two persisted values, restore both.

Prefer computing such state on demand from persisted fields over holding it in a field at all. A field that cannot be persisted is a field that can be forgotten.

## [identifiers] Identifiers

Expose the identifier deliberately as a named field on the response type. Never let the storage identifier reach a client incidentally through direct serialisation of a document.

## [optimistic-locking] Optimistic locking

A version field on a document makes the mapping layer include the current version in every update. A write against a stale version matches no document and raises a conflict rather than silently overwriting.

- **Use the primitive integer form, not the boxed form.** An unset primitive is zero, which the framework treats as "not yet stored". The boxed form is accepted in principle, but the primitive is the reliable choice for document stores.
- **Requires acknowledged writes.** An unacknowledged write concern discards the conflict silently, reintroducing exactly the defect the version field prevents. Leave the default alone.
- **Decide what happens to documents written before the field existed.** Either backfill them or accept that their first save is treated as an insert. Write the decision down.

Handling the conflict is a separate concern: see `design/concurrency-consistency.md`.

## [transactions] Transactions

Reach for a transaction last. The manual says it costs more than a single-document write and is no replacement for schema design, so first try to put what changes together in one document.

Multi-document transactions require a replica set or a sharded cluster. A standalone instance does not support them, and the two ways this goes wrong look nothing alike:

- **No transaction manager registered:** the framework's support is off, so a transactional annotation does nothing and gives no warning. Each write commits alone.
- **Manager registered, standalone server:** the first operation fails at runtime, even a read, with `Transaction numbers are only allowed on a replica set member or mongos`.

Before designing anything that needs two documents written atomically, confirm the deployment topology in every environment including local development. If it is standalone, remove the need for the second write.

When a transaction is the right answer:

- Keep it under a minute. Past that it is aborted as expired.
- Expect aborts. A lock not acquired within 5 ms aborts it, and so does a write conflict with a change made outside it.
- **Nothing retries for you on the framework path.** Only the driver's callback form retries by itself, and direct driver calls are a defect. The framework's transaction manager ignores the error labels, and a failed commit raises an exception.
  - `TransientTransactionError`: retry the whole method, with the framework's bounded retry support placed outside the transaction boundary. The body runs again, so it must be safe to run again.
  - `UnknownTransactionCommitResult`: retry the commit only, through the transaction manager's commit hook. Re-running the method could apply the change twice. Fail loudly when the attempts run out.
- Individual writes inside a transaction are never retried on their own, whatever `retryWrites` says.
- Commit with `w: majority`. Read concern guarantees inside a transaction hold only then.
- Create collections and indexes beforehand. The framework's documentation requires it, and a pending index build blocks new transactions on that collection.
- Put `replicaSet` in the connection string.

## [write-and-read-concern] Write and read concern

Write concern decides what "acknowledged" means. Durability depends on it, and it behaves differently per topology.

| Topology | Default | Acknowledged when |
|---|---|---|
| Replica set | `w: majority`, journaled | A majority has journaled it |
| Replica set with an arbiter, where non-arbiters do not exceed the voting majority | `w: 1` | The primary applied it, in memory |
| Standalone | Not reported: `getDefaultRWConcern` is refused on a standalone | `w: 1`: in memory. `w: majority`: on-disk journal |

- **Set `w=majority` in the connection in every environment, where no environment can bypass it.** It gives journaled writes on a standalone and on a replica set, and removes the dependence on topology. A connection string is often replaced per environment, test containers included, so prefer the client settings: an option in a replaced string is lost, and nothing reports it.
- **Prove it from the server.** A replica set defaults to `w: majority`, so reading the outcome of a write proves nothing. With the profiler on, `system.profile` records the write concern each command arrived with.
- **The journal is flushed every 100 ms.** A write acknowledged in memory can be lost on a hard shutdown inside that window.
- **`j: true` alone does not survive a failover.** It says nothing about replication. Use `w: majority`.
- **Never use `w: 0`.** It cannot report a conflict, and it cannot be retried.
- **Always bound the wait.** With no limit, an unachievable write concern blocks forever. Use `timeoutMS` where the driver supports it: it covers the whole operation, retries included, and the driver then ignores `socketTimeoutMS` and `wtimeoutMS`. Zero means no limit. `wtimeoutMS` is deprecated in its favour, and the bare `wtimeout` is the older name for that, so use `wtimeoutMS` only on a driver without `timeoutMS`.
- **A write concern error does not undo the write.** The change stays and may replicate or roll back. Treat it as an unknown outcome: see `[idempotency]` in `design/concurrency-consistency.md`.

Reads:

- **The default read concern is `local`, which can return data later rolled back.** A version check on the write protects a decision made on it, so an unguarded decision is the defect, not the read.
- **Use `majority` when returned data must be durable.** It costs about the same.
- **Read your own writes across members needs a causally consistent session,** with `majority` on both the read and the write.

Retryable writes are on by default in current drivers, but they need a replica set or sharded cluster: a standalone gives none. They retry once by default, for single-document operations only, and the manual does not promise exactly-once. They cover a dropped connection between the driver and the server. They are no substitute for a key from the caller.

## [indexes] Indexes

Add an index when a query filters on something other than the identifier, never speculatively. An index on an unqueried field costs write throughput and storage and returns nothing.

Derive them from the queries that exist: list the repository methods, index what they filter and sort on, confirm with the engine's query plan that the index is actually used.

**The exception is a constraint.** A unique index that enforces an invariant earns its place without a query: it is a rule, not a speed-up. Say which invariant it enforces, and prove it with a test that a duplicate is refused.

Automatic index creation from annotations is off by default in current versions. Creating indexes as an explicit deployment step is preferable beyond development, because it makes index changes reviewable and lets them be applied without a redeploy.

## [auditing] Auditing

Created and modified timestamps and actors are provided by the framework's auditing support, enabled with an annotation and a configuration switch. Never maintain them by hand in a service: hand-maintained audit fields are wrong the first time a write path is added that forgets them.

## [local-setup-and-initialisation] Local setup and initialisation

- The database runs in a container, pinned to the same major version as production.
- **The topology matches production too.** Hosted services run a replica set. A one-member replica set gives the same transaction and write concern behaviour locally, though the manual's production advice is several members, so it has no failover.
- **Start it so the host can reach it.** Run the server with `--replSet rs0` and initiate it once, naming a member address the application can resolve: `rs.initiate({_id: 'rs0', members: [{_id: 0, host: 'localhost:27017'}]})`. A plain `rs.initiate()` in a container registers the container's own hostname, so an application on the host using `replicaSet=rs0` discovers an address it cannot resolve and every operation fails with a server selection timeout. `directConnection=true` also gets through, for test and development only. A replica set with authorization also needs a keyfile or certificates for its members.
- **A test container's topology is a setting: check it.** Some container libraries start a standalone unless asked for a replica set, so tests can run on a weaker topology than production. Confirm it with `db.hello().setName`, which is absent on a standalone.
- **With `replicaSet` set, a standalone looks unreachable.** The driver drops it from its view, so every command times out exactly as on a dead server. A member nobody initiated does the same. To tell them apart, listen for server description changes: they report the real server type within milliseconds, in every connection shape. Heartbeat events do not help: the first hello is the handshake, which never reports success.
- **A member can report "not initiated" for a moment as it restarts.** Do not stop a running application on it.
- Connection settings come from the environment with local defaults, never hardcoded.
- Schema is implicit, so the burden moves to migrations: a change in field name or type must be paired with a plan for documents already written in the old shape. There is no engine-level error for a shape mismatch: it surfaces as a null field or a mapping failure at read time.
- Never point tests at the developer's local database. Tests own their own instance: see `web-frameworks/spring-boot/testing/integration.md`.

## [verify] Verify

- Every document type has an explicit persistence creator.
- A save-then-load round trip preserves every field, including those the creator does not take.
- Removing a document type's version field makes a concurrency test fail.
- Every declared index corresponds to a query in the codebase and is confirmed used by the query plan, or enforces a named invariant and has a test proving it.
- Everything in `[verify]` in `design/acid.md` holds, as tests.
- Any multi-document write runs in a transaction, or has a written reason why it cannot.
- Where transactions are used, a test proves one rolls back, on the same topology as production.
- The running connection uses `w=majority` and a bound on the wait in every environment.
- `db.hello().setName` gives the same kind of answer in local, test and production.
