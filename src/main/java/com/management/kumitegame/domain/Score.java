package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * A score, kept on its side's list. Its points come from the settings, never from here.
 *
 * @param exchange the number of main-clock starts it belongs to; empty before the start
 * @param exchangeNamed whether the client named the exchange; false when the server filled it in
 * @param clockRemainingMs the time the screen showed when it was given
 */
public record Score(
    ScoreLevel level,
    ScoreSource source,
    @Nullable Integer exchange,
    boolean exchangeNamed,
    @Nullable Long clockRemainingMs,
    EntryMeta meta)
    implements Entry {}
