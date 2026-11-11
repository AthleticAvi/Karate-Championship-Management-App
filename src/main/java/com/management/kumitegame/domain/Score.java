package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * A score, kept on its side's list. Its points come from the settings, never from here.
 *
 * @param exchange the number of main-clock starts it belongs to; empty before the start
 * @param clockRemainingMs the time the screen showed when it was given
 */
public record Score(
    ScoreLevel level,
    ScoreSource source,
    @Nullable Integer exchange,
    @Nullable Long clockRemainingMs,
    EntryMeta meta)
    implements Entry {}
