package com.management.services;

import com.management.config.GameProperties;
import com.management.dto.KumiteGameRequestDTO;
import com.management.dto.PlayerDTO;
import com.management.dto.PlayerRequestDTO;
import com.management.enums.GameState;
import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.exceptions.GameNotFoundException;
import com.management.exceptions.IllegalStateTransitionException;
import com.management.exceptions.InvalidGameRequestException;
import com.management.exceptions.PlayerNotFoundException;
import com.management.models.GameWithFighters;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.models.Referee;
import com.management.repositories.KumiteGameRepository;
import com.management.util.KumiteGameManagementUtils;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  public KumiteGameService(
      KumiteGameRepository kumiteGameRepository,
      GameProperties gameProperties,
      PlayerService playerService) {
    this.kumiteGameRepository = kumiteGameRepository;
    this.gameProperties = gameProperties;
    this.playerService = playerService;
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
    PointsType point = KumiteGameManagementUtils.mapPointToPointType(pointType);
    PlayerColor scoringColour = KumiteGameManagementUtils.mapPlayerColor(color);
    Player scored = playerService.addPoint(fighterId(kumiteGame, scoringColour), point);
    return withFighters(kumiteGame, scoringColour, scored);
  }

  public GameWithFighters removePoint(String gameId, String color, String pointType) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    PointsType point = KumiteGameManagementUtils.mapPointToPointType(pointType);
    PlayerColor scoringColour = KumiteGameManagementUtils.mapPlayerColor(color);
    Player scored = playerService.removePoint(fighterId(kumiteGame, scoringColour), point);
    return withFighters(kumiteGame, scoringColour, scored);
  }

  public GameWithFighters addFoul(String gameId, String color) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    PlayerColor penalisedColour = KumiteGameManagementUtils.mapPlayerColor(color);
    Player penalised = playerService.addFoul(fighterId(kumiteGame, penalisedColour));
    return withFighters(kumiteGame, penalisedColour, penalised);
  }

  public GameWithFighters removeFoul(String gameId, String color) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    PlayerColor penalisedColour = KumiteGameManagementUtils.mapPlayerColor(color);
    Player penalised = playerService.removeFoul(fighterId(kumiteGame, penalisedColour));
    return withFighters(kumiteGame, penalisedColour, penalised);
  }

  public GameWithFighters updateKumiteGameWinner(String gameId, String color) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    PlayerColor playerColor = KumiteGameManagementUtils.mapPlayerColor(color);
    kumiteGame.updateWinner(playerColor);
    return withFighters(saveGame(kumiteGame));
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
