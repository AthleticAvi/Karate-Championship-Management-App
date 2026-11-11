package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.athlete.data.AthleteDocumentMapper;
import com.management.kumitegame.data.KumiteGameDocumentMapper;
import com.management.kumitegame.domain.ClockActionKind;
import com.management.kumitegame.domain.KumiteGame;
import com.management.kumitegame.domain.NewClockAction;
import com.management.kumitegame.domain.NewScore;
import com.management.kumitegame.domain.ScoreLevel;
import com.management.kumitegame.domain.ScoreSource;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.Timer;
import com.management.testsupport.InMemoryMongo;
import com.management.testsupport.TestClock;
import com.management.testsupport.TestGames;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/**
 * Pins how a game and an athlete are represented on disk.
 *
 * <p><strong>This is a characterization test, not a specification.</strong> It records what the
 * current stack actually produces so that a framework upgrade cannot change it silently. If one of
 * these fails after an upgrade, that is the point: the failure is the finding, and the change is
 * either intended and re-pinned here, or it is a defect.
 *
 * <p>Re-pinned when the game became one document of ordered entries, apart from its domain type.
 * The JSON half of the contract is pinned in {@code KumiteGameControllerSliceTest}, at the HTTP
 * boundary, where the application's configured mapper runs.
 *
 * <p>Assertions are on <em>shape and format</em>. Instants are pinned to their literal value as
 * well as their BSON date type: if one became a string, or moved with the default time-zone, this
 * test would say so.
 */
class RepresentationCharacterizationTest {

  private static Document asStoredDocument(Object entity) {
    return new InMemoryMongo().writeForInspection(entity);
  }

  private static KumiteGame scoredGame() {
    KumiteGame game = TestGames.game();
    game.recordClockAction(
        new NewClockAction("start-1", Timer.MAIN, null, ClockActionKind.START, 120_000, null, null),
        TestGames.SCOREKEEPER,
        TestClock.NOW);
    game.recordScore(
        new NewScore("score-1", Side.AKA, ScoreLevel.WAZA_ARI, ScoreSource.JUDGES, null, 118_000L),
        TestGames.SCOREKEEPER,
        TestClock.NOW.plusSeconds(2));
    return game;
  }

  @Test
  void kumiteGame_storedForm_isPinned() {
    Document stored = asStoredDocument(KumiteGameDocumentMapper.toNewDocument(scoredGame()));

    // The id is given before the insert; the version and the schema version are stored.
    assertThat(stored.get("_id")).isEqualTo(TestGames.GAME_ID);
    assertThat(stored.get("version")).isEqualTo(0L);
    assertThat(stored.get("schemaVersion")).isEqualTo(3);
    // Spring Data writes a type hint alongside the data.
    assertThat(stored.get("_class")).isEqualTo("com.management.kumitegame.data.KumiteGameDocument");
    // Enums are stored as their names.
    assertThat(stored.get("format")).isEqualTo("ELIMINATION");
    // Durations are whole milliseconds, and point values a map keyed by level name.
    Document settings = stored.get("settings", new Document());
    assertThat(settings.get("durationMs")).isEqualTo(120_000L);
    assertThat(settings.get("pointValues", Document.class)).containsEntry("WAZA_ARI", 2);
    // Scores sit on their side, keyed by the side's name, each with its meta embedded.
    Document aka = stored.get("sides", Document.class).get("AKA", Document.class);
    Document score = aka.getList("scores", Document.class).getFirst();
    assertThat(score.get("level")).isEqualTo("WAZA_ARI");
    assertThat(score.get("exchange")).isEqualTo(1);
    Document meta = score.get("meta", Document.class);
    // The client's id is stored as entryId: a nested "id" would be mapped to "_id".
    assertThat(meta.get("entryId")).isEqualTo("score-1");
    assertThat(meta).doesNotContainKeys("id", "_id");
    assertThat(meta.get("order")).isEqualTo(5);
    // An Instant is stored as a BSON date.
    assertThat(meta.get("recordedAt")).isEqualTo(Date.from(TestClock.NOW.plusSeconds(2)));
    assertThat(meta.getList("raisedWarnings", String.class)).isEmpty();
    // Nothing derived is stored: no status, clock state or total beside the entries.
    assertThat(stored.keySet())
        .doesNotContain("status", "mainClock", "totals", "currentResult")
        .containsAll(
            List.of(
                "assignments",
                "clockActions",
                "senshuRecords",
                "amendments",
                "results",
                "statusChanges",
                "sides",
                "settings"));
  }

  @Test
  void athlete_storedForm_isPinned() {
    Document stored = asStoredDocument(AthleteDocumentMapper.toNewDocument("Kenji", 61.5));

    assertThat(stored.get("name")).isEqualTo("Kenji");
    assertThat(stored.get("weightKg")).isEqualTo(61.5);
    assertThat(stored.get("schemaVersion")).isEqualTo(1);
    assertThat(stored.get("_class")).isEqualTo("com.management.athlete.data.AthleteDocument");
    // No score or penalty lives on an athlete any more: they are on the game.
    assertThat(stored.keySet()).doesNotContain("points", "fouls", "scores", "penalties");
  }
}
