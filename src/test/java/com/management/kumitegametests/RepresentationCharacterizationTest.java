package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.testsupport.InMemoryMongo;
import com.management.testsupport.KumiteGameBuilder;
import com.management.testsupport.PlayerBuilder;
import java.time.Duration;
import java.util.Date;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/**
 * Pins how the domain is represented on disk.
 *
 * <p><strong>This is a characterization test, not a specification.</strong> It records what the
 * current stack actually produces so that a framework upgrade cannot change it silently. If one of
 * these fails after an upgrade, that is the point: the failure is the finding, and the change is
 * either intended and re-pinned here, or it is a defect.
 *
 * <p>Written before the Spring Boot 4 upgrade of Epic #89 specifically so the "before" of #93's
 * before-and-after comparison is executable rather than a screenshot. The MongoDB driver crosses a
 * major version in that upgrade, and it owns this representation.
 *
 * <p><strong>Audit result (#93): nothing moved.</strong> Every assertion below passed unchanged
 * across Spring Boot 3.2.3 → 4.1.0, which carried Jackson 2 → 3 and MongoDB driver 4.11 → 5.8.
 * Durations are still ISO-8601 strings, enums are still names, {@code startTime} is still a BSON
 * date, and the type hint is still written. The same holds for the JSON side in {@link
 * KumiteGameControllerSliceTest}. No re-pinning was needed, and no migration of existing documents
 * is required.
 *
 * <p>That is a result, not an absence of one — it is only credible because these assertions existed
 * <em>before</em> the upgrade rather than being written afterwards to match whatever came out.
 *
 * <p>The JSON half of the contract is pinned in {@link KumiteGameControllerSliceTest}, at the HTTP
 * boundary, because that is where the application's real configured mapper runs. Constructing a
 * bare {@code ObjectMapper} here would pin a format the application never actually produces — it
 * lacks the date handling Spring Boot configures.
 *
 * <p>Assertions are on <em>shape and format</em>, never on a value that depends on the machine. The
 * instant stored for {@code startTime} is resolved through the default time-zone, so pinning the
 * literal value would fail on a colleague's laptop rather than on a real change. What is pinned is
 * that it is stored as a BSON date at all — if it became a string, this test would say so. See #93.
 */
class RepresentationCharacterizationTest {

  private static Document asStoredDocument(Object entity) {
    return new InMemoryMongo().writeForInspection(entity);
  }

  @Test
  void kumiteGame_storedForm_isPinned() {
    KumiteGame game =
        KumiteGameBuilder.newGame().runningWithRemaining(Duration.ofSeconds(87)).build();

    Document stored = asStoredDocument(game);

    // Durations are stored as ISO-8601 strings.
    assertThat(stored.get("remainingTime")).isEqualTo("PT1M27S");
    assertThat(stored.get("gameDuration")).isEqualTo("PT2M");
    // Enums are stored as their names.
    assertThat(stored.get("gameState")).isEqualTo("RUNNING");
    // LocalDateTime is stored as a BSON date.
    assertThat(stored.get("startTime")).isInstanceOf(Date.class);
    // The transient timer is never written.
    assertThat(stored).doesNotContainKey("timer");
    // Spring Data writes a type hint alongside the data.
    assertThat(stored.get("_class")).isEqualTo("com.management.models.KumiteGame");
    // The optimistic-lock version is part of the stored form (#48). Written as 0 pre-save; the
    // real increment happens in MongoTemplate, which this double deliberately does not model.
    assertThat(stored.get("version")).isEqualTo(0L);

    // Fighters are stored as id references keyed by colour, never as embedded copies (#49).
    Document players = (Document) stored.get("playerIds");
    assertThat(players).containsKeys(PlayerColor.RED.name(), PlayerColor.BLUE.name());
    assertThat(players.get(PlayerColor.RED.name())).isInstanceOf(String.class);
  }

  @Test
  void player_storedForm_isPinned() {
    Player player =
        PlayerBuilder.newPlayer()
            .named("Red Fighter")
            .scoring(PointsType.IPPON)
            .withFouls(1)
            .build();

    Document stored = asStoredDocument(player);

    assertThat(stored.get("name")).isEqualTo("Red Fighter");
    assertThat(((Document) stored.get("points")).get("numOfPoints")).isEqualTo(3);
    assertThat(((Document) stored.get("fouls")).get("numOfFouls")).isEqualTo(1);
  }
}
