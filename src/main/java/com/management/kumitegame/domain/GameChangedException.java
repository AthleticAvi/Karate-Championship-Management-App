package com.management.kumitegame.domain;

/**
 * An absolute action lost to another write on the same game: setting the time, recording a result,
 * or changing the settings.
 *
 * <p>Not retried, because it sets a value the caller chose while looking at what is now stale. The
 * game as it now stands goes back instead, so the caller can decide.
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
