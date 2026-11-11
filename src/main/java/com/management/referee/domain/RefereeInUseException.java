package com.management.referee.domain;

/** A game refers to this referee, so deleting it would leave that game naming nobody. */
public class RefereeInUseException extends RuntimeException {

  public RefereeInUseException(String message) {
    super(message);
  }
}
