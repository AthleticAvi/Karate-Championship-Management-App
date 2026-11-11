package com.management.kumitegame.domain;

import java.util.List;
import java.util.Map;

/**
 * Settings as the client sent them: every value a game may change before it starts. The rulebook
 * version is not one of them: it stays what configuration gave the game.
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

  Settings under(String rulebookVersion) {
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
        rulebookVersion);
  }
}
