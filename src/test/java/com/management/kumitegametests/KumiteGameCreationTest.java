package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.management.athlete.data.PlayerRepository;
import com.management.athlete.domain.Player;
import com.management.athlete.service.PlayerService;
import com.management.config.GameProperties;
import com.management.kumitegame.data.KumiteGameRepository;
import com.management.kumitegame.domain.GameWithFighters;
import com.management.kumitegame.domain.InvalidGameRequestException;
import com.management.kumitegame.domain.KumiteGame;
import com.management.kumitegame.domain.NewMatch;
import com.management.kumitegame.domain.PlayerColor;
import com.management.kumitegame.service.KumiteGameService;
import com.management.kumitegame.service.MatchStateWriter;
import com.management.testsupport.FakeRepositories;
import com.management.testsupport.InMemoryMongo;
import com.management.testsupport.TestClock;
import com.management.testsupport.TestGameProperties;
import com.management.testsupport.TestRulesEngine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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
    service = serviceWith(FakeRepositories.kumiteGames(storage), playerService);
  }

  @Test
  void createKumiteGame_withOneRedAndOneBlue_savesTheMatchAndBothFighters() {
    GameWithFighters created =
        service.createKumiteGame(
            request(new NewMatch.Fighter("Kenji", "RED"), new NewMatch.Fighter("Sato", "BLUE")));

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
        serviceWith(FakeRepositories.kumiteGames(storage), new PlayerService(flakyFighters));

    assertThatThrownBy(() ->
            flakyService.createKumiteGame(
                request(
                    new NewMatch.Fighter("Kenji", "RED"), new NewMatch.Fighter("Sato", "BLUE"))))
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
            new MatchStateWriter(FakeRepositories.kumiteGames(storage)),
            TestClock.FIXED);

    GameWithFighters created =
        customised.createKumiteGame(
            request(new NewMatch.Fighter("Kenji", "RED"), new NewMatch.Fighter("Sato", "BLUE")));

    assertThat(created.game().getGameDuration()).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void createKumiteGame_withDurationOutsideTheConfiguredSet_fallsBackToTheDefault() {
    NewMatch oddDuration =
        new NewMatch(
            List.of(new NewMatch.Fighter("Kenji", "RED"), new NewMatch.Fighter("Sato", "BLUE")),
            List.of("Referee One"),
            100);

    GameWithFighters created = service.createKumiteGame(oddDuration);

    assertThat(created.game().getGameDuration())
        .isEqualTo(TestGameProperties.standard().defaultDuration());
  }

  @Test
  void createKumiteGame_withSingleFighter_isRejectedAndWritesNothing() {
    NewMatch onlyRed = request(new NewMatch.Fighter("Kenji", "RED"));

    assertThatThrownBy(() -> service.createKumiteGame(onlyRed))
        .isInstanceOf(InvalidGameRequestException.class)
        .hasMessageContaining("RED");

    assertThat(storage.count(KumiteGame.class)).isZero();
    assertThat(storage.count(Player.class)).isZero();
  }

  @Test
  void createKumiteGame_withThreeFighters_isRejectedNamingTheDuplicatedColour() {
    NewMatch threeFighters =
        request(
            new NewMatch.Fighter("Kenji", "RED"),
            new NewMatch.Fighter("Sato", "BLUE"),
            new NewMatch.Fighter("Hiro", "RED"));

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
    KumiteGameService failingService = serviceWith(failingRepository, playerService);

    assertThatThrownBy(() ->
            failingService.createKumiteGame(
                request(
                    new NewMatch.Fighter("Kenji", "RED"), new NewMatch.Fighter("Sato", "BLUE"))))
        .isInstanceOf(IllegalStateException.class);

    assertThat(storage.count(Player.class))
        .as("the compensating delete removed the fighters the failed creation wrote")
        .isZero();
  }

  @Test
  void createKumiteGame_whenNoFighterWasSaved_logsThatNothingWasCompensated() {
    PlayerRepository failingFighters = mock(PlayerRepository.class);
    when(failingFighters.save(any(Player.class)))
        .thenThrow(new IllegalStateException("simulated failure saving the first fighter"));
    KumiteGameService failing =
        serviceWith(FakeRepositories.kumiteGames(storage), new PlayerService(failingFighters));

    assertThat(errorsLoggedBy(failing))
        .containsExactly("Match creation failed with no fighter save confirmed");
  }

  @Test
  void createKumiteGame_whenEveryFighterIsDeleted_logsTheCompensation() {
    KumiteGameRepository failingMatches = mock(KumiteGameRepository.class);
    when(failingMatches.save(any(KumiteGame.class)))
        .thenThrow(new IllegalStateException("simulated write failure"));

    assertThat(errorsLoggedBy(serviceWith(failingMatches, playerService)))
        .containsExactly(
            "Match creation failed after fighters were saved; compensated by deletion");
  }

  @Test
  void createKumiteGame_whenFighterCannotBeDeleted_neverClaimsTheCompensation() {
    KumiteGameRepository failingMatches = mock(KumiteGameRepository.class);
    when(failingMatches.save(any(KumiteGame.class)))
        .thenThrow(new IllegalStateException("simulated write failure"));
    PlayerRepository undeletableFighters = mock(PlayerRepository.class);
    when(undeletableFighters.save(any(Player.class)))
        .thenAnswer(call -> storage.save((Player) call.getArgument(0)));
    when(undeletableFighters.findById(anyString()))
        .thenAnswer(call -> storage.findById(Player.class, call.getArgument(0)));
    doThrow(new IllegalStateException("simulated delete failure"))
        .when(undeletableFighters)
        .delete(any(Player.class));

    List<String> errors =
        errorsLoggedBy(serviceWith(failingMatches, new PlayerService(undeletableFighters)));

    assertThat(errors).hasSize(3).noneMatch(line -> line.contains("compensated"));
    assertThat(errors.getLast()).contains("stranded");
  }

  @Test
  void createKumiteGame_whenOnlyOneFighterIsDeleted_namesOnlyTheStrandedOne() {
    KumiteGameRepository failingMatches = mock(KumiteGameRepository.class);
    when(failingMatches.save(any(KumiteGame.class)))
        .thenThrow(new IllegalStateException("simulated write failure"));
    PlayerRepository halfDeletingFighters = mock(PlayerRepository.class);
    when(halfDeletingFighters.save(any(Player.class)))
        .thenAnswer(call -> storage.save((Player) call.getArgument(0)));
    when(halfDeletingFighters.findById(anyString()))
        .thenAnswer(call -> storage.findById(Player.class, call.getArgument(0)));
    List<String> deleted = new ArrayList<>();
    doAnswer(
            call -> {
              Player fighter = call.getArgument(0);
              deleted.add(fighter.getId());
              storage.delete(fighter);
              return null;
            })
        .doThrow(new IllegalStateException("simulated delete failure"))
        .when(halfDeletingFighters)
        .delete(any(Player.class));

    List<String> errors =
        errorsLoggedBy(serviceWith(failingMatches, new PlayerService(halfDeletingFighters)));

    assertThat(deleted).hasSize(1);
    assertThat(storage.count(Player.class)).isEqualTo(1);
    assertThat(errors.getLast()).contains("stranded").doesNotContain(deleted.getFirst());
  }

  private KumiteGameService serviceWith(KumiteGameRepository matches, PlayerService players) {
    return new KumiteGameService(
        matches,
        TestGameProperties.standard(),
        players,
        TestRulesEngine.standard(),
        new MatchStateWriter(FakeRepositories.kumiteGames(storage)),
        TestClock.FIXED);
  }

  private static List<String> errorsLoggedBy(KumiteGameService failing) {
    Logger log = (Logger) LoggerFactory.getLogger(KumiteGameService.class);
    ListAppender<ILoggingEvent> lines = new ListAppender<>();
    lines.start();
    log.addAppender(lines);
    try {
      assertThatThrownBy(() ->
              failing.createKumiteGame(
                  request(
                      new NewMatch.Fighter("Kenji", "RED"), new NewMatch.Fighter("Sato", "BLUE"))))
          .isInstanceOf(IllegalStateException.class);
    } finally {
      log.detachAppender(lines);
    }
    return lines.list.stream()
        .filter(line -> line.getLevel() == Level.ERROR)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  private static NewMatch request(NewMatch.Fighter... fighters) {
    return new NewMatch(List.of(fighters), List.of("Referee One"), null);
  }
}
