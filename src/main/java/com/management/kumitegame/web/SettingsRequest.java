package com.management.kumitegame.web;

import com.management.kumitegame.domain.ScoreLevel;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Map;

/**
 * Every setting a game may change before it starts, in whole milliseconds. The rulebook version is
 * not here: it stays what configuration gave the game.
 */
public record SettingsRequest(
    @Positive long durationMs,
    @NotEmpty List<@NotNull @Positive Long> durationsOfferedMs,
    @NotEmpty Map<@NotNull ScoreLevel, @NotNull @Positive Integer> pointValues,
    @Positive int leadToWin,
    @Positive long finalWindowMs,
    @PositiveOrZero long lateScoreWindowMs,
    @Positive int chuiLimit,
    @Positive int roundRobinForfeitPoints,
    @NotEmpty List<@NotNull @Positive Long> addTimeStepsMs,
    @Positive long medicalTimeMs,
    @PositiveOrZero long timeShownToleranceMs) {}
