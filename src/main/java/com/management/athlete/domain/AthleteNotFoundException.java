package com.management.athlete.domain;

import com.management.domain.IdentityNotFoundException;

/** No athlete has this id. */
public class AthleteNotFoundException extends IdentityNotFoundException {

  public AthleteNotFoundException(String message) {
    super("athlete", message);
  }
}
