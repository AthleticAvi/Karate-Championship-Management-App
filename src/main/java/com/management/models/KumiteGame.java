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
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A match: two fighter references, the officials, the clock, the state, and the result.
 *
 * <p><strong>Fighters are referenced, never embedded.</strong> The match stores each fighter's
 * document id keyed by colour; the fighter documents own their names and scores. The previous shape
 * embedded a full copy of each fighter here, which made every score two writes to two collections
 * with nothing tying them together — on a standalone MongoDB, with no transaction to span them, a
 * failure between the writes left the copies permanently disagreeing. One fact, one owner: the
 * score lives on {@link Player} and nowhere else, and reads compose the two aggregates (see {@link
 * GameWithFighters}).
 */
@Document
public class KumiteGame {
  @Id private String id;

  /**
   * Detects concurrent modification: a save against a stale version matches no document and raises
   * {@code OptimisticLockingFailureException} instead of silently overwriting.
   *
   * <p>Primitive {@code long}, so an unset version reads as {@code 0} — which Spring Data treats as
   * <em>new</em>. A document written before this field existed therefore cannot be updated: its
   * next save is attempted as an insert and fails on the duplicate identifier. That is accepted
   * rather than backfilled, because there is no production data; see {@code CLAUDE.md}.
   */
  @Version private long version;

  private GameState gameState;

  /** Fighter document ids keyed by colour. Absent on a document written before #49. */
  @Nullable private Map<PlayerColor, String> playerIds;

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

  /**
   * The mapper's way in (#52): builds an empty shell that the mapping layer populates field by
   * field from the stored document. Explicit, so loading no longer runs the new-match constructor
   * below and then overwrites its work — and so a future constructor-only field cannot be silently
   * reset on every read.
   */
  @PersistenceCreator
  KumiteGame() {}

  /** A new match: queued, undecided, full time on the clock. */
  public KumiteGame(
      Map<PlayerColor, String> playerIds, List<Referee> referees, Duration gameDuration) {
    this.gameState = GameState.QUEUED;
    this.playerIds = playerIds;
    this.referees = referees;
    this.gameDuration = gameDuration;
    this.remainingTime = gameDuration;
  }

  public void updateWinner(PlayerColor color) {
    if (playerIds == null || !playerIds.containsKey(color)) {
      throw new PlayerNotFoundException(
          "Match " + id + " has no " + color + " fighter to declare the winner.");
    }
    setWinner(color);
  }

  public String getId() {
    return id;
  }

  public long getVersion() {
    return version;
  }

  public GameState getGameState() {
    return gameState;
  }

  public void setGameState(GameState gameState) {
    this.gameState = gameState;
  }

  public @Nullable Map<PlayerColor, String> getPlayerIds() {
    return playerIds;
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

  /**
   * Identity equality on the persistent id (#60): two objects with the same id are the same stored
   * match, even if one is stale. An unsaved match has no id and is equal only to itself.
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof KumiteGame otherGame)) {
      return false;
    }
    return id != null && id.equals(otherGame.id);
  }

  /**
   * Constant, deliberately.
   *
   * <p>Hashing the identifier looks better distributed but breaks the contract: {@code save}
   * assigns the id, so an entity put in a {@code HashSet} before saving lands in one bucket and is
   * looked for in another afterwards — silently unreachable. A constant keeps the hash stable
   * across that transition, which is the property collections actually require; equality still
   * separates the instances.
   */
  @Override
  public int hashCode() {
    return KumiteGame.class.hashCode();
  }

  @Override
  public String toString() {
    return "KumiteGame{id="
        + id
        + ", state="
        + gameState
        + ", playerIds="
        + playerIds
        + ", winner="
        + winner
        + ", remainingTime="
        + remainingTime
        + "}";
  }
}
