package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.management.dto.KumiteGameRequestDTO;
import com.management.dto.PlayerDTO;
import com.management.exceptions.InvalidGameRequestException;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.services.GameHelperService;
import com.management.services.KumiteGameService;
import com.management.testsupport.FakeRepositories;
import com.management.testsupport.InMemoryMongo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The fighter-count rule at creation (#28): a match is exactly one RED and one BLUE, checked before
 * anything is written.
 *
 * <p>The duplicate-colour case, including the proof that nothing reaches the database, is covered
 * end-to-end in {@code KumiteGameFlowIT}. Here the cheaper counts — one fighter, three fighters —
 * are pinned at the service level.
 */
class KumiteGameCreationTest {

  private InMemoryMongo storage;
  private KumiteGameService service;

  @BeforeEach
  void setUp() {
    storage = new InMemoryMongo();

    GameHelperService helper = mock(GameHelperService.class);
    when(helper.createNewPlayer(any()))
        .thenAnswer(call -> storage.save(new Player("Created Fighter")));

    service = new KumiteGameService();
    ReflectionTestUtils.setField(
        service, "kumiteGameRepository", FakeRepositories.kumiteGames(storage));
    ReflectionTestUtils.setField(service, "gameHelperService", helper);
  }

  @Test
  void createKumiteGame_withOneRedAndOneBlue_isAccepted() {
    KumiteGame created =
        service.createKumiteGame(
            request(new PlayerDTO("Kenji", "RED"), new PlayerDTO("Sato", "BLUE")));

    assertThat(created.getId()).isNotNull();
    assertThat(storage.count(KumiteGame.class)).isEqualTo(1);
  }

  @Test
  void createKumiteGame_withSingleFighter_isRejectedAndWritesNothing() {
    KumiteGameRequestDTO onlyRed = request(new PlayerDTO("Kenji", "RED"));

    assertThatThrownBy(() -> service.createKumiteGame(onlyRed))
        .isInstanceOf(InvalidGameRequestException.class)
        .hasMessageContaining("RED");

    assertThat(storage.count(KumiteGame.class)).isZero();
    assertThat(storage.count(Player.class)).isZero();
  }

  @Test
  void createKumiteGame_withThreeFighters_isRejectedNamingTheDuplicatedColour() {
    KumiteGameRequestDTO threeFighters =
        request(
            new PlayerDTO("Kenji", "RED"),
            new PlayerDTO("Sato", "BLUE"),
            new PlayerDTO("Hiro", "RED"));

    assertThatThrownBy(() -> service.createKumiteGame(threeFighters))
        .isInstanceOf(InvalidGameRequestException.class)
        .hasMessageContaining("RED");

    assertThat(storage.count(KumiteGame.class)).isZero();
    assertThat(storage.count(Player.class)).isZero();
  }

  private static KumiteGameRequestDTO request(PlayerDTO... fighters) {
    KumiteGameRequestDTO dto = new KumiteGameRequestDTO();
    Map<String, PlayerDTO> byKey = new java.util.LinkedHashMap<>();
    for (int i = 0; i < fighters.length; i++) {
      byKey.put("fighter" + i, fighters[i]);
    }
    dto.setPlayersMap(byKey);
    dto.setRefereeList(List.of("Referee One"));
    return dto;
  }
}
