package com.management.kumitegame.domain;

/**
 * An absolute action chosen against a version the game has since moved past, or that lost to
 * another write inside the request: setting the time, recording a result, changing the settings, or
 * approving, reopening or voiding.
 *
 * <p>Not applied and not retried, because it sets a value the caller chose while looking at what is
 * now stale. The game as it now stands goes back instead, with its version, so the caller can
 * decide.
 */
public class GameChangedException extends RuntimeException {

  private final transient GameRead current;

  public GameChangedException(GameRead current, Throwable cause) {
    super("Game " + current.game().id() + " was changed by another request", cause);
    this.current = current;
  }

  public GameRead current() {
    return current;
  }
}
