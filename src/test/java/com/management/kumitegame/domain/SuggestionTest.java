package com.management.kumitegame.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.testsupport.TestClock;
import com.management.testsupport.TestGames;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The suggestion inside a game, with the real chain: live in every state, kept on each result as it
 * was when recorded, the warnings a result or a correction raises against it, and the round-robin
 * effective score.
 */
class SuggestionTest {

  private static final Instant T0 = TestClock.NOW;
  private static final String SK = TestGames.SCOREKEEPER;

  private KumiteGame game = TestGames.game();
  private int next;

  private String id() {
    return "entry-" + ++next;
  }

  private String lastId() {
    return "entry-" + next;
  }

  private static Instant at(double seconds) {
    return T0.plusMillis(Math.round(seconds * 1000));
  }

  private void start() {
    game.recordClockAction(
        new NewClockAction(id(), Timer.MAIN, null, ClockActionKind.START, 120_000, null, null),
        SK,
        T0);
  }

  private void timeUp() {
    game.recordClockAction(
        new NewClockAction(id(), Timer.MAIN, null, ClockActionKind.TIME_UP, 0, null, null),
        SK,
        at(120));
  }

  private Recorded score(Side side, ScoreLevel level) {
    return game.recordScore(
        new NewScore(id(), side, level, ScoreSource.JUDGES, null, null), SK, at(10));
  }

  private Recorded penalty(Side side, PenaltyLevel level) {
    return game.recordPenalty(new NewPenalty(id(), side, level, null, null, null), SK, at(10));
  }

  private Recorded result(Outcome outcome, @Nullable Side winner, ResultReason reason) {
    return game.recordResult(
        new NewResult(id(), outcome, winner, reason, null, false), SK, at(121));
  }

  private Recorded cancel(String targetId) {
    return game.recordAmendment(
        new NewAmendment(id(), AmendmentKind.CANCEL, targetId, null), SK, at(122));
  }

  private GameState state() {
    return game.state();
  }

  private Result current() {
    return Objects.requireNonNull(state().currentResult());
  }

  @Nested
  class Live {

    @Test
    void isInEveryState_andFollowsTheEntries() {
      start();
      assertThat(state().suggested()).isNull();
      score(Side.AKA, ScoreLevel.WAZA_ARI);
      timeUp();

      assertThat(state().suggested()).isEqualTo(SuggestedResult.win(Side.AKA, ResultReason.POINTS));
    }

    @Test
    void holdsWhileDecided_asTheClockLeftTheGame() {
      start();
      timeUp();
      result(Outcome.WIN, Side.AO, ResultReason.HANTEI);

      assertThat(state().status()).isEqualTo(GameStatus.DECIDED);
      assertThat(state().suggested()).isEqualTo(SuggestedResult.hantei());
    }

    @Test
    void timePutBackWhileDecided_endsTheTimeUpSuggestion() {
      start();
      score(Side.AKA, ScoreLevel.YUKO);
      timeUp();
      result(Outcome.WIN, Side.AKA, ResultReason.POINTS);

      game.recordClockAction(
          new NewClockAction(id(), Timer.MAIN, null, ClockActionKind.ADD_TIME, 0, 10_000L, null),
          SK,
          at(125));

      assertThat(state().suggested()).isNull();
    }

    @Test
    void voidedGame_suggestsNothing() {
      start();
      score(Side.AKA, ScoreLevel.YUKO);
      timeUp();
      game.recordStatusChange(
          new NewStatusChange(id(), StatusChangeKind.VOID, "protest upheld"),
          ActingRole.ADMIN,
          SK,
          at(130));

      assertThat(state().suggested()).isNull();
    }

    @Test
    void cancelledPenalty_doesNotDisqualify() {
      start();
      penalty(Side.AO, PenaltyLevel.HANSOKU);
      assertThat(state().suggested())
          .isEqualTo(SuggestedResult.win(Side.AKA, ResultReason.HANSOKU));

      cancel(lastId());

      assertThat(state().suggested()).isNull();
    }
  }

  @Nested
  class RecordedResult {

    @Test
    void keepsTheSuggestionItWasRecordedAgainst_andAgreeing_raisesNothing() {
      start();
      score(Side.AO, ScoreLevel.IPPON);
      timeUp();

      Recorded recorded = result(Outcome.WIN, Side.AO, ResultReason.POINTS);

      assertThat(recorded.raisedWarnings()).isEmpty();
      assertThat(current().suggested())
          .isEqualTo(SuggestedResult.win(Side.AO, ResultReason.POINTS));
    }

