package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.dto.ClockAdjustmentRequest;
import com.management.dto.WinnerOverrideRequest;
import com.management.enums.GameState;
import com.management.enums.MatchEndReason;
import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.exceptions.IllegalStateTransitionException;
import com.management.exceptions.InvalidGameRequestException;
import com.management.models.GameWithFighters;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.services.KumiteGameService;
import com.management.services.MatchStateWriter;
import com.management.services.PlayerService;
import com.management.testsupport.FakeRepositories;
import com.management.testsupport.InMemoryMongo;
import com.management.testsupport.KumiteGameBuilder;
import com.management.testsupport.PlayerBuilder;
import com.management.testsupport.TestGameProperties;
import com.management.testsupport.TestRulesEngine;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The rules engine driven through the service, across a real save-and-reload boundary.
 *
 * <p>{@code MatchOutcomeEvaluatorTest} proves the rules decide correctly in isolation. This proves
 * the service asks them at the right moments, records what they say, and persists it — the wiring
 * between a scoring call and a finished match, which neither the rule tests nor the slice tests can
 * see.
 */
class MatchOutcomeServiceTest {

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
  void addPoint_whenTheScoreCrossesTheThreshold_finishesTheMatchWithoutAnyoneEndingIt() {
    String gameId = runningMatch();

    service.addPoint(gameId, "RED", PointsType.IPPON.name());
    service.addPoint(gameId, "RED", PointsType.IPPON.name());
    GameWithFighters afterThird = service.addPoint(gameId, "RED", PointsType.IPPON.name());

    assertThat(afterThird.game().getGameState()).isEqualTo(GameState.FINISHED);
    assertThat(afterThird.game().getWinner()).isEqualTo(PlayerColor.RED);
    assertThat(afterThird.game().getEndReason()).contains(MatchEndReason.POINTS_THRESHOLD);

    KumiteGame stored = storage.findById(KumiteGame.class, gameId).orElseThrow();
    assertThat(stored.getGameState())
        .as("the ending is persisted, not just returned")
        .isEqualTo(GameState.FINISHED);
    assertThat(stored.getDecidedBy()).contains("HighestScoreWinnerRule");
  }

  /**
   * A match that ends early keeps the time that was left on the clock.
   *
   * <p>{@code applyOutcome} used to null {@code startTime} before reading the timer, and the timer
   * rebuilds itself from that field — so it was constructed as a clock that had never started and
   * reported the full duration. An eight-point finish 30 seconds in was recorded as though it had
   * run its distance.
   */
  @Test
  void addPoint_endingTheMatchEarly_recordsTheTimeThatWasLeft() {
    String gameId =
        storedMatch(
            match ->
                match.runningSince(LocalDateTime.now().minusSeconds(30), Duration.ofSeconds(120)));

    service.addPoint(gameId, "RED", PointsType.IPPON.name());
    service.addPoint(gameId, "RED", PointsType.IPPON.name());
    GameWithFighters finished = service.addPoint(gameId, "RED", PointsType.IPPON.name());

    assertThat(finished.game().getGameState()).isEqualTo(GameState.FINISHED);
    assertThat(finished.game().getRemainingTime())
        .as("roughly 90s were left when the ninth point landed, not the full 120s")
        .isBetween(Duration.ofSeconds(85), Duration.ofSeconds(92));
  }

  @Test
  void overrideWinner_onRunningMatch_recordsTheTimeThatWasLeft() {
    String gameId =
        storedMatch(
            match ->
                match.runningSince(LocalDateTime.now().minusSeconds(30), Duration.ofSeconds(120)));

    GameWithFighters overridden =
        service.overrideWinner(
            gameId, new WinnerOverrideRequest("RED", "Stopped on the mat", "Head Referee"));

    assertThat(overridden.game().getRemainingTime())
        .isBetween(Duration.ofSeconds(85), Duration.ofSeconds(92));
  }

  /**
   * A point that arrives after the clock ran out must not count.
   *
   * <p>Expiry is noticed rather than pushed, so the match is still RUNNING when the late point
   * arrives. It used to be written to the fighter and only then did the evaluator notice the expiry
   * — with the late point already in the score, turning a draw into a win.
   */
  @Test
  void addPoint_afterTheClockRanOut_isRefusedAndFinishesTheMatchAsDrawn() {
    String gameId =
        storedMatch(
            match ->
                match.runningSince(LocalDateTime.now().minusMinutes(5), Duration.ofSeconds(30)));

    assertThatThrownBy(() -> service.addPoint(gameId, "RED", PointsType.IPPON.name()))
        .isInstanceOf(IllegalStateTransitionException.class)
        .hasMessageContaining("ran out of time");

    KumiteGame stored = storage.findById(KumiteGame.class, gameId).orElseThrow();
    assertThat(stored.getGameState()).isEqualTo(GameState.FINISHED);
    assertThat(stored.getEndReason()).contains(MatchEndReason.TIME_EXPIRED);
    assertThat(stored.getWinner()).as("the late point did not decide it").isNull();
  }

