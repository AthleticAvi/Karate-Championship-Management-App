package com.management.referee.domain;

import com.management.domain.IdentityInUseException;

/** A game refers to this referee, so deleting it would leave that game naming nobody. */
public class RefereeInUseException extends IdentityInUseException {

  public RefereeInUseException(String message) {
    super("referee", message);
  }
}
