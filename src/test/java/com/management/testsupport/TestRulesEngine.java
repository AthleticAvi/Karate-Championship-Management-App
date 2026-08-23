package com.management.testsupport;

import com.management.rules.MatchOutcomeEvaluator;
import com.management.rules.conditions.DisqualificationCondition;
import com.management.rules.conditions.FoulLimitCondition;
import com.management.rules.conditions.PointsThresholdCondition;
import com.management.rules.conditions.TimeExpiredCondition;
import com.management.rules.winners.DisqualificationWinnerRule;
import com.management.rules.winners.DrawWinnerRule;
import com.management.rules.winners.FoulLimitWinnerRule;
import com.management.rules.winners.HighestScoreWinnerRule;
import com.management.rules.winners.SenshuWinnerRule;
import java.util.List;

/**
 * The rules engine wired the way the container wires it, for tests that construct services with
 * {@code new}.
 *
 * <p><strong>Both lists are in {@code @Order} order, and that is load-bearing.</strong> The
 * container sorts by the annotation; this class cannot, so the order is written out by hand. An
 * earlier version listed the conditions in a different order from the annotations, which meant
 * every test agreed on a priority the application did not have. If a rule's {@code @Order} changes,
 * change it here in the same commit — {@code MatchOutcomeEvaluatorTest} asserts which rule decided,
 * so a mismatch shows up as a failing assertion rather than as silence.
 */
public final class TestRulesEngine {

  private TestRulesEngine() {}

  public static MatchOutcomeEvaluator standard() {
    return new MatchOutcomeEvaluator(
        List.of(
            new DisqualificationCondition(),
            new FoulLimitCondition(TestGameProperties.standard()),
            new PointsThresholdCondition(TestGameProperties.standard()),
            new TimeExpiredCondition()),
        List.of(
            new DisqualificationWinnerRule(),
            new FoulLimitWinnerRule(TestGameProperties.standard()),
            new HighestScoreWinnerRule(),
            new SenshuWinnerRule(),
            new DrawWinnerRule()));
  }
}
