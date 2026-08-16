package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.models.KumiteGame;
import com.management.models.Referee;
import com.management.repositories.KumiteGameRepository;
import com.management.testsupport.IntegrationTestBase;
import com.management.testsupport.KumiteGameBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Referees survive a real save and reload through MongoDB. */
class RefereePersistenceIT extends IntegrationTestBase {

  @Autowired private KumiteGameRepository kumiteGameRepository;

  @Test
  void referees_survivePersistenceAndReload() {
    KumiteGame game =
        KumiteGameBuilder.newGame()
            .refereedBy(new Referee("Referee 1"), new Referee("Referee 2"))
            .build();

    KumiteGame saved = kumiteGameRepository.save(game);
    assertThat(saved.getId()).isNotNull();

    KumiteGame reloaded = kumiteGameRepository.findById(saved.getId()).orElseThrow();

    assertThat(reloaded.getReferees()).hasSize(2);
    assertThat(reloaded.getReferees().get(0).name()).isEqualTo("Referee 1");
    assertThat(reloaded.getReferees().get(1).name()).isEqualTo("Referee 2");
  }

  /**
   * The database is empty at the start of a test even though another test just filled it.
   *
   * <p>This used to assert {@code count() == 0} and explain itself with "the previous test saved a
   * game" — which depended on that test having run first. JUnit's default order is deterministic
   * but not alphabetical and not declaration order, so under the order that actually runs, this one
   * went first and passed against a database no test had touched. It would have kept passing if
   * {@code dropEveryCollection} had stopped working entirely.
   *
   * <p>It now writes the state it needs and proves it is gone, so it depends on nothing but itself.
   * The isolation rule is stated in {@code workflow/patterns/testing-integration.md}: clean before,
   * not after.
   */
  @Test
  void eachTestStartsWithAnEmptyDatabase() {
    assertThat(kumiteGameRepository.count())
        .as("whatever ran before this test, its data is gone")
        .isZero();

    kumiteGameRepository.save(KumiteGameBuilder.newGame().build());

    assertThat(kumiteGameRepository.count())
        .as("precondition: this test really did leave a document behind for the next one")
        .isEqualTo(1);
  }
}
