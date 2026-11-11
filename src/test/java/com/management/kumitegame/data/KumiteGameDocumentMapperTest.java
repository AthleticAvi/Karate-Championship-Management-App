package com.management.kumitegame.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.kumitegame.domain.AmendmentKind;
import com.management.kumitegame.domain.AssignmentKind;
import com.management.kumitegame.domain.ClockActionKind;
import com.management.kumitegame.domain.GameFormat;
import com.management.kumitegame.domain.Infraction;
import com.management.kumitegame.domain.KumiteGame;
import com.management.kumitegame.domain.NewAmendment;
import com.management.kumitegame.domain.NewAssignment;
import com.management.kumitegame.domain.NewClockAction;
import com.management.kumitegame.domain.NewPenalty;
import com.management.kumitegame.domain.NewResult;
import com.management.kumitegame.domain.NewScore;
import com.management.kumitegame.domain.NewSenshuRecord;
import com.management.kumitegame.domain.Outcome;
import com.management.kumitegame.domain.PanelRole;
import com.management.kumitegame.domain.PauseReason;
import com.management.kumitegame.domain.PenaltyLevel;
import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.ScoreLevel;
import com.management.kumitegame.domain.ScoreSource;
import com.management.kumitegame.domain.SenshuRecordKind;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.Timer;
import com.management.testsupport.TestClock;
import com.management.testsupport.TestGames;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every field of a game survives the trip to its stored form and back. */
class KumiteGameDocumentMapperTest {

  private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");
  private static final Instant MODIFIED = Instant.parse("2026-01-02T08:30:00.123Z");
  private static final String SK = TestGames.SCOREKEEPER;

  /** Every kind of entry, with every optional field set, and warnings on some. */
  private static KumiteGame everyEntry() {
    KumiteGame game = TestGames.game();
    Instant t = TestClock.NOW;
    game.recordClockAction(
        new NewClockAction("c1", Timer.MAIN, null, ClockActionKind.START, 120_000, null, null),
        SK,
        t);
    game.recordClockAction(
        new NewClockAction(
            "c2", Timer.MAIN, null, ClockActionKind.PAUSE, 50_000, null, PauseReason.INJURY),
        SK,
        t.plusSeconds(10));
    game.recordClockAction(
        new NewClockAction(
            "c3", Timer.MEDICAL, Side.AO, ClockActionKind.ADD_TIME, 180_000, 30_000L, null),
        SK,
        t.plusSeconds(11));
    game.recordScore(
        new NewScore("s1", Side.AKA, ScoreLevel.IPPON, ScoreSource.VIDEO_REVIEW, 1, 109_000L),
        SK,
        t.plusSeconds(12));
    game.recordPenalty(
        new NewPenalty("p1", Side.AO, PenaltyLevel.HANSOKU, Infraction.JOGAI, 1, 109_000L),
        SK,
        t.plusSeconds(13));
    game.recordSenshu(
        new NewSenshuRecord("k1", Side.AKA, SenshuRecordKind.AWARDED, "video review", 1, 109_000L),
        SK,
        t.plusSeconds(12));
    game.recordAssignment(
        new NewAssignment("a1", AssignmentKind.PANEL_ADD, null, null, "referee-2", PanelRole.KANSA),
        SK,
        t.plusSeconds(14));
    game.recordAmendment(
        new NewAmendment("m1", AmendmentKind.CANCEL, "p1", "wrong side"), SK, t.plusSeconds(15));
    game.recordResult(
        new NewResult("r1", Outcome.WIN, Side.AKA, ResultReason.KIKEN, "10-second rule", true),
        SK,
        t.plusSeconds(16));
    return game;
  }

  private static KumiteGameDocument stored(KumiteGame game) {
    KumiteGameDocument fresh = KumiteGameDocumentMapper.toNewDocument(game);
    return KumiteGameDocumentMapper.toDocument(
        game,
        new KumiteGameDocument(
            fresh.getId(),
            7,
            fresh.getSchemaVersion(),
            fresh.getFormat(),
            fresh.isMedalGame(),
            fresh.isExtraGame(),
            fresh.getSettings(),
            fresh.getSides(),
            fresh.getAssignments(),
            fresh.getClockActions(),
            fresh.getSenshuRecords(),
            fresh.getAmendments(),
            fresh.getResults(),
            CREATED,
            MODIFIED));
  }

