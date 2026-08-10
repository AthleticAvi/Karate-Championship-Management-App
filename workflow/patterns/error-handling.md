# Error Handling

Applies when: mapping an exception to a response, exception handler, problem detail, error body shape, catch-all
Status: enforced
Framework-first: RFC 9457 problem details are a first-class framework type with a base handler class that already maps every framework exception. Never invent an error body.

## Response format

Errors use the standard problem-detail media type and body. It is a specified format with a defined content type, so clients and tooling can parse it without a bespoke contract.

A hand-rolled error type — typically a status integer and a message string — reimplements a subset of the standard badly: no type identifier, no instance, no extension fields, and a content type that lies about the payload.

Extend the framework's base exception handler class and register it as the global advice. Doing so maps every exception the framework itself raises — unreadable body, unsupported media type, missing parameter, failed binding — to correct statuses and problem-detail bodies, with no code. Everything not overridden is inherited correctly rather than falling through to a catch-all.

```java
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(WidgetNotFoundException.class)
    ProblemDetail handleNotFound(WidgetNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Widget not found");
        problem.setType(URI.create("https://example.invalid/problems/widget-not-found"));
        return problem;
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    ProblemDetail handleConflict(IllegalStateTransitionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
```

Override the base class's binding-failure hook to add per-field detail as an extension property rather than writing a separate handler for it — otherwise two handlers compete for the same exception.

## Rules

- **Status semantics are decided once**, per the table in `patterns/service-exposure.md`. A handler that returns the wrong status is a defect even when the message is right.
- **Every custom exception has exactly one handler**, and every handler has at least one throw site. An exception type that is never thrown, or a handler for one, is dead code that suggests coverage which does not exist.
- **The exception type carries the meaning**, not the message. Two different failures reported by the same type cannot be mapped to different statuses no matter what the message says. Where a lookup can fail for "not a valid value" and "valid but not present here", those are two types.
- **Bare `IllegalArgumentException` and parse failures map to 400**, not 500. Left unmapped they reach the catch-all and report a client mistake as a server fault.
- **The catch-all returns 500 and a generic detail.** Never reflect the exception message into the response body from the catch-all — an unhandled exception's message may contain internal identifiers, query fragments or paths.
- **Log server faults, not client faults.** A 400 is normal traffic; logging it at error level trains people to ignore the error log. Log 5xx at error with the stack trace, 4xx at debug if at all.
- **Never handle exceptions in a controller.** A `try`/`catch` in a controller duplicates the advice and diverges from it.

## Messages

The detail field is read by a developer integrating against the API. It states what was wrong and what would be acceptable. It never contains a stack trace, an internal identifier, a class name, or anything derived from a secret.

Never reflect unvalidated client input back into a message verbatim.

## Extension fields

Per-field validation failures, a correlation identifier, or a retry hint go in extension properties on the problem detail, not concatenated into the detail string. That keeps the body machine-readable.

Include the correlation identifier on every error response so a report can be tied to a log entry. See `patterns/logging.md`.

## Verify

- Every handler has a test asserting status, content type, and body shape.
- Every custom exception type has a throw site and a handler.
- An unmapped exception returns 500 with a generic body containing nothing internal.
- A validation failure returns every failing field, not the first.

## Sources

- RFC 9457 — Problem Details for HTTP APIs. Defines the body fields and the `application/problem+json` media type. Supersedes RFC 7807.
