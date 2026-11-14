package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * SENSHU awarded to a side, or withdrawn from it, as the officials decided. Who holds it is derived
 * from these, never stored: see {@link SenshuFold}.
 *
 * @param reason why, such as a video review (Article 12.2.9)
 * @param exchange the number of main-clock starts it belongs to; empty before the start
 * @param exchangeNamed whether the client named the exchange; false when the server filled it in
 * @param clockRemainingMs the time the screen showed when it was given
 */
public record SenshuRecord(
    Side side,
    SenshuRecordKind kind,
    @Nullable String reason,
    @Nullable Integer exchange,
    boolean exchangeNamed,
    @Nullable Long clockRemainingMs,
    EntryMeta meta)
    implements Entry {}
