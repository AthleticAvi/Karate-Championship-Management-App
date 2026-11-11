package com.management.kumitegame.service;

import com.management.kumitegame.data.KumiteGameDocument;
import com.management.kumitegame.data.KumiteGameDocumentMapper;
import com.management.kumitegame.data.KumiteGameRepository;
import com.management.kumitegame.domain.GameNotFoundException;
import com.management.kumitegame.domain.KumiteGame;
import com.management.kumitegame.domain.Recorded;
import java.util.function.Function;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Applies an action to a game and saves it, checked against the version that was read.
 *
 * <p><strong>An append is retried</strong> when another write lands first: re-read, re-apply,
 * bounded, with Spring Framework's own {@code @Retryable}. Appending is relative, so applying it to
 * the newer game is what the caller meant. A retry that finds its entry id already on record
 * applies nothing, which is what makes retrying safe. <strong>An absolute action is not
 * retried</strong>: the lost race goes back to the caller.
 *
 * <p><strong>A separate bean, not a private method.</strong> {@code @Retryable} is applied by a
 * proxy, so a service calling its own annotated method would bypass it.
 *
 * <p>The action is applied to a freshly read game on every attempt, and reads the time itself, so a
 * retried entry takes the instant and order of the attempt that lands.
 */
@Service
public class KumiteGameWriter {

  private final KumiteGameRepository kumiteGameRepository;

  public KumiteGameWriter(KumiteGameRepository kumiteGameRepository) {
    this.kumiteGameRepository = kumiteGameRepository;
  }

  /** The game after the action, and what the action did. */
  public record Applied(KumiteGame game, Recorded recorded) {}

  @Retryable(
      includes = OptimisticLockingFailureException.class,
      maxRetries = 9,
      delay = 20,
      jitter = 20,
      multiplier = 1.5,
      maxDelay = 200)
  public Applied append(String gameId, Function<KumiteGame, Recorded> action) {
    return applyOnce(gameId, action);
  }

  /**
   * One attempt. A lost race raises {@link OptimisticLockingFailureException}. A retried entry
   * already on record writes nothing.
   */
  public Applied applyOnce(String gameId, Function<KumiteGame, Recorded> action) {
    KumiteGameDocument stored =
        kumiteGameRepository
            .findById(gameId)
            .orElseThrow(() -> new GameNotFoundException("No game has the id " + gameId));
    KumiteGame game = KumiteGameDocumentMapper.toDomain(stored);
    Recorded recorded = action.apply(game);
    if (recorded.replayed()) {
      return new Applied(game, recorded);
    }
    KumiteGameDocument saved =
        kumiteGameRepository.save(KumiteGameDocumentMapper.toDocument(game, stored));
    return new Applied(KumiteGameDocumentMapper.toDomain(saved), recorded);
  }
}
