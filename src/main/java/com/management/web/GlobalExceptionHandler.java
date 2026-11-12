package com.management.web;

import com.management.account.domain.AccountAlreadyExistsException;
import com.management.account.domain.AccountNotFoundException;
import com.management.account.domain.LastAdminException;
import com.management.athlete.domain.AthleteChangedException;
import com.management.athlete.domain.AthleteInUseException;
import com.management.athlete.domain.AthleteNotFoundException;
import com.management.athlete.web.AthleteMapper;
import com.management.config.KumiteGameProperties;
import com.management.kumitegame.domain.ActionForbiddenException;
import com.management.kumitegame.domain.ActionRefusedException;
import com.management.kumitegame.domain.GameChangedException;
import com.management.kumitegame.domain.GameNotFoundException;
import com.management.kumitegame.domain.InvalidGameRequestException;
import com.management.kumitegame.web.KumiteGameMapper;
import com.management.referee.domain.RefereeChangedException;
import com.management.referee.domain.RefereeInUseException;
import com.management.referee.domain.RefereeNotFoundException;
import com.management.referee.web.RefereeMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.exc.StreamConstraintsException;

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
 * ProblemDetail} is a first-class framework type and {@code
 * context/workflow/web-frameworks/spring-boot/design/error-handling.md} forbids inventing one.
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

  private final KumiteGameProperties gameProperties;

  /** The side colours, so a game sent back in {@code current} reads as every other answer does. */
  public GlobalExceptionHandler(KumiteGameProperties gameProperties) {
    this.gameProperties = gameProperties;
  }

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

  /**
   * Missing or wrong credentials. One fixed detail, so nothing says which of the two it was or
   * whether the account exists.
   */
  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleUnauthenticated(AuthenticationException ex) {
    log.debug("Authentication failed: {}", ex.getClass().getSimpleName());
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, "Valid credentials are required.");
    problem.setTitle("Authentication required");
    return problem;
  }

  /** Accounts could not be read, so nobody knows if the credentials are right: not a 401. */
  @ExceptionHandler(AuthenticationServiceException.class)
  public ProblemDetail handleAuthenticationFault(AuthenticationServiceException ex) {
    return handleUnexpectedException(ex);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    log.debug("Access denied");
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "This account may not do this.");
    problem.setTitle("Access denied");
    return problem;
  }

  /**
   * An action the account's role may not take on the game as it stands. The role table admitted the
   * endpoint, so the game decided, and the detail says which move needs an Admin.
   */
  @ExceptionHandler(ActionForbiddenException.class)
  public ProblemDetail handleActionForbidden(ActionForbiddenException ex) {
    return clientError(HttpStatus.FORBIDDEN, "Access denied", ex);
  }

  @ExceptionHandler(AccountNotFoundException.class)
  public ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
    return notFound("Account not found", ex);
  }

  @ExceptionHandler(AccountAlreadyExistsException.class)
  public ProblemDetail handleAccountAlreadyExists(AccountAlreadyExistsException ex) {
    return clientError(HttpStatus.CONFLICT, "Account already exists", ex);
  }

  @ExceptionHandler(LastAdminException.class)
  public ProblemDetail handleLastAdmin(LastAdminException ex) {
    return clientError(HttpStatus.CONFLICT, "Last Admin", ex);
  }

  @ExceptionHandler(GameNotFoundException.class)
  public ProblemDetail handleGameNotFound(GameNotFoundException ex) {
    return notFound("Game not found", ex);
  }

  @ExceptionHandler(AthleteNotFoundException.class)
  public ProblemDetail handleAthleteNotFound(AthleteNotFoundException ex) {
    return notFound("Athlete not found", ex);
  }

  @ExceptionHandler(RefereeNotFoundException.class)
  public ProblemDetail handleRefereeNotFound(RefereeNotFoundException ex) {
    return notFound("Referee not found", ex);
  }

  /**
   * A rename that lost to another write: 409, with the referee as it now stands.
   *
   * <p>A rename sets a value, so retrying it would overwrite a name the caller never saw. The
   * current referee goes back in a {@code current} extension property, so the caller can decide
   * without a second read.
   */
  @ExceptionHandler(RefereeChangedException.class)
  public ProblemDetail handleRefereeChanged(RefereeChangedException ex) {
    return changed(ex, "referee", RefereeMapper.toResponse(ex.current()));
  }

  /** An update that lost to another write: 409, with the athlete as it now stands. */
  @ExceptionHandler(AthleteChangedException.class)
  public ProblemDetail handleAthleteChanged(AthleteChangedException ex) {
    return changed(ex, "athlete", AthleteMapper.toResponse(ex.current()));
  }

  /**
   * An absolute action on a game that lost to another write: 409, with the game as it now stands.
   *
   * <p>Setting the time, recording a result or changing the settings sets a value, so retrying it
   * would overwrite what the caller never saw.
   */
  @ExceptionHandler(GameChangedException.class)
  public ProblemDetail handleGameChanged(GameChangedException ex) {
    return changed(
        ex, "game", KumiteGameMapper.toResponse(ex.current(), gameProperties.sideColours()));
  }

  @ExceptionHandler(AthleteInUseException.class)
  public ProblemDetail handleAthleteInUse(AthleteInUseException ex) {
    return clientError(HttpStatus.CONFLICT, "Athlete in use", ex);
  }

  @ExceptionHandler(RefereeInUseException.class)
  public ProblemDetail handleRefereeInUse(RefereeInUseException ex) {
    return clientError(HttpStatus.CONFLICT, "Referee in use", ex);
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
   * Bean Validation failures, with every failing field listed.
   *
   * <p>Overrides the base class's hook rather than declaring a separate {@code @ExceptionHandler} —
   * two handlers competing for the same exception is the failure mode {@code
   * context/workflow/web-frameworks/spring-boot/design/error-handling.md} warns about. The
   * per-field messages go in an {@code errors} extension property so the body stays
   * machine-readable: a caller fixing three fields gets all three in one round trip, keyed by field
   * path.
   */
  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(fieldError ->
            errors.merge(
                fieldError.getField(),
                String.valueOf(fieldError.getDefaultMessage()),
                (first, second) -> first + "; " + second));

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(status, "The request failed validation.");
    problem.setTitle("Invalid request");
    problem.setProperty("errors", errors);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  /**
   * A query value that does not convert, such as a game status that does not exist.
   *
   * <p>The framework's detail repeats the value the caller sent. Only a message this project wrote
   * is returned, so the detail names the parameter alone.
   */
  @Override
  protected @Nullable ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String parameter = ex.getPropertyName();
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            status,
            parameter == null
                ? "A value sent is not one it accepts."
                : "The value of " + parameter + " is not one it accepts.");
    problem.setTitle("Invalid request");
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  /**
   * A body past the JSON parser's size limit is 413, not the framework's 400.
   *
   * <p>The caller's JSON may be perfectly valid, so a parse error would send them looking for the
   * wrong fault. Everything else unreadable stays with the base class, including the parser's other
   * limits, such as nesting depth, which a smaller body would not cure.
   */
  @Override
  protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (!isBodyTooLong(ex)) {
      return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }
    HttpStatus tooLarge = HttpStatus.CONTENT_TOO_LARGE;
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(tooLarge, "The request body is too large.");
    problem.setTitle("Request too large");
    return handleExceptionInternal(ex, problem, headers, tooLarge, request);
  }

  /**
   * A concurrent modification the application could not absorb.
   *
   * <p>An append to a game is retried; this handler sees only a conflict that persisted through
   * every attempt. The detail names nothing, because it covers every document, and the framework's
   * own message can carry document identifiers and query fragments, which is internal text.
   *
   * <p>A transaction that lost a write conflict through every attempt arrives as a data integrity
   * violation, because that is what the mapping layer makes of one. A duplicate key on a create is
   * caught in the service, so nothing else raises it here.
   */
  @ExceptionHandler({
    org.springframework.dao.OptimisticLockingFailureException.class,
    org.springframework.dao.DataIntegrityViolationException.class
  })
  public ProblemDetail handleConcurrentWrite(org.springframework.dao.DataAccessException ex) {
    log.warn("A concurrent write reached the handler", ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Someone else changed this at the same moment. Retry the operation.");
    problem.setTitle("Concurrent update");
    return problem;
  }

  /**
   * An action refused because the data would contradict itself.
   *
   * <p>409 rather than 400: the request was well formed and the game exists, and it is what the
   * game already holds that forbids it. The detail is echoed because this project wrote it, and it
   * names what conflicts.
   */
  @ExceptionHandler(ActionRefusedException.class)
  public ProblemDetail handleActionRefused(ActionRefusedException ex) {
    return clientError(HttpStatus.CONFLICT, "Action refused", ex);
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
   * InvalidGameRequestException} instead and keeps its message; request-shape validation is
   * constraints at the edge since #39 and never reaches this handler.
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

  /** A write that lost to another: 409, with what it lost to in {@code current}. */
  private static ProblemDetail changed(Exception ex, String noun, Object current) {
    log.debug("Concurrent update (409): {}", ex.getMessage());
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Someone else changed this "
                + noun
                + " at the same moment. It is returned as it now stands.");
    problem.setTitle("Concurrent update");
    problem.setProperty("current", current);
    return problem;
  }

  /** Jackson has one exception type for every limit, so only its wording tells them apart. */
  private static boolean isBodyTooLong(HttpMessageNotReadableException ex) {
    return ex.getMostSpecificCause() instanceof StreamConstraintsException limit
        && limit.getOriginalMessage().startsWith("Document length");
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
