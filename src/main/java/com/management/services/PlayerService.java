package com.management.services;

import com.management.dto.PlayerRequestDTO;
import com.management.enums.PointsType;
import com.management.exceptions.PlayerNotFoundException;
import com.management.models.Player;
import com.management.repositories.PlayerRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * The fighter aggregate: creation, retrieval, and score mutation, all keyed by fighter id.
 *
 * <p>A leaf service — it knows nothing about matches. Which fighter a colour refers to in a given
 * match is {@code KumiteGameService}'s knowledge; it resolves the id and calls down here. That one
 * direction of dependency is what replaced the {@code KumiteGameService} and {@code PlayerService}
 * cycle and the {@code GameHelperService}/{@code @Lazy} workaround that papered over it (#53).
 *
 * <p><strong>Scoring retries on conflict.</strong> Every mutation is read-modify-write, and two
 * referees scoring the same fighter at the same moment used to lose one of the points silently.
 * {@code @Version} on {@link Player} turns the overwrite into an {@code
 * OptimisticLockingFailureException}; {@code @Retryable} re-runs the whole method — a fresh read,
 * the change re-applied, a new save — a bounded number of times. The attempt count and jitter are
 * sized for a realistic burst of simultaneous referees: with N contenders one write wins each
 * round, so a loser needs up to N-1 rounds, and jittered short delays stop the losers colliding
 * again in lockstep (three fixed-delay retries demonstrably exhausted under a six-way burst).
 * Retrying is safe precisely because these are relative changes ("add a point"), not absolute ones.
 * Exhausting the attempts propagates the exception, which the handler maps to 409 rather than
 * letting it vanish.
 */
@Service
public class PlayerService {
  private static final Logger log = LoggerFactory.getLogger(PlayerService.class);
  private static final String PLAYER_NOT_FOUND = "Player not found";
  private static final String PLAYER_ID = " Player ID: ";

  private final PlayerRepository playerRepository;

  public PlayerService(PlayerRepository playerRepository) {
    this.playerRepository = playerRepository;
  }

  public Player createPlayer(PlayerRequestDTO playerDTO) {
    Player newPlayer = new Player(playerDTO.name());
    return playerRepository.save(newPlayer);
  }

  public Player getPlayer(String playerId) {
    Optional<Player> fetchedPlayer = playerRepository.findById(playerId);
    if (fetchedPlayer.isEmpty()) {
      log.error("PlayerService - getPlayer - {}  {}: {}", PLAYER_NOT_FOUND, PLAYER_ID, playerId);
      throw new PlayerNotFoundException(PLAYER_NOT_FOUND + PLAYER_ID + playerId);
    }
    return fetchedPlayer.get();
  }

  public Player updatePlayer(String playerId, Player playerDetails) {

    Player player = getPlayer(playerId);
    player.setName(playerDetails.getName());
    player.setPoints(playerDetails.getPoints());
    player.setFouls(playerDetails.getFouls());

    return playerRepository.save(player);
  }

  public void deletePlayer(String playerId) {
    Player player = getPlayer(playerId);
    playerRepository.delete(player);
  }

  @Retryable(
      includes = OptimisticLockingFailureException.class,
      maxRetries = 9,
      delay = 20,
      jitter = 20,
      multiplier = 1.5,
      maxDelay = 200)
  public Player addPoint(String playerId, PointsType pointType) {
    Player player = getPlayer(playerId);
    player.addPoint(pointType);
    return playerRepository.save(player);
  }

  @Retryable(
      includes = OptimisticLockingFailureException.class,
      maxRetries = 9,
      delay = 20,
      jitter = 20,
      multiplier = 1.5,
      maxDelay = 200)
  public Player removePoint(String playerId, PointsType pointType) {
    Player player = getPlayer(playerId);
    player.removePoint(pointType);
    return playerRepository.save(player);
  }

  @Retryable(
      includes = OptimisticLockingFailureException.class,
      maxRetries = 9,
      delay = 20,
      jitter = 20,
      multiplier = 1.5,
      maxDelay = 200)
  public Player addFoul(String playerId) {
    Player player = getPlayer(playerId);
    player.addFoul();
    return playerRepository.save(player);
  }

  @Retryable(
      includes = OptimisticLockingFailureException.class,
      maxRetries = 9,
      delay = 20,
      jitter = 20,
      multiplier = 1.5,
      maxDelay = 200)
  public Player removeFoul(String playerId) {
    Player player = getPlayer(playerId);
    player.removeFoul();
    return playerRepository.save(player);
  }
}
