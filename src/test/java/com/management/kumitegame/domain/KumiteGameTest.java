package com.management.kumitegame.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.testsupport.TestClock;
import com.management.testsupport.TestGames;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The game's rules, without a container: the status matrix, the clock table, every warning, the
 * CANCEL rules, and a retried action applying once.
 */
class KumiteGameTest {

  private static final Instant T0 = TestClock.NOW;
  private static final String SK = TestGames.SCOREKEEPER;

  private final KumiteGame game = TestGames.game();
  private int next;

  private String id() {
    return "entry-" + ++next;
  }

  private static Instant at(double seconds) {
    return T0.plusMillis(Math.round(seconds * 1000));
  }

  private Recorded clock(
      ClockActionKind action, long shownMs, @Nullable Long amountMs, Instant at) {
    return game.recordClockAction(
        new NewClockAction(id(), Timer.MAIN, null, action, shownMs, amountMs, null), SK, at);
  }

  private Recorded medical(Side side, ClockActionKind action, long shownMs, Instant at) {
    return game.recordClockAction(
        new NewClockAction(id(), Timer.MEDICAL, side, action, shownMs, null, null), SK, at);
  }

  private Recorded score(Side side, ScoreLevel level, @Nullable Long clockRemainingMs, Instant at) {
    return game.recordScore(
        new NewScore(id(), side, level, ScoreSource.JUDGES, null, clockRemainingMs), SK, at);
  }

  private Recorded penalty(Side side, PenaltyLevel level, Instant at) {
    return game.recordPenalty(new NewPenalty(id(), side, level, null, null, null), SK, at);
  }

  private Recorded result(Outcome outcome, @Nullable Side winner, ResultReason reason, Instant at) {
    return game.recordResult(new NewResult(id(), outcome, winner, reason, null, false), SK, at);
  }

  private Recorded cancel(String targetId) {
    return game.recordAmendment(
        new NewAmendment(id(), AmendmentKind.CANCEL, targetId, null), SK, T0);
  }

  private String lastId() {
    return "entry-" + next;
  }

  private void start() {
    clock(ClockActionKind.START, 120_000, null, T0);
  }

  /** Reached through entries only, as a game reaches it. */
  private void bring(GameStatus status) {
    if (status == GameStatus.FIGHTING || status == GameStatus.TIME_UP) {
      start();
    }
    if (status == GameStatus.TIME_UP) {
      clock(ClockActionKind.TIME_UP, 0, null, at(120));
    }
    if (status == GameStatus.DECIDED) {
      result(Outcome.WIN, Side.AKA, ResultReason.KIKEN, T0);
    }
    assertThat(game.state().status()).isEqualTo(status);
  }

  /** An action of each kind, taken just after the status was reached, raising nothing else. */
  private Recorded act(EntryKind kind, GameStatus status) {
    Instant at = status == GameStatus.TIME_UP ? at(120.5) : at(1);
    return switch (kind) {
      case SCORE -> score(Side.AKA, ScoreLevel.YUKO, null, at);
      case PENALTY -> penalty(Side.AO, PenaltyLevel.CHUI, at);
      case CLOCK_ACTION -> medical(Side.AKA, ClockActionKind.START, 180_000, at);
      case SENSHU_RECORD ->
          game.recordSenshu(
              new NewSenshuRecord(id(), Side.AKA, SenshuRecordKind.AWARDED, null, null, null),
              SK,
              at);
      case AMENDMENT ->
          game.recordAmendment(
              new NewAmendment(id(), AmendmentKind.ACKNOWLEDGE, "created-1", null), SK, at);
      case ASSIGNMENT ->
          game.recordAssignment(
              new NewAssignment(
                  id(), AssignmentKind.PANEL_ADD, null, null, "referee-2", PanelRole.JUDGE),
              SK,
              at);
      case SETTINGS_CHANGE -> game.changeSettings(newSettings(100_000));
      case RESULT -> result(Outcome.WIN, Side.AO, ResultReason.POINTS, at);
    };
  }

  private static NewSettings newSettings(long durationMs) {
    Settings defaults = TestGames.settings();
    return new NewSettings(
        durationMs,
        defaults.durationsOfferedMs(),
        defaults.pointValues(),
        defaults.leadToWin(),
        defaults.finalWindowMs(),
        defaults.lateScoreWindowMs(),
        defaults.chuiLimit(),
        defaults.roundRobinForfeitPoints(),
        defaults.addTimeStepsMs(),
        defaults.medicalTimeMs(),
        defaults.timeShownToleranceMs());
  }

  /** The planning record's status matrix, row by row: applied, applied with a warning, refused. */
  @ParameterizedTest(name = "{0} while {1}: {2}")
  @CsvSource({
    "SCORE, NOT_STARTED, SCORE_BEFORE_START",
    "SCORE, FIGHTING, APPLIED",
    "SCORE, TIME_UP, APPLIED",
    "SCORE, DECIDED, SCORE_AFTER_RESULT",
    "PENALTY, NOT_STARTED, APPLIED",
    "PENALTY, FIGHTING, APPLIED",
    "PENALTY, TIME_UP, APPLIED",
    "PENALTY, DECIDED, APPLIED",
    "CLOCK_ACTION, NOT_STARTED, APPLIED",
    "CLOCK_ACTION, FIGHTING, APPLIED",
    "CLOCK_ACTION, TIME_UP, APPLIED",
    "CLOCK_ACTION, DECIDED, CLOCK_ACTION_AFTER_RESULT",
    "SENSHU_RECORD, NOT_STARTED, SENSHU_BEFORE_START",
    "SENSHU_RECORD, FIGHTING, APPLIED",
    "SENSHU_RECORD, TIME_UP, APPLIED",
    "SENSHU_RECORD, DECIDED, SENSHU_AFTER_RESULT",
    "AMENDMENT, NOT_STARTED, APPLIED",
    "AMENDMENT, FIGHTING, APPLIED",
    "AMENDMENT, TIME_UP, APPLIED",
    "AMENDMENT, DECIDED, APPLIED",
    "ASSIGNMENT, NOT_STARTED, APPLIED",
    "ASSIGNMENT, FIGHTING, ASSIGNMENT_AFTER_START",
    "ASSIGNMENT, TIME_UP, ASSIGNMENT_AFTER_START",
    "ASSIGNMENT, DECIDED, ASSIGNMENT_AFTER_START",
    "SETTINGS_CHANGE, NOT_STARTED, APPLIED",
    "SETTINGS_CHANGE, FIGHTING, REFUSED",
    "SETTINGS_CHANGE, TIME_UP, REFUSED",
    "SETTINGS_CHANGE, DECIDED, REFUSED",
    "RESULT, NOT_STARTED, RESULT_BEFORE_START",
    "RESULT, FIGHTING, APPLIED",
    "RESULT, TIME_UP, APPLIED",
    "RESULT, DECIDED, RESULT_WHILE_DECIDED"
  })
  void statusMatrix(EntryKind kind, GameStatus status, String expected) {
    bring(status);
    int entries = game.entriesInOrder().size();

    if (expected.equals("REFUSED")) {
      assertThatThrownBy(() -> act(kind, status)).isInstanceOf(ActionRefusedException.class);
      assertThat(game.entriesInOrder()).hasSize(entries);
      return;
    }
    Recorded recorded = act(kind, status);

    List<Warning> warnings =
        expected.equals("APPLIED") ? List.of() : List.of(Warning.valueOf(expected));
    assertThat(recorded.raisedWarnings()).isEqualTo(warnings);
    assertThat(recorded.replayed()).isFalse();
    if (kind != EntryKind.SETTINGS_CHANGE) {
      Entry stored = game.entriesInOrder().getLast().entry();
      assertThat(stored.id()).isEqualTo(lastId());
      assertThat(stored.meta().raisedWarnings()).isEqualTo(warnings);
    }
  }

