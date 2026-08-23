package com.management.services;

import com.management.config.GameProperties;
import com.management.dto.ClockAdjustmentRequest;
import com.management.dto.KumiteGameRequestDTO;
import com.management.dto.PlayerDTO;
import com.management.dto.PlayerRequestDTO;
import com.management.dto.WinnerOverrideRequest;
import com.management.enums.FoulTypes;
import com.management.enums.GameState;
import com.management.enums.MatchEndReason;
import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.exceptions.GameNotFoundException;
import com.management.exceptions.IllegalStateTransitionException;
import com.management.exceptions.InvalidGameRequestException;
import com.management.exceptions.PlayerNotFoundException;
import com.management.models.ClockAdjustment;
import com.management.models.GameWithFighters;
import com.management.models.KumiteGame;
import com.management.models.MatchOutcome;
import com.management.models.Player;
import com.management.models.Referee;
import com.management.models.RefereeOverride;
import com.management.repositories.KumiteGameRepository;
import com.management.rules.MatchOutcomeEvaluator;
import com.management.util.KumiteGameManagementUtils;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The match aggregate, and the owner of every operation that spans a match and its fighters.
 *
 * <p>Scoring, creation and match rendering all need both aggregates: the match knows which fighter
 * id wears which colour, the fighter documents own the names and scores. Those operations live here
 * and call <em>down</em> into {@link PlayerService} — one direction, acyclically, per {@code
 * workflow/patterns/service-interaction.md}. This replaced the mutual dependency that {@code
 * GameHelperService} and {@code @Lazy} used to paper over.
 */
@Service
public class KumiteGameService {

  private static final Logger log = LoggerFactory.getLogger(KumiteGameService.class);
  private static final String GAME_NOT_FOUND = "Game not found!";
  private static final String GAME_ID = " Game Id: ";

  private final KumiteGameRepository kumiteGameRepository;
  private final GameProperties gameProperties;
  private final PlayerService playerService;
  private final MatchOutcomeEvaluator outcomeEvaluator;
  private final MatchStateWriter matchStateWriter;

  public KumiteGameService(
      KumiteGameRepository kumiteGameRepository,
      GameProperties gameProperties,
      PlayerService playerService,
      MatchOutcomeEvaluator outcomeEvaluator,
      MatchStateWriter matchStateWriter) {
    this.kumiteGameRepository = kumiteGameRepository;
    this.gameProperties = gameProperties;
    this.playerService = playerService;
    this.outcomeEvaluator = outcomeEvaluator;
    this.matchStateWriter = matchStateWriter;
  }

  /**
   * Creates the match and its two fighters, and returns both.
   *
   * <p>Ordered so everything that can fail does so before the first write: colours are resolved and
   * validated, referees mapped and the duration determined, and only then are the fighters and the
   * match saved, adjacent (#51). Those writes are separate operations on a standalone MongoDB with
   * no transaction to span them, so <strong>every</strong> write after the first is inside the
   * compensating block — a failure part-way through, including between the two fighter saves,
   * deletes whatever was already written. Hand-rolled rollback, with one documented residual: the
   * compensation itself can fail, and that narrow double-fault window is logged loudly with the
   * stranded identifier rather than hidden.
   *
   * <p>Returns the composition rather than the bare match so the caller can answer without a second
   * read. Re-reading would put a fallible round trip between "the match exists" and "the caller was
   * told its id" — exactly the unrecoverable shape #51 exists to remove.
   */
  public GameWithFighters createKumiteGame(KumiteGameRequestDTO gameRequestDTO) {
    Map<PlayerColor, PlayerDTO> byColour = new EnumMap<>(PlayerColor.class);
    gameRequestDTO
        .playersMap()
        .forEach(
            (key, playerDTO) -> {
              PlayerColor playerColor = KumiteGameManagementUtils.mapPlayerColor(playerDTO.color());
              PlayerDTO clash = byColour.put(playerColor, playerDTO);
              if (clash != null) {
                throw new InvalidGameRequestException(
                    "A match fields exactly one fighter per colour, but two were given as "
                        + playerColor
                        + ".");
              }
            });
    validateColours(byColour.keySet());

    List<Referee> refereesList = gameRequestDTO.refereeList().stream().map(Referee::new).toList();
    Duration gameDuration = determineGameDuration(gameRequestDTO);

    Map<PlayerColor, Player> createdFighters = new EnumMap<>(PlayerColor.class);
    try {
      byColour.forEach(
          (playerColor, requested) ->
              createdFighters.put(
                  playerColor, playerService.createPlayer(new PlayerRequestDTO(requested.name()))));

      Map<PlayerColor, String> playerIds = new EnumMap<>(PlayerColor.class);
      createdFighters.forEach(
          (playerColor, fighter) -> playerIds.put(playerColor, fighter.getId()));

      KumiteGame saved = saveGame(new KumiteGame(playerIds, refereesList, gameDuration));
      return new GameWithFighters(saved, createdFighters);
    } catch (RuntimeException creationFailure) {
      deleteOrphanedFighters(createdFighters, creationFailure);
      throw creationFailure;
    }
  }