    @Test
    void differentFromTheSuggestion_isAppliedWithWarning() {
      start();
      score(Side.AO, ScoreLevel.IPPON);
      timeUp();

      Recorded recorded = result(Outcome.WIN, Side.AKA, ResultReason.POINTS);

      assertThat(recorded.raisedWarnings()).containsExactly(Warning.RESULT_DIFFERS_FROM_SUGGESTION);
      assertThat(current().winner()).isEqualTo(Side.AKA);
      assertThat(current().meta().raisedWarnings())
          .containsExactly(Warning.RESULT_DIFFERS_FROM_SUGGESTION);
    }

    @Test
    void sameWinner_otherReason_differs() {
      start();
      for (int i = 0; i < 3; i++) {
        score(Side.AKA, ScoreLevel.IPPON);
      }
      timeUp();

      assertThat(result(Outcome.WIN, Side.AKA, ResultReason.POINTS).raisedWarnings())
          .containsExactly(Warning.RESULT_DIFFERS_FROM_SUGGESTION);
    }

    @Test
    void hantei_agrees_whicheverSideTheOfficialsName() {
      start();
      timeUp();

      assertThat(result(Outcome.WIN, Side.AO, ResultReason.HANTEI).raisedWarnings()).isEmpty();
    }

    /** With no suggestion only KIKEN and SHIKKAKU agree: the system cannot see them coming. */
    @Test
    void withNoSuggestion_onlyKikenAndShikkaku_agree() {
      start();
      score(Side.AKA, ScoreLevel.YUKO);

      assertThat(result(Outcome.WIN, Side.AKA, ResultReason.POINTS).raisedWarnings())
          .containsExactly(Warning.RESULT_DIFFERS_FROM_SUGGESTION);
      assertThat(current().suggested()).isNull();
      assertThat(result(Outcome.WIN, Side.AO, ResultReason.KIKEN).raisedWarnings())
          .containsExactly(Warning.RESULT_WHILE_DECIDED);
      assertThat(result(Outcome.WIN, Side.AO, ResultReason.SHIKKAKU).raisedWarnings())
          .containsExactly(Warning.RESULT_WHILE_DECIDED);
    }

    /** A withdrawal decides whatever the score, so KIKEN agrees even against a suggestion. */
    @Test
    void kiken_agrees_evenAgainstSuggestion() {
      start();
      score(Side.AKA, ScoreLevel.IPPON);
      timeUp();

      assertThat(result(Outcome.WIN, Side.AO, ResultReason.KIKEN).raisedWarnings()).isEmpty();
      assertThat(current().suggested())
          .isEqualTo(SuggestedResult.win(Side.AKA, ResultReason.POINTS));
    }

    @Test
    void shikkaku_againstSuggestionOfOtherReason_differs() {
      start();
      score(Side.AKA, ScoreLevel.IPPON);
      timeUp();

      assertThat(result(Outcome.WIN, Side.AO, ResultReason.SHIKKAKU).raisedWarnings())
          .containsExactly(Warning.RESULT_DIFFERS_FROM_SUGGESTION);
    }

    /** DOUBLE_DISQUALIFICATION means both are disqualified, whether or not the flag is sent. */
    @Test
    void doubleDisqualification_agrees_withOrWithoutTheFlag() {
      start();
      penalty(Side.AKA, PenaltyLevel.HANSOKU);
      penalty(Side.AO, PenaltyLevel.HANSOKU);

      for (boolean flag : new boolean[] {false, true}) {
        assertThat(
                game.recordResult(
                        new NewResult(
                            id(),
                            Outcome.NO_RESULT,
                            null,
                            ResultReason.DOUBLE_DISQUALIFICATION,
                            null,
                            flag),
                        SK,
                        at(30))
                    .raisedWarnings())
            .doesNotContain(Warning.RESULT_DIFFERS_FROM_SUGGESTION);
      }
    }

    @Test
    void drawInElimination_differs() {
      start();
      timeUp();

      assertThat(result(Outcome.DRAW, null, ResultReason.HIKIWAKE).raisedWarnings())
          .containsExactly(Warning.RESULT_DIFFERS_FROM_SUGGESTION);
    }

