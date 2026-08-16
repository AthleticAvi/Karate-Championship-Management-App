package com.management.services;

import com.management.dto.KumiteGameRequestDTO;
import com.management.dto.PlayerDTO;
import com.management.dto.PlayerRequestDTO;
import com.management.enums.GameState;
import com.management.enums.PlayerColor;
import com.management.exceptions.GameNotFoundException;
import com.management.exceptions.IllegalStateTransitionException;
import com.management.exceptions.InvalidGameRequestException;
import com.management.exceptions.PlayerNotFoundException;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.models.Referee;
import com.management.repositories.KumiteGameRepository;
import com.management.util.GameConfig;
import com.management.util.KumiteGameManagementUtils;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class KumiteGameService {

  private static final Logger log = LoggerFactory.getLogger(KumiteGameService.class);
  private static final String GAME_NOT_FOUND = "Game not found!";
  private static final String GAME_ID = " Game Id: ";
  @Autowired private KumiteGameRepository kumiteGameRepository;

  private final GameConfig config = new GameConfig();
  @Autowired @Lazy private GameHelperService gameHelperService;

  public KumiteGame createKumiteGame(KumiteGameRequestDTO gameRequestDTO) {

    validateGameRequestDTO(gameRequestDTO);

    // Resolve every colour before creating anything. The colours are what decide whether this
    // request can produce a valid match, and a player saved for a request that turns out to be
    // malformed cannot be taken back -- this service has no transaction to roll back with, because
    // the database is a standalone MongoDB. See validateColours for what makes a set valid.
    Map<PlayerColor, PlayerDTO> byColour = new EnumMap<>(PlayerColor.class);
    gameRequestDTO
        .getPlayersMap()
        .forEach(
            (key, playerDTO) -> {
              PlayerColor playerColor =
                  KumiteGameManagementUtils.mapPlayerColor(playerDTO.getColor());
              PlayerDTO clash = byColour.put(playerColor, playerDTO);
              if (clash != null) {
                throw new InvalidGameRequestException(
                    "A match fields exactly one fighter per colour, but two were given as "
                        + playerColor
                        + ".");
              }
            });
    validateColours(byColour.keySet());

    Map<PlayerColor, Player> playersMap = new EnumMap<>(PlayerColor.class);
    byColour.forEach(
        (playerColor, requested) -> {
          PlayerRequestDTO playerRequestDTO = new PlayerRequestDTO();
          playerRequestDTO.setName(requested.getName());
          playersMap.put(playerColor, gameHelperService.createNewPlayer(playerRequestDTO));
        });

    List<Referee> refereesList =
        gameRequestDTO.getRefereeList().stream().map(Referee::new).toList();

    Duration gameDuration = determineGameDuration(gameRequestDTO);

    KumiteGame kumiteGame = new KumiteGame(playersMap, refereesList, gameDuration);

    return saveGame(kumiteGame);
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

  public KumiteGame getKumiteGame(String gameId) {

    Optional<KumiteGame> fetchedKumiteGame = kumiteGameRepository.findById(gameId);
    if (fetchedKumiteGame.isEmpty()) {
      log.error("KumiteGameService - getKumiteGame - couldn't find game with id: {}", gameId);
      throw new GameNotFoundException(GAME_NOT_FOUND + GAME_ID + gameId);
    }

    return fetchedKumiteGame.get();
  }

  public KumiteGame updateKumiteGamePlayers(String gameId, String color) {
    KumiteGame kumiteGame = getKumiteGame(gameId);
    PlayerColor playerColor = KumiteGameManagementUtils.mapPlayerColor(color);

    Player snapshot = kumiteGame.getPlayersMap().get(playerColor);
    if (snapshot == null) {
      throw new PlayerNotFoundException("Match " + gameId + " has no " + playerColor + " fighter.");
    }
    Player updatedPlayer = gameHelperService.getPlayerById(snapshot.getId());

    kumiteGame.updatePlayer(playerColor, updatedPlayer);
    return saveGame(kumiteGame);
  }

  public KumiteGame updateKumiteGameWinner(String gameId, String color) {

    KumiteGame kumiteGame = getKumiteGame(gameId);
    PlayerColor playerColor = KumiteGameManagementUtils.mapPlayerColor(color);
    kumiteGame.updateWinner(playerColor);

    return saveGame(kumiteGame);
  }

  private void validateGameRequestDTO(KumiteGameRequestDTO gameRequestDTO) {
    if (gameRequestDTO.getPlayersMap() == null || gameRequestDTO.getPlayersMap().isEmpty()) {
      throw new InvalidGameRequestException("Players cannot be empty");
    }
    if (gameRequestDTO.getRefereeList() == null || gameRequestDTO.getRefereeList().isEmpty()) {
      throw new InvalidGameRequestException("Referee list cannot be empty");
    }
  }

  /**
   * Enforces the one-of-each-colour rule at the point of creation.
   *
   * <p>A match fields exactly one RED and one BLUE — a settled domain rule, not a request-time
   * option. Nothing checked it before: the colour is read from each entry's own {@code color}
   * field, so two entries could both claim RED, and the resulting {@code EnumMap} would simply hold
   * one of them. The half-formed match was then saved along with both fighters, and the failure
   * surfaced later out of the response mapper as a 500 — after the writes had committed, with no id
   * returned and three orphaned documents left behind.
   *
   * <p>Checking here turns that into a 400 that changes nothing in the database, which is what the
   * caller can actually act on. {@code KumiteGameMapper} keeps its own check for the same
   * invariant: this one guards what enters the database, that one guards what leaves it, and a
   * document predating this guard can still be read back.
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
    if (gameRequestDTO.getGameDuration() != null) {
      Duration requestedDuration =
          Duration.ofSeconds(Integer.parseInt(gameRequestDTO.getGameDuration()));
      if (config.getOptionalDurations().contains(requestedDuration)) {
        return requestedDuration;
      }
    }
    return config.getDefaultDuration();
  }

  private KumiteGame saveGame(KumiteGame kumiteGame) {
    log.debug("Saving game with ID: {}", kumiteGame.getId());
    return kumiteGameRepository.save(kumiteGame);
  }
}
