package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * A result as the officials decided it. A later one supersedes it, and it stays on record.
 *
 * @param winner only for a WIN
 * @param bothDisqualified keeps the fact in a medal game decided on points (Article 12.2.11)
 * @param suggested what the rules suggested when it was recorded, kept as the scorekeeper saw it:
 *     later corrections change the live suggestion, never this. Empty when they suggested nothing
 */
public record Result(
    Outcome outcome,
    @Nullable Side winner,
    ResultReason reason,
    @Nullable String reasonDetail,
    boolean bothDisqualified,
    @Nullable SuggestedResult suggested,
    EntryMeta meta)
    implements Entry {}
