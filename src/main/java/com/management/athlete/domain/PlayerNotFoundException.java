package com.management.athlete.domain;

public class PlayerNotFoundException extends RuntimeException {
  public PlayerNotFoundException(String message) {
    super(message);
  }
}
