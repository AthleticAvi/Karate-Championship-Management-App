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

  @Test
  void eachTestStartsWithAnEmptyDatabase() {
    assertThat(kumiteGameRepository.count())
        .as("the previous test saved a game; cleaning happens before each test, not after")
        .isZero();
  }
}
