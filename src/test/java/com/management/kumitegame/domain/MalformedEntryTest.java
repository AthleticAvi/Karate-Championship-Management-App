package com.management.kumitegame.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.testsupport.TestClock;
import com.management.testsupport.TestGames;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Malformed input is refused when it is appended, and malformed history is skipped when it is
 * folded, never thrown.
 */
class MalformedEntryTest {

  private static final Instant T0 = TestClock.NOW;
  private static final String SK = TestGames.SCOREKEEPER;

  private static EntryMeta meta(String id, int order) {
    return new EntryMeta(id, order, T0, SK, List.of());
  }

  static List<NewAssignment> malformedAssignments() {
    return List.of(
        new NewAssignment("a", AssignmentKind.ATHLETE, Side.AKA, null, null, null),
        new NewAssignment("a", AssignmentKind.ATHLETE, Side.AKA, "x", "r", null),
        new NewAssignment("a", AssignmentKind.ATHLETE, Side.AKA, "x", null, PanelRole.JUDGE),
        new NewAssignment("a", AssignmentKind.PANEL_ADD, null, null, null, PanelRole.JUDGE),
        new NewAssignment("a", AssignmentKind.PANEL_ADD, null, null, "r", null),
        new NewAssignment("a", AssignmentKind.PANEL_ADD, Side.AO, null, "r", PanelRole.JUDGE),
        new NewAssignment("a", AssignmentKind.PANEL_ADD, null, "x", "r", PanelRole.JUDGE),
        new NewAssignment("a", AssignmentKind.PANEL_REMOVE, null, null, null, null),
        new NewAssignment("a", AssignmentKind.PANEL_REMOVE, Side.AO, null, "r", null),
        new NewAssignment("a", AssignmentKind.PANEL_REMOVE, null, "x", "r", null));
  }

  @ParameterizedTest
  @MethodSource("malformedAssignments")
  void malformedAssignment_isRefused(NewAssignment assignment) {
    KumiteGame game = TestGames.game();

    assertThatThrownBy(() -> game.recordAssignment(assignment, SK, T0))
        .isInstanceOf(InvalidGameRequestException.class);
    assertThat(game.assignments()).hasSize(3);
  }

  static List<NewClockAction> malformedClockActions() {
    return List.of(
        new NewClockAction("c", Timer.MAIN, null, ClockActionKind.START, 0, 1_000L, null),
        new NewClockAction("c", Timer.MAIN, null, ClockActionKind.SET_TIME, 0, -1L, null),
        new NewClockAction("c", Timer.MAIN, null, ClockActionKind.ADD_TIME, 0, -1L, null));
  }

  @ParameterizedTest
  @MethodSource("malformedClockActions")
  void malformedClockAction_isRefused(NewClockAction action) {
    KumiteGame game = TestGames.game();

    assertThatThrownBy(() -> game.recordClockAction(action, SK, T0))
        .isInstanceOf(InvalidGameRequestException.class);
    assertThat(game.clockActions()).isEmpty();
  }

  @Test
  void setTimeToZero_isAllowed() {
    KumiteGame game = TestGames.game();

    game.recordClockAction(
        new NewClockAction("c", Timer.MAIN, null, ClockActionKind.SET_TIME, 120_000, 0L, null),
        SK,
        T0);

    assertThat(game.state().mainClock().remainingAt(T0)).isZero();
  }

  /** Each of these could only come from history written under other rules: each is skipped. */
  @Test
  void malformedHistory_isSkippedWithWarning_andTheRestStillFolds() {
    KumiteGame stored =
        new KumiteGame(
            "game-5",
            GameFormat.ELIMINATION,
            false,
            false,
            TestGames.settings(),
            Map.of(),
            Map.of(),
            List.of(
                new Assignment(AssignmentKind.ATHLETE, Side.AKA, "aka", null, null, meta("a1", 1)),
                new Assignment(AssignmentKind.ATHLETE, null, "x", null, null, meta("a2", 2)),
                new Assignment(AssignmentKind.PANEL_ADD, null, null, "r", null, meta("a3", 3)),
                new Assignment(AssignmentKind.PANEL_REMOVE, null, null, null, null, meta("a4", 4))),
            List.of(
                new ClockAction(
                    Timer.MAIN, null, ClockActionKind.ADD_TIME, 0, null, null, meta("c1", 5)),
                new ClockAction(
                    Timer.MEDICAL, null, ClockActionKind.START, 0, null, null, meta("c2", 6)),
                new ClockAction(
                    Timer.MAIN, null, ClockActionKind.START, 120_000, null, null, meta("c3", 7))),
            List.of(),
            List.of(
                new Amendment(AmendmentKind.CANCEL, "a1", null, meta("m1", 8)),
                new Amendment(AmendmentKind.ACKNOWLEDGE, "missing", null, meta("m2", 9))),
            List.of(),
            List.of(
                new StatusChange(StatusChangeKind.APPROVE, null, meta("z1", 10)),
                new StatusChange(StatusChangeKind.REOPEN, null, meta("z2", 11))),
            TestGames.rules());

    GameState state = stored.state();

    assertThat(state.warnings())
        .extracting(GameWarning::entryId)
        .containsExactly("a2", "a3", "a4", "c1", "c2", "m1", "m2", "z1", "z2");
    assertThat(state.side(Side.AKA).athleteId()).isEqualTo("aka");
    assertThat(state.status()).isEqualTo(GameStatus.FIGHTING);
    assertThat(state.panel()).isEmpty();
  }
}
