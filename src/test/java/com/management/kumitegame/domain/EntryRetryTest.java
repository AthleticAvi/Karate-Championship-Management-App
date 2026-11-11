package com.management.kumitegame.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.testsupport.TestClock;
import com.management.testsupport.TestGames;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * A retry is recognised by its id for every kind of entry: the same request applies once, and the
 * same id with any one field changed, or from another account, is refused.
 */
class EntryRetryTest {

  private static final Instant T0 = TestClock.NOW;
  private static final String SK = TestGames.SCOREKEEPER;

  /** Records the original, replays it, then sends each variant under the same id. */
  private static <T> void provesRetries(
      KumiteGame game, T original, List<T> variants, Function<T, Recorded> record) {
    final int entries = game.entriesInOrder().size();
    assertThat(record.apply(original).replayed()).isFalse();

    assertThat(record.apply(original).replayed()).as("the same request again").isTrue();
    for (T variant : variants) {
      assertThatThrownBy(() -> record.apply(variant))
          .as("same id, another request: %s", variant)
          .isInstanceOf(ActionRefusedException.class);
    }
    assertThat(game.entriesInOrder()).hasSize(entries + 1);
  }

  @Test
  void penalty() {
    KumiteGame game = TestGames.game();
    NewPenalty original =
        new NewPenalty("p", Side.AKA, PenaltyLevel.CHUI, Infraction.JOGAI, 1, 100_000L);
    provesRetries(
        game,
        original,
        List.of(
            new NewPenalty("p", Side.AO, PenaltyLevel.CHUI, Infraction.JOGAI, 1, 100_000L),
            new NewPenalty("p", Side.AKA, PenaltyLevel.HANSOKU, Infraction.JOGAI, 1, 100_000L),
            new NewPenalty("p", Side.AKA, PenaltyLevel.CHUI, Infraction.MUBOBI, 1, 100_000L),
            new NewPenalty("p", Side.AKA, PenaltyLevel.CHUI, Infraction.JOGAI, 2, 100_000L),
            new NewPenalty("p", Side.AKA, PenaltyLevel.CHUI, Infraction.JOGAI, 1, 90_000L)),
        penalty -> game.recordPenalty(penalty, SK, T0));
    assertThatThrownBy(() -> game.recordPenalty(original, "other", T0))
        .isInstanceOf(ActionRefusedException.class);
  }

  @Test
  void score() {
    KumiteGame game = TestGames.game();
    NewScore original =
        new NewScore("s", Side.AKA, ScoreLevel.IPPON, ScoreSource.JUDGES, 1, 100_000L);
    provesRetries(
        game,
        original,
        List.of(
            new NewScore("s", Side.AKA, ScoreLevel.YUKO, ScoreSource.JUDGES, 1, 100_000L),
            new NewScore("s", Side.AKA, ScoreLevel.IPPON, ScoreSource.VIDEO_REVIEW, 1, 100_000L),
            new NewScore("s", Side.AKA, ScoreLevel.IPPON, ScoreSource.JUDGES, 2, 100_000L),
            new NewScore("s", Side.AKA, ScoreLevel.IPPON, ScoreSource.JUDGES, 1, null)),
        score -> game.recordScore(score, SK, T0));
  }

  @Test
  void clockAction() {
    KumiteGame game = TestGames.game();
    NewClockAction original =
        new NewClockAction(
            "c", Timer.MEDICAL, Side.AKA, ClockActionKind.ADD_TIME, 1_000, 5_000L, null);
    provesRetries(
        game,
        original,
        List.of(
            new NewClockAction(
                "c", Timer.MAIN, null, ClockActionKind.ADD_TIME, 1_000, 5_000L, null),
            new NewClockAction(
                "c", Timer.MEDICAL, Side.AO, ClockActionKind.ADD_TIME, 1_000, 5_000L, null),
            new NewClockAction(
                "c", Timer.MEDICAL, Side.AKA, ClockActionKind.SET_TIME, 1_000, 5_000L, null),
            new NewClockAction(
                "c", Timer.MEDICAL, Side.AKA, ClockActionKind.ADD_TIME, 2_000, 5_000L, null),
            new NewClockAction(
                "c", Timer.MEDICAL, Side.AKA, ClockActionKind.ADD_TIME, 1_000, 6_000L, null),
            new NewClockAction(
                "c",
                Timer.MEDICAL,
                Side.AKA,
                ClockActionKind.ADD_TIME,
                1_000,
                5_000L,
                PauseReason.INJURY)),
        action -> game.recordClockAction(action, SK, T0));
    assertThatThrownBy(() -> game.recordClockAction(original, "other", T0))
        .isInstanceOf(ActionRefusedException.class);
  }

