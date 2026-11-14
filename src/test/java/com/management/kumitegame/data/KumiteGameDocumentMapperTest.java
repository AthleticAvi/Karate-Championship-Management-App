package com.management.kumitegame.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.kumitegame.data.KumiteGameDocument.StoredPenalty;
import com.management.kumitegame.data.KumiteGameDocument.StoredScore;
import com.management.kumitegame.data.KumiteGameDocument.StoredSenshuRecord;
import com.management.kumitegame.data.KumiteGameDocument.StoredSettings;
import com.management.kumitegame.data.KumiteGameDocument.StoredSide;
import com.management.kumitegame.domain.ActingRole;
import com.management.kumitegame.domain.ActionRefusedException;
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
import com.management.kumitegame.domain.NewStatusChange;
import com.management.kumitegame.domain.Outcome;
import com.management.kumitegame.domain.PanelRole;
import com.management.kumitegame.domain.PauseReason;
import com.management.kumitegame.domain.PenaltyLevel;
import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.ScoreLevel;
import com.management.kumitegame.domain.ScoreSource;
import com.management.kumitegame.domain.SenshuRecordKind;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.StatusChangeKind;
import com.management.kumitegame.domain.SuggestedResult;
import com.management.kumitegame.domain.Timer;
import com.management.testsupport.TestClock;
import com.management.testsupport.TestGames;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Every field of a game survives the trip to its stored form and back. */
class KumiteGameDocumentMapperTest {

  private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");
  private static final Instant MODIFIED = Instant.parse("2026-01-02T08:30:00.123Z");
  private static final String SK = TestGames.SCOREKEEPER;

