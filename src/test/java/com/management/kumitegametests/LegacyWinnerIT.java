package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.dto.KumiteGameResponse;
import com.management.enums.PlayerColor;
import com.management.models.KumiteGame;
import com.management.testsupport.IntegrationTestBase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * A match whose {@code winner} is in the legacy sentence form is still readable, through the
 * mapping layer and over HTTP.
 *
 * <p><strong>Why this needs a real database.</strong> #34 changed {@code winner} from a display
 * sentence to a {@link PlayerColor}, and the pull request claimed no migration was needed because
 * nothing had ever read the field. The field is read — by the document mapper, on every load.
 * Spring Data converts a stored string to an enum with {@code Enum.valueOf}, so such a document
 * failed with {@code No enum constant ...PlayerColor.Pending game ending} and took {@code GET
 * /api/kumitegame/&#123;id&#125;} and every scoring endpoint down with it, permanently, for that
 * match.
 *
 * <p>No unit test could have caught it and neither could the rest of this suite: every other test
 * writes its fixtures through the current mapping, so the legacy form never appears. These tests
 * insert raw BSON, which is the only way to reproduce the failure.
 *
 * <p><strong>The fixture carries the legacy winner in an otherwise current document</strong>, and
 * that is deliberate. #49 replaced the embedded fighter copies with id references, and the
 * migration decision recorded in {@code CLAUDE.md} is that documents predating it are not supported
 * — so a genuine pre-#34 document, which also carries embedded fighters, no longer loads for a
 * reason that has nothing to do with the winner field. What {@link
 * com.management.models.converters.LegacyWinnerConverter} does is a property-level conversion, and
 * that is exactly what these tests exercise. Whether the converter still earns its place is a
 * question for the engineer, raised in the pull request rather than answered here.
 */
class LegacyWinnerIT extends IntegrationTestBase {

  @Autowired private TestRestTemplate rest;

  @Test
  void readMatch_whenTheWinnerIsThePlaceholder_isUndecided() {
    insertLegacyMatch("legacy-placeholder", "Pending game ending");

    KumiteGame loaded = mongoTemplate.findById("legacy-placeholder", KumiteGame.class);

    assertThat(loaded).isNotNull();
    assertThat(loaded.getWinner()).as("the placeholder meant undecided").isNull();
  }

  @Test
  void readMatch_whenTheWinnerIsSentenceForm_isThatColour() {
    insertLegacyMatch("legacy-decided", "RED player: Kenji");

    KumiteGame loaded = mongoTemplate.findById("legacy-decided", KumiteGame.class);

    assertThat(loaded).isNotNull();
    assertThat(loaded.getWinner()).isEqualTo(PlayerColor.RED);
  }

  /** The failure as a caller met it: a 500 on a match that had done nothing wrong. */
  @Test
  void getMatch_whenStoredBeforeTheChange_returns200() {
    insertLegacyMatch("legacy-over-http", "BLUE player: Sato");

    ResponseEntity<KumiteGameResponse> response =
        rest.getForEntity("/api/kumitegame/legacy-over-http", KumiteGameResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    KumiteGameResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.winner()).isEqualTo(PlayerColor.BLUE);
    assertThat(body.red().name()).isEqualTo("Kenji");
    assertThat(body.blue().name()).isEqualTo("Sato");
  }

  /** Reading a legacy document and saving it writes the current form back. */
  @Test
  void saveMatch_whenReadFromTheLegacyForm_storesTheColourName() {
    insertLegacyMatch("legacy-rewritten", "RED player: Kenji");

    KumiteGame loaded = mongoTemplate.findById("legacy-rewritten", KumiteGame.class);
    assertThat(loaded).isNotNull();
    mongoTemplate.save(loaded);

    Document stored =
        mongoTemplate
            .getCollection("kumiteGame")
            .find(new Document("_id", "legacy-rewritten"))
            .first();

    assertThat(stored).isNotNull();
    assertThat(stored.getString("winner"))
        .as("a document migrates itself on its next write")
        .isEqualTo("RED");
  }

  /**
   * Inserts a match whose {@code winner} is stored in the pre-#34 sentence form.
   *
   * <p>Written as raw BSON on purpose. Building it through {@code KumiteGameBuilder} and saving it
   * would produce the current form and prove nothing. Everything other than {@code winner} is the
   * current form — see the class comment for why.
   */
  private void insertLegacyMatch(String id, String legacyWinner) {
    insertFighter(id + "-red", "Kenji", 3, 0);
    insertFighter(id + "-blue", "Sato", 0, 1);

    Document match =
        new Document("_id", id)
            .append("gameState", "FINISHED")
            .append("winner", legacyWinner)
            .append("playerIds", new Document("RED", id + "-red").append("BLUE", id + "-blue"))
            .append("referees", java.util.List.of(new Document("name", "Test Referee")))
            // ISO-8601 strings, the form pinned by RepresentationCharacterizationTest.
            .append("remainingTime", "PT0S")
            .append("gameDuration", "PT2M")
            // A document that has been written at least once. Omitting the version entirely makes
            // Spring Data treat the load as a new entity and turn the next save into an insert,
            // which collides on the identifier -- see the migration note in CLAUDE.md.
            .append("version", 1L);

    mongoTemplate.getCollection("kumiteGame").insertOne(match);
  }

  private void insertFighter(String id, String name, int points, int fouls) {
    mongoTemplate
        .getCollection("player")
        .insertOne(
            new Document("_id", id)
                .append("name", name)
                .append("points", new Document("numOfPoints", points))
                .append("fouls", new Document("numOfFouls", fouls))
                .append("version", 1L));
  }
}
