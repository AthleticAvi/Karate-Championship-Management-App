# Concurrency and Consistency

Applies when: two writes must agree, simultaneous updates, lost updates, retry, idempotency, idempotency key, duplicate request, aggregate boundaries, dual write, derived state, history, current value
Status: enforced
Framework-first: optimistic locking, retry and transaction management are framework features. A hand-written revision counter or retry loop is a defect.

## Index

- [default-failure-mode-is] The default failure mode is silence
- [one-fact-one-owner] Rule 1: one fact, one owner
- [version-writes] Rule 2: read-modify-write needs a version
- [write-last] Rule 3: write nothing until everything that can fail has failed
- [idempotency] Rule 4: idempotency where a caller may retry
- [verify] Verify
- [sources] Sources

## [default-failure-mode-is] The default failure mode is silence

Both problems in this document fail without an error:

- **Lost update.** Two callers read the same record, both modify it, both write. The second overwrites the first. Neither caller sees a failure. The loss is discovered, if ever, when someone disputes the result: by which time the evidence is gone.
- **Divergent copies.** The same fact is written to two places in sequence. The second write fails. Nothing reports it. Reads now return different answers depending on which copy they consult.

Neither produces a log line, an alert, or a failed request. Design against them up front; they cannot be monitored into visibility afterwards.

## [one-fact-one-owner] Rule 1: one fact, one owner

Every fact has exactly one authoritative location. A second copy maintained by application code is a defect regardless of how carefully it is synchronised.

The dual-write anti-pattern:

```
save(record)                 // write 1, succeeds
syncCopyElsewhere(record)    // write 2, may fail
```

There is no ordering of these two writes that is safe without a transaction spanning both, and a transaction spanning both is often unavailable: check the persistence file for the datastore.

**Remove the second write.** Store a reference and resolve it on read. The cost of an extra read is negligible next to a consistency bug that cannot be detected.

Options in descending order of preference:

1. **Eliminate the duplication.** Store the reference, compose on read. The problem stops existing.
2. **Make the pair recoverable.** Accept temporary divergence, reconcile on next read, and make the operation idempotent so replay is safe. More moving parts, and the window is visible to clients.
3. **Span both writes in a transaction.** Correct where the engine supports it. Requires the topology to support transactions in every environment.

Choose 1 unless there is a measured reason not to. Record the choice and the reason.

**A current value stored beside its own history is a second copy.** A status field next to the
list of status changes, or a total next to the list of entries, disagrees the first time a code
path appends to the history and forgets the field. One atomic write does not prevent that: it only
makes the mistake land whole. Derive the current value by folding the history in order. When a
query needs it stored, write it from the same derived getter, so there is one code path.

**A fold over stored history never throws.** Check validity when an entry is appended, not when the
history is replayed. A rule tightened later must not make old records unloadable; an inconsistency
found while folding is reported, not raised.

## [version-writes] Rule 2: read-modify-write needs a version

Any sequence of read, change in memory, write is exposed to lost updates. A version field on the record turns the silent overwrite into a detectable conflict.

Conflict handling depends on the operation:

- **Relative change**: "add three", "increment", "append". Safe to retry: re-read, re-apply, write again, bounded to a small number of attempts. After exhausting attempts, fail loudly.
- **Absolute change**: "set the name to X". Retrying blindly reasserts a value the user chose while looking at stale data. Return the conflict to the caller with the current state so they can decide.

Never retry unboundedly, and never swallow the final failure. A retry that gives up silently is the original bug with extra steps.

Prefer an atomic engine-side update over read-modify-write where the operation allows it. An engine-level increment has no window to lose.

## [write-last] Rule 3: write nothing until everything that can fail has failed

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

This narrows the window rather than closing it. Where a residual window remains, either make the sequence idempotent so it can be safely repeated, or add a compensating delete, and record the residual risk.

Orphans are invisible: nothing errors, nothing references them, and no query distinguishes an orphan from a legitimately unattached record. Prevention is the only cheap option.

## [idempotency] Rule 4: idempotency where a caller may retry

Any operation a client might send twice (because of a timeout, a retry, or a double submission) must produce the same result the second time. Adding a point twice because the network hiccuped is indistinguishable from a genuine second point unless the operation carries a caller-supplied key.

- **The client generates the key**, one per logical action, a random identifier, and sends the
  same key on every retry of that action.
- **Store the key in the same atomic unit as the change.** A key kept in a separate store is a
  dual write: the change can land without the key, and the retry applies it again.
- **Same key, same payload:** return the original result and apply nothing.
- **Same key, different payload:** refuse. Silently returning the first result hides a client bug.
- **Compare only what the client sent.** Values the server assigns (order, timestamps, warnings)
  differ on a retry by design. Count the caller's identity as part of the payload.
- **Scope and bound the keys.** Check them per aggregate, or expire them under a published policy,
  so the set never grows without limit.
- **A version conflict is not a duplicate.** Retry a relative change under its key; return an
  absolute change to the caller ([version-writes]).
- **One client, fast successive actions:** send them one at a time, each after the previous
  answer. Concurrent sends from one client race each other on the version and can land out of
  order.

## [verify] Verify

- A test drives two genuinely concurrent modifications of the same record and asserts both are reflected. Sequential calls do not reproduce the defect; use a latch.
- Forcing a failure at each write in a multi-write sequence leaves no partial state.
- Every duplicated fact in the model has a written justification, or has been eliminated.
- Retry limits are bounded and exhaustion surfaces an error.
- A retried request with the same key applies once; the same key with another payload is refused.
- No current value is stored beside its history unless it is written from the derived getter.
- Loading a record whose history breaks a current rule succeeds.

## [sources] Sources

- IETF draft-ietf-httpapi-idempotency-key-header (work in progress, revision 07, October 2025):
  client-generated key, reuse with a different payload refused (422), a retry while the first is
  still processing answered 409, an expiry policy published by the server.
