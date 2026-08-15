package com.management.kumitegametests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.management.kumitegame.KumiteGameStarter;
import com.management.models.KumiteGame;
import com.management.models.Referee;
import com.management.repositories.KumiteGameRepository;
import com.management.testsupport.KumiteGameBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = KumiteGameStarter.class)
@Testcontainers
class RefereePersistenceIT {

  @Container @ServiceConnection
  static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7");

  @Autowired private KumiteGameRepository kumiteGameRepository;

  @Test
  void shouldPersistAndReloadReferees() {
    KumiteGame game =
        KumiteGameBuilder.newGame()
            .refereedBy(new Referee("Referee 1"), new Referee("Referee 2"))
            .build();

    KumiteGame savedGame = kumiteGameRepository.save(game);

    assertNotNull(savedGame.getId());

    KumiteGame reloadedGame = kumiteGameRepository.findById(savedGame.getId()).orElseThrow();

    assertEquals(2, reloadedGame.getReferees().size());
    assertEquals("Referee 1", reloadedGame.getReferees().get(0).name());
    assertEquals("Referee 2", reloadedGame.getReferees().get(1).name());
  }
}
