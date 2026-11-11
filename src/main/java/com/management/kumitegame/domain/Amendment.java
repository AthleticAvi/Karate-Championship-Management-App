package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * A correction, as an entry of its own that names its target. Never a flag changed in place, so it
 * is ordered, attributed and recognised on a retry like any other entry.
 */
public record Amendment(
    AmendmentKind kind, String targetId, @Nullable String reason, EntryMeta meta)
    implements Entry {}
