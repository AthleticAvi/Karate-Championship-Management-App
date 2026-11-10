package com.management.referee.domain;

/** No referee has this id. */
public class RefereeNotFoundException extends RuntimeException {

  public RefereeNotFoundException(String message) {
    super(message);
  }
}
