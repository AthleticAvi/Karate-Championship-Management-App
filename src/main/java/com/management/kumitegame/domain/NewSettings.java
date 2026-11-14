package com.management.kumitegame.domain;

import java.util.List;
import java.util.Map;

/**
 * Settings as the client sent them: every value a game may change before it starts. The rulebook
 * version and the panel limit are not among them: they stay what configuration gave the game.
 */
public record NewSettings(
    long durationMs,
    List<Long> durationsOfferedMs,
    Map<ScoreLevel, Integer> pointValues,
    int leadToWin,
    long finalWindowMs,
    long lateScoreWindowMs,
    int chuiLimit,
    int roundRobinForfeitPoints,
    List<Long> addTimeStepsMs,
    long medicalTimeMs,
    long timeShownToleranceMs) {

  /** These values, with what the game's current settings keep from configuration. */
  Settings replacing(Settings current) {
    return new Settings(
        durationMs,
        durationsOfferedMs,
        pointValues,
        leadToWin,
        finalWindowMs,
        lateScoreWindowMs,
        chuiLimit,
        roundRobinForfeitPoints,
        addTimeStepsMs,
        medicalTimeMs,
        timeShownToleranceMs,
        current.panelLimit(),
        current.rulebookVersion());
  }
}