    @Test
    void theSnapshot_staysAsRecorded_whileTheLiveSuggestionMoves() {
      start();
      score(Side.AKA, ScoreLevel.WAZA_ARI);
      String scoreId = lastId();
      timeUp();
      result(Outcome.WIN, Side.AKA, ResultReason.POINTS);

      cancel(scoreId);

      assertThat(state().suggested()).isEqualTo(SuggestedResult.hantei());
      assertThat(current().suggested())
          .isEqualTo(SuggestedResult.win(Side.AKA, ResultReason.POINTS));
    }

    @Test
    void retriedResult_replays_whateverTheSuggestionNowIs() {
      start();
      timeUp();
      NewResult hantei =
          new NewResult("r1", Outcome.WIN, Side.AKA, ResultReason.HANTEI, null, false);
      game.recordResult(hantei, SK, at(121));
      penalty(Side.AKA, PenaltyLevel.SHIKKAKU);

      Recorded retry = game.recordResult(hantei, SK, at(130));

      assertThat(retry.replayed()).isTrue();
      assertThat(game.results()).hasSize(1);
    }
  }

  @Nested
  class CorrectionWhileDecided {

    @Test
    void thatChangesTheSuggestion_awayFromTheResult_warns_andTheGameStaysDecided() {
      start();
      score(Side.AKA, ScoreLevel.WAZA_ARI);
      timeUp();
      result(Outcome.WIN, Side.AKA, ResultReason.POINTS);

      Recorded correction = score(Side.AO, ScoreLevel.IPPON);

      assertThat(correction.raisedWarnings())
          .containsExactly(Warning.SCORE_AFTER_RESULT, Warning.SUGGESTION_DIFFERS_FROM_RESULT);
      Entry stored = game.entriesInOrder().getLast().entry();
      assertThat(stored.id()).isEqualTo(lastId());
      assertThat(stored.meta().raisedWarnings()).isEqualTo(correction.raisedWarnings());
      assertThat(state().status()).isEqualTo(GameStatus.DECIDED);
      assertThat(current().winner()).isEqualTo(Side.AKA);
    }

    @Test
    void thatTakesTheSuggestionAway_warns() {
      start();
      penalty(Side.AO, PenaltyLevel.HANSOKU);
      String hansoku = lastId();
      result(Outcome.WIN, Side.AKA, ResultReason.HANSOKU);

      Recorded correction = cancel(hansoku);

      assertThat(state().suggested()).isNull();
      assertThat(correction.raisedWarnings())
          .containsExactly(Warning.SUGGESTION_DIFFERS_FROM_RESULT);
      assertThat(state().status()).isEqualTo(GameStatus.DECIDED);
    }

    @Test
    void thatLeavesTheResultAgreeing_raisesNothing() {
      start();
      score(Side.AKA, ScoreLevel.IPPON);
      timeUp();
      result(Outcome.WIN, Side.AKA, ResultReason.POINTS);

      assertThat(penalty(Side.AO, PenaltyLevel.CHUI).raisedWarnings()).isEmpty();
      assertThat(score(Side.AKA, ScoreLevel.YUKO).raisedWarnings())
          .containsExactly(Warning.SCORE_AFTER_RESULT);
    }

    @Test
    void thatBringsTheSuggestionToTheResult_raisesNothing() {
      start();
      timeUp();
      result(Outcome.WIN, Side.AKA, ResultReason.POINTS);

      assertThat(score(Side.AKA, ScoreLevel.YUKO).raisedWarnings())
          .containsExactly(Warning.SCORE_AFTER_RESULT);
    }

    /** KIKEN decides whatever the score, so time up after it changes nothing that matters. */
    @Test
    void timeUpAfterKiken_raisesOnlyTheClockWarning() {
      start();
      score(Side.AKA, ScoreLevel.IPPON);
      result(Outcome.WIN, Side.AO, ResultReason.KIKEN);

      game.recordClockAction(
          new NewClockAction(id(), Timer.MAIN, null, ClockActionKind.TIME_UP, 0, null, null),
          SK,
          at(120));

      assertThat(state().suggested()).isEqualTo(SuggestedResult.win(Side.AKA, ResultReason.POINTS));
      assertThat(game.entriesInOrder().getLast().entry().meta().raisedWarnings())
          .doesNotContain(Warning.SUGGESTION_DIFFERS_FROM_RESULT);
    }

    @Test
    void beforeAnyResult_isNoCorrection() {
      start();
      score(Side.AKA, ScoreLevel.YUKO);
      timeUp();

      assertThat(score(Side.AO, ScoreLevel.IPPON).raisedWarnings()).isEmpty();
    }
  }

