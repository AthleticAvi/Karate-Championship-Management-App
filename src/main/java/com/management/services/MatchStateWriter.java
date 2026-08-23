package com.management.services;

import com.management.exceptions.GameNotFoundException;
import com.management.models.KumiteGame;
import com.management.repositories.KumiteGameRepository;
import java.util.function.Consumer;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Applies an idempotent change to a match document, retrying a losing write.
 *
 * <p>Recording the first scorer and recording the result are both write-once facts: the second
 * caller to try either is meant to change nothing. Without a retry they instead collide — the
 * versioned save loses and the caller gets a 409 for an operation that had already succeeded from
 * their point of view. Two referees scoring simultaneously on an untouched match hit exactly that,
 * because both see SENSHU as unclaimed and both try to claim it.
 *
 * <p><strong>A separate bean, not a private method.</strong> {@code @Retryable} is applied by a
 * proxy, so a service calling its own annotated method would bypass it entirely. The change is
 * re-applied to a freshly read document on every attempt, which is what makes retrying safe: {@code
 * recordFirstScorer} and {@code applyOutcome} both no-op once the fact is already recorded.
 */
@Service
public class MatchStateWriter {

  private final KumiteGameRepository kumiteGameRepository;

  public MatchStateWriter(KumiteGameRepository kumiteGameRepository) {
    this.kumiteGameRepository = kumiteGameRepository;
  }

  @Retryable(
      includes = OptimisticLockingFailureException.class,
      maxRetries = 9,
      delay = 20,
      jitter = 20,
      multiplier = 1.5,
      maxDelay = 200)
  public KumiteGame applyAndSave(String gameId, Consumer<KumiteGame> change) {
    KumiteGame fresh =
        kumiteGameRepository
            .findById(gameId)
            .orElseThrow(() -> new GameNotFoundException("Game not found! Game Id: " + gameId));
    change.accept(fresh);
    return kumiteGameRepository.save(fresh);
  }
}
