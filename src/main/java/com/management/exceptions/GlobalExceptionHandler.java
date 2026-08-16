package com.management.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every exception this API can raise to a status code and an RFC 9457 problem detail.
 *
 * <p><strong>Extends the framework's base handler</strong>, which is what makes the exceptions the
 * framework itself raises — unreadable body, unsupported media type, missing parameter, failed
 * binding — come out as correct statuses and problem details without a line of code here. Anything
 * not overridden below is inherited correctly rather than falling through to the catch-all as a
 * 500.
 *
 * <p><strong>Problem details rather than a hand-rolled body.</strong> This class used to return a
 * nested {@code ErrorResponse} of a status integer and a message, served as {@code
 * application/json} — a worse subset of a specified format: no title, no type, no instance, no room
 * for extension fields, and a content type that did not say the payload was an error. {@code
 * ProblemDetail} is a first-class framework type and {@code workflow/patterns/error-handling.md}
 * forbids inventing one.
 *
 * <p><strong>The {@code type} field is deliberately left at its default.</strong> RFC 9457 defines
 * an absent type as {@code about:blank}, and a URI pointing at documentation that does not exist is
 * worse than no URI. Add one per problem when there is somewhere real to point it.
 *
 * <p><strong>Logging follows the status class.</strong> A 4xx is normal traffic and is logged at
 * debug, if at all; logging client mistakes at error level trains people to ignore the error log. A
 * 5xx is logged at error with the throwable, because it is the server's fault and someone has to be
 * able to find it.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * The catch-all.
   *
   * <p>Returns a generic detail and never reflects the exception message, which may carry internal
   * identifiers, query fragments or file paths. The message goes to the log instead, with its stack
   * trace, which is where it is useful and safe.
   */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpectedException(Exception ex) {
    log.error("Unhandled exception reached the catch-all", ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    problem.setTitle("Internal server error");
    return problem;
  }

  @ExceptionHandler(GameNotFoundException.class)
  public ProblemDetail handleGameNotFound(GameNotFoundException ex) {
    return notFound("Match not found", ex);
  }

  @ExceptionHandler(PlayerNotFoundException.class)
  public ProblemDetail handlePlayerNotFound(PlayerNotFoundException ex) {
    return notFound("Fighter not found", ex);
  }

  /**
   * An unparseable colour is 400, not 404.
   *
   * <p>The caller sent something that is not a colour; nothing is missing. Answering 404 sent them
   * hunting for a resource they never referenced. The genuinely different case — a real colour that
   * this match does not have — stays 404 as {@link PlayerNotFoundException}.
   */
  @ExceptionHandler(InvalidPlayerColorException.class)
  public ProblemDetail handleInvalidPlayerColor(InvalidPlayerColorException ex) {
    return badRequest("Invalid player colour", ex);
  }

  @ExceptionHandler(PointTypeNotFoundException.class)
  public ProblemDetail handleInvalidPointType(PointTypeNotFoundException ex) {
    return badRequest("Invalid point type", ex);
  }

  /**
   * Input this application inspected and rejected, with a message it wrote itself.
   *
   * <p>The detail is echoed because {@link InvalidGameRequestException} is only ever raised by this
   * project's own validation, so the text is known to be safe and useful to the caller.
   */
  @ExceptionHandler(InvalidGameRequestException.class)
  public ProblemDetail handleInvalidGameRequest(InvalidGameRequestException ex) {
    return badRequest("Invalid request", ex);
  }

  /**
   * A lifecycle operation on a match whose state does not allow it.
   *
   * <p>409 rather than 400: the request was syntactically fine and the match exists — it is the
   * resource's current state that forbids the operation, which is exactly what Conflict means. The
   * detail is echoed because the message is written by this project's own guard and names the
   * current state and the attempted transition, which is what the caller needs to recover.
   */
  @ExceptionHandler(IllegalStateTransitionException.class)
  public ProblemDetail handleIllegalStateTransition(IllegalStateTransitionException ex) {
    return clientError(HttpStatus.CONFLICT, "Illegal match-state transition", ex);
  }

  /**
   * Any other illegal argument, reported as bad input but <strong>without its message</strong>.
   *
   * <p>#36 requires that {@link IllegalArgumentException} and {@code NumberFormatException} answer
   * 400 rather than 500, and they still do — one handler covers both, since the latter is a
   * subclass. What changed is the body. This handler catches illegal arguments raised anywhere
   * beneath the controller, including inside the framework and the driver: Spring Data's "The given
   * id must not be null" and the enum conversion failures a legacy document can provoke are both
   * {@code IllegalArgumentException}s that say nothing a client should see and describe a fault the
   * client did not cause. Echoing them leaked internal text through a status that blamed the
   * caller.
   *
   * <p>So the message goes to the log, exactly as the catch-all does it, and the caller gets a
   * fixed detail. Validation whose wording is genuinely useful raises {@link
   * InvalidGameRequestException} instead and keeps its message.
   *
   * <p>Still a stopgap: the service parses the match duration out of a string by hand, and hand
   * validation inside a service is what Epic #38 replaces with constraints at the edge.
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
    log.warn("Illegal argument surfaced below the controller", ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "The request contained an invalid value.");
    problem.setTitle("Invalid request");
    return problem;
  }

  private ProblemDetail notFound(String title, Exception ex) {
    return clientError(HttpStatus.NOT_FOUND, title, ex);
  }

  private ProblemDetail badRequest(String title, Exception ex) {
    return clientError(HttpStatus.BAD_REQUEST, title, ex);
  }

  private ProblemDetail clientError(HttpStatus status, String title, Exception ex) {
    log.debug("{} ({}): {}", title, status.value(), ex.getMessage());
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    problem.setTitle(title);
    return problem;
  }
}
