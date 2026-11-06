# exceptions

## Index

- [purpose] Purpose
- [owns] Owns
- [contract] Error contract
- [facts] Project facts

## [purpose] Purpose

The failure types business code throws, and the one place they become HTTP responses.

## [owns] Owns

- `GlobalExceptionHandler`: the inbound gateway for failures. Extends
  `ResponseEntityExceptionHandler`, so framework exceptions map too rather than falling through to
  the catch-all as 500s.
- One exception type per meaning.

## [contract] Error contract

Errors are RFC 9457 problem details: `application/problem+json`, with `status`, `title`, `detail`
and `instance`. `type` is not sent, because no problem has a page to point at.

| Failure | Status | Exception | Detail |
|---|---|---|---|
| Request fails Bean Validation | 400 | `MethodArgumentNotValidException` (framework) | generic, plus an `errors` extension property mapping each failing field path to its message |
| Not a colour (`color=purple`) | 400 | `InvalidPlayerColorException` | the message |
| Not a point type | 400 | `PointTypeNotFoundException` | the message |
| A request this app validated and rejected | 400 | `InvalidGameRequestException` | the message |
| Any other illegal argument, incl. `NumberFormatException` | 400 | `IllegalArgumentException` | **generic**, message logged |
| Missing or wrong credentials | 401 | `AuthenticationException` (framework) | fixed, the same for every cause |
| Valid credentials without the role | 403 | `AccessDeniedException` (framework) | fixed |
| Body past the JSON size limit | 413 | `HttpMessageNotReadableException` (framework) | generic |
| A lifecycle call the match's state forbids | 409 | `IllegalStateTransitionException` | the message |
| No such match | 404 | `GameNotFoundException` | the message |
| No such fighter, or a real colour this match does not field | 404 | `PlayerNotFoundException` | the message |
| Accounts could not be read while checking credentials | 500 | `AuthenticationServiceException` (framework) | **generic**, message logged |
| Anything else | 500 | catch-all | **generic**, message logged |

## [facts] Project facts

- The 413 is told apart by the wording of the Jackson message, because every parser limit raises the
  same type. `RequestLimitsIT` fails if an upgrade changes it. The other limits, such as nesting
  depth, stay 400.
- **Tomcat refuses some requests before Spring runs,** so they are not problem details. Headers past
  the limit answer 400 as `text/html`, and a silent connection answers 408. No part of this
  application sees them, so they carry no correlation ID and leave no request line.
- **A 401 and a 403 start in the security filter chain, outside Spring MVC.** `SecurityFailureResponder`
  hands them to this handler through the framework's exception resolver, so they get the same form,
  `instance` and content type as every other failure. A 401 sends no challenge: see the security
  README.
- **Spring Security's firewall refuses some requests before the chain,** such as a path with a `;`.
  They answer 400 through the error dispatch, with a correlation ID, and are not problem details.
- **A CORS refusal is not a problem detail.** It is the framework's 403, answered before Spring MVC:
  see `[cors]` in the controllers README.
- The two colour failures differ on purpose: *not a colour* is the caller's mistake (400), *a colour
  this match does not have* is a missing resource (404).
- **Only messages this project wrote are returned.** `InvalidGameRequestException` marks wording
  meant for the caller, so its detail is echoed. A bare `IllegalArgumentException` can come from
  anywhere, such as Spring Data's "The given id must not be null", so it answers 400 with a fixed
  detail and the real message in the log.
