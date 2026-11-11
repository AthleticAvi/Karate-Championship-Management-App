package com.management.athlete.domain;

/** No athlete has this id. */
public class AthleteNotFoundException extends RuntimeException {

  public AthleteNotFoundException(String message) {
    super(message);
  }
}
