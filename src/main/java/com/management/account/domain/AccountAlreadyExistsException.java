package com.management.account.domain;

/** An account already has this username. */
public class AccountAlreadyExistsException extends RuntimeException {

  public AccountAlreadyExistsException(String message, Throwable cause) {
    super(message, cause);
  }
}
