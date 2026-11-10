package com.management.referee.domain;

/**
 * A rename lost to another write on the same referee.
 *
 * <p>A rename is an absolute change, so it is not retried: that would overwrite a name the caller
 * never saw. The referee as it now stands goes back instead, so the caller can decide.
 */
public class RefereeChangedException extends RuntimeException {

  private final transient Referee current;

  public RefereeChangedException(Referee current, Throwable cause) {
    super("Referee " + current.id() + " was changed by another request", cause);
    this.current = current;
  }

  public Referee current() {
    return current;
  }
}
