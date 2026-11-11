package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * An athlete put on a side, or a referee added to or removed from the panel. The athlete per side
 * and the panel are derived from these, so a change stays on record.
 */
public record Assignment(
    AssignmentKind kind,
    @Nullable Side side,
    @Nullable String athleteId,
    @Nullable String refereeId,
    @Nullable PanelRole role,
    EntryMeta meta)
    implements Entry {}