  @Nested
  class RoundRobinScore {

    private void roundRobin() {
      game = TestGames.game(GameFormat.ROUND_ROBIN, false, false);
    }

    private EffectiveScore of(Side side) {
      return state().effectiveScore(side);
    }

    private static EffectiveScore effective(int total, int ippon, int wazaAri, int added) {
      return new EffectiveScore(total, ippon, wazaAri, added);
    }

    private int scoreEntries() {
      return game.scores(Side.AKA).size() + game.scores(Side.AO).size();
    }

    @Test
    void afterHansoku_theOffenderHasNothing_theOpponentTheForfeitPoints_asYuko() {
      roundRobin();
      start();
      score(Side.AKA, ScoreLevel.IPPON);
      score(Side.AO, ScoreLevel.WAZA_ARI);
      penalty(Side.AO, PenaltyLevel.HANSOKU);
      assertThat(of(Side.AO)).isEqualTo(effective(2, 0, 1, 0));

      result(Outcome.WIN, Side.AKA, ResultReason.HANSOKU);

      assertThat(of(Side.AKA)).isEqualTo(effective(4, 1, 0, 1));
      assertThat(of(Side.AO)).isEqualTo(effective(0, 0, 0, 0));
      assertThat(scoreEntries()).isEqualTo(2);
      assertThat(state().side(Side.AKA).total()).isEqualTo(3);
    }

    @Test
    void afterKiken_beforeTheStart_theOpponentHasTheForfeitPoints() {
      roundRobin();

      result(Outcome.WIN, Side.AO, ResultReason.KIKEN);

      assertThat(of(Side.AO)).isEqualTo(effective(4, 0, 0, 4));
      assertThat(of(Side.AKA)).isEqualTo(effective(0, 0, 0, 0));
      assertThat(scoreEntries()).isZero();
    }

    @Test
    void afterShikkaku_theOpponentKeepsMoreThanTheForfeitPoints() {
      roundRobin();
      start();
      for (int i = 0; i < 2; i++) {
        score(Side.AKA, ScoreLevel.IPPON);
      }
      penalty(Side.AO, PenaltyLevel.SHIKKAKU);

      result(Outcome.WIN, Side.AKA, ResultReason.SHIKKAKU);

      assertThat(of(Side.AKA)).isEqualTo(effective(6, 2, 0, 0));
      assertThat(of(Side.AO)).isEqualTo(effective(0, 0, 0, 0));
    }

    @Test
    void afterDoubleDisqualification_bothHaveNothing() {
      roundRobin();
      start();
      score(Side.AKA, ScoreLevel.IPPON);
      penalty(Side.AKA, PenaltyLevel.HANSOKU);
      penalty(Side.AO, PenaltyLevel.SHIKKAKU);
      assertThat(state().suggested())
          .isEqualTo(
              new SuggestedResult(
                  Outcome.NO_RESULT, null, ResultReason.DOUBLE_DISQUALIFICATION, true));

      game.recordResult(
          new NewResult(
              id(), Outcome.NO_RESULT, null, ResultReason.DOUBLE_DISQUALIFICATION, null, true),
          SK,
          at(30));

      assertThat(state().effectiveScores())
          .isEqualTo(Map.of(Side.AKA, effective(0, 0, 0, 0), Side.AO, effective(0, 0, 0, 0)));
      assertThat(scoreEntries()).isEqualTo(1);
    }

    @Test
    void inElimination_isWhatWasScored() {
      start();
      score(Side.AKA, ScoreLevel.WAZA_ARI);
      penalty(Side.AO, PenaltyLevel.HANSOKU);

      result(Outcome.WIN, Side.AKA, ResultReason.HANSOKU);

      assertThat(of(Side.AKA)).isEqualTo(effective(2, 0, 1, 0));
      assertThat(of(Side.AO)).isEqualTo(effective(0, 0, 0, 0));
    }

    @Test
    void withResultOnPoints_isWhatWasScored() {
      roundRobin();
      start();
      score(Side.AO, ScoreLevel.YUKO);
      timeUp();

      result(Outcome.WIN, Side.AO, ResultReason.POINTS);

      assertThat(state().effectiveScores())
          .isEqualTo(Map.of(Side.AKA, effective(0, 0, 0, 0), Side.AO, effective(1, 0, 0, 0)));
    }
  }
}
