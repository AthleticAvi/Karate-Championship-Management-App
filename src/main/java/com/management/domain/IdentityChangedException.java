package com.management.domain;

/**
 * A change to a named identity lost to another write on it.
 *
 * <p>A change sets values, so it is not retried: that would overwrite what the caller never saw.
 * The identity as it now stands goes back instead, so the caller can decide. Each kind has its own
 * subclass, and the one handler for them all answers with {@link #current()}.
 *
 * <p>{@link #current()} is an {@code Object} because Java allows no generic exception type.
 */
public abstract class IdentityChangedException extends RuntimeException {

  private final String noun;
  private final transient Object current;

  /**
   * The noun is in lower case, such as {@code athlete}.
   *
   * @param current the domain type as it now stands
   */
  protected IdentityChangedException(String noun, String message, Object current, Throwable cause) {
    super(message, cause);
    this.noun = noun;
    this.current = current;
  }

  public String noun() {
    return noun;
  }

  public Object current() {
    return current;
  }
}
