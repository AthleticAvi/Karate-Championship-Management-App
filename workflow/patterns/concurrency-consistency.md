# Concurrency and Consistency

Applies when: two writes must agree, simultaneous updates, lost updates, retry, idempotency, aggregate boundaries, dual write
Status: enforced
Framework-first: optimistic locking, retry and transaction management are framework features. A hand-written revision counter or retry loop is a defect.

## The default failure mode is silence

Both problems in this document fail without an error:

- **Lost update.** Two callers read the same record, both modify it, both write. The second overwrites the first. Neither caller sees a failure. The loss is discovered, if ever, when someone disputes the result — by which time the evidence is gone.
- **Divergent copies.** The same fact is written to two places in sequence. The second write fails. Nothing reports it. Reads now return different answers depending on which copy they consult.

Neither produces a log line, an alert, or a failed request. Design against them up front; they cannot be monitored into visibility afterwards.

## Rule 1 — one fact, one owner

Every fact has exactly one authoritative location. A second copy maintained by application code is a defect regardless of how carefully it is synchronised.

The dual-write anti-pattern:

```
save(record)                 // write 1, succeeds
syncCopyElsewhere(record)    // write 2, may fail
```

There is no ordering of these two writes that is safe without a transaction spanning both, and a transaction spanning both is often unavailable — see `patterns/persistence-mongodb.md`.

**Remove the second write.** Store a reference and resolve it on read. The cost of an extra read is negligible next to a consistency bug that cannot be detected.

Options in descending order of preference:

1. **Eliminate the duplication.** Store the reference, compose on read. The problem stops existing.
2. **Make the pair recoverable.** Accept temporary divergence, reconcile on next read, and make the operation idempotent so replay is safe. More moving parts, and the window is visible to clients.
3. **Span both writes in a transaction.** Correct where the engine supports it. Requires the topology to support transactions in every environment.

Choose 1 unless there is a measured reason not to. Record the choice and the reason.

## Rule 2 — read-modify-write needs a version

Any sequence of read, change in memory, write is exposed to lost updates. A version field on the record turns the silent overwrite into a detectable conflict.

Conflict handling depends on the operation:

- **Relative change** — "add three", "increment", "append". Safe to retry: re-read, re-apply, write again, bounded to a small number of attempts. After exhausting attempts, fail loudly.
- **Absolute change** — "set the name to X". Retrying blindly reasserts a value the user chose while looking at stale data. Return the conflict to the caller with the current state so they can decide.

Never retry unboundedly, and never swallow the final failure. A retry that gives up silently is the original bug with extra steps.

Prefer an atomic engine-side update over read-modify-write where the operation allows it. An engine-level increment has no window to lose.

## Rule 3 — write nothing until everything that can fail has failed

Order an operation so all validation, lookup and computation happens before the first write. A failure after a partial write leaves orphaned records that nothing references and nothing will clean up.

```
// wrong: fallible work between writes
child = save(child)          // written
duration = resolve(request)  // throws -> child is now an orphan, referenced by nothing
parent  = save(parent)

// right
duration = resolve(request)  // all fallible work first
validate(request)
child  = save(child)         // writes last, adjacent
parent = save(parent)
```

This narrows the window rather than closing it. Where a residual window remains, either make the sequence idempotent so it can be safely repeated, or add a compensating delete — and record the residual risk.

Orphans are invisible: nothing errors, nothing references them, and no query distinguishes an orphan from a legitimately unattached record. Prevention is the only cheap option.

## Rule 4 — idempotency where a caller may retry

Any operation a client might send twice — because of a timeout, a retry, or a double submission — must produce the same result the second time. Adding a point twice because the network hiccuped is indistinguishable from a genuine second point unless the operation carries a caller-supplied key.

## Verify

- A test drives two genuinely concurrent modifications of the same record and asserts both are reflected. Sequential calls do not reproduce the defect; use a latch.
- Forcing a failure at each write in a multi-write sequence leaves no partial state.
- Every duplicated fact in the model has a written justification, or has been eliminated.
- Retry limits are bounded and exhaustion surfaces an error.
