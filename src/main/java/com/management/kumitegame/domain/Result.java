package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * A result as the officials decided it. A later one supersedes it, and it stays on record.
 *
 * <p>What the system suggested is not here yet: it is added, as a field of its own, when the system
 * suggests results.
 *
 * @param winner only for a WIN
 * @param bothDisqualified keeps the fact in a medal game decided on points (Article 12.2.11)
 */
public record Result(
    Outcome outcome,
    @Nullable Side winner,
    ResultReason reason,
    @Nullable String reasonDetail,
    boolean bothDisqualified,
    EntryMeta meta)
    implements Entry {}
