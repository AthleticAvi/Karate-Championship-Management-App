package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.management.config.GameProperties;
import com.management.dto.KumiteGameRequestDTO;
import com.management.dto.PlayerDTO;
import com.management.enums.PlayerColor;
import com.management.exceptions.InvalidGameRequestException;
import com.management.models.GameWithFighters;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.repositories.KumiteGameRepository;
import com.management.repositories.PlayerRepository;
import com.management.services.KumiteGameService;
import com.management.services.MatchStateWriter;
import com.management.services.PlayerService;
import com.management.testsupport.FakeRepositories;
import com.management.testsupport.InMemoryMongo;
import com.management.testsupport.TestGameProperties;
import com.management.testsupport.TestRulesEngine;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Match creation: the fighter-count rule (#28), write ordering and compensation (#51), and duration
 * selection (#42) — all against a real {@link PlayerService} over the round-trip-faithful storage
 * double, because since #53 the player service is a leaf that can simply be constructed.
 *
 * <p>The duplicate-colour case, including the end-to-end proof that nothing reaches a real
 * database, stays in {@code KumiteGameFlowIT}.
 */
class KumiteGameCreationTest {

  private InMemoryMongo storage;
  private PlayerService playerService;
  private KumiteGameService service;

  @BeforeEach
  void setUp() {
    storage = new InMemoryMongo();
    playerService = new PlayerService(FakeRepositories.players(storage));
    service =
        new KumiteGameService(
            FakeRepositories.kumiteGames(storage),
            TestGameProperties.standard(),
            playerService,
            TestRulesEngine.standard(),
            new MatchStateWriter(FakeRepositories.kumiteGames(storage)));
  }

  @Test
  void createKumiteGame_withOneRedAndOneBlue_savesTheMatchAndBothFighters() {
    GameWithFighters created =
        service.createKumiteGame(
            request(new PlayerDTO("Kenji", "RED"), new PlayerDTO("Sato", "BLUE")));

    assertThat(created.game().getId()).isNotNull();
    assertThat(storage.count(KumiteGame.class)).isEqualTo(1);
    assertThat(storage.count(Player.class)).isEqualTo(2);
    assertThat(created.game().getPlayerIds())
        .as("the match references its fighters by id, one per colour")
        .containsOnlyKeys(PlayerColor.RED, PlayerColor.BLUE);
    assertThat(created.fighters().get(PlayerColor.RED).getName())
        .as("the fighters come back with the match, so no second read is needed to answer")
        .isEqualTo("Kenji");
  }

  /**
   * The gap the first cut of #51 left: compensation covered the match save but not a failure
   * <em>between</em> the two fighter saves, which stranded the first fighter with nothing pointing
   * at it.
   */
  @Test
  void createKumiteGame_whenTheSecondFighterFails_deletesTheFirst() {
    PlayerRepository flakyFighters = mock(PlayerRepository.class);
    when(flakyFighters.save(any(Player.class)))
        .thenAnswer(call -> storage.save((Player) call.getArgument(0)))
        .thenThrow(new IllegalStateException("simulated failure saving the second fighter"));
    when(flakyFighters.findById(anyString()))
        .thenAnswer(call -> storage.findById(Player.class, call.getArgument(0)));
    doAnswer(
            call -> {
              storage.delete(call.getArgument(0));
              return null;
            })
        .when(flakyFighters)
        .delete(any(Player.class));

    KumiteGameService flakyService =
        new KumiteGameService(
            FakeRepositories.kumiteGames(storage),
            TestGameProperties.standard(),
            new PlayerService(flakyFighters),
            TestRulesEngine.standard(),
            new MatchStateWriter(FakeRepositories.kumiteGames(storage)));

    assertThatThrownBy(
            () ->
                flakyService.createKumiteGame(
                    request(new PlayerDTO("Kenji", "RED"), new PlayerDTO("Sato", "BLUE"))))
        .isInstanceOf(IllegalStateException.class);

    assertThat(storage.count(Player.class))
        .as("the fighter that was written before the failure is compensated away")
        .isZero();
    assertThat(storage.count(KumiteGame.class)).isZero();
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
        new GameProperties(
            Duration.ofMinutes(5),
            List.of(Duration.ofMinutes(5)),
            TestGameProperties.WINNING_POINTS,
            TestGameProperties.FOULS_ENDING_MATCH,
            List.of(Duration.ofSeconds(10)));
    KumiteGameService customised =
        new KumiteGameService(
            FakeRepositories.kumiteGames(storage),
            fiveMinutes,
            playerService,
            TestRulesEngine.standard(),
            new MatchStateWriter(FakeRepositories.kumiteGames(storage)));

    GameWithFighters created =
        customised.createKumiteGame(
            request(new PlayerDTO("Kenji", "RED"), new PlayerDTO("Sato", "BLUE")));

    assertThat(created.game().getGameDuration()).isEqualTo(Duration.ofMinutes(5));
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

    GameWithFighters created = service.createKumiteGame(oddDuration);

    assertThat(created.game().getGameDuration())
        .isEqualTo(TestGameProperties.standard().defaultDuration());
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

  /**
   * The compensation path (#51): when the match save fails after the fighters were written, the
   * fighters are deleted again rather than stranded as orphans nothing references and nothing can
   * find.
   */
  @Test
  void createKumiteGame_whenTheMatchSaveFails_leavesNoOrphanedFighters() {
    KumiteGameRepository failingRepository = mock(KumiteGameRepository.class);
    when(failingRepository.save(any(KumiteGame.class)))
        .thenThrow(new IllegalStateException("simulated write failure"));
    KumiteGameService failingService =
        new KumiteGameService(
            failingRepository,
            TestGameProperties.standard(),
            playerService,
            TestRulesEngine.standard(),
            new MatchStateWriter(FakeRepositories.kumiteGames(storage)));

    assertThatThrownBy(
            () ->
                failingService.createKumiteGame(
                    request(new PlayerDTO("Kenji", "RED"), new PlayerDTO("Sato", "BLUE"))))
        .isInstanceOf(IllegalStateException.class);

    assertThat(storage.count(Player.class))
        .as("the compensating delete removed the fighters the failed creation wrote")
        .isZero();
  }

  private static KumiteGameRequestDTO request(PlayerDTO... fighters) {
    Map<String, PlayerDTO> byKey = new LinkedHashMap<>();
    for (int i = 0; i < fighters.length; i++) {
      byKey.put("fighter" + i, fighters[i]);
    }
    return new KumiteGameRequestDTO(byKey, List.of("Referee One"), null);
  }
}
