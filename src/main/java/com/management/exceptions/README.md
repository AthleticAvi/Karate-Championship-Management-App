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

Errors are RFC 9457 problem details: `application/problem+json`, with `status`, `title`, `detail`.

| Failure | Status | Exception | Detail |
|---|---|---|---|
| Request fails Bean Validation | 400 | `MethodArgumentNotValidException` (framework) | generic, plus an `errors` extension property mapping each failing field path to its message |
| Not a colour (`color=purple`) | 400 | `InvalidPlayerColorException` | the message |
| Not a point type | 400 | `PointTypeNotFoundException` | the message |
| A request this app validated and rejected | 400 | `InvalidGameRequestException` | the message |
| Any other illegal argument, incl. `NumberFormatException` | 400 | `IllegalArgumentException` | **generic**, message logged |
| A lifecycle call the match's state forbids | 409 | `IllegalStateTransitionException` | the message |
| No such match | 404 | `GameNotFoundException` | the message |
| No such fighter, or a real colour this match does not field | 404 | `PlayerNotFoundException` | the message |
| Anything else | 500 | catch-all | **generic**, message logged |

## [facts] Project facts

- The two colour failures differ on purpose: *not a colour* is the caller's mistake (400), *a colour
  this match does not have* is a missing resource (404).
- **Only messages this project wrote are returned.** `InvalidGameRequestException` marks wording
  meant for the caller, so its detail is echoed. A bare `IllegalArgumentException` can come from
  anywhere, such as Spring Data's "The given id must not be null", so it answers 400 with a fixed
  detail and the real message in the log.
