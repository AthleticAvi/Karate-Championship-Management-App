package com.management.models;

import org.jspecify.annotations.Nullable;

/**
 * A referee's override, as the business layer sees it. The gateway maps the request into this.
 *
 * @param winner the colour declared the winner, or {@code null} for no winner
 */
public record NewOverride(@Nullable String winner, String reason, String decidedBy) {}
