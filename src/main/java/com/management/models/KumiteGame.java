package com.management.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.management.enums.GameState;
import com.management.enums.PlayerColor;
import com.management.exceptions.PlayerNotFoundException;
import com.management.models.converters.LegacyWinnerConverter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class KumiteGame {
  @Id private String id;
  private GameState gameState;
  private Map<PlayerColor, Player> playersMap;
  private List<Referee> referees;

  /**
   * The colour that won, or {@code null} while the match has no winner.
   *
   * <p>Was a display sentence — {@code "RED player: Kenji"}, starting life as the literal {@code
   * "Pending game ending"}. That is a value field doing a rendering job: no client could reliably
   * tell whether a match had been decided without comparing strings, and the fighter's name was
   * baked into a field that changes when the fighter is renamed. {@code java.md} forbids a magic
   * string for absent state; {@code null} is the absence.
   *
   * <p>{@link LegacyWinnerConverter} keeps documents written before that change readable — without
   * it, {@code Enum.valueOf} fails on the stored sentence and the whole match becomes unloadable.
   * See that class for why the conversion is bound to this property rather than to the type.
   */
  @ValueConverter(LegacyWinnerConverter.class)
  @Nullable
  private PlayerColor winner;

  private LocalDateTime startTime;
  private Duration remainingTime;
  private Duration gameDuration;
  @JsonIgnore @Transient private GameTimer timer;

  private static final Logger log = LoggerFactory.getLogger(KumiteGame.class);
  private static final String PLAYER_COLOR_NOT_FOUND = "Player color not found in the game";
  private static final String PLAYER_COLOR = " Player color: ";

  public KumiteGame(
      Map<PlayerColor, Player> playersMap, List<Referee> referees, Duration gameDuration) {
    this.gameState = GameState.QUEUED;
    this.playersMap = playersMap;
    this.referees = referees;
    this.gameDuration = gameDuration;
    this.remainingTime = gameDuration;
  }

  public void updatePlayer(PlayerColor color, Player updatedPlayer) {
    if (!(playersMap.containsKey(color))) {
      log.error(
          "KumiteGame - updatePlayer - {}, {}}: {}", PLAYER_COLOR_NOT_FOUND, PLAYER_COLOR, color);
      throw new PlayerNotFoundException(PLAYER_COLOR_NOT_FOUND + PLAYER_COLOR + color);
    }
    playersMap.put(color, updatedPlayer);
  }

  public void updateWinner(PlayerColor color) {
    if (!(playersMap.containsKey(color))) {
      log.error(
          "KumiteGame - updateWinner - {}, {}}: {}", PLAYER_COLOR_NOT_FOUND, PLAYER_COLOR, color);
      throw new PlayerNotFoundException(PLAYER_COLOR_NOT_FOUND + PLAYER_COLOR + color);
    }
    setWinner(color);
  }

  public String getId() {
    return id;
  }

  public GameState getGameState() {
    return gameState;
  }

  public void setGameState(GameState gameState) {
    this.gameState = gameState;
  }

  public Map<PlayerColor, Player> getPlayersMap() {
    return playersMap;
  }

  public void setPlayersMap(Map<PlayerColor, Player> playersMap) {
    this.playersMap = playersMap;
  }

  public List<Referee> getReferees() {
    return referees;
  }

  public void setReferees(List<Referee> referees) {
    this.referees = referees;
  }

  public @Nullable PlayerColor getWinner() {
    return winner;
  }

  public void setWinner(@Nullable PlayerColor winner) {
    this.winner = winner;
  }

  public void setStartTime(LocalDateTime startTime) {
    this.startTime = startTime;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public Duration getRemainingTime() {
    return remainingTime;
  }

  public void setRemainingTime(Duration remainingTime) {
    this.remainingTime = remainingTime;
  }

  public Duration getGameDuration() {
    return gameDuration;
  }

  public void setGameDuration(Duration gameDuration) {
    this.gameDuration = gameDuration;
  }

  /**
   * The match clock, rebuilt on demand from persisted state.
   *
   * <p>The {@code timer} field is {@code @Transient}, so a game loaded from MongoDB never has one.
   * Rebuilding here — from both {@code remainingTime} and {@code startTime} — is what makes every
   * lifecycle method safe against a freshly loaded game; a caller cannot forget a rebuild it never
   * has to perform. Dropping {@code startTime} from the reconstruction would silently freeze a
   * running clock (see {@link GameTimer}), so both values go in.
   *
   * <p>Order matters for callers that also mutate {@code startTime}: the rebuild captures the
   * persisted value at the first {@code getTimer()} call, so read the timer before overwriting the
   * field it rebuilds from.
   */
  public GameTimer getTimer() {
    if (timer == null) {
      timer = new GameTimer(remainingTime, startTime);
    }
    return timer;
  }
}
