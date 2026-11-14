package com.management.athlete.domain;

import com.management.domain.IdentityInUseException;

/** A game refers to this athlete, so deleting it would leave that game naming nobody. */
public class AthleteInUseException extends IdentityInUseException {

  public AthleteInUseException(String message) {
    super("athlete", message);
  }
}
