package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * A warning the fold found, rather than one an action raised: the game's format, or stored history
 * this build cannot fold as written.
 *
 * @param entryId the entry it concerns; empty when it concerns the game
 */
public record GameWarning(Warning code, @Nullable String entryId) {}
