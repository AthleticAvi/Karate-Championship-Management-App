package com.management.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.testsupport.KumiteGameBuilder;
import com.management.testsupport.PlayerBuilder;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Equality, hashing and printing on the domain models (#60).
 *
 * <p>Entities with a persistent id — {@link Player}, {@link KumiteGame} — compare on that id alone:
 * two objects with the same id are the same stored record, even if one is stale. An unsaved entity
 * has no id and is equal only to itself. Value objects — {@link Points}, {@link Foul} — compare by
 * content; {@link Referee} is a record and gets all three methods from the language.
 */
class DomainModelEqualityTest {

  @Test
  void players_withTheSameId_areEqualEvenWhenStale() {
    Player fresh = PlayerBuilder.newPlayer().alreadyPersistedAs("p-1").named("Kenji").build();
    Player stale = PlayerBuilder.newPlayer().alreadyPersistedAs("p-1").named("Kenji (old)").build();

    assertThat(fresh).isEqualTo(stale).hasSameHashCodeAs(stale);
  }

  @Test
  void players_withDifferentIds_areNotEqual() {
    Player one = PlayerBuilder.newPlayer().alreadyPersistedAs("p-1").build();
    Player other = PlayerBuilder.newPlayer().alreadyPersistedAs("p-2").build();

    assertThat(one).isNotEqualTo(other);
  }

  @Test
  void unsavedPlayer_isEqualOnlyToItself() {
    Player unsaved = PlayerBuilder.newPlayer().build();
    Player alsoUnsaved = PlayerBuilder.newPlayer().build();

    assertThat(unsaved).isEqualTo(unsaved).isNotEqualTo(alsoUnsaved);
  }

  @Test
  void players_inSet_deduplicateByIdentifier() {
    Set<Player> fighters = new HashSet<>();
    fighters.add(PlayerBuilder.newPlayer().alreadyPersistedAs("p-1").named("Kenji").build());
    fighters.add(PlayerBuilder.newPlayer().alreadyPersistedAs("p-1").named("Kenji again").build());
    fighters.add(PlayerBuilder.newPlayer().alreadyPersistedAs("p-2").named("Sato").build());

    assertThat(fighters).hasSize(2);
  }

  @Test
  void games_withTheSameId_areEqual() {
    KumiteGame one = KumiteGameBuilder.newGame().alreadyPersistedAs("g-1").build();
    KumiteGame same = KumiteGameBuilder.newGame().alreadyPersistedAs("g-1").finished().build();
    KumiteGame other = KumiteGameBuilder.newGame().alreadyPersistedAs("g-2").build();

    assertThat(one).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(other);
  }

  @Test
  void unsavedGame_isEqualOnlyToItself() {
    KumiteGame unsaved = KumiteGameBuilder.newGame().build();

    assertThat(unsaved).isEqualTo(unsaved).isNotEqualTo(KumiteGameBuilder.newGame().build());
  }

  @Test
  void pointsAndFouls_compareByValue() {
    Points three = new Points();
    three.setNumOfPoints(3);
    Points alsoThree = new Points();
    alsoThree.setNumOfPoints(3);

    Foul two = new Foul();
    two.setNumOfFouls(2);
    Foul alsoTwo = new Foul();
    alsoTwo.setNumOfFouls(2);

    assertThat(three).isEqualTo(alsoThree).hasSameHashCodeAs(alsoThree);
    assertThat(two).isEqualTo(alsoTwo).hasSameHashCodeAs(alsoTwo);
    alsoTwo.addFoul();
    assertThat(two).isNotEqualTo(alsoTwo);
  }

  @Test
  void toString_showsTheFieldsWorthDebuggingWith() {
    Player player = PlayerBuilder.newPlayer().alreadyPersistedAs("p-1").named("Kenji").build();
    KumiteGame game = KumiteGameBuilder.newGame().alreadyPersistedAs("g-1").build();

    assertThat(player.toString()).contains("p-1", "Kenji");
    assertThat(game.toString()).contains("g-1", "QUEUED");
  }
}
