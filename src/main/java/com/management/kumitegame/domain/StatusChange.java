package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * A status move, as an entry of its own: approve, reopen or void. Carries no exchange or clock
 * remaining, which mean nothing here.
 *
 * @param reason required for an Admin's move: a reopen of an APPROVED game, or a void
 */
public record StatusChange(StatusChangeKind kind, @Nullable String reason, EntryMeta meta)
    implements Entry {}
