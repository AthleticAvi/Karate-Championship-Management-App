package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.management.config.GameProperties;
import com.management.dto.KumiteGameRequestDTO;
import com.management.dto.PlayerDTO;
import com.management.exceptions.InvalidGameRequestException;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.services.GameHelperService;
import com.management.services.KumiteGameService;
import com.management.testsupport.FakeRepositories;
import com.management.testsupport.InMemoryMongo;
import com.management.testsupport.TestGameProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
  private GameHelperService helper;
  private KumiteGameService service;

  @BeforeEach
  void setUp() {
    storage = new InMemoryMongo();

    helper = mock(GameHelperService.class);
    when(helper.createNewPlayer(any()))
        .thenAnswer(call -> storage.save(new Player("Created Fighter")));

    service = serviceWith(TestGameProperties.standard(), helper);
  }

  private KumiteGameService serviceWith(GameProperties properties, GameHelperService helper) {
    return new KumiteGameService(FakeRepositories.kumiteGames(storage), properties, helper);
  }

  /**
   * The substitution #42 exists for: a test supplies its own {@link GameProperties} and that
   * configuration actually takes effect. Under the old {@code new GameConfig()} field this was
   * impossible — a mock never reached the service, and every test silently ran against the real
   * file.
   */
  @Test
  void createKumiteGame_withNoDuration_usesTheInjectedDefault() {
    GameProperties fiveMinutes =
        new GameProperties(Duration.ofMinutes(5), List.of(Duration.ofMinutes(5)));
    KumiteGameService customised = serviceWith(fiveMinutes, helper);

    KumiteGame created =
        customised.createKumiteGame(
            request(new PlayerDTO("Kenji", "RED"), new PlayerDTO("Sato", "BLUE")));

    assertThat(created.getGameDuration()).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void createKumiteGame_withDurationOutsideTheConfiguredSet_fallsBackToTheDefault() {
    KumiteGameRequestDTO oddDuration =
        new KumiteGameRequestDTO(
            Map.of(
                "red", new PlayerDTO("Kenji", "RED"),
                "blue", new PlayerDTO("Sato", "BLUE")),
            List.of("Referee One"),
            100);

    KumiteGame created = service.createKumiteGame(oddDuration);

    assertThat(created.getGameDuration())
        .isEqualTo(TestGameProperties.standard().defaultDuration());
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
    Map<String, PlayerDTO> byKey = new java.util.LinkedHashMap<>();
    for (int i = 0; i < fighters.length; i++) {
      byKey.put("fighter" + i, fighters[i]);
    }
    return new KumiteGameRequestDTO(byKey, List.of("Referee One"), null);
  }
}
