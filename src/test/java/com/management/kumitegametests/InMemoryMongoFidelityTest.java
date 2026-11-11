package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.athlete.data.AthleteDocument;
import com.management.athlete.data.AthleteDocumentMapper;
import com.management.kumitegame.data.KumiteGameDocument;
import com.management.kumitegame.data.KumiteGameDocumentMapper;
import com.management.kumitegame.domain.ScoreLevel;
import com.management.kumitegame.domain.Side;
import com.management.testsupport.InMemoryMongo;
import com.management.testsupport.TestGames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves the persistence double loses what a real database loses.
 *
 * <p>These tests are about the double itself, not about the domain. They exist because an earlier
 * double returned the same in-memory instance it was handed, which made the whole suite incapable
 * of failing for the most important class of defect.
 */
class InMemoryMongoFidelityTest {

  private InMemoryMongo storage;

  @BeforeEach
  void setUp() {
    storage = new InMemoryMongo();
  }

  private static KumiteGameDocument game() {
    return KumiteGameDocumentMapper.toNewDocument(TestGames.game());
  }

  private static AthleteDocument athlete(String name) {
    return AthleteDocumentMapper.toNewDocument(name, 61.5);
  }

  @Test
  void findById_afterSave_returnsDifferentInstance() {
    KumiteGameDocument saved = storage.save(game());

    KumiteGameDocument reloaded =
        storage.findById(KumiteGameDocument.class, saved.getId()).orElseThrow();

    assertThat(reloaded).isNotSameAs(saved);
    assertThat(reloaded.getAssignments())
        .as("nested structures must be distinct too, not shared references")
        .isNotSameAs(saved.getAssignments());
  }

  @Test
  void save_assignsAnIdentifier_whenTheEntityHasNone() {
    AthleteDocument unsaved = athlete("Kenji");
    assertThat(unsaved.getId()).isNull();

    assertThat(storage.save(unsaved).getId()).isNotNull();
  }

  @Test
  void findById_afterSave_preservesPersistedState() {
    KumiteGameDocument saved = storage.save(game());

    KumiteGameDocument reloaded =
        storage.findById(KumiteGameDocument.class, saved.getId()).orElseThrow();

    assertThat(reloaded).usingRecursiveComparison().isEqualTo(saved);
    assertThat(
            KumiteGameDocumentMapper.toDomain(reloaded, TestGames.rules())
                .settings()
                .pointsFor(ScoreLevel.IPPON))
        .isEqualTo(3);
    assertThat(
            KumiteGameDocumentMapper.toDomain(reloaded, TestGames.rules())
                .state()
                .side(Side.AO)
                .athleteId())
        .isEqualTo(TestGames.AO_ATHLETE);
  }

  @Test
  void findById_forAnUnknownIdentifier_isEmpty() {
    assertThat(storage.findById(KumiteGameDocument.class, "does-not-exist")).isEmpty();
  }

  /**
   * The instance handed to {@code save} is usable afterwards, as it is with a real repository.
   *
   * <p>Deleting through it used to be a silent no-op: {@code save} never wrote the generated
   * identifier back, so {@code delete} found none and removed nothing while reporting success.
   */
  @Test
  void delete_throughTheSavedInstance_removesTheEntity() {
    AthleteDocument fresh = athlete("Kenji");
    storage.save(fresh);

    assertThat(fresh.getId())
        .as("save assigns the identifier to the instance it was given")
        .isNotNull();

    storage.delete(fresh);

    assertThat(storage.count(AthleteDocument.class)).isZero();
  }

  @Test
  void delete_forAnUnsavedEntity_isRejectedRatherThanIgnored() {
    AthleteDocument neverSaved = athlete("Kenji");

    assertThatThrownBy(() -> storage.delete(neverSaved))
        .as("silently succeeding would let a deletion test pass against code that deletes nothing")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no identifier");
  }

  /** A second save of the same instance updates the document, as an upsert on an id does. */
  @Test
  void save_calledTwiceOnTheSameInstance_storesOneDocument() {
    AthleteDocument athlete = athlete("Kenji");

    storage.save(athlete);
    storage.save(athlete);

    assertThat(storage.count(AthleteDocument.class))
        .as("one athlete, saved twice, is one document")
        .isEqualTo(1);
  }

  @Test
  void athletesAndGames_areStoredSeparately() {
    storage.save(game());
    storage.save(athlete("Kenji"));

    assertThat(storage.count(KumiteGameDocument.class)).isEqualTo(1);
    assertThat(storage.count(AthleteDocument.class)).isEqualTo(1);
  }

  @Test
  void clear_discardsEverything() {
    storage.save(game());
    storage.clear();

    assertThat(storage.count(KumiteGameDocument.class)).isZero();
  }
}
