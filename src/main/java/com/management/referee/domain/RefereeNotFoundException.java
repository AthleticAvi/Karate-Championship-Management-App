package com.management.referee.domain;

import com.management.domain.IdentityNotFoundException;

/** No referee has this id. */
public class RefereeNotFoundException extends IdentityNotFoundException {

  public RefereeNotFoundException(String message) {
    super("referee", message);
  }
}
