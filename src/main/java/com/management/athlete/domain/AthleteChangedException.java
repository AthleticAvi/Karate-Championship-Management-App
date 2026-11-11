package com.management.athlete.domain;

/**
 * An update lost to another write on the same athlete.
 *
 * <p>An update is an absolute change, so it is not retried: that would overwrite values the caller
 * never saw. The athlete as it now stands goes back instead, so the caller can decide.
 */
public class AthleteChangedException extends RuntimeException {

  private final transient Athlete current;

  public AthleteChangedException(Athlete current, Throwable cause) {
    super("Athlete " + current.id() + " was changed by another request", cause);
    this.current = current;
  }

  public Athlete current() {
    return current;
  }
}