  /** A corrected-away point takes SENSHU with it, so it cannot decide a level match later. */
  @Test
  void removePoint_takingTheScorerBackToZero_clearsSenshu() {
    String gameId = runningMatch();
    service.addPoint(gameId, "RED", PointsType.YUKO.name());

    assertThat(storage.findById(KumiteGame.class, gameId).orElseThrow().getSenshu())
        .contains(PlayerColor.RED);

    service.removePoint(gameId, "RED", PointsType.YUKO.name());

    assertThat(storage.findById(KumiteGame.class, gameId).orElseThrow().getSenshu())
        .as("RED did not score first after all")
        .isEmpty();
  }

  @Test
  void forfeit_onFinishedMatch_isRefused() {
    String gameId = runningMatch();
    service.disqualify(gameId, "RED");

    assertThatThrownBy(() -> service.forfeit(gameId, "BLUE"))
        .as("a decided match cannot be quietly forfeited")
        .isInstanceOf(IllegalStateTransitionException.class);
  }

  @Test
  void overrideWinner_appliedTwice_stillRecordsWhatTheEngineDecided() {
    String gameId = runningMatch();
    service.disqualify(gameId, "RED");

    service.overrideWinner(gameId, new WinnerOverrideRequest("RED", "First call", "Referee One"));
    GameWithFighters second =
        service.overrideWinner(
            gameId, new WinnerOverrideRequest("BLUE", "Corrected again", "Head Referee"));

    assertThat(second.game().getRefereeOverride().orElseThrow().supersededOutcome())
        .as("the engine's original result survives a second correction")
        .isNotNull()
        .satisfies(
            engine -> {
              assertThat(engine.reason()).isEqualTo(MatchEndReason.DISQUALIFICATION);
              assertThat(engine.winner()).isEqualTo(PlayerColor.BLUE);
            });
  }

  @Test
  void addPoint_theFirstScoreOfTheMatch_recordsSenshu() {
    String gameId = runningMatch();

    service.addPoint(gameId, "BLUE", PointsType.YUKO.name());
    service.addPoint(gameId, "RED", PointsType.YUKO.name());

    KumiteGame stored = storage.findById(KumiteGame.class, gameId).orElseThrow();
    assertThat(stored.getSenshu())
        .as("SENSHU is the first to score, not the last")
        .contains(PlayerColor.BLUE);
  }

  @Test
  void addFoul_reachingTheFoulLimit_finishesTheMatch() {
    String gameId = runningMatch();

    GameWithFighters afterFourth = null;
    for (int i = 0; i < TestGameProperties.FOULS_ENDING_MATCH; i++) {
      afterFourth = service.addFoul(gameId, "BLUE");
    }

    assertThat(afterFourth).isNotNull();
    assertThat(afterFourth.game().getGameState()).isEqualTo(GameState.FINISHED);
    assertThat(afterFourth.game().getEndReason()).contains(MatchEndReason.FOUL_LIMIT);
  }

  /**
   * The reversal decision (#70): a stage that carried a consequence cannot be walked back by
   * removing the foul, because the consequence has already moved a score or ended the match.
   */
  @Test
  void removeFoul_whenTheStageCarriedNoConsequence_isAllowed() {
    String gameId = runningMatch();
    service.addFoul(gameId, "BLUE");

    GameWithFighters afterRemoval = service.removeFoul(gameId, "BLUE");

    assertThat(afterRemoval.fighters().get(PlayerColor.BLUE).getFouls().getNumOfFouls()).isZero();
  }

  @Test
  void disqualify_putsTheFighterOutAndAwardsTheMatchToTheOpponent() {
    String gameId = runningMatch();

    GameWithFighters afterDisqualification = service.disqualify(gameId, "RED");

    assertThat(afterDisqualification.game().getGameState()).isEqualTo(GameState.FINISHED);
    assertThat(afterDisqualification.game().getWinner()).isEqualTo(PlayerColor.BLUE);
    assertThat(afterDisqualification.game().getEndReason())
        .contains(MatchEndReason.DISQUALIFICATION);
  }

  @Test
  void forfeit_awardsTheMatchToTheFighterWhoTurnedUp() {
    String gameId = runningMatch();

    GameWithFighters afterForfeit = service.forfeit(gameId, "BLUE");

    assertThat(afterForfeit.game().getGameState()).isEqualTo(GameState.FINISHED);
    assertThat(afterForfeit.game().getWinner()).isEqualTo(PlayerColor.RED);
    assertThat(afterForfeit.game().getEndReason()).contains(MatchEndReason.KIKEN);
  }

