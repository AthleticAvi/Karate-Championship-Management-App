# filters

## Index

- [purpose] Purpose
- [owns] Owns
- [contract] Contract
- [may-call] May call
- [facts] Project facts

## [purpose] Purpose

The inbound gateway's first step: what happens to every request before and after a controller.

## [owns] Owns

`RequestCorrelationFilter`: the correlation ID, and the one log line per request.

## [contract] Contract

| Request sends `X-Correlation-ID` | Result |
|---|---|
| Nothing | A new UUID |
| 1 to 64 letters, digits, `_` or `-` | Used as it is |
| Anything else | Replaced with a new UUID, and not logged |

The response always returns the ID in `X-Correlation-ID`, on errors too. Every line logged while
the chain runs carries it as `correlationId`.

The request line is `Request completed` at INFO, with these fields:

| Field | Value |
|---|---|
| `method` | The HTTP method, or `OTHER` if it is not a standard one |
| `route` | The mapped pattern, such as `/api/v1/players/{playerId}`, or `UNMATCHED` |
| `status` | The status answered, 500 if the chain threw before answering |
| `durationMs` | Time spent in the chain |

## [may-call] May call

Nothing in this application. Never `services`, `repositories` or `rules`.

## [facts] Project facts

- **The route is logged, never the path.** A path holds what the client typed, so it could carry a
  line break or a credential into the logs. The pattern also groups cleanly in a query.
- `UNMATCHED` is a request no handler took. That is every refusal by the security chain, and so any
  unknown path or unlisted verb, because neither has a rule. The one exception is a path the chain
  lets through with nothing there, under the description's paths in `prod`: it reaches the static
  resource handler, so its route is `/**`.
- **Tomcat refuses some requests before any filter runs,** such as headers past the size limit. They
  get no ID and no line: see `[facts]` in the exceptions README.
- **An exception that escapes the chain outlives the ID.** The container logs it after this filter
  finishes, so that line has no `correlationId`. `GlobalExceptionHandler` catches everything an
  endpoint throws, so only a failure in the handler itself or in a later filter gets this far.
- The framework's `PageNotFound` logger is set to ERROR, because its line for a refused method
  repeats what the client sent.
- Actuator requests get an ID and a line like any other, so health probes appear in the logs.
- **Spring Security runs inside this filter,** so a 401 or a 403 gets an ID and a line. It answers
  before a handler is chosen, so the route is `UNMATCHED`, except on a request that the chain let
  through. A request line for a 401 therefore cannot say which endpoint was tried.
- **The CORS filter runs inside this one,** so a preflight or a refused origin gets an ID and a line.
  It answers before a handler is chosen, so its route is `UNMATCHED`. See `[cors]` in the controllers
  README.
- Text output shows the ID and fields through `logging.pattern.correlation`: see `[logging]` in the
  config README.
