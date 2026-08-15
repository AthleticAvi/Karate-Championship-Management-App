package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.enums.GameState;
import com.management.enums.PlayerColor;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.testsupport.InMemoryMongo;
import com.management.testsupport.KumiteGameBuilder;
import com.management.testsupport.PlayerBuilder;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves the persistence double loses what a real database loses.
 *
 * <p>These tests are about the double itself, not about the domain. They exist because the previous
 * double returned the same in-memory instance it was handed, which made the whole suite incapable
 * of failing for the most important class of defect.
 */
class InMemoryMongoFidelityTest {

  private InMemoryMongo storage;

  @BeforeEach
  void setUp() {
    storage = new InMemoryMongo();
  }

  @Test
  void findById_afterSave_returnsDifferentInstance() {
    KumiteGame saved = storage.save(KumiteGameBuilder.newGame().build());

    KumiteGame reloaded = storage.findById(KumiteGame.class, saved.getId()).orElseThrow();

    assertThat(reloaded).isNotSameAs(saved);
    assertThat(reloaded.getPlayersMap().get(PlayerColor.RED))
        .as("nested objects must be distinct too, not shared references")
        .isNotSameAs(saved.getPlayersMap().get(PlayerColor.RED));
  }

  @Test
  void findById_afterSave_dropsTheTransientTimer() {
    KumiteGame withTimer = KumiteGameBuilder.newGame().withLiveTimer().build();
    assertThat(withTimer.getTimer()).as("precondition: the timer is set before saving").isNotNull();

    KumiteGame saved = storage.save(withTimer);
    KumiteGame reloaded = storage.findById(KumiteGame.class, saved.getId()).orElseThrow();

    assertThat(reloaded.getTimer())
        .as("@Transient GameTimer is never persisted, so a reloaded game has none")
        .isNull();
  }

  @Test
  void save_assignsAnIdentifier_whenTheEntityHasNone() {
    KumiteGame unsaved = KumiteGameBuilder.newGame().build();
    assertThat(unsaved.getId()).isNull();

    assertThat(storage.save(unsaved).getId()).isNotNull();
  }

  @Test
  void findById_afterSave_preservesPersistedState() {
    KumiteGame running =
        KumiteGameBuilder.newGame().runningWithRemaining(Duration.ofSeconds(87)).build();

    KumiteGame saved = storage.save(running);
    KumiteGame reloaded = storage.findById(KumiteGame.class, saved.getId()).orElseThrow();

    assertThat(reloaded.getGameState()).isEqualTo(GameState.RUNNING);
    assertThat(reloaded.getRemainingTime()).isEqualTo(Duration.ofSeconds(87));
    assertThat(reloaded.getStartTime()).isEqualTo(running.getStartTime());
    assertThat(reloaded.getPlayersMap()).containsOnlyKeys(PlayerColor.RED, PlayerColor.BLUE);
    assertThat(reloaded.getReferees()).hasSize(1);
  }

  @Test
  void mutatingReloadedEntity_doesNotAffectStoredState() {
    KumiteGame saved = storage.save(KumiteGameBuilder.newGame().build());

    KumiteGame first = storage.findById(KumiteGame.class, saved.getId()).orElseThrow();
    first.setWinner("RED player: tampered");

    KumiteGame second = storage.findById(KumiteGame.class, saved.getId()).orElseThrow();
    assertThat(second.getWinner()).isNotEqualTo("RED player: tampered");
  }

  @Test
  void findById_forAnUnknownIdentifier_isEmpty() {
    assertThat(storage.findById(KumiteGame.class, "does-not-exist")).isEmpty();
  }

  @Test
  void delete_removesTheEntity() {
    Player saved = storage.save(PlayerBuilder.newPlayer().named("Deleted Fighter").build());
    assertThat(storage.count(Player.class)).isEqualTo(1);

    storage.delete(saved);

    assertThat(storage.count(Player.class)).isZero();
    assertThat(storage.findById(Player.class, saved.getId())).isEmpty();
  }

  @Test
  void playersAndGames_areStoredSeparately() {
    storage.save(KumiteGameBuilder.newGame().build());
    storage.save(PlayerBuilder.newPlayer().build());

    assertThat(storage.count(KumiteGame.class)).isEqualTo(1);
    assertThat(storage.count(Player.class)).isEqualTo(1);
  }

  @Test
  void clear_discardsEverything() {
    storage.save(KumiteGameBuilder.newGame().build());
    storage.clear();

    assertThat(storage.count(KumiteGame.class)).isZero();
  }

  @Test
  void savedPlayerScore_survivesTheRoundTrip() {
    Player scorer =
        PlayerBuilder.newPlayer()
            .named("Scoring Fighter")
            .scoring(com.management.enums.PointsType.IPPON)
            .withFouls(2)
            .build();

    Player saved = storage.save(scorer);
    Optional<Player> reloaded = storage.findById(Player.class, saved.getId());

    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getPoints().getNumOfPoints()).isEqualTo(3);
    assertThat(reloaded.get().getFouls().getNumOfFouls()).isEqualTo(2);
  }
}