  @Test
  void domainToDocumentToDomain_keepsEveryField() {
    KumiteGame game = everyEntry();

    KumiteGame read = KumiteGameDocumentMapper.toDomain(stored(game));

    assertThat(read).usingRecursiveComparison().isEqualTo(game);
    assertThat(read.state()).isEqualTo(game.state());
    assertThat(read.penalties(Side.AO).getFirst().meta().raisedWarnings()).isNotEmpty();
  }

  @Test
  void documentToDomainToDocument_keepsEveryField() {
    KumiteGameDocument stored = stored(everyEntry());

    KumiteGameDocument written =
        KumiteGameDocumentMapper.toDocument(KumiteGameDocumentMapper.toDomain(stored), stored);

    assertThat(written).usingRecursiveComparison().isEqualTo(stored);
    assertThat(written.getVersion()).isEqualTo(7);
    assertThat(written.getCreatedAt()).isEqualTo(CREATED);
    assertThat(written.getSchemaVersion()).isEqualTo(1);
  }

  @Test
  void newDocument_isUnsavedAtTheCurrentShape() {
    KumiteGameDocument written = KumiteGameDocumentMapper.toNewDocument(TestGames.game());

    assertThat(written.getId()).isEqualTo(TestGames.GAME_ID);
    assertThat(written.getVersion()).isZero();
    assertThat(written.getSchemaVersion()).isEqualTo(KumiteGameDocument.CURRENT_SCHEMA_VERSION);
    assertThat(written.getCreatedAt()).isNull();
  }

  @Test
  void toDocument_refusesAnotherGamesStoredDocument() {
    KumiteGameDocument other =
        KumiteGameDocumentMapper.toNewDocument(
            KumiteGame.create(
                "game-2",
                TestGames.newGame(GameFormat.ELIMINATION, false, false),
                TestGames.settings(),
                TestGames.ids("x"),
                SK,
                TestClock.NOW));

    assertThatThrownBy(() -> KumiteGameDocumentMapper.toDocument(TestGames.game(), other))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toDomain_refusesShapesNewerThanThisBuild() {
    KumiteGameDocument current = KumiteGameDocumentMapper.toNewDocument(TestGames.game());
    KumiteGameDocument newer =
        new KumiteGameDocument(
            current.getId(),
            1,
            KumiteGameDocument.CURRENT_SCHEMA_VERSION + 1,
            current.getFormat(),
            false,
            false,
            current.getSettings(),
            current.getSides(),
            current.getAssignments(),
            current.getClockActions(),
            current.getSenshuRecords(),
            current.getAmendments(),
            current.getResults(),
            CREATED,
            MODIFIED);

    assertThatThrownBy(() -> KumiteGameDocumentMapper.toDomain(newer))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("schema version 2");
  }

  /** A stored value this build cannot read is a fault in the data, never the caller's 400. */
  @Test
  void toDomain_withValueThisBuildDoesNotKnow_isServerFault() {
    KumiteGameDocument current = KumiteGameDocumentMapper.toNewDocument(TestGames.game());
    KumiteGameDocument unknown =
        new KumiteGameDocument(
            current.getId(),
            1,
            1,
            "TEAM",
            false,
            false,
            current.getSettings(),
            current.getSides(),
            current.getAssignments(),
            current.getClockActions(),
            current.getSenshuRecords(),
            current.getAmendments(),
            current.getResults(),
            CREATED,
            MODIFIED);

    assertThatThrownBy(() -> KumiteGameDocumentMapper.toDomain(unknown))
        .isInstanceOf(IllegalStateException.class)
        .isNotInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot read");
  }

  /** A list stored as absent, such as one added to the shape later, reads as empty. */
  @Test
  void absentLists_readAsEmpty() {
    KumiteGameDocument current = KumiteGameDocumentMapper.toNewDocument(TestGames.game());
    KumiteGameDocument sparse =
        new KumiteGameDocument(
            current.getId(),
            1,
            1,
            current.getFormat(),
            false,
            false,
            current.getSettings(),
            null,
            current.getAssignments(),
            null,
            null,
            null,
            null,
            CREATED,
            MODIFIED);

    KumiteGame read = KumiteGameDocumentMapper.toDomain(sparse);

    assertThat(read.scores(Side.AKA)).isEmpty();
    assertThat(read.results()).isEqualTo(List.of());
    assertThat(read.senshuRecords()).isEmpty();
    assertThat(read.state().side(Side.AO).athleteId()).isEqualTo(TestGames.AO_ATHLETE);
  }
}