  /**
   * Every kind of entry, with every optional field set, and warnings on some. One penalty leaves
   * its exchange out, for the server to fill in.
   */
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
        new NewPenalty("p1", Side.AO, PenaltyLevel.HANSOKU, Infraction.JOGAI, null, 109_000L),
        SK,
        t.plusSeconds(13));
    game.recordPenalty(
        new NewPenalty("p2", Side.AKA, PenaltyLevel.CHUI, Infraction.MUBOBI, 1, 109_000L),
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
    // At time up, so the result keeps a suggestion: AKA on points.
    game.recordClockAction(
        new NewClockAction("c4", Timer.MAIN, null, ClockActionKind.TIME_UP, 0, null, null),
        SK,
        t.plusSeconds(16));
    game.recordResult(
        new NewResult("r1", Outcome.WIN, Side.AKA, ResultReason.KIKEN, "10-second rule", true),
        SK,
        t.plusSeconds(16));
    game.recordStatusChange(
        new NewStatusChange("z1", StatusChangeKind.APPROVE, null),
        ActingRole.SCOREKEEPER,
        SK,
        t.plusSeconds(17));
    game.recordStatusChange(
        new NewStatusChange("z2", StatusChangeKind.REOPEN, "protest accepted"),
        ActingRole.ADMIN,
        SK,
        t.plusSeconds(18));
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
            fresh.getStatusChanges(),
            CREATED,
            MODIFIED));
  }

  @Test
  void domainToDocumentToDomain_keepsEveryField() {
    KumiteGame game = everyEntry();

    KumiteGame read = KumiteGameDocumentMapper.toDomain(stored(game), TestGames.rules());

    assertThat(read).usingRecursiveComparison().isEqualTo(game);
    assertThat(read.state()).isEqualTo(game.state());
    assertThat(read.penalties(Side.AO).getFirst().meta().raisedWarnings()).isNotEmpty();
  }

  @Test
  void documentToDomainToDocument_keepsEveryField() {
    KumiteGameDocument stored = stored(everyEntry());

    KumiteGameDocument written =
        KumiteGameDocumentMapper.toDocument(
            KumiteGameDocumentMapper.toDomain(stored, TestGames.rules()), stored);

    assertThat(written).usingRecursiveComparison().isEqualTo(stored);
    assertThat(written.getVersion()).isEqualTo(7);
    assertThat(written.getCreatedAt()).isEqualTo(CREATED);
    assertThat(written.getSchemaVersion()).isEqualTo(KumiteGameDocument.CURRENT_SCHEMA_VERSION);
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
                TestClock.NOW,
                TestGames.rules()));

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
            current.getStatusChanges(),
            CREATED,
            MODIFIED);

    assertThatThrownBy(() -> KumiteGameDocumentMapper.toDomain(newer, TestGames.rules()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("schema version " + (KumiteGameDocument.CURRENT_SCHEMA_VERSION + 1));
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
            current.getStatusChanges(),
            CREATED,
            MODIFIED);

    assertThatThrownBy(() -> KumiteGameDocumentMapper.toDomain(unknown, TestGames.rules()))
        .isInstanceOf(IllegalStateException.class)
        .isNotInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot read");
  }

  @Test
  void resultKeepsItsSuggestion_throughTheStoredForm() {
    KumiteGame game = everyEntry();
    assertThat(game.results().getFirst().suggested())
        .isEqualTo(SuggestedResult.win(Side.AKA, ResultReason.POINTS));

    KumiteGame read = KumiteGameDocumentMapper.toDomain(stored(game), TestGames.rules());

    assertThat(read.results().getFirst().suggested())
        .isEqualTo(SuggestedResult.win(Side.AKA, ResultReason.POINTS));
  }

  /** The stored game at this shape, its settings holding this panel limit. */
  private static KumiteGameDocument withPanelLimit(
      KumiteGameDocument current, int schemaVersion, @Nullable Integer panelLimit) {
    StoredSettings settings = current.getSettings();
    return new KumiteGameDocument(
        current.getId(),
        1,
        schemaVersion,
        current.getFormat(),
        current.isMedalGame(),
        current.isExtraGame(),
        new StoredSettings(
            settings.durationMs(),
            settings.durationsOfferedMs(),
            settings.pointValues(),
            settings.leadToWin(),
            settings.finalWindowMs(),
            settings.lateScoreWindowMs(),
            settings.chuiLimit(),
            settings.roundRobinForfeitPoints(),
            settings.addTimeStepsMs(),
            settings.medicalTimeMs(),
            settings.timeShownToleranceMs(),
            panelLimit,
            settings.rulebookVersion()),
        current.getSides(),
        current.getAssignments(),
        current.getClockActions(),
        current.getSenshuRecords(),
        current.getAmendments(),
        current.getResults(),
        current.getStatusChanges(),
        CREATED,
        MODIFIED);
  }

  /**
   * Stored history may hold a larger panel than its limit: it loads as written, and an add is
   * refused naming the limit, not the panel's size.
   */
  @Test
  void storedPanelOverItsLimit_loadsAsWritten_andAnAddIsRefusedNamingTheLimit() {
    KumiteGame tenJudges = TestGames.game();
    for (int judge = 2; judge <= 10; judge++) {
      tenJudges.recordAssignment(
          new NewAssignment(
              "add-" + judge,
              AssignmentKind.PANEL_ADD,
              null,
              null,
              "judge-" + judge,
              PanelRole.JUDGE),
          TestGames.SCOREKEEPER,
          TestClock.NOW);
    }
    KumiteGameDocument overItsLimit =
        withPanelLimit(
            KumiteGameDocumentMapper.toNewDocument(tenJudges),
            KumiteGameDocument.CURRENT_SCHEMA_VERSION,
            5);

    KumiteGame read = KumiteGameDocumentMapper.toDomain(overItsLimit, TestGames.rules());

    assertThat(read.state().panel()).hasSize(10);
    assertThat(read.state().warnings()).isEmpty();
    assertThatThrownBy(() ->
            read.recordAssignment(
                new NewAssignment(
                    "add-11", AssignmentKind.PANEL_ADD, null, null, "judge-11", PanelRole.JUDGE),
                TestGames.SCOREKEEPER,
                TestClock.NOW))
        .isInstanceOf(ActionRefusedException.class)
        .hasMessage("The panel already holds 10 referees, and this game's panel holds at most 5.");
  }

  /**
   * Shape 4 stored no panel limit: the game was created with none, so it reads with none, takes a
   * panel of any size, and is saved with none.
   */
  @Test
  void shapeFourSettings_readWithNoPanelLimit_andSaveWithNone() {
    KumiteGameDocument shapeFour =
        withPanelLimit(KumiteGameDocumentMapper.toNewDocument(TestGames.game()), 4, null);

    KumiteGame read = KumiteGameDocumentMapper.toDomain(shapeFour, TestGames.rules());
    for (int judge = 2; judge <= 11; judge++) {
      read.recordAssignment(
          new NewAssignment(
              "add-" + judge,
              AssignmentKind.PANEL_ADD,
              null,
              null,
              "judge-" + judge,
              PanelRole.JUDGE),
          TestGames.SCOREKEEPER,
          TestClock.NOW);
    }

    assertThat(read.settings().panelLimit()).isNull();
    assertThat(read.state().panel()).hasSize(11);
    assertThat(KumiteGameDocumentMapper.toDocument(read, shapeFour).getSettings().panelLimit())
        .isNull();
  }

  /**
   * Shape 5 stored no {@code exchangeNamed}: each score, penalty and SENSHU record reads as one
   * whose exchange the server filled in, so a retry leaving the exchange out replays, one naming it
   * is refused even where the first request named it, and it is saved so.
   */
  @Test
  void shapeFiveEntries_readAsExchangeFilledIn_andSaveSo() {
    KumiteGame game = everyEntry();
    KumiteGameDocument current = stored(game);
    KumiteGameDocument shapeFive =
        new KumiteGameDocument(
            current.getId(),
            current.getVersion(),
            5,
            current.getFormat(),
            current.isMedalGame(),
            current.isExtraGame(),
            current.getSettings(),
            current.getSides().entrySet().stream()
                .collect(
                    Collectors.toMap(
                        Map.Entry::getKey,
                        side ->
                            new StoredSide(
                                side.getValue().scores().stream()
                                    .map(s ->
                                        new StoredScore(
                                            s.level(),
                                            s.source(),
                                            s.exchange(),
                                            null,
                                            s.clockRemainingMs(),
                                            s.meta()))
                                    .toList(),
                                side.getValue().penalties().stream()
                                    .map(p ->
                                        new StoredPenalty(
                                            p.level(),
                                            p.infraction(),
                                            p.exchange(),
                                            null,
                                            p.clockRemainingMs(),
                                            p.meta()))
                                    .toList()))),
            current.getAssignments(),
            current.getClockActions(),
            current.getSenshuRecords().stream()
                .map(r ->
                    new StoredSenshuRecord(
                        r.side(),
                        r.kind(),
                        r.reason(),
                        r.exchange(),
                        null,
                        r.clockRemainingMs(),
                        r.meta()))
                .toList(),
            current.getAmendments(),
            current.getResults(),
            current.getStatusChanges(),
            CREATED,
            MODIFIED);

    KumiteGame read = KumiteGameDocumentMapper.toDomain(shapeFive, TestGames.rules());

    assertThat(read.state()).isEqualTo(game.state());
    assertThat(
            read.recordScore(
                    new NewScore(
                        "s1", Side.AKA, ScoreLevel.IPPON, ScoreSource.VIDEO_REVIEW, null, 109_000L),
                    SK,
                    TestClock.NOW)
                .replayed())
        .isTrue();
    assertThat(
            read.recordPenalty(
                    new NewPenalty(
                        "p1", Side.AO, PenaltyLevel.HANSOKU, Infraction.JOGAI, null, 109_000L),
                    SK,
                    TestClock.NOW)
                .replayed())
        .isTrue();
    assertThat(
            read.recordSenshu(
                    new NewSenshuRecord(
                        "k1", Side.AKA, SenshuRecordKind.AWARDED, "video review", null, 109_000L),
                    SK,
                    TestClock.NOW)
                .replayed())
        .isTrue();
    assertThatThrownBy(() ->
            read.recordScore(
                new NewScore(
                    "s1", Side.AKA, ScoreLevel.IPPON, ScoreSource.VIDEO_REVIEW, 1, 109_000L),
                SK,
                TestClock.NOW))
        .isInstanceOf(ActionRefusedException.class);
    KumiteGameDocument saved = KumiteGameDocumentMapper.toDocument(read, shapeFive);
    assertThat(saved.getSchemaVersion()).isEqualTo(KumiteGameDocument.CURRENT_SCHEMA_VERSION);
    assertThat(saved.getSides().get("AKA").scores().getFirst().exchangeNamed()).isFalse();
    assertThat(saved.getSides().get("AO").penalties().getFirst().exchangeNamed()).isFalse();
    assertThat(saved.getSenshuRecords().getFirst().exchangeNamed()).isFalse();
  }

  /** Shape 3 stored no suggestion on a result: it reads with none. */
  @Test
  void shapeThreeResult_readsWithNoSuggestion() {
    KumiteGameDocument current = stored(everyEntry());
    KumiteGameDocument.StoredResult result = current.getResults().getFirst();
    KumiteGameDocument shapeThree =
        new KumiteGameDocument(
            current.getId(),
            current.getVersion(),
            3,
            current.getFormat(),
            current.isMedalGame(),
            current.isExtraGame(),
            current.getSettings(),
            current.getSides(),
            current.getAssignments(),
            current.getClockActions(),
            current.getSenshuRecords(),
            current.getAmendments(),
            List.of(
                new KumiteGameDocument.StoredResult(
                    result.outcome(),
                    result.winner(),
                    result.reason(),
                    result.reasonDetail(),
                    result.bothDisqualified(),
                    null,
                    result.meta())),
            current.getStatusChanges(),
            CREATED,
            MODIFIED);

    KumiteGame read = KumiteGameDocumentMapper.toDomain(shapeThree, TestGames.rules());

    assertThat(read.results().getFirst().suggested()).isNull();
    assertThat(read.results().getFirst().reason()).isEqualTo(ResultReason.KIKEN);
  }

  /** A list stored as absent, such as one added to the shape later, reads as empty: shapes 1, 2. */
  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void absentLists_readAsEmpty(int schemaVersion) {
    KumiteGameDocument current = KumiteGameDocumentMapper.toNewDocument(TestGames.game());
    KumiteGameDocument sparse =
        new KumiteGameDocument(
            current.getId(),
            1,
            schemaVersion,
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
            null,
            CREATED,
            MODIFIED);

    KumiteGame read = KumiteGameDocumentMapper.toDomain(sparse, TestGames.rules());

    assertThat(read.scores(Side.AKA)).isEmpty();
    assertThat(read.results()).isEqualTo(List.of());
    assertThat(read.senshuRecords()).isEmpty();
    assertThat(read.statusChanges()).isEmpty();
    assertThat(read.state().side(Side.AO).athleteId()).isEqualTo(TestGames.AO_ATHLETE);
  }
}
