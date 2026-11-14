package com.management.domain;

/**
 * A game refers to this named identity, so deleting it would leave that game naming nobody. Each
 * kind has its own subclass, and the one handler for them all titles the answer with {@link
 * #noun()}.
 */
public abstract class IdentityInUseException extends RuntimeException {

  private final String noun;

  /** The noun is in lower case, such as {@code athlete}. */
  protected IdentityInUseException(String noun, String message) {
    super(message);
    this.noun = noun;
  }

  public String noun() {
    return noun;
  }
}
