package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * A penalty, kept on its side's list, at the level the referee gave. In elimination it gives the
 * opponent no points.
 *
 * @param exchangeNamed whether the client named the exchange; false when the server filled it in
 */
public record Penalty(
    PenaltyLevel level,
    @Nullable Infraction infraction,
    @Nullable Integer exchange,
    boolean exchangeNamed,
    @Nullable Long clockRemainingMs,
    EntryMeta meta)
    implements Entry {}
