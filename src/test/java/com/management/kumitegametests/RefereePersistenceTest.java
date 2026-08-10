package com.management.kumitegametests;

import com.management.enums.PlayerColor;
import com.management.kumitegame.KumiteGameStarter;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.models.Referee;
import com.management.repositories.KumiteGameRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = KumiteGameStarter.class)
class RefereePersistenceTest {

    @Autowired
    private KumiteGameRepository kumiteGameRepository;

    @Test
    void shouldPersistAndReloadReferees() {
        Map<PlayerColor, Player> playersMap = new EnumMap<>(PlayerColor.class);
        playersMap.put(PlayerColor.RED, new Player("Player 1"));
        playersMap.put(PlayerColor.BLUE, new Player("Player 2"));

        List<Referee> referees = List.of(
                new Referee("Referee 1"),
                new Referee("Referee 2")
        );

        KumiteGame game = new KumiteGame(
                playersMap,
                referees,
                Duration.ofSeconds(120)
        );

        KumiteGame savedGame = kumiteGameRepository.save(game);

        assertNotNull(savedGame.getId());

        KumiteGame reloadedGame = kumiteGameRepository
                .findById(savedGame.getId())
                .orElseThrow();

        assertEquals(2, reloadedGame.getReferees().size());
        assertEquals("Referee 1", reloadedGame.getReferees().get(0).name());
        assertEquals("Referee 2", reloadedGame.getReferees().get(1).name());

        kumiteGameRepository.deleteById(savedGame.getId());
    }
}