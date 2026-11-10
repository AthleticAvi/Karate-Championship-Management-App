package com.management.account.domain;

/** Removing this account would leave nobody able to manage accounts. */
public class LastAdminException extends RuntimeException {

  public LastAdminException(String message) {
    super(message);
  }
}
