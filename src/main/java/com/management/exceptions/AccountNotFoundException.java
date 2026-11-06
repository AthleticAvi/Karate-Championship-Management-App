package com.management.exceptions;

/** No account has this username. */
public class AccountNotFoundException extends RuntimeException {

  public AccountNotFoundException(String message) {
    super(message);
  }
}
