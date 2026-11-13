package com.management.athlete.domain;

import com.management.domain.IdentityChangedException;

/** An update lost to another write on the same athlete. */
public class AthleteChangedException extends IdentityChangedException {

  public AthleteChangedException(Athlete current, Throwable cause) {
    super("athlete", "Athlete " + current.id() + " was changed by another request", current, cause);
  }
}