  /** The match with both fighters loaded — what everything that renders a match consumes. */
  public GameWithFighters getGameWithFighters(String gameId) {
    return withFighters(getKumiteGame(gameId));
  }

  public GameWithFighters addPoint(String gameId, String color, String pointType) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    requireOpen(kumiteGame, "scored against");
    requireClockNotExpired(kumiteGame, "scored against");
    PointsType point = KumiteGameManagementUtils.mapPointToPointType(pointType);
    PlayerColor scoringColour = KumiteGameManagementUtils.mapPlayerColor(color);
    Player scored = playerService.addPoint(fighterId(kumiteGame, scoringColour), point);

    boolean firstScore =
        kumiteGame.getSenshu().isEmpty() && scored.getPoints().getNumOfPoints() > 0;
    if (firstScore) {
      kumiteGame.recordFirstScorer(scoringColour);
    }

    return settle(
        withFighters(kumiteGame, scoringColour, scored), firstScore ? scoringColour : null);
  }

  /**
   * Takes a point back, and takes SENSHU with it when the fighter is left with nothing.
   *
   * <p>SENSHU is "the first to score", so a fighter whose score has been corrected back to zero
   * cannot hold it: leaving it set let a mistakenly-awarded point that was then removed decide a
   * level match at time expiry, in favour of a fighter who never actually scored first. Clearing it
   * is enough — SENSHU only breaks a tie, and a fighter on zero against an opponent with points is
   * not in one.
   */
  public GameWithFighters removePoint(String gameId, String color, String pointType) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    requireOpen(kumiteGame, "scored against");
    PointsType point = KumiteGameManagementUtils.mapPointToPointType(pointType);
    PlayerColor scoringColour = KumiteGameManagementUtils.mapPlayerColor(color);
    Player scored = playerService.removePoint(fighterId(kumiteGame, scoringColour), point);

    boolean senshuLapsed =
        kumiteGame.getSenshu().filter(scoringColour::equals).isPresent()
            && scored.getPoints().getNumOfPoints() == 0;

    return settleWith(
        withFighters(kumiteGame, scoringColour, scored),
        game -> {
          if (senshuLapsed) {
            game.clearFirstScorer();
          }
        },
        senshuLapsed);
  }

  /**
   * Records a foul and applies whatever its new penalty stage carries.
   *
   * <p>The stage is derived from the count, so this only has to act on the consequence: a stage
   * that awards points hands them to the opponent through the ordinary scoring path — the same
   * versioned, retried write a referee's award goes through — and a disqualifying stage puts the
   * fighter out. Doing it any other way would give the score a second route into the database.
   */
  public GameWithFighters addFoul(String gameId, String color) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    requireOpen(kumiteGame, "penalised");
    PlayerColor penalisedColour = KumiteGameManagementUtils.mapPlayerColor(color);
    Player penalised = playerService.addFoul(fighterId(kumiteGame, penalisedColour));

    Optional<FoulTypes> stage = penalised.foulStage();
    PlayerColor opponentColour = opponentOf(penalisedColour);
    PlayerColor firstScorer =
        stage
            .flatMap(FoulTypes::opponentAward)
            .map(
                award -> {
                  boolean unclaimed = kumiteGame.getSenshu().isEmpty();
                  playerService.addPoint(fighterId(kumiteGame, opponentColour), award);
                  return unclaimed ? opponentColour : null;
                })
            .orElse(null);
    if (stage.filter(FoulTypes::isDisqualifying).isPresent()) {
      penalised = playerService.disqualify(penalised.getId());
    }

    return settle(withFighters(kumiteGame, penalisedColour, penalised), firstScorer);
  }

  /**
   * Removes a foul, provided its stage carried no consequence.
   *
   * <p>Walking a consequence backwards is not the same operation as walking a count backwards: a
   * point handed to the opponent has since become part of their score, which they may have added
   * to, and a disqualification has already ended the match. Rather than guess how far back to
   * unwind, a reversal that would have to undo a consequence is refused — the referee's route out
   * is an override, which records why. Reversing a plain warning is unrestricted.
   */
  public GameWithFighters removeFoul(String gameId, String color) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    requireOpen(kumiteGame, "penalised");
    PlayerColor penalisedColour = KumiteGameManagementUtils.mapPlayerColor(color);
    String penalisedId = fighterId(kumiteGame, penalisedColour);

    Player current = playerService.getPlayer(penalisedId);
    current
        .foulStage()
        .filter(stage -> stage.opponentAward().isPresent() || stage.isDisqualifying())
        .ifPresent(
            stage -> {
              throw new IllegalStateTransitionException(
                  "Fighter "
                      + penalisedColour
                      + " is at "
                      + stage
                      + ", which already had a consequence. Removing that foul would have to undo"
                      + " it; use a referee override instead.");
            });

    Player penalised = playerService.removeFoul(penalisedId);
    return settle(withFighters(kumiteGame, penalisedColour, penalised), null);
  }

  /** Puts a fighter out of the match, which the rules engine turns into a win for the opponent. */
  public GameWithFighters disqualify(String gameId, String color) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    requireOpen(kumiteGame, "disqualified from");
    PlayerColor colour = KumiteGameManagementUtils.mapPlayerColor(color);
    Player disqualified = playerService.disqualify(fighterId(kumiteGame, colour));
    return settle(withFighters(kumiteGame, colour, disqualified), null);
  }

  /**
   * Records a forfeit — KIKEN — against a fighter who did not appear or withdrew.
   *
   * <p>Operator-initiated and immediate: no condition can detect an absence, so the reason is
   * supplied rather than evaluated, and the opponent wins on the spot.
   */
  public GameWithFighters forfeit(String gameId, String color) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    requireOpen(kumiteGame, "forfeited");
    PlayerColor forfeitingColour = KumiteGameManagementUtils.mapPlayerColor(color);
    fighterId(kumiteGame, forfeitingColour);

    GameWithFighters match = withFighters(kumiteGame);
    kumiteGame.applyOutcome(
        new MatchOutcome(opponentOf(forfeitingColour), MatchEndReason.KIKEN, "Kiken"));
    return new GameWithFighters(saveGame(kumiteGame), match.fighters());
  }

  /**
   * Replaces the result with a referee's decision, and records why.
   *
   * <p>Permitted on a match that has started, including one the engine has already finished —
   * correcting a decided result is the main thing an override is for. A queued match is refused:
   * nothing has happened yet to overrule. Whatever the engine had determined is kept inside the
   * override record rather than overwritten.
   *
   * <p><strong>An override of an override keeps the engine's original result.</strong> Reading the
   * current outcome blindly would record the previous <em>override</em> as the superseded one,
   * because the first override has already stamped itself onto those fields — so the one thing the
   * record exists to preserve, what the system itself concluded, would be lost on the second
   * correction.
   */
  public GameWithFighters overrideWinner(String gameId, WinnerOverrideRequest request) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    if (kumiteGame.getGameState() == GameState.QUEUED) {
      throw new IllegalStateTransitionException(
          "Match " + gameId + " has not started, so there is no result to override.");
    }
    PlayerColor declared =
        request.winner() == null
            ? null
            : KumiteGameManagementUtils.mapPlayerColor(request.winner());

    MatchOutcome engineOutcome =
        kumiteGame
            .getRefereeOverride()
            .map(RefereeOverride::supersededOutcome)
            .orElseGet(() -> kumiteGame.outcome().orElse(null));

    kumiteGame.applyOverride(
        new RefereeOverride(
            declared, request.reason(), request.decidedBy(), LocalDateTime.now(), engineOutcome));
    return withFighters(saveGame(kumiteGame));
  }

  /**
   * Adds time to the clock to compensate for a stop that came late.
   *
   * <p>Refused on a finished match: its clock is history. Allowed while running and while paused —
   * the timer handles the elapsed-time arithmetic that makes the running case correct.
   */
  public GameWithFighters addTime(String gameId, ClockAdjustmentRequest request) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    if (kumiteGame.getGameState() == GameState.FINISHED) {
      throw new IllegalStateTransitionException(
          "Match " + gameId + " has finished; its clock cannot be extended.");
    }
    Duration extra = Duration.ofSeconds(request.seconds());
    if (!gameProperties.clockIncrements().contains(extra)) {
      throw new InvalidGameRequestException(
          "Time may be added in increments of "
              + gameProperties.clockIncrements()
              + ", but "
              + extra
              + " was requested.");
    }

    kumiteGame.addTime(new ClockAdjustment(extra, LocalDateTime.now(), request.addedBy()));
    return withFighters(saveGame(kumiteGame));
  }

  /**
   * The manual winner path, kept as the rules engine's escape hatch until #73's override replaced
   * it as the documented route.
   */
  public GameWithFighters updateKumiteGameWinner(String gameId, String color) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    PlayerColor playerColor = KumiteGameManagementUtils.mapPlayerColor(color);
    kumiteGame.updateWinner(playerColor);
    return withFighters(saveGame(kumiteGame));
  }

  /**
   * Asks the rules engine whether the match is over, and records the answer if it is — along with
   * the first scorer, when this event produced it.
   *
   * <p>Both facts are write-once, so the write goes through {@link MatchStateWriter}, which
   * re-reads and re-applies on a conflict. Nothing is written when neither fact changed, which
   * keeps an ordinary mid-match point to a single document write.
   */
  private GameWithFighters settle(GameWithFighters match, @Nullable PlayerColor firstScorer) {
    return settleWith(
        match,
        game -> {
          if (firstScorer != null) {
            game.recordFirstScorer(firstScorer);
          }
        },
        firstScorer != null);
  }

  /**
   * The general form: apply a write-once change to the match, then whatever the engine decided.
   *
   * @param alsoChanged whether {@code change} actually alters anything, so an event that neither
   *     ends the match nor touches match-level state still costs no document write
   */
  private GameWithFighters settleWith(
      GameWithFighters match, Consumer<KumiteGame> change, boolean alsoChanged) {
    Optional<MatchOutcome> outcome = outcomeEvaluator.evaluate(match);
    if (outcome.isEmpty() && !alsoChanged) {
      return match;
    }

    KumiteGame saved =
        matchStateWriter.applyAndSave(
            match.game().getId(),
            game -> {
              change.accept(game);
              outcome.ifPresent(game::applyOutcome);
            });
    return new GameWithFighters(saved, match.fighters());
  }

  /**
   * Refuses an operation on a match that has already produced a result.
   *
   * <p>Scoring a finished match would move the score while the recorded result stayed put, which is
   * worse than refusing: the two would disagree with no indication which is authoritative. A
   * referee who genuinely needs to change a decided match uses an override, which records why.
   */
  private static void requireOpen(KumiteGame kumiteGame, String action) {
    if (kumiteGame.getGameState() == GameState.FINISHED) {
      throw new IllegalStateTransitionException(
          "Match " + kumiteGame.getId() + " has finished and cannot be " + action + ".");
    }
  }

  /**
   * Refuses an event that arrives after the clock has already run out, finishing the match first.
   *
   * <p>Expiry is noticed rather than pushed, so a match whose time ran out is still {@code RUNNING}
   * until something asks. Without this, a point landing seconds late was written to the fighter and
   * only *then* did the evaluator notice the expiry — with the late point already counted, turning
   * a drawn match into a win. The match is settled here so the ending is recorded at once, and the
   * event itself is refused.
   */
  private void requireClockNotExpired(KumiteGame kumiteGame, String action) {
    if (kumiteGame.getGameState() != GameState.RUNNING
        || !kumiteGame.getTimer().getRemainingTime().isZero()) {
      return;
    }
    settle(withFighters(kumiteGame), null);
    throw new IllegalStateTransitionException(
        "Match "
            + kumiteGame.getId()
            + " ran out of time before this event, so it cannot be "
            + action
            + ".");
  }

  private static PlayerColor opponentOf(PlayerColor color) {
    return color == PlayerColor.RED ? PlayerColor.BLUE : PlayerColor.RED;
  }

  /**
   * Starts a queued match. Setting {@code startTime} is what starts the clock: the timer is derived
   * from the persisted fields on demand, so no timer object needs touching here, and the remaining
   * time is left exactly as it was — it is defined as the time left when {@code startTime} was last
   * set.
   */
  public KumiteGame startGame(String gameId) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    requireCurrentState(kumiteGame, "started", EnumSet.of(GameState.QUEUED));
    kumiteGame.setGameState(GameState.RUNNING);
    kumiteGame.setStartTime(LocalDateTime.now());
    return saveGame(kumiteGame);
  }

  public KumiteGame pauseGame(String gameId) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    requireCurrentState(kumiteGame, "paused", EnumSet.of(GameState.RUNNING));
    // The timer must be read before startTime is cleared: getTimer() rebuilds it from the
    // persisted startTime, which is what the elapsed time is measured against.
    kumiteGame.getTimer().pause();
    updateRemainingTime(kumiteGame);
    kumiteGame.setStartTime(null);
    kumiteGame.setGameState(GameState.PAUSED);
    return saveGame(kumiteGame);
  }

  public KumiteGame resumeGame(String gameId) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    requireCurrentState(kumiteGame, "resumed", EnumSet.of(GameState.PAUSED));
    kumiteGame.setGameState(GameState.RUNNING);
    kumiteGame.setStartTime(LocalDateTime.now());
    return saveGame(kumiteGame);
  }

  public KumiteGame endGame(String gameId) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    requireCurrentState(kumiteGame, "ended", EnumSet.of(GameState.RUNNING, GameState.PAUSED));
    kumiteGame.getTimer().stop();
    updateRemainingTime(kumiteGame);
    kumiteGame.setStartTime(null);
    kumiteGame.setGameState(GameState.FINISHED);
    return saveGame(kumiteGame);
  }

  public KumiteGame getKumiteGame(String gameId) {

    Optional<KumiteGame> fetchedKumiteGame = kumiteGameRepository.findById(gameId);
    if (fetchedKumiteGame.isEmpty()) {
      log.error("KumiteGameService - getKumiteGame - couldn't find game with id: {}", gameId);
      throw new GameNotFoundException(GAME_NOT_FOUND + GAME_ID + gameId);
    }

    return fetchedKumiteGame.get();
  }

  /**
   * Rejects a lifecycle call the match's current state does not allow.
   *
   * <p>Each method names its own allowed starting states rather than checking target legality on
   * {@link GameState}, because the verbs are narrower than the state machine: {@code QUEUED} and
   * {@code PAUSED} both transition legally to {@code RUNNING}, but only one of them may be
   * <em>started</em> and only the other <em>resumed</em>. The enum keeps the full transition table
   * as data for the planned Game Orchestrator; every allowed set here is a subset of it.
   *
   * <p>Rejecting before anything is touched matters for {@code endGame} in particular, which zeroes
   * the clock — ending a queued match and then starting it used to produce a match that began with
   * no time on it.
   */
  private static void requireCurrentState(
      KumiteGame kumiteGame, String action, Set<GameState> allowedCurrent) {
    GameState current = kumiteGame.getGameState();
    if (!allowedCurrent.contains(current)) {
      throw new IllegalStateTransitionException(
          "Match " + kumiteGame.getId() + " is " + current + " and cannot be " + action + ".");
    }
  }

  private void updateRemainingTime(KumiteGame kumiteGame) {
    kumiteGame.setRemainingTime(kumiteGame.getTimer().getRemainingTime());
  }

  private GameWithFighters withFighters(KumiteGame kumiteGame) {
    return withFighters(kumiteGame, null, null);
  }

  /**
   * Loads the match's fighters, reusing one already in hand.
   *
   * <p>A mutation has just read and written the fighter it changed, so re-reading it would be a
   * third round trip for a value the caller is holding. Only the other colour is fetched.
   */
  private GameWithFighters withFighters(
      KumiteGame kumiteGame, @Nullable PlayerColor knownColour, @Nullable Player knownFighter) {
    Map<PlayerColor, Player> fighters = new EnumMap<>(PlayerColor.class);
    referencedFighters(kumiteGame)
        .forEach(
            (color, playerId) ->
                fighters.put(
                    color,
                    color == knownColour && knownFighter != null
                        ? knownFighter
                        : playerService.getPlayer(playerId)));
    return new GameWithFighters(kumiteGame, fighters);
  }

  /** Resolves a colour to a fighter id, or 404s: a real colour this match does not field. */
  private static String fighterId(KumiteGame kumiteGame, PlayerColor playerColor) {
    String playerId = referencedFighters(kumiteGame).get(playerColor);
    if (playerId == null) {
      throw new PlayerNotFoundException(
          "Match " + kumiteGame.getId() + " has no " + playerColor + " fighter.");
    }
    return playerId;
  }

  /**
   * The match's fighter references, refusing a document that has none.
   *
   * <p>A match stored before #49 embedded its fighters and has no {@code playerIds} at all. Such a
   * document still loads — the mapping layer simply leaves the field null — and would then fail
   * with a {@code NullPointerException} reported as an unattributable 500. Saying so plainly is
   * cheaper to diagnose, and matches the recorded decision that pre-#49 documents are unsupported.
   */
  private static Map<PlayerColor, String> referencedFighters(KumiteGame kumiteGame) {
    Map<PlayerColor, String> playerIds = kumiteGame.getPlayerIds();
    if (playerIds == null) {
      throw new IllegalStateException(
          "Stored match "
              + kumiteGame.getId()
              + " has no fighter references. Documents written before fighters were stored by id"
              + " are not supported; drop the database rather than migrating it.");
    }
    return playerIds;
  }

  private void deleteOrphanedFighters(
      Map<PlayerColor, Player> createdFighters, RuntimeException cause) {
    createdFighters.forEach(
        (color, fighter) -> {
          try {
            playerService.deletePlayer(fighter.getId());
          } catch (RuntimeException compensationFailure) {
            log.error(
                "Match creation failed after fighters were saved, and deleting orphaned fighter {}"
                    + " failed too — the document is stranded and must be removed by hand",
                fighter.getId(),
                compensationFailure);
          }
        });
    log.error("Match creation failed after fighters were saved; compensated by deletion", cause);
  }

  /**
   * Enforces the one-of-each-colour rule at the point of creation.
   *
   * <p>A match fields exactly one RED and one BLUE — a settled domain rule, not a request-time
   * option. Checking before anything is written turns a malformed request into a 400 that changes
   * nothing in the database. {@code KumiteGameMapper} keeps its own check for the same invariant:
   * this one guards what enters the database, that one guards what leaves it.
   */
  private void validateColours(Set<PlayerColor> colours) {
    Set<PlayerColor> required = EnumSet.allOf(PlayerColor.class);
    if (!colours.equals(required)) {
      throw new InvalidGameRequestException(
          "A match needs exactly one fighter of each colour "
              + required
              + ", but the request gave "
              + colours
              + ".");
    }
  }

  private Duration determineGameDuration(KumiteGameRequestDTO gameRequestDTO) {
    if (gameRequestDTO.gameDuration() != null) {
      Duration requestedDuration = Duration.ofSeconds(gameRequestDTO.gameDuration());
      if (gameProperties.optionalDurations().contains(requestedDuration)) {
        return requestedDuration;
      }
    }
    return gameProperties.defaultDuration();
  }

  private KumiteGame saveGame(KumiteGame kumiteGame) {
    log.debug("Saving game with ID: {}", kumiteGame.getId());
    return kumiteGameRepository.save(kumiteGame);
  }
}
