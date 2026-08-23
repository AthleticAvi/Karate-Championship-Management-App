package com.management.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.enums.MatchEndReason;
import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.models.GameWithFighters;
import com.management.models.MatchOutcome;
import com.management.testsupport.KumiteGameBuilder;
import com.management.testsupport.PlayerBuilder;
import com.management.testsupport.TestRulesEngine;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The rules engine assembled as the container assembles it, but constructed with {@code new}.
 *
 * <p>The wiring comes from {@link TestRulesEngine} rather than being restated here, because the
 * order is the thing under test: this class used to build its own list in a different order from
 * the production {@code @Order} values, so every scenario below confirmed a priority the
 * application did not actually have. One source for the order means a change to it shows up as a
 * failing assertion on {@code decidedBy}.
 */
class MatchOutcomeEvaluatorTest {

  private final MatchOutcomeEvaluator evaluator = TestRulesEngine.standard();

  @Test
  void evaluate_whileTheMatchIsBeingFought_findsNoOutcome() {
    GameWithFighters match =
        KumiteGameBuilder.newGame()
            .runningSince(LocalDateTime.now(), Duration.ofSeconds(60))
            .buildWithFighters();

    assertThat(evaluator.evaluate(match)).isEmpty();
  }

  @Test
  void evaluate_whenFighterCrossesTheThreshold_theyWinOnPoints() {
    GameWithFighters match =
        running().with(PlayerColor.RED, scorer(PointsType.IPPON, 3)).buildWithFighters();

    Optional<MatchOutcome> outcome = evaluator.evaluate(match);

    assertThat(outcome).isPresent();
    assertThat(outcome.get().winner()).isEqualTo(PlayerColor.RED);
    assertThat(outcome.get().reason()).isEqualTo(MatchEndReason.POINTS_THRESHOLD);
    assertThat(outcome.get().decidedBy()).isEqualTo("HighestScoreWinnerRule");
  }

  @Test
  void evaluate_whenFighterReachesTheFoulLimit_theOpponentWins() {
    GameWithFighters match =
        running()
            .with(
                PlayerColor.BLUE,
                PlayerBuilder.newPlayer().alreadyPersistedAs("blue-1").withFouls(4).build())
            .buildWithFighters();

    Optional<MatchOutcome> outcome = evaluator.evaluate(match);

    assertThat(outcome).isPresent();
    assertThat(outcome.get().reason()).isEqualTo(MatchEndReason.FOUL_LIMIT);
    assertThat(outcome.get().winner()).isEqualTo(PlayerColor.RED);
    assertThat(outcome.get().decidedBy()).isEqualTo("FoulLimitWinnerRule");
  }

  /**
   * The rule this exists to prevent: the score used to decide a foul-limit ending, so a fighter who
   * was ahead could foul out and still win — a penalty that rewards the fighter it penalises.
   */
  @Test
  void evaluate_whenTheLeaderFoulsOut_theyDoNotWinOnTheirOwnFoul() {
    GameWithFighters match =
        running()
            .with(
                PlayerColor.BLUE,
                PlayerBuilder.newPlayer()
                    .alreadyPersistedAs("blue-1")
                    .scoring(PointsType.IPPON)
                    .withFouls(4)
                    .build())
            .buildWithFighters();

    Optional<MatchOutcome> outcome = evaluator.evaluate(match);

    assertThat(outcome).isPresent();
    assertThat(outcome.get().winner())
        .as("BLUE leads 3-0 and fouls out; the match goes to RED")
        .isEqualTo(PlayerColor.RED);
  }

  /** Disqualification outranks the score: a fighter who is out cannot win on points. */
  @Test
  void evaluate_whenTheLeaderIsDisqualified_theOpponentWins() {
    GameWithFighters match =
        running().with(PlayerColor.RED, scorer(PointsType.IPPON, 1)).buildWithFighters();
    match.fighters().get(PlayerColor.RED).disqualify();

    Optional<MatchOutcome> outcome = evaluator.evaluate(match);

    assertThat(outcome).isPresent();
    assertThat(outcome.get().winner()).isEqualTo(PlayerColor.BLUE);
    assertThat(outcome.get().reason()).isEqualTo(MatchEndReason.DISQUALIFICATION);
    assertThat(outcome.get().decidedBy()).isEqualTo("DisqualificationWinnerRule");
  }

  @Test
  void evaluate_whenTimeExpiresWithLevelScores_senshuDecidesIt() {
    GameWithFighters match =
        KumiteGameBuilder.newGame()
            .runningSince(LocalDateTime.now().minusMinutes(5), Duration.ofSeconds(30))
            .firstScorer(PlayerColor.BLUE)
            .with(PlayerColor.RED, scorer(PointsType.YUKO, 1))
            .with(
                PlayerColor.BLUE,
                PlayerBuilder.newPlayer()
                    .alreadyPersistedAs("blue-1")
                    .scoring(PointsType.YUKO, 1)
                    .build())
            .buildWithFighters();

    Optional<MatchOutcome> outcome = evaluator.evaluate(match);

    assertThat(outcome).isPresent();
    assertThat(outcome.get().winner()).isEqualTo(PlayerColor.BLUE);
    assertThat(outcome.get().reason()).isEqualTo(MatchEndReason.TIME_EXPIRED);
    assertThat(outcome.get().decidedBy()).isEqualTo("SenshuWinnerRule");
  }

  @Test
  void evaluate_whenTimeExpiresWithNobodyHavingScored_isDrawn() {
    GameWithFighters match =
        KumiteGameBuilder.newGame()
            .runningSince(LocalDateTime.now().minusMinutes(5), Duration.ofSeconds(30))
            .buildWithFighters();

    Optional<MatchOutcome> outcome = evaluator.evaluate(match);

    assertThat(outcome).isPresent();
    assertThat(outcome.get().winner()).as("a draw is a result, not a missing one").isNull();
    assertThat(outcome.get().decidedBy()).isEqualTo("DrawWinnerRule");
  }

  @Test
  void evaluate_onMatchThatHasAlreadyFinished_findsNothingToChange() {
    GameWithFighters finished =
        KumiteGameBuilder.newGame()
            .finished()
            .with(PlayerColor.RED, scorer(PointsType.IPPON, 3))
            .buildWithFighters();

    assertThat(evaluator.evaluate(finished))
        .as("the first result stands; re-evaluating must never rewrite it")
        .isEmpty();
  }

  private static KumiteGameBuilder running() {
    return KumiteGameBuilder.newGame().runningSince(LocalDateTime.now(), Duration.ofSeconds(60));
  }

  private static com.management.models.Player scorer(PointsType type, int times) {
    return PlayerBuilder.newPlayer()
        .alreadyPersistedAs("red-1")
        .named("Kenji")
        .scoring(type, times)
        .build();
  }
}