  @Test
  void senshuRecord() {
    KumiteGame game = TestGames.game();
    NewSenshuRecord original =
        new NewSenshuRecord("s", Side.AKA, SenshuRecordKind.AWARDED, "judges", 1, 100_000L);
    provesRetries(
        game,
        original,
        List.of(
            new NewSenshuRecord("s", Side.AO, SenshuRecordKind.AWARDED, "judges", 1, 100_000L),
            new NewSenshuRecord("s", Side.AKA, SenshuRecordKind.WITHDRAWN, "judges", 1, 100_000L),
            new NewSenshuRecord("s", Side.AKA, SenshuRecordKind.AWARDED, null, 1, 100_000L),
            new NewSenshuRecord("s", Side.AKA, SenshuRecordKind.AWARDED, "judges", 2, 100_000L),
            new NewSenshuRecord("s", Side.AKA, SenshuRecordKind.AWARDED, "judges", 1, null)),
        record -> game.recordSenshu(record, SK, T0));
    assertThatThrownBy(() -> game.recordSenshu(original, "other", T0))
        .isInstanceOf(ActionRefusedException.class);
  }

  @Test
  void assignment() {
    KumiteGame game = TestGames.game();
    NewAssignment original =
        new NewAssignment("a", AssignmentKind.PANEL_ADD, null, null, "r2", PanelRole.JUDGE);
    provesRetries(
        game,
        original,
        List.of(
            new NewAssignment("a", AssignmentKind.PANEL_REMOVE, null, null, "r2", PanelRole.JUDGE),
            new NewAssignment("a", AssignmentKind.PANEL_ADD, Side.AKA, null, "r2", PanelRole.JUDGE),
            new NewAssignment("a", AssignmentKind.PANEL_ADD, null, "x", "r2", PanelRole.JUDGE),
            new NewAssignment("a", AssignmentKind.PANEL_ADD, null, null, "r3", PanelRole.JUDGE),
            new NewAssignment("a", AssignmentKind.PANEL_ADD, null, null, "r2", PanelRole.KANSA)),
        assignment -> game.recordAssignment(assignment, SK, T0));
    assertThatThrownBy(() -> game.recordAssignment(original, "other", T0))
        .isInstanceOf(ActionRefusedException.class);
  }

  @Test
  void amendment() {
    KumiteGame game = TestGames.game();
    NewAmendment original = new NewAmendment("m", AmendmentKind.ACKNOWLEDGE, "created-1", "seen");
    provesRetries(
        game,
        original,
        List.of(
            new NewAmendment("m", AmendmentKind.CANCEL, "created-1", "seen"),
            new NewAmendment("m", AmendmentKind.ACKNOWLEDGE, "created-2", "seen"),
            new NewAmendment("m", AmendmentKind.ACKNOWLEDGE, "created-1", null)),
        amendment -> game.recordAmendment(amendment, SK, T0));
    assertThatThrownBy(() -> game.recordAmendment(original, "other", T0))
        .isInstanceOf(ActionRefusedException.class);
  }

  @Test
  void result() {
    KumiteGame game = TestGames.game();
    NewResult original =
        new NewResult("r", Outcome.WIN, Side.AKA, ResultReason.KIKEN, "10-second rule", false);
    provesRetries(
        game,
        original,
        List.of(
            new NewResult("r", Outcome.DRAW, null, ResultReason.KIKEN, "10-second rule", false),
            new NewResult("r", Outcome.WIN, Side.AO, ResultReason.KIKEN, "10-second rule", false),
            new NewResult("r", Outcome.WIN, Side.AKA, ResultReason.HANTEI, "10-second rule", false),
            new NewResult("r", Outcome.WIN, Side.AKA, ResultReason.KIKEN, null, false),
            new NewResult("r", Outcome.WIN, Side.AKA, ResultReason.KIKEN, "10-second rule", true)),
        result -> game.recordResult(result, SK, T0));
    assertThatThrownBy(() -> game.recordResult(original, "other", T0))
        .isInstanceOf(ActionRefusedException.class);
  }

  /** A retry is recognised before the status is checked, so a void sent again replays. */
  @Test
  void statusChange() {
    KumiteGame game = TestGames.game();
    NewStatusChange original = new NewStatusChange("v", StatusChangeKind.VOID, "never fought");
    provesRetries(
        game,
        original,
        List.of(
            new NewStatusChange("v", StatusChangeKind.REOPEN, "never fought"),
            new NewStatusChange("v", StatusChangeKind.VOID, "protest"),
            new NewStatusChange("v", StatusChangeKind.VOID, null)),
        change -> game.recordStatusChange(change, ActingRole.ADMIN, SK, T0));
    assertThatThrownBy(() -> game.recordStatusChange(original, ActingRole.ADMIN, "other", T0))
        .isInstanceOf(ActionRefusedException.class);
  }
}
