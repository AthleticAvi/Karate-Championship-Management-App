package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * A penalty, kept on its side's list, at the level the referee gave. In elimination it gives the
 * opponent no points.
 */
public record Penalty(
    PenaltyLevel level,
    @Nullable Infraction infraction,
    @Nullable Integer exchange,
    @Nullable Long clockRemainingMs,
    EntryMeta meta)
    implements Entry {}