  /** The matrix as code holds every status the game has, so no cell is left to a default. */
  @ParameterizedTest
  @EnumSource(EntryKind.class)
  void everyKind_hasCellForEveryStatus(EntryKind kind) {
    for (GameStatus status : GameStatus.values()) {
      assertThat(kind.admission(status)).isNotNull();
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = ResultReason.class,
      names = {"KIKEN", "SHIKKAKU"})
  void resultBeforeTheStart_forAnAbsentOrDisqualifiedAthlete_raisesNothing(ResultReason reason) {
    assertThat(result(Outcome.WIN, Side.AO, reason, T0).raisedWarnings()).isEmpty();
    assertThat(game.state().status()).isEqualTo(GameStatus.DECIDED);
  }

  @Nested
  class Clock {

    @Test
    void start_runsTheClock_keepsTheTime_andOpensAnExchange() {
      start();

      GameState state = game.state();
      assertThat(state.mainClock().running()).isTrue();
      assertThat(state.mainClock().remainingAt(T0)).isEqualTo(120_000);
      assertThat(state.mainClock().remainingAt(at(10))).isEqualTo(110_000);
      assertThat(state.exchange()).isEqualTo(1);
      assertThat(state.status()).isEqualTo(GameStatus.FIGHTING);
    }

    @Test
    void pause_stopsAtTheTimeShown_andKeepsTheExchange() {
      start();
      clock(ClockActionKind.PAUSE, 110_200, null, at(10));

      GameState state = game.state();
      assertThat(state.mainClock().running()).isFalse();
      assertThat(state.mainClock().remainingAt(at(60))).isEqualTo(110_200);
      assertThat(state.exchange()).isEqualTo(1);
    }

    @Test
    void restart_pausesAtTheStartingDuration_andKeepsTheExchange() {
      start();
      clock(ClockActionKind.RESTART, 110_000, null, at(10));

      GameState state = game.state();
      assertThat(state.mainClock().running()).isFalse();
      assertThat(state.mainClock().remainingAt(at(60))).isEqualTo(120_000);
      assertThat(state.exchange()).isEqualTo(1);
    }

    @Test
    void addTime_keepsTheRunState_andAddsTheAmount() {
      start();
      clock(ClockActionKind.ADD_TIME, 110_000, 10_000L, at(10));

      GameState state = game.state();
      assertThat(state.mainClock().running()).isTrue();
      assertThat(state.mainClock().remainingAt(at(10))).isEqualTo(120_000);
      assertThat(state.exchange()).isEqualTo(1);
    }

    @Test
    void setTime_pausesAtTheAmount_andKeepsTheExchange() {
      start();
      clock(ClockActionKind.SET_TIME, 110_000, 50_000L, at(10));

      GameState state = game.state();
      assertThat(state.mainClock().running()).isFalse();
      assertThat(state.mainClock().remainingAt(at(60))).isEqualTo(50_000);
      assertThat(state.exchange()).isEqualTo(1);
    }

    @Test
    void timeUp_pausesAtZero_andNeverEndsTheGame() {
      start();
      clock(ClockActionKind.TIME_UP, 0, null, at(120));

      GameState state = game.state();
      assertThat(state.mainClock().running()).isFalse();
      assertThat(state.mainClock().remainingAt(at(130))).isZero();
      assertThat(state.exchange()).isEqualTo(1);
      assertThat(state.status()).isEqualTo(GameStatus.TIME_UP);
      assertThat(state.currentResult()).isNull();
    }

    @Test
    void secondTimeUp_isRecordedAndChangesNothing() {
      start();
      clock(ClockActionKind.TIME_UP, 0, null, at(120));
      GameState before = game.state();

      Recorded second = clock(ClockActionKind.TIME_UP, 0, null, at(125));

      GameState after = game.state();
      assertThat(second.raisedWarnings()).isEmpty();
      assertThat(game.clockActions()).hasSize(3);
      assertThat(after.status()).isEqualTo(GameStatus.TIME_UP);
      assertThat(after.timeUpAt()).isEqualTo(before.timeUpAt());
      assertThat(after.mainClock().remainingAt(at(130))).isZero();
      assertThat(after.exchange()).isEqualTo(before.exchange());
    }

    @ParameterizedTest
    @CsvSource({"ADD_TIME, 10000", "RESTART, ", "SET_TIME, 30000"})
    void timePutBackAfterZero_returnsToFighting(ClockActionKind action, @Nullable Long amount) {
      start();
      clock(ClockActionKind.TIME_UP, 0, null, at(120));

      clock(action, 0, amount, at(130));

      assertThat(game.state().status()).isEqualTo(GameStatus.FIGHTING);
      assertThat(game.state().timeUpAt()).isNull();
    }

    /** A START on a clock already running, such as a double tap, opens no new exchange. */
    @Test
    void startWhileRunning_opensNoExchange() {
      start();
      clock(ClockActionKind.START, 119_000, null, at(1));

      assertThat(game.state().exchange()).isEqualTo(1);
      clock(ClockActionKind.PAUSE, 110_000, null, at(10));
      clock(ClockActionKind.START, 110_000, null, at(20));
      assertThat(game.state().exchange()).isEqualTo(2);
    }

    /** Setting the clock to zero puts no time back, so the game stays TIME_UP. */
    @Test
    void setTimeToZero_afterZero_staysTimeUp() {
      start();
      clock(ClockActionKind.TIME_UP, 0, null, at(120));

      clock(ClockActionKind.SET_TIME, 0, 0L, at(125));

      assertThat(game.state().status()).isEqualTo(GameStatus.TIME_UP);
      assertThat(game.state().timeUpAt()).isEqualTo(at(120));
    }

    /** Only a result ends a game: no clock action takes it out of FIGHTING or TIME_UP. */
    @ParameterizedTest
    @EnumSource(ClockActionKind.class)
    void noClockAction_endsTheGame(ClockActionKind action) {
      start();
      clock(action, 119_000, action.hasAmount() ? 5_000L : null, at(1));

      assertThat(game.state().status()).isNotEqualTo(GameStatus.DECIDED);
      assertThat(game.state().currentResult()).isNull();
    }

    @Test
    void onlyResult_decidesTheGame() {
      start();
      clock(ClockActionKind.TIME_UP, 0, null, at(120));

      result(Outcome.WIN, Side.AKA, ResultReason.HANTEI, at(130));

      assertThat(game.state().status()).isEqualTo(GameStatus.DECIDED);
    }

    @Test
    void medicalTimer_runsPerSide_withoutTouchingStatusOrExchange() {
      start();
      clock(ClockActionKind.PAUSE, 110_000, null, at(10));
      medical(Side.AO, ClockActionKind.START, 180_000, at(11));
      medical(Side.AO, ClockActionKind.TIME_UP, 0, at(191));

      GameState state = game.state();
      assertThat(state.status()).isEqualTo(GameStatus.FIGHTING);
      assertThat(state.exchange()).isEqualTo(1);
      assertThat(state.medicalClock(Side.AO).remainingAt(at(200))).isZero();
      assertThat(state.medicalClock(Side.AKA).remainingAt(at(200))).isEqualTo(180_000);
      assertThat(state.mainClock().remainingAt(at(200))).isEqualTo(110_000);
    }

    @Test
    void medicalTimerWithoutSide_orMainClockWithSide_isRefused() {
      assertThatThrownBy(() ->
              game.recordClockAction(
                  new NewClockAction(
                      id(), Timer.MEDICAL, null, ClockActionKind.START, 0, null, null),
                  SK,
                  T0))
          .isInstanceOf(InvalidGameRequestException.class);
      assertThatThrownBy(() ->
              game.recordClockAction(
                  new NewClockAction(
                      id(), Timer.MAIN, Side.AKA, ClockActionKind.START, 0, null, null),
                  SK,
                  T0))
          .isInstanceOf(InvalidGameRequestException.class);
    }

    @Test
    void addOrSetTime_withoutAnAmount_isRefused() {
      assertThatThrownBy(() -> clock(ClockActionKind.ADD_TIME, 120_000, null, T0))
          .isInstanceOf(InvalidGameRequestException.class);
      assertThatThrownBy(() -> clock(ClockActionKind.SET_TIME, 120_000, null, T0))
          .isInstanceOf(InvalidGameRequestException.class);
      assertThatThrownBy(() -> clock(ClockActionKind.ADD_TIME, 120_000, 0L, T0))
          .isInstanceOf(InvalidGameRequestException.class);
      assertThat(game.clockActions()).isEmpty();
    }
  }

  @Nested
  class Warnings {

    @Test
    void timeShown_outsideTheTolerance_warns() {
      start();

      Recorded far = clock(ClockActionKind.PAUSE, 100_000, null, at(10));

      assertThat(far.raisedWarnings()).containsExactly(Warning.TIME_SHOWN_OUT_OF_TOLERANCE);
      assertThat(game.clockActions().getLast().meta().raisedWarnings())
          .containsExactly(Warning.TIME_SHOWN_OUT_OF_TOLERANCE);
    }

    @Test
    void timeShown_withinTheTolerance_raisesNothing() {
      start();

      assertThat(clock(ClockActionKind.PAUSE, 110_900, null, at(10)).raisedWarnings()).isEmpty();
    }

    @Test
    void clockRemaining_outsideTheTolerance_warns() {
      start();

      assertThat(score(Side.AKA, ScoreLevel.YUKO, 100_000L, at(10)).raisedWarnings())
          .containsExactly(Warning.CLOCK_REMAINING_OUT_OF_TOLERANCE);
      assertThat(score(Side.AKA, ScoreLevel.YUKO, 109_500L, at(10)).raisedWarnings()).isEmpty();
    }

    /** A video review names an earlier exchange, with the time of that exchange: not warned. */
    @Test
    void clockRemaining_ofEarlierExchange_isNotCheckedAgainstTheClockNow() {
      start();
      clock(ClockActionKind.PAUSE, 100_000, null, at(20));
      clock(ClockActionKind.START, 100_000, null, at(30));

      Recorded review =
          game.recordScore(
              new NewScore(
                  id(), Side.AO, ScoreLevel.WAZA_ARI, ScoreSource.VIDEO_REVIEW, 1, 115_000L),
              SK,
              at(40));
      Recorded current =
          game.recordScore(
              new NewScore(id(), Side.AO, ScoreLevel.YUKO, ScoreSource.JUDGES, 2, 115_000L),
              SK,
              at(40));

      assertThat(review.raisedWarnings()).isEmpty();
      assertThat(current.raisedWarnings())
          .containsExactly(Warning.CLOCK_REMAINING_OUT_OF_TOLERANCE);
    }

    @Test
    void scoreAfterTheLateScoreWindow_warns_andOneWithinItDoesNot() {
      start();
      clock(ClockActionKind.TIME_UP, 0, null, at(120));

      assertThat(score(Side.AKA, ScoreLevel.YUKO, null, at(121.4)).raisedWarnings()).isEmpty();
      assertThat(score(Side.AKA, ScoreLevel.YUKO, null, at(121.6)).raisedWarnings())
          .containsExactly(Warning.SCORE_AFTER_LATE_SCORE_WINDOW);
    }

    @Test
    void chuiPastTheLimit_warns_andCancelledOneDoesNotCount() {
      start();
      penalty(Side.AO, PenaltyLevel.CHUI, at(1));
      String cancelled = lastId();
      penalty(Side.AO, PenaltyLevel.CHUI, at(2));
      penalty(Side.AO, PenaltyLevel.CHUI, at(3));
      cancel(cancelled);

      assertThat(penalty(Side.AO, PenaltyLevel.CHUI, at(4)).raisedWarnings()).isEmpty();
      assertThat(penalty(Side.AO, PenaltyLevel.CHUI, at(5)).raisedWarnings())
          .containsExactly(Warning.CHUI_OVER_LIMIT);
      assertThat(game.state().side(Side.AO).chuiCount()).isEqualTo(4);
    }

    @Test
    void hansoku_withNoHansokuChuiBefore_warns() {
      start();

      assertThat(penalty(Side.AKA, PenaltyLevel.HANSOKU, at(1)).raisedWarnings())
          .containsExactly(Warning.HANSOKU_WITHOUT_HANSOKU_CHUI);
      penalty(Side.AO, PenaltyLevel.HANSOKU_CHUI, at(2));
      assertThat(penalty(Side.AO, PenaltyLevel.HANSOKU, at(3)).raisedWarnings()).isEmpty();
    }

    /** The level is recorded as given: a skipped level is never refused. */
    @Test
    void penaltyLevel_isRecordedAsGiven() {
      start();
      penalty(Side.AKA, PenaltyLevel.SHIKKAKU, at(1));

      assertThat(game.penalties(Side.AKA).getFirst().level()).isEqualTo(PenaltyLevel.SHIKKAKU);
    }

    @ParameterizedTest
    @CsvSource({
      "ROUND_ROBIN, true, false, true",
      "ELIMINATION, false, true, true",
      "ELIMINATION, true, false, false",
      "ROUND_ROBIN, false, true, false"
    })
    void formatCombination_thatMakesNoSense_warns(
        GameFormat format, boolean medalGame, boolean extraGame, boolean warns) {
      KumiteGame created =
          KumiteGame.create(
              "game-2",
              TestGames.newGame(format, medalGame, extraGame),
              TestGames.settings(),
              TestGames.ids("created"),
              SK,
              T0);

      assertThat(created.state().warnings())
          .isEqualTo(
              warns ? List.of(new GameWarning(Warning.FORMAT_COMBINATION, null)) : List.of());
    }
  }

  @Nested
  class Amendments {

    @Test
    void cancelledScore_leavesTheTotals() {
      start();
      score(Side.AKA, ScoreLevel.IPPON, null, at(1));
      String ippon = lastId();
      score(Side.AKA, ScoreLevel.WAZA_ARI, null, at(2));

      cancel(ippon);

      SideState aka = game.state().side(Side.AKA);
      assertThat(aka.total()).isEqualTo(2);
      assertThat(aka.ipponCount()).isZero();
      assertThat(aka.wazaAriCount()).isEqualTo(1);
      assertThat(game.scores(Side.AKA)).hasSize(2);
      assertThat(game.state().isCancelled(ippon)).isTrue();
    }

    @Test
    void cancelledPenalty_leavesTheCounts() {
      start();
      penalty(Side.AO, PenaltyLevel.CHUI, at(1));

      cancel(lastId());

      assertThat(game.state().side(Side.AO).chuiCount()).isZero();
    }

    @Test
    void cancelOfMissingEntry_isRefused() {
      assertThatThrownBy(() -> cancel("nothing")).isInstanceOf(ActionRefusedException.class);
      assertThat(game.amendments()).isEmpty();
    }

    @Test
    void secondCancelOfTheSameTarget_isRefused() {
      start();
      score(Side.AKA, ScoreLevel.YUKO, null, at(1));
      String yuko = lastId();
      cancel(yuko);

      assertThatThrownBy(() -> cancel(yuko)).isInstanceOf(ActionRefusedException.class);
      assertThat(game.amendments()).hasSize(1);
    }

    @Test
    void cancelOfCancel_isRefused() {
      start();
      score(Side.AKA, ScoreLevel.YUKO, null, at(1));
      cancel(lastId());

      assertThatThrownBy(() -> cancel(lastId())).isInstanceOf(ActionRefusedException.class);
    }

    @Test
    void cancelOfWhatCannotBeCancelled_isRefused() {
      assertThatThrownBy(() -> cancel("created-1")).isInstanceOf(ActionRefusedException.class);
    }

    @Test
    void acknowledgeOfMissingEntry_isRefused_andOfAnyOtherEntryApplies() {
      assertThatThrownBy(() ->
              game.recordAmendment(
                  new NewAmendment(id(), AmendmentKind.ACKNOWLEDGE, "nothing", null), SK, T0))
          .isInstanceOf(ActionRefusedException.class);

      assertThat(
              game.recordAmendment(
                      new NewAmendment(id(), AmendmentKind.ACKNOWLEDGE, "created-1", "seen"),
                      SK,
                      T0)
                  .raisedWarnings())
          .isEmpty();
    }

    /** History written before a rule existed must load: the fold skips it, with a warning. */
    @Test
    void historyThatBreaksCurrentRule_stillLoads_withWarning() {
      EntryMeta scoreMeta = new EntryMeta("score-1", 4, T0, SK, List.of());
      KumiteGame stored =
          new KumiteGame(
              "game-3",
              GameFormat.ELIMINATION,
              false,
              false,
              TestGames.settings(),
              Map.of(
                  Side.AKA,
                  List.of(new Score(ScoreLevel.IPPON, ScoreSource.JUDGES, 1, null, scoreMeta)),
                  Side.AO,
                  List.of(
                      new Score(
                          ScoreLevel.IPPON,
                          ScoreSource.JUDGES,
                          1,
                          null,
                          new EntryMeta("score-1", 8, T0, SK, List.of())))),
              Map.of(),
              TestGames.game().assignments(),
              List.of(),
              List.of(),
              List.of(
                  new Amendment(
                      AmendmentKind.CANCEL,
                      "score-1",
                      null,
                      new EntryMeta("cancel-1", 5, T0, SK, List.of())),
                  new Amendment(
                      AmendmentKind.CANCEL,
                      "score-1",
                      null,
                      new EntryMeta("cancel-2", 6, T0, SK, List.of())),
                  new Amendment(
                      AmendmentKind.CANCEL,
                      "missing",
                      null,
                      new EntryMeta("cancel-3", 7, T0, SK, List.of()))),
              List.of());

      GameState state = stored.state();

      assertThat(state.side(Side.AKA).total()).isZero();
      assertThat(state.side(Side.AO).total()).as("a reused id is skipped, not counted").isZero();
      assertThat(state.warnings())
          .containsExactly(
              new GameWarning(Warning.HISTORY_INCONSISTENT, "cancel-2"),
              new GameWarning(Warning.HISTORY_INCONSISTENT, "cancel-3"),
              new GameWarning(Warning.HISTORY_INCONSISTENT, "score-1"));
      assertThat(state.lastOrder()).isEqualTo(8);
    }
  }

  @Nested
  class Retries {

    private final NewScore yuko =
        new NewScore("score-1", Side.AKA, ScoreLevel.YUKO, ScoreSource.JUDGES, null, 110_000L);

    @Test
    void sameIdAndPayload_appliesOnce_andAnswersTheStoredWarnings() {
      start();
      Recorded first = game.recordScore(yuko, SK, at(1));

      Recorded retry = game.recordScore(yuko, SK, at(2));

      assertThat(first.raisedWarnings()).containsExactly(Warning.CLOCK_REMAINING_OUT_OF_TOLERANCE);
      assertThat(retry.replayed()).isTrue();
      assertThat(retry.raisedWarnings()).isEqualTo(first.raisedWarnings());
      assertThat(game.scores(Side.AKA)).hasSize(1);
      assertThat(game.scores(Side.AKA).getFirst().meta().recordedAt()).isEqualTo(at(1));
    }

    /** The server filled the exchange in, so a retry that leaves it out is the same request. */
    @Test
    void retryLeavingOutTheExchange_isTheSameRequest_afterTheCounterMoved() {
      start();
      game.recordScore(yuko, SK, at(1));
      clock(ClockActionKind.PAUSE, 110_000, null, at(10));
      clock(ClockActionKind.START, 110_000, null, at(11));

      assertThat(game.recordScore(yuko, SK, at(12)).replayed()).isTrue();
      assertThat(
              game.recordScore(
                      new NewScore(
                          "score-1", Side.AKA, ScoreLevel.YUKO, ScoreSource.JUDGES, 1, 110_000L),
                      SK,
                      at(12))
                  .replayed())
          .isTrue();
    }

    @Test
    void sameIdWithAnotherPayload_isRefused() {
      game.recordScore(yuko, SK, T0);

      assertThatThrownBy(() ->
              game.recordScore(
                  new NewScore(
                      "score-1", Side.AO, ScoreLevel.YUKO, ScoreSource.JUDGES, null, 110_000L),
                  SK,
                  T0))
          .isInstanceOf(ActionRefusedException.class);
      assertThatThrownBy(() ->
              game.recordPenalty(
                  new NewPenalty("score-1", Side.AKA, PenaltyLevel.CHUI, null, null, null), SK, T0))
          .isInstanceOf(ActionRefusedException.class);
      assertThat(game.entriesInOrder()).hasSize(4);
    }

    @Test
    void sameIdFromAnotherAccount_isRefused() {
      game.recordScore(yuko, SK, T0);

      assertThatThrownBy(() -> game.recordScore(yuko, "someone-else", T0))
          .isInstanceOf(ActionRefusedException.class);
    }
  }

  @Nested
  class Assignments {

    @Test
    void athleteOnBothSides_isRefused() {
      assertThatThrownBy(() ->
              game.recordAssignment(
                  new NewAssignment(
                      id(), AssignmentKind.ATHLETE, Side.AKA, TestGames.AO_ATHLETE, null, null),
                  SK,
                  T0))
          .isInstanceOf(ActionRefusedException.class);
    }

    @Test
    void anotherAthlete_replacesTheSide_andStaysOnRecord() {
      game.recordAssignment(
          new NewAssignment(id(), AssignmentKind.ATHLETE, Side.AKA, "athlete-3", null, null),
          SK,
          T0);

      assertThat(game.state().side(Side.AKA).athleteId()).isEqualTo("athlete-3");
      assertThat(game.assignments()).hasSize(4);
    }

    @Test
    void panelChanges_applyAsGiven() {
      game.recordAssignment(
          new NewAssignment(
              id(), AssignmentKind.PANEL_ADD, null, null, TestGames.REFEREE, PanelRole.KANSA),
          SK,
          T0);
      game.recordAssignment(
          new NewAssignment(id(), AssignmentKind.PANEL_REMOVE, null, null, "nobody", null), SK, T0);

      assertThat(game.state().panel())
          .containsExactly(new PanelMember(TestGames.REFEREE, PanelRole.KANSA));
    }

    @Test
    void malformedAssignment_isRefused() {
      assertThatThrownBy(() ->
              game.recordAssignment(
                  new NewAssignment(id(), AssignmentKind.ATHLETE, null, "athlete-3", null, null),
                  SK,
                  T0))
          .isInstanceOf(InvalidGameRequestException.class);
    }

    @Test
    void creation_refusesOneAthleteOnBothSides_andRefereeTwice() {
      assertThatThrownBy(() ->
              KumiteGame.create(
                  "game-4",
                  new NewGame(GameFormat.ELIMINATION, false, false, "a", "a", List.of()),
                  TestGames.settings(),
                  TestGames.ids("x"),
                  SK,
                  T0))
          .isInstanceOf(InvalidGameRequestException.class);
      assertThatThrownBy(() ->
              KumiteGame.create(
                  "game-4",
                  new NewGame(
                      GameFormat.ELIMINATION,
                      false,
                      false,
                      "a",
                      "b",
                      List.of(
                          new PanelMember("r", PanelRole.REFEREE),
                          new PanelMember("r", PanelRole.JUDGE))),
                  TestGames.settings(),
                  TestGames.ids("x"),
                  SK,
                  T0))
          .isInstanceOf(InvalidGameRequestException.class);
    }

    @Test
    void creation_recordsTheAthletesAndPanelAsTheFirstEntries() {
      assertThat(game.assignments())
          .extracting(assignment -> assignment.meta().order())
          .containsExactly(1, 2, 3);
      assertThat(game.state().side(Side.AKA).athleteId()).isEqualTo(TestGames.AKA_ATHLETE);
      assertThat(game.state().panel())
          .containsExactly(new PanelMember(TestGames.REFEREE, PanelRole.REFEREE));
    }
  }

  @Nested
  class SettingsAndResults {

    @Test
    void settingsChange_beforeTheStart_keepsTheRulebookVersion() {
      game.changeSettings(newSettings(90_000));

      assertThat(game.settings().durationMs()).isEqualTo(90_000);
      assertThat(game.settings().rulebookVersion()).isEqualTo("WKF 2026.01");
      assertThat(game.state().mainClock().remainingMs()).isEqualTo(90_000);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1"})
    void durationOfZeroOrLess_isRefused(long durationMs) {
      assertThatThrownBy(() -> game.changeSettings(newSettings(durationMs)))
          .isInstanceOf(InvalidGameRequestException.class);
      assertThat(game.settings().durationMs()).isEqualTo(120_000);
    }

    @Test
    void scoreTotals_comeFromTheSettings() {
      game.changeSettings(
          new NewSettings(
              120_000,
              List.of(120_000L),
              Map.of(ScoreLevel.IPPON, 5, ScoreLevel.WAZA_ARI, 3, ScoreLevel.YUKO, 1),
              8,
              15_000,
              1_500,
              3,
              4,
              List.of(10_000L),
              180_000,
              1_000));
      start();
      score(Side.AO, ScoreLevel.IPPON, null, at(1));

      assertThat(game.state().side(Side.AO).total()).isEqualTo(5);
    }

    @Test
    void winWithoutWinner_orDrawWithOne_isRefused() {
      assertThatThrownBy(() -> result(Outcome.WIN, null, ResultReason.POINTS, T0))
          .isInstanceOf(InvalidGameRequestException.class);
      assertThatThrownBy(() -> result(Outcome.DRAW, Side.AKA, ResultReason.HIKIWAKE, T0))
          .isInstanceOf(InvalidGameRequestException.class);
    }

    @Test
    void laterResult_supersedes_andTheEarlierStaysOnRecord() {
      start();
      result(Outcome.WIN, Side.AKA, ResultReason.POINTS, at(1));
      result(Outcome.WIN, Side.AO, ResultReason.HANTEI, at(2));

      assertThat(game.results()).hasSize(2);
      Result current = Objects.requireNonNull(game.state().currentResult());
      assertThat(current.winner()).isEqualTo(Side.AO);
    }
  }
}
