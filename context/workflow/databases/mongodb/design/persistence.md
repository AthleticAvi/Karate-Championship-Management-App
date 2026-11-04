# MongoDB Persistence

Applies when: defining a document, mapping fields, object construction on read, indexes, transactions, local database setup, versioning
Status: enforced
Framework-first: the repository abstraction, mapping layer, auditing, index declaration and optimistic locking are all provided. Direct driver calls in a service are a defect.

## Document design

- **One document type per aggregate root.** A repository exists per aggregate root, not per class.
- **Store references, not copies.** Embedding a copy of another aggregate creates two sources of truth that must be written together — see `patterns/concurrency-consistency.md`. Embed only value objects that have no independent identity and are always read with their parent.
- **Documents are not API types.** They never appear in a controller signature. See `patterns/service-exposure.md`.

## Object construction on read

The mapping layer must be able to rebuild an instance from a stored document. It resolves a creator in a fixed order: a no-argument constructor if present, otherwise a single constructor, otherwise one explicitly annotated as the persistence creator. Fields not covered by the chosen creator are populated afterwards.

Two failure modes follow, both silent:

- **A single constructor that also assigns defaults runs those assignments on every read.** They are then overwritten by the stored values — but only for fields the mapping layer knows about. Any field assigned in the constructor with no stored counterpart is reset on every load. Nothing reports this.
- **Constructor-argument matching is by parameter name**, which requires the compiler to retain parameter names. Without that build setting, construction fails at runtime with no compile-time signal. See `standards/enforcement.md`.

**Rule:** every document type declares its creator explicitly — either a no-argument constructor, or a constructor annotated as the persistence creator. Never rely on "there happens to be only one constructor". Keep construction of a *new* aggregate separate from reconstitution of a stored one; a factory method expresses the first, the creator expresses the second.

## Excluded fields

A field marked transient is never written and never populated on read. It is `null` on every loaded instance.

Any object holding a transient field must reconstruct it before use, from state that *is* persisted, on every path that touches it. Reconstructing from partial state is worse than leaving it null: it produces an object that looks valid and behaves wrongly. If a transient field derives from two persisted values, restore both.

Prefer computing such state on demand from persisted fields over holding it in a field at all. A field that cannot be persisted is a field that can be forgotten.

## Identifiers

Expose the identifier deliberately as a named field on the response type. Never let the storage identifier reach a client incidentally through direct serialisation of a document.

## Optimistic locking

A version field on a document makes the mapping layer include the current version in every update. A write against a stale version matches no document and raises a conflict rather than silently overwriting.

- **Use the primitive integer form, not the boxed form.** An unset primitive is zero, which the framework treats as "not yet stored". The boxed form is accepted in principle, but the primitive is the reliable choice for document stores.
- **Requires acknowledged writes.** An unacknowledged write concern discards the conflict silently, reintroducing exactly the defect the version field prevents. Leave the default alone.
- **Decide what happens to documents written before the field existed.** Either backfill them or accept that their first save is treated as an insert. Write the decision down.

Handling the conflict is a separate concern: see `patterns/concurrency-consistency.md`.

## Transactions

Multi-document transactions require the engine to run as a replica set. A standalone instance does not support them at all, so an annotation requesting one has no effect and gives no warning.

Before designing anything that needs two documents written atomically, confirm the deployment topology in every environment including local development. If it is standalone, the correct response is to remove the need for the second write, not to add an annotation that does nothing.

## Indexes

Add an index when a query filters on something other than the identifier — never speculatively. An index on an unqueried field costs write throughput and storage and returns nothing.

Derive them from the queries that exist: list the repository methods, index what they filter and sort on, confirm with the engine's query plan that the index is actually used.

Automatic index creation from annotations is off by default in current versions. Creating indexes as an explicit deployment step is preferable beyond development, because it makes index changes reviewable and lets them be applied without a redeploy.

## Auditing

Created and modified timestamps and actors are provided by the framework's auditing support, enabled with an annotation and a configuration switch. Never maintain them by hand in a service — hand-maintained audit fields are wrong the first time a write path is added that forgets them.

## Local setup and initialisation

- The database runs in a container, pinned to the same major version as production.
- Connection settings come from the environment with local defaults, never hardcoded.
- Schema is implicit, so the burden moves to migrations: a change in field name or type must be paired with a plan for documents already written in the old shape. There is no engine-level error for a shape mismatch — it surfaces as a null field or a mapping failure at read time.
- Never point tests at the developer's local database. Tests own their own instance: see `patterns/testing-integration.md`.

## Verify

- Every document type has an explicit persistence creator.
- A save-then-load round trip preserves every field, including those the creator does not take.
- Removing a document type's version field makes a concurrency test fail.
- Every declared index corresponds to a query in the codebase and is confirmed used by the query plan.