  @Test
  void overrideWinner_replacesTheResultAndKeepsWhatTheEngineDecided() {
    String gameId = runningMatch();
    service.addPoint(gameId, "RED", PointsType.IPPON.name());
    service.addPoint(gameId, "RED", PointsType.IPPON.name());
    service.addPoint(gameId, "RED", PointsType.IPPON.name());

    GameWithFighters overridden =
        service.overrideWinner(
            gameId,
            new WinnerOverrideRequest("BLUE", "Scoring error on the third IPPON", "Head Referee"));

    assertThat(overridden.game().getWinner()).isEqualTo(PlayerColor.BLUE);
    assertThat(overridden.game().getEndReason()).contains(MatchEndReason.REFEREE_OVERRIDE);
    assertThat(overridden.game().getRefereeOverride()).isPresent();
    assertThat(overridden.game().getRefereeOverride().orElseThrow().supersededOutcome())
        .as("what the engine decided is kept, not overwritten")
        .isNotNull()
        .satisfies(superseded -> assertThat(superseded.winner()).isEqualTo(PlayerColor.RED));
  }

  @Test
  void overrideWinner_onAQueuedMatch_isRejected() {
    String gameId = storage.save(KumiteGameBuilder.newGame().build()).getId();

    assertThatThrownBy(
            () ->
                service.overrideWinner(
                    gameId, new WinnerOverrideRequest("RED", "any reason", "Head Referee")))
        .isInstanceOf(IllegalStateTransitionException.class);
  }

  @Test
  void addPoint_afterAnOverride_doesNotLetTheEngineTakeTheResultBack() {
    String gameId = runningMatch();
    service.overrideWinner(
        gameId, new WinnerOverrideRequest("BLUE", "Called on the mat", "Head Referee"));

    assertThatThrownBy(() -> service.addPoint(gameId, "RED", PointsType.IPPON.name()))
        .as("the match is finished, so scoring against it is refused")
        .isInstanceOf(RuntimeException.class);

    KumiteGame stored = storage.findById(KumiteGame.class, gameId).orElseThrow();
    assertThat(stored.getWinner()).isEqualTo(PlayerColor.BLUE);
    assertThat(stored.getEndReason()).contains(MatchEndReason.REFEREE_OVERRIDE);
  }

  @Test
  void addTime_whileRunning_extendsTheClockAndRecordsWhoDidIt() {
    String gameId =
        storedMatch(
            match ->
                match.runningSince(LocalDateTime.now().minusSeconds(30), Duration.ofSeconds(120)));

    GameWithFighters extended =
        service.addTime(gameId, new ClockAdjustmentRequest(30, "Timekeeper"));

    assertThat(extended.game().getRemainingTime())
        .as("about 90s were left, and 30s were added")
        .isBetween(Duration.ofSeconds(118), Duration.ofSeconds(121));
    assertThat(extended.game().getClockAdjustments()).hasSize(1);
    assertThat(extended.game().getClockAdjustments().get(0).addedBy()).isEqualTo("Timekeeper");
  }

  @Test
  void addTime_whilePaused_extendsTheClockWithoutRestartingIt() {
    String gameId = storedMatch(match -> match.pausedWithRemaining(Duration.ofSeconds(45)));

    GameWithFighters extended =
        service.addTime(gameId, new ClockAdjustmentRequest(10, "Timekeeper"));

    assertThat(extended.game().getRemainingTime()).isEqualTo(Duration.ofSeconds(55));
    assertThat(extended.game().getStartTime()).as("a paused clock stays paused").isNull();
    assertThat(extended.game().getGameState()).isEqualTo(GameState.PAUSED);
  }

  @Test
  void addTime_onFinishedMatch_isRejected() {
    String gameId = storedMatch(KumiteGameBuilder::finished);

    assertThatThrownBy(() -> service.addTime(gameId, new ClockAdjustmentRequest(10, "Timekeeper")))
        .isInstanceOf(IllegalStateTransitionException.class);
  }

  @Test
  void addTime_withIncrementThatIsNotOffered_isRejected() {
    String gameId = runningMatch();

    assertThatThrownBy(() -> service.addTime(gameId, new ClockAdjustmentRequest(7, "Timekeeper")))
        .isInstanceOf(InvalidGameRequestException.class)
        .hasMessageContaining("increments");
  }

  /** A running match whose fighters are stored, so scoring behaves as production does. */
  private String runningMatch() {
    return storedMatch(match -> match.runningSince(LocalDateTime.now(), Duration.ofSeconds(120)));
  }

  /**
   * Stores two fighters and a match referring to them.
   *
   * <p>The fighters have to exist in storage: since #49 the match holds only their ids, so a
   * fixture that builds a match without saving its fighters describes a match whose fighters cannot
   * be loaded.
   */
  private String storedMatch(UnaryOperator<KumiteGameBuilder> customise) {
    Player red = storage.save(PlayerBuilder.newPlayer().named("Kenji").build());
    Player blue = storage.save(PlayerBuilder.newPlayer().named("Sato").build());
    KumiteGameBuilder match =
        KumiteGameBuilder.newGame().with(PlayerColor.RED, red).with(PlayerColor.BLUE, blue);
    return storage.save(customise.apply(match).build()).getId();
  }
}
