package com.management.kumitegame.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.testsupport.TestClock;
import com.management.testsupport.TestGames;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * SENSHU as officials award and withdraw it (WKF Kumite Competition Rules 2026.01, Article 12.2):
 * the holder and the closure derived from the records, the fold rules for a cancel, the refusals,
 * and every warning. The default game is two minutes with a 15 second final window.
 */
class SenshuTest {

  private static final Instant T0 = TestClock.NOW;
  private static final String SK = TestGames.SCOREKEEPER;

  private final KumiteGame game = TestGames.game();
  private int next;

  private String id() {
    return "entry-" + ++next;
  }

  private String lastId() {
    return "entry-" + next;
  }

  /** Seconds after the first START, so the main clock shows 120 minus that. */
  private static Instant at(double seconds) {
    return T0.plusMillis(Math.round(seconds * 1000));
  }

  private void start() {
    game.recordClockAction(
        new NewClockAction(id(), Timer.MAIN, null, ClockActionKind.START, 120_000, null, null),
        SK,
        T0);
  }

  private void pauseAndRestart(Instant pausedAt, long shownMs, Instant startedAt) {
    game.recordClockAction(
        new NewClockAction(id(), Timer.MAIN, null, ClockActionKind.PAUSE, shownMs, null, null),
        SK,
        pausedAt);
    game.recordClockAction(
        new NewClockAction(id(), Timer.MAIN, null, ClockActionKind.START, shownMs, null, null),
        SK,
        startedAt);
  }

  private Recorded award(Side side, Instant at) {
    return senshu(side, SenshuRecordKind.AWARDED, null, null, at);
  }

  private Recorded withdraw(Side side, Instant at) {
    return senshu(side, SenshuRecordKind.WITHDRAWN, null, null, at);
  }

  private Recorded senshu(
      Side side,
      SenshuRecordKind kind,
      @Nullable Integer exchange,
      @Nullable Long clockRemainingMs,
      Instant at) {
    return game.recordSenshu(
        new NewSenshuRecord(id(), side, kind, null, exchange, clockRemainingMs), SK, at);
  }

  private Recorded score(Side side, @Nullable Integer exchange, Instant at) {
    return game.recordScore(
        new NewScore(id(), side, ScoreLevel.YUKO, ScoreSource.JUDGES, exchange, null), SK, at);
  }

  private Recorded penalty(Side side, @Nullable Infraction infraction, Instant at) {
    return game.recordPenalty(
        new NewPenalty(id(), side, PenaltyLevel.HANSOKU_CHUI, infraction, null, null), SK, at);
  }

  private Recorded cancel(String targetId) {
    return game.recordAmendment(
        new NewAmendment(id(), AmendmentKind.CANCEL, targetId, null), SK, at(100));
  }

  private SenshuState held() {
    return game.state().senshu();
  }

  @Nested
  class Recording {

    @ParameterizedTest
    @EnumSource(Side.class)
    void awarded_makesThatSideTheHolder(Side side) {
      start();

      award(side, at(10));

      assertThat(held()).isEqualTo(new SenshuState(side, false));
      assertThat(game.senshuRecords().getFirst().exchange()).isEqualTo(1);
    }

    @Test
    void withdrawn_outsideTheFinalWindow_takesItAway_andLeavesItOpen() {
      start();
      award(Side.AKA, at(10));

      withdraw(Side.AKA, at(100));

      assertThat(held()).isEqualTo(new SenshuState(null, false));
      assertThat(award(Side.AO, at(101)).raisedWarnings()).isEmpty();
    }

    @Test
    void withdrawn_inTheFinalWindow_byTheMainClock_closesIt() {
      start();
      award(Side.AKA, at(10));

      withdraw(Side.AKA, at(106));

      assertThat(held()).isEqualTo(new SenshuState(null, true));
    }

    @Test
    void withdrawn_atTheEdgeOfTheWindow_leavesItOpen() {
      start();
      award(Side.AKA, at(10));

      withdraw(Side.AKA, at(105));

      assertThat(held().closed()).as("15 s left is not less than 15 s").isFalse();
    }

    /** The time the screen showed is the fact, not the server's clock. */
    @Test
    void withdrawn_withTheTimeShownInTheWindow_closesIt() {
      start();
      award(Side.AKA, at(10));

      senshu(Side.AKA, SenshuRecordKind.WITHDRAWN, null, 14_000L, at(50));

      assertThat(held().closed()).isTrue();
    }

