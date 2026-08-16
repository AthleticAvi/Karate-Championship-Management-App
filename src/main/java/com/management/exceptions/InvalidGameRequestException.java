package com.management.exceptions;

/**
 * A request this application has inspected and rejected, carrying a message written for the caller.
 *
 * <p><strong>Why a type rather than a bare {@link IllegalArgumentException}.</strong> The handler
 * for that exception cannot tell a message this project authored — "Players cannot be empty" — from
 * one thrown deep inside a library, such as Spring Data's "The given id must not be null". Both
 * arrived as 400 with the message echoed to the client, which meant a server-side fault was
 * reported as the caller's mistake with internal text attached. This type marks the messages that
 * are safe to return because we wrote them; everything else falls back to a generic detail.
 *
 * <p>Extends {@link IllegalArgumentException} so it remains an illegal argument to any caller
 * reasoning about it as one, and so #36's contract that bad input answers 400 holds by inheritance
 * even if this class's own handler is ever removed.
 */
public class InvalidGameRequestException extends IllegalArgumentException {
  public InvalidGameRequestException(String message) {
    super(message);
  }
}
