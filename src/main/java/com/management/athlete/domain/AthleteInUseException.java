package com.management.athlete.domain;

/** A game refers to this athlete, so deleting it would leave that game naming nobody. */
public class AthleteInUseException extends RuntimeException {

  public AthleteInUseException(String message) {
    super(message);
  }
}
