package com.management.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.enums.MatchEndReason;
import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.models.GameWithFighters;
import com.management.rules.conditions.DisqualificationCondition;
import com.management.rules.conditions.FoulLimitCondition;
import com.management.rules.conditions.PointsThresholdCondition;
import com.management.rules.conditions.TimeExpiredCondition;
import com.management.testsupport.KumiteGameBuilder;
import com.management.testsupport.PlayerBuilder;
import com.management.testsupport.TestGameProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * One test class per condition would be four files of three lines each; they are grouped here
 * because each condition is a single predicate and the interesting part is the boundary — one point
 * short, exactly at, one past.
 */
class MatchEndConditionTest {

  private final PointsThresholdCondition pointsThreshold =
      new PointsThresholdCondition(TestGameProperties.standard());
  private final FoulLimitCondition foulLimit =
      new FoulLimitCondition(TestGameProperties.standard());
  private final TimeExpiredCondition timeExpired = new TimeExpiredCondition();
  private final DisqualificationCondition disqualification = new DisqualificationCondition();

  @Test
  void pointsThreshold_belowTheWinningScore_doesNotEndTheMatch() {
    GameWithFighters match = matchWhereRedHasScored(PointsType.WAZARI, 3);

    assertThat(match.fighters().get(PlayerColor.RED).getPoints().getNumOfPoints()).isEqualTo(6);
    assertThat(pointsThreshold.evaluate(match)).isEmpty();
  }

  @Test
  void pointsThreshold_exactlyAtTheWinningScore_endsTheMatch() {
    GameWithFighters match = matchWhereRedHasScored(PointsType.WAZARI, 4);

    assertThat(pointsThreshold.evaluate(match)).contains(MatchEndReason.POINTS_THRESHOLD);
  }

  /** Crossing, not landing on: the score is never clamped to the threshold. */
  @Test
  void pointsThreshold_whenTheScoreOvershoots_endsTheMatchAndKeepsTheMargin() {
    GameWithFighters match = matchWhereRedHasScored(PointsType.IPPON, 4);

    assertThat(pointsThreshold.evaluate(match)).contains(MatchEndReason.POINTS_THRESHOLD);
    assertThat(match.fighters().get(PlayerColor.RED).getPoints().getNumOfPoints())
        .as("12 points stands; the threshold triggers the end, it does not cap the score")
        .isEqualTo(12);
  }

  @Test
  void foulLimit_belowTheLimit_doesNotEndTheMatch() {
    assertThat(foulLimit.evaluate(matchWhereBlueHasFouled(3))).isEmpty();
  }

  @Test
  void foulLimit_atTheLimit_endsTheMatch() {
    assertThat(foulLimit.evaluate(matchWhereBlueHasFouled(4))).contains(MatchEndReason.FOUL_LIMIT);
  }

  @Test
  void timeExpired_whileTimeRemains_doesNotEndTheMatch() {
    GameWithFighters running =
        KumiteGameBuilder.newGame()
            .runningSince(LocalDateTime.now(), Duration.ofSeconds(30))
            .buildWithFighters();

    assertThat(timeExpired.evaluate(running)).isEmpty();
  }

  @Test
  void timeExpired_whenTheClockHasRunOut_endsTheMatch() {
    GameWithFighters expired =
        KumiteGameBuilder.newGame()
            .runningSince(LocalDateTime.now().minusMinutes(5), Duration.ofSeconds(30))
            .buildWithFighters();

    assertThat(timeExpired.evaluate(expired)).contains(MatchEndReason.TIME_EXPIRED);
  }

  /** A paused clock is not counting, so it cannot expire. */
  @Test
  void timeExpired_whenTheMatchIsPaused_doesNotEndTheMatch() {
    GameWithFighters paused =
        KumiteGameBuilder.newGame().pausedWithRemaining(Duration.ZERO).buildWithFighters();

    assertThat(timeExpired.evaluate(paused)).isEmpty();
  }

  @Test
  void disqualification_whenNobodyIsOut_doesNotEndTheMatch() {
    assertThat(disqualification.evaluate(KumiteGameBuilder.newGame().buildWithFighters()))
        .isEmpty();
  }

  @Test
  void disqualification_whenFighterIsOut_endsTheMatch() {
    GameWithFighters match = KumiteGameBuilder.newGame().buildWithFighters();
    match.fighters().get(PlayerColor.BLUE).disqualify();

    assertThat(disqualification.evaluate(match)).contains(MatchEndReason.DISQUALIFICATION);
  }

  private static GameWithFighters matchWhereRedHasScored(PointsType type, int times) {
    return KumiteGameBuilder.newGame()
        .with(
            PlayerColor.RED,
            PlayerBuilder.newPlayer()
                .alreadyPersistedAs("red-1")
                .named("Kenji")
                .scoring(type, times)
                .build())
        .buildWithFighters();
  }

  private static GameWithFighters matchWhereBlueHasFouled(int fouls) {
    return KumiteGameBuilder.newGame()
        .with(
            PlayerColor.BLUE,
            PlayerBuilder.newPlayer()
                .alreadyPersistedAs("blue-1")
                .named("Sato")
                .withFouls(fouls)
                .build())
        .buildWithFighters();
  }
}
