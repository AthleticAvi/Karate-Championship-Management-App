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

The response always returns the ID in `X-Correlation-ID`, on errors too. Every line logged during
the request carries it as `correlationId`.

The request line is `Request completed` at INFO, with these fields:

| Field | Value |
|---|---|
| `method` | The HTTP method, or `OTHER` if it is not a standard one |
| `route` | The mapped pattern, such as `/api/v1/players/{playerId}`, or `UNMATCHED` |
| `status` | The status answered, 500 if the chain threw |
| `durationMs` | Time spent in the chain |

## [may-call] May call

Nothing in this application. Never `services`, `repositories` or `rules`.

## [facts] Project facts

- **The route is logged, never the path.** A path holds what the client typed, so it could carry a
  line break or a credential into the logs. The pattern also groups cleanly in a query.
- An unknown path reaches the static resource handler, so its route is `/**`. `UNMATCHED` is what is
  left: a request no handler took, such as a verb the path does not allow.
- **Tomcat refuses some requests before any filter runs,** such as headers past the size limit. They
  get no ID and no line: see `[facts]` in the exceptions README.
- Actuator requests get an ID and a line like any other, so health probes appear in the logs.
- Text output shows the ID and fields through `logging.pattern.correlation`: see `[logging]` in the
  config README.
