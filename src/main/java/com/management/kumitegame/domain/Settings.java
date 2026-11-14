package com.management.kumitegame.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The rule values a game runs under: a snapshot copied from configuration when the game is created.
 *
 * <p>Rules read only this, so a configuration change never reaches a game already created, and a
 * running or finished game never changes its rules. It changes only while the game has not started.
 * Every duration is in milliseconds.
 *
 * @param durationMs the main clock's starting time (Articles 5.1, 5.2)
 * @param durationsOfferedMs the durations a screen offers; any other positive one is allowed too
 * @param pointValues the points each score level is worth (Article 8)
 * @param leadToWin the lead that wins the game (Articles 7.7, 12.2.1)
 * @param finalWindowMs the end of the game where SENSHU rules change (Articles 12.2.8, 12.2.10)
 * @param lateScoreWindowMs how long after TIME_UP a score still counts (Article 8.10)
 * @param chuiLimit the CHUI a side may get before the next one warns (Article 10.3)
 * @param roundRobinForfeitPoints the score a round-robin opponent gets on a forfeit (Article
 *     10.7.1)
 * @param addTimeStepsMs the time additions a screen offers; any other positive one is allowed too
 * @param medicalTimeMs a medical timer's starting time (Article 11.2.3)
 * @param timeShownToleranceMs how far the time a screen showed may be from the derived clock
 * @param panelLimit the most referees the panel holds: more are refused (Article 4.1.1 composes the
 *     panel; the limit is the owner's). None for a game created before the limit existed
 * @param rulebookVersion which rulebook the game ran under
 */
public record Settings(
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
    long timeShownToleranceMs,
    @Nullable Integer panelLimit,
    String rulebookVersion) {

  /** Refuses values no game can run under: a duration of zero or less, a level with no value. */
  public Settings {
    if (durationMs <= 0) {
      throw new InvalidGameRequestException(
          "A game's duration must be more than zero, but " + durationMs + " ms was given.");
    }
    if (medicalTimeMs <= 0) {
      throw new InvalidGameRequestException(
          "The medical time must be more than zero, but " + medicalTimeMs + " ms was given.");
    }
    if (!pointValues.keySet().containsAll(List.of(ScoreLevel.values()))) {
      throw new InvalidGameRequestException(
          "Every score level needs a value, but only " + pointValues.keySet() + " were given.");
    }
    durationsOfferedMs = List.copyOf(durationsOfferedMs);
    pointValues = Map.copyOf(new EnumMap<>(pointValues));
    addTimeStepsMs = List.copyOf(addTimeStepsMs);
  }

  public int pointsFor(ScoreLevel level) {
    return Objects.requireNonNull(pointValues.get(level));
  }
}
