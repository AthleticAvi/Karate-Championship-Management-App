package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.enums.PlayerColor;
import com.management.models.ClockAdjustment;
import com.management.models.KumiteGame;
import com.management.models.RefereeOverride;
import com.management.repositories.KumiteGameRepository;
import com.management.testsupport.IntegrationTestBase;
import com.management.testsupport.KumiteGameBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Stored times name one point in time, whichever instance wrote or reads them.
 *
 * <p>Asserted on the raw document as well as the reload: a conversion that shifted a value on write
 * and shifted it back on read would pass a round trip alone.
 */
class MatchTimePersistenceIT extends IntegrationTestBase {

  private static final String COLLECTION = "kumiteGame";
  private static final Instant STARTED = Instant.parse("2026-03-29T00:59:30.123Z");
  private static final Instant ADJUSTED = Instant.parse("2026-03-29T01:00:30.456Z");
  private static final Instant DECIDED = Instant.parse("2026-03-29T01:01:30.789Z");
  private static final Duration FULL_MATCH = Duration.ofSeconds(120);

  @Autowired private KumiteGameRepository kumiteGameRepository;

  @Test
  void startTime_survivesSaveAndLoadExactly() {
    KumiteGame saved = kumiteGameRepository.save(runningMatch());

    assertThat(storedDocument(saved).getDate("startTime")).isEqualTo(Date.from(STARTED));
    assertThat(reload(saved).getStartTime()).isEqualTo(STARTED);
  }

  @Test
  void clockAdjustmentTime_survivesSaveAndLoadExactly() {
    KumiteGame game = runningMatch();
    game.addTime(new ClockAdjustment(Duration.ofSeconds(10), ADJUSTED, "Timekeeper"));

    KumiteGame saved = kumiteGameRepository.save(game);

    List<Document> stored = storedDocument(saved).getList("clockAdjustments", Document.class);
    assertThat(stored.get(0).getDate("addedAt")).isEqualTo(Date.from(ADJUSTED));
    assertThat(reload(saved).getClockAdjustments().get(0).addedAt()).isEqualTo(ADJUSTED);
  }

  @Test
  void overrideTime_survivesSaveAndLoadExactly() {
    KumiteGame game = runningMatch();
    game.applyOverride(new RefereeOverride(PlayerColor.RED, "Reason", "Referee", DECIDED, null));

    KumiteGame saved = kumiteGameRepository.save(game);

    Document stored = storedDocument(saved).get("refereeOverride", Document.class);
    assertThat(stored.getDate("decidedAt")).isEqualTo(Date.from(DECIDED));
    assertThat(reload(saved).getRefereeOverride().orElseThrow().decidedAt()).isEqualTo(DECIDED);
  }

  /** Two instances in different zones, or one moved between them, read the same instant. */
  @Test
  void startTime_writtenInOneZoneAndReadInAnother_isTheSameInstant() {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jerusalem"));
      KumiteGame saved = kumiteGameRepository.save(runningMatch());

      TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

      assertThat(reload(saved).getStartTime()).isEqualTo(STARTED);
    } finally {
      TimeZone.setDefault(original);
    }
  }

  private static KumiteGame runningMatch() {
    return KumiteGameBuilder.newGame().runningSince(STARTED, FULL_MATCH).build();
  }

  private KumiteGame reload(KumiteGame saved) {
    return kumiteGameRepository.findById(saved.getId()).orElseThrow();
  }

  private Document storedDocument(KumiteGame saved) {
    return mongoTemplate.findById(saved.getId(), Document.class, COLLECTION);
  }
}