    /** A video review names an earlier exchange with that exchange's time: the window is now. */
    @Test
    void withdrawn_namingEarlierExchange_isTimedByTheClockNow() {
      start();
      award(Side.AKA, at(10));
      pauseAndRestart(at(20), 100_000, at(21));

      senshu(Side.AKA, SenshuRecordKind.WITHDRAWN, 1, 110_000L, at(107));

      assertThat(held().closed()).isTrue();
    }

    @Test
    void withdrawn_afterTimeUp_closesIt() {
      start();
      award(Side.AKA, at(10));
      game.recordClockAction(
          new NewClockAction(id(), Timer.MAIN, null, ClockActionKind.TIME_UP, 0, null, null),
          SK,
          at(120));

      withdraw(Side.AKA, at(125));

      assertThat(held().closed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    void awarded_whileSideHoldsIt_isRefused(Side side) {
      start();
      award(Side.AKA, at(10));

      assertThatThrownBy(() -> award(side, at(11))).isInstanceOf(ActionRefusedException.class);
      assertThat(game.senshuRecords()).hasSize(1);
      assertThat(held().holder()).isEqualTo(Side.AKA);
    }

    /** The id is checked before the holder, so a retried award answers instead of refusing. */
    @Test
    void retriedAward_whileItHolds_answersTheStoredWarnings() {
      start();
      NewSenshuRecord awarded =
          new NewSenshuRecord("s-1", Side.AKA, SenshuRecordKind.AWARDED, null, null, null);
      game.recordSenshu(awarded, SK, at(10));

      Recorded retry = game.recordSenshu(awarded, SK, at(11));

      assertThat(retry.replayed()).isTrue();
      assertThat(game.senshuRecords()).hasSize(1);
    }

    @Test
    void withdrawn_fromSideThatDoesNotHoldIt_warns_andChangesNothing() {
      start();
      award(Side.AKA, at(10));

      Recorded fromOther = senshu(Side.AO, SenshuRecordKind.WITHDRAWN, null, null, at(110));

      assertThat(fromOther.raisedWarnings()).containsExactly(Warning.SENSHU_WITHDRAWN_NOT_HELD);
      assertThat(game.senshuRecords().getLast().meta().raisedWarnings())
          .containsExactly(Warning.SENSHU_WITHDRAWN_NOT_HELD);
      assertThat(held()).as("held, and not closed").isEqualTo(new SenshuState(Side.AKA, false));
    }

    @Test
    void acknowledgeOfSenshuRecord_applies() {
      start();
      award(Side.AKA, at(10));

      Recorded seen =
          game.recordAmendment(
              new NewAmendment(id(), AmendmentKind.ACKNOWLEDGE, "entry-2", null), SK, at(11));

      assertThat(seen.raisedWarnings()).isEmpty();
      assertThat(held().holder()).isEqualTo(Side.AKA);
    }
  }

  /** The fold rules for a cancelled SENSHU record. */
  @Nested
  class Cancelled {

    @Test
    void cancelledAward_neverHappened_theHolderGoes() {
      start();
      award(Side.AKA, at(10));

      cancel(lastId());

      assertThat(held()).isEqualTo(new SenshuState(null, false));
      assertThat(game.state().isCancelled("entry-2")).isTrue();
      assertThat(award(Side.AO, at(101)).raisedWarnings()).isEmpty();
    }

    @Test
    void cancelledAward_whoseWithdrawalClosedIt_doesNotCloseIt() {
      start();
      award(Side.AKA, at(10));
      String awarded = lastId();
      withdraw(Side.AKA, at(110));
      assertThat(held().closed()).isTrue();

      cancel(awarded);

      assertThat(held()).isEqualTo(new SenshuState(null, false));
    }

    @Test
    void cancelledWithdrawal_restoresTheHolder_andUndoesTheClosure() {
      start();
      award(Side.AO, at(10));
      withdraw(Side.AO, at(110));
      assertThat(held()).isEqualTo(new SenshuState(null, true));

      cancel(lastId());

      assertThat(held()).isEqualTo(new SenshuState(Side.AO, false));
    }

    /** Giving SENSHU back while a later award stands would leave two holders. */
    @Test
    void cancelledWithdrawal_whileLaterAwardStands_isRefused() {
      start();
      award(Side.AKA, at(10));
      withdraw(Side.AKA, at(20));
      String withdrawn = lastId();
      award(Side.AO, at(30));
      String later = lastId();

      assertThatThrownBy(() -> cancel(withdrawn)).isInstanceOf(ActionRefusedException.class);

      cancel(later);
      cancel(withdrawn);
      assertThat(held()).isEqualTo(new SenshuState(Side.AKA, false));
    }

    @Test
    void secondCancelOfSenshuRecord_isRefused() {
      start();
      award(Side.AKA, at(10));
      String awarded = lastId();
      cancel(awarded);

      assertThatThrownBy(() -> cancel(awarded)).isInstanceOf(ActionRefusedException.class);
    }
  }

  @Nested
  class Warnings {

    /** Article 12.2.2: both scored before the signal, so nobody gets SENSHU. */
    @Test
    void award_whenTheOtherSideScoredInTheSameExchange_warns() {
      start();
      score(Side.AKA, null, at(5));
      score(Side.AO, null, at(5));

      Recorded awarded = award(Side.AKA, at(6));

      assertThat(awarded.raisedWarnings()).containsExactly(Warning.SENSHU_WHEN_BOTH_SCORED);
      assertThat(game.senshuRecords().getLast().meta().raisedWarnings())
          .containsExactly(Warning.SENSHU_WHEN_BOTH_SCORED);
      assertThat(held().holder()).as("applied all the same").isEqualTo(Side.AKA);
    }

    @Test
    void award_whenTheOtherSideScoredOnlyInEarlierExchange_orItsScoreIsCancelled_raisesNothing() {
      start();
      score(Side.AO, null, at(5));
      pauseAndRestart(at(10), 110_000, at(11));
      score(Side.AO, null, at(12));
      cancel(lastId());
      score(Side.AKA, null, at(13));

      assertThat(award(Side.AKA, at(14)).raisedWarnings()).isEmpty();
    }

    /** Article 12.2.9: a video review adds the opponent's score after SENSHU was awarded. */
    @Test
    void scoreByTheOpponent_inTheExchangeOfTheAward_warns() {
      start();
      score(Side.AKA, null, at(5));
      award(Side.AKA, at(6));
      pauseAndRestart(at(10), 110_000, at(11));

      Recorded review =
          game.recordScore(
              new NewScore(id(), Side.AO, ScoreLevel.YUKO, ScoreSource.VIDEO_REVIEW, 1, null),
              SK,
              at(20));
      Recorded later = score(Side.AO, null, at(21));
      Recorded holders = score(Side.AKA, 1, at(22));

      assertThat(review.raisedWarnings()).containsExactly(Warning.SENSHU_WHEN_BOTH_SCORED);
      assertThat(game.scores(Side.AO).getFirst().meta().raisedWarnings())
          .containsExactly(Warning.SENSHU_WHEN_BOTH_SCORED);
      assertThat(later.raisedWarnings()).isEmpty();
      assertThat(holders.raisedWarnings()).isEmpty();
    }

    /** Article 12.2.10: withdrawn in the final window, nobody earns it again. */
    @Test
    void award_afterItClosed_warns() {
      start();
      award(Side.AKA, at(10));
      withdraw(Side.AKA, at(110));

      Recorded awarded = award(Side.AO, at(111));

      assertThat(awarded.raisedWarnings()).containsExactly(Warning.SENSHU_AFTER_CLOSED);
      assertThat(held().holder()).isEqualTo(Side.AO);
    }

    /** Articles 12.2.8, 10.4.16: avoiding combat in the final window forfeits SENSHU. */
    @Test
    void avoidingCombatByTheHolder_inTheFinalWindow_warns() {
      start();
      award(Side.AKA, at(10));

      Recorded jogai = penalty(Side.AKA, Infraction.JOGAI, at(110));

      assertThat(jogai.raisedWarnings()).containsExactly(Warning.SENSHU_HOLDER_AVOIDING_COMBAT);
      assertThat(game.penalties(Side.AKA).getLast().meta().raisedWarnings())
          .containsExactly(Warning.SENSHU_HOLDER_AVOIDING_COMBAT);
      assertThat(held().holder()).as("the referee withdraws it").isEqualTo(Side.AKA);
    }

    @Test
    void avoidingCombat_byTheTimeShownInTheWindow_warns() {
      start();
      award(Side.AKA, at(10));

      Recorded clinching =
          game.recordPenalty(
              new NewPenalty(
                  id(), Side.AKA, PenaltyLevel.CHUI, Infraction.CLINCHING, null, 10_000L),
              SK,
              at(50));

      assertThat(clinching.raisedWarnings())
          .contains(
              Warning.SENSHU_HOLDER_AVOIDING_COMBAT, Warning.CLOCK_REMAINING_OUT_OF_TOLERANCE);
    }

    @Test
    void penalty_outsideTheWindow_orToTheOtherSide_orForAnotherInfraction_raisesNothing() {
      start();
      award(Side.AKA, at(10));

      assertThat(penalty(Side.AKA, Infraction.JOGAI, at(104)).raisedWarnings()).isEmpty();
      assertThat(penalty(Side.AO, Infraction.JOGAI, at(110)).raisedWarnings()).isEmpty();
      assertThat(penalty(Side.AKA, Infraction.MUBOBI, at(110)).raisedWarnings()).isEmpty();
      assertThat(penalty(Side.AKA, null, at(110)).raisedWarnings()).isEmpty();
    }

    /** A penalty naming an earlier exchange carries that exchange's time: the window is now. */
    @Test
    void avoidingCombat_namingEarlierExchange_isTimedByTheClockNow() {
      start();
      award(Side.AKA, at(10));
      pauseAndRestart(at(20), 100_000, at(21));

      Recorded jogai =
          game.recordPenalty(
              new NewPenalty(id(), Side.AKA, PenaltyLevel.CHUI, Infraction.JOGAI, 1, 110_000L),
              SK,
              at(110));

      assertThat(jogai.raisedWarnings()).containsExactly(Warning.SENSHU_HOLDER_AVOIDING_COMBAT);
    }

    @Test
    void avoidingCombat_withNoHolder_raisesNothing() {
      start();

      assertThat(penalty(Side.AKA, Infraction.JOGAI, at(110)).raisedWarnings()).isEmpty();
    }

    @Test
    void cancelledScore_leavingTheHolderWithNoUnopposedScore_warns() {
      start();
      score(Side.AKA, null, at(5));
      String unopposed = lastId();
      award(Side.AKA, at(6));

      Recorded cancelled = cancel(unopposed);

      assertThat(cancelled.raisedWarnings())
          .containsExactly(Warning.SENSHU_HOLDER_WITHOUT_UNOPPOSED_SCORE);
      assertThat(game.amendments().getLast().meta().raisedWarnings())
          .containsExactly(Warning.SENSHU_HOLDER_WITHOUT_UNOPPOSED_SCORE);
      assertThat(held().holder()).as("recorded as given").isEqualTo(Side.AKA);
    }

    @Test
    void cancelledScore_leavingTheHolderOnlyOpposedScores_warns() {
      start();
      score(Side.AKA, null, at(5));
      final String unopposed = lastId();
      award(Side.AKA, at(6));
      pauseAndRestart(at(10), 110_000, at(11));
      score(Side.AKA, null, at(12));
      score(Side.AO, null, at(12));

      assertThat(cancel(unopposed).raisedWarnings())
          .containsExactly(Warning.SENSHU_HOLDER_WITHOUT_UNOPPOSED_SCORE);
    }

    @Test
    void cancelledScore_whileTheHolderKeepsAnUnopposedOne_raisesNothing() {
      start();
      score(Side.AKA, null, at(5));
      award(Side.AKA, at(6));
      score(Side.AKA, null, at(7));
      String second = lastId();
      pauseAndRestart(at(10), 110_000, at(11));
      score(Side.AO, null, at(12));

      assertThat(cancel(second).raisedWarnings()).isEmpty();
    }

    @Test
    void cancelledScore_ofTheOtherSide_orWithNoHolder_raisesNothing() {
      start();
      score(Side.AO, null, at(5));
      String noHolder = lastId();
      assertThat(cancel(noHolder).raisedWarnings()).isEmpty();

      score(Side.AKA, null, at(6));
      award(Side.AKA, at(7));
      pauseAndRestart(at(10), 110_000, at(11));
      score(Side.AO, null, at(12));

      assertThat(cancel(lastId()).raisedWarnings()).isEmpty();
    }

    /** A score before the start belongs to no exchange, so it is never unopposed. */
    @Test
    void scoreBeforeTheStart_isNotAnUnopposedScore() {
      score(Side.AKA, null, T0);
      award(Side.AKA, T0);
      start();
      score(Side.AKA, null, at(5));

      assertThat(cancel(lastId()).raisedWarnings())
          .containsExactly(Warning.SENSHU_HOLDER_WITHOUT_UNOPPOSED_SCORE);
    }
  }

  @Nested
  class History {

    private EntryMeta meta(String id, int order) {
      return new EntryMeta(id, order, T0, SK, List.of());
    }

    /** A main-clock action at the full duration, so the clock shows what it has. */
    private ClockAction mainClock(ClockActionKind action, EntryMeta meta) {
      return new ClockAction(Timer.MAIN, null, action, 120_000, null, null, meta);
    }

    private SenshuRecord record(String id, int order, Side side, SenshuRecordKind kind) {
      return new SenshuRecord(side, kind, null, null, false, null, meta(id, order));
    }

    /** Two holders cannot come from appending, only from stored history: it loads, and warns. */
    @Test
    void awardWhileHeld_inStoredHistory_isSkippedWithWarning() {
      KumiteGame stored =
          new KumiteGame(
              "game-6",
              GameFormat.ELIMINATION,
              false,
              false,
              TestGames.settings(),
              Map.of(),
              Map.of(),
              TestGames.game().assignments(),
              List.of(),
              List.of(
                  record("s1", 4, Side.AKA, SenshuRecordKind.AWARDED),
                  record("s2", 5, Side.AO, SenshuRecordKind.AWARDED),
                  record("s1", 6, Side.AO, SenshuRecordKind.WITHDRAWN)),
              List.of(),
              List.of(),
              List.of(),
              TestGames.rules());

      GameState state = stored.state();

      assertThat(state.senshu()).isEqualTo(new SenshuState(Side.AKA, false));
      assertThat(state.warnings())
          .containsExactly(
              new GameWarning(Warning.HISTORY_INCONSISTENT, "s1"),
              new GameWarning(Warning.HISTORY_INCONSISTENT, "s2"));
    }

    /** The holder holds SENSHU by the award the fold applied, never by one it skipped. */
    @Test
    void opposingScoreWarning_followsTheAppliedAward_notSkippedOne() {
      KumiteGame stored =
          new KumiteGame(
              "game-7",
              GameFormat.ELIMINATION,
              false,
              false,
              TestGames.settings(),
              Map.of(),
              Map.of(),
              TestGames.game().assignments(),
              List.of(
                  mainClock(ClockActionKind.START, meta("c1", 4)),
                  mainClock(ClockActionKind.PAUSE, meta("c2", 5)),
                  mainClock(ClockActionKind.START, meta("c3", 6)),
                  mainClock(ClockActionKind.PAUSE, meta("c4", 7)),
                  mainClock(ClockActionKind.START, meta("c5", 8))),
              List.of(
                  new SenshuRecord(
                      Side.AKA, SenshuRecordKind.AWARDED, null, 1, true, null, meta("s1", 9)),
                  new SenshuRecord(
                      Side.AKA, SenshuRecordKind.AWARDED, null, 3, false, null, meta("s2", 10))),
              List.of(),
              List.of(),
              List.of(),
              TestGames.rules());

      Recorded inFirst =
          stored.recordScore(
              new NewScore("o1", Side.AO, ScoreLevel.YUKO, ScoreSource.VIDEO_REVIEW, 1, null),
              SK,
              T0);
      Recorded inSkipped =
          stored.recordScore(
              new NewScore("o2", Side.AO, ScoreLevel.YUKO, ScoreSource.VIDEO_REVIEW, 3, null),
              SK,
              T0);

      assertThat(inFirst.raisedWarnings()).contains(Warning.SENSHU_WHEN_BOTH_SCORED);
      assertThat(inSkipped.raisedWarnings()).doesNotContain(Warning.SENSHU_WHEN_BOTH_SCORED);
    }
  }
}
