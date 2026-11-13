package com.management.domain;

/**
 * No named identity of one kind, such as an athlete, has this id. Each kind has its own subclass,
 * and the one handler for them all titles the answer with {@link #noun()}.
 */
public abstract class IdentityNotFoundException extends RuntimeException {

  private final String noun;

  /** The noun is in lower case, such as {@code athlete}. */
  protected IdentityNotFoundException(String noun, String message) {
    super(message);
    this.noun = noun;
  }

  public String noun() {
    return noun;
  }
}
