package com.management.kumitegame.service;

import com.management.kumitegame.data.KumiteGameDocument;
import com.management.kumitegame.data.KumiteGameDocumentMapper;
import com.management.kumitegame.data.KumiteGameRepository;
import com.management.kumitegame.domain.GameNotFoundException;
import com.management.kumitegame.domain.KumiteGame;
import com.management.kumitegame.domain.Recorded;
import com.management.kumitegame.domain.ResultRules;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
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
 * retried</strong>, and is checked against the version the caller read: a stale one, or a race lost
 * inside the request, goes back to the caller.
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
  private final ResultRules rules;

  public KumiteGameWriter(KumiteGameRepository kumiteGameRepository, ResultRules rules) {
    this.kumiteGameRepository = kumiteGameRepository;
    this.rules = rules;
  }

  /** The game after the action, the stored version it now stands at, and what the action did. */
  public record Applied(KumiteGame game, long version, Recorded recorded) {}

  @Retryable(
      includes = OptimisticLockingFailureException.class,
      maxRetries = 9,
      delay = 20,
      jitter = 20,
      multiplier = 1.5,
      maxDelay = 200)
  public Applied append(String gameId, Function<KumiteGame, Recorded> action) {
    return attempt(gameId, null, action);
  }

  /**
   * One attempt at an absolute action, made against {@code expectedVersion}, the version the caller
   * read. A stored game at any other version, or a race lost inside the request, raises {@link
   * OptimisticLockingFailureException} and writes nothing.
   *
   * <p>A retry of an action already on record answers as an append's does, whatever the version:
   * its first attempt is what moved the game on.
   */
  public Applied applyAt(
      String gameId, long expectedVersion, Function<KumiteGame, Recorded> action) {
    return attempt(gameId, expectedVersion, action);
  }

  /**
   * One attempt. The action goes first, so a retried entry already on record writes nothing and
   * answers, before the version is compared.
   *
   * @param expectedVersion the version an absolute action was chosen against; none for an append
   */
  private Applied attempt(
      String gameId, @Nullable Long expectedVersion, Function<KumiteGame, Recorded> action) {
    KumiteGameDocument stored =
        kumiteGameRepository
            .findById(gameId)
            .orElseThrow(() -> new GameNotFoundException("No game has the id " + gameId));
    KumiteGame game = KumiteGameDocumentMapper.toDomain(stored, rules);
    Recorded recorded = action.apply(game);
    if (recorded.replayed()) {
      return new Applied(game, stored.getVersion(), recorded);
    }
    if (expectedVersion != null && expectedVersion != stored.getVersion()) {
      throw new OptimisticLockingFailureException(
          "Game " + gameId + " is at version " + stored.getVersion() + ", not " + expectedVersion);
    }
    KumiteGameDocument saved =
        kumiteGameRepository.save(KumiteGameDocumentMapper.toDocument(game, stored));
    return new Applied(
        KumiteGameDocumentMapper.toDomain(saved, rules), saved.getVersion(), recorded);
  }
}
