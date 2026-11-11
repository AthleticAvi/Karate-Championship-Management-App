package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * One side, derived from the entries: never stored. Cancelled scores and penalties do not count.
 *
 * @param athleteId the latest athlete put on this side
 */
public record SideState(
    @Nullable String athleteId,
    int total,
    int ipponCount,
    int wazaAriCount,
    int chuiCount,
    int hansokuChuiCount) {}
