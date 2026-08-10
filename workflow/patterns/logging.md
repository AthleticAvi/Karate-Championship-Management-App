# Logging

Applies when: adding a log line, choosing a level, correlation, structured output, what not to log, tracing
Status: enforced
Framework-first: the logging facade, level configuration, structured output and request correlation are provided. Never write a custom log wrapper.

## Rules

- **Log through the facade**, never the console. A print statement bypasses levels, formatting, correlation and shipping — it is invisible in every environment that matters.
- **Parameterised messages only.** Placeholders, arguments passed separately. String concatenation builds the message even when the level is disabled, and defeats structured output.
- **The throwable is the last argument**, never concatenated into the text. That is what preserves the stack trace.
- **Log an event, not a location.** Method-entry and method-exit lines carry no information a stack trace or a tracing tool does not already have, and they bury the lines that do. They are noise at any level.
- **Log where the decision is made**, once. The same failure logged at three layers produces three entries for one event and makes the log look like three failures.
- **Never log and rethrow.** Either handle and log, or throw and let the handler log. Both produces duplicates with no added information.
- **Client errors are not error level.** A rejected request is normal traffic. Reserve error for faults the operator must act on; anything logged at error that nobody acts on trains people to ignore the level.

## Levels

| Level | Meaning | Action |
|---|---|---|
| error | The system failed to do its job; a fault needing attention | operator acts |
| warn | Degraded, recovered, or approaching a limit | investigate later |
| info | A significant state change worth seeing in production | none |
| debug | Detail for diagnosing a specific problem | off in production |
| trace | Fine-grained flow | off everywhere except a live hunt |

Info is the production default. If info is too noisy to read, the problem is what is being logged at info, not the level.

## Correlation

Every request carries an identifier, put into the diagnostic context at the boundary and cleared when the request finishes. Without it, concurrent requests interleave into an unreadable stream.

- Accept an inbound correlation identifier if present; generate one if not.
- Return it on the response, including on errors, so a bug report can be tied to log lines. See `patterns/error-handling.md`.
- **Always clear the context.** Threads are pooled, so a value left behind is attributed to an unrelated later request. Clear it in a finally block or use the framework's filter.

Where distributed tracing is present, take trace and span identifiers from it rather than inventing a parallel scheme, and use the diagnostic context only for application-specific keys.

## Structured output

Log as structured records in any environment where logs are aggregated. Text is for a human reading a terminal; structure is for a query across a fleet. Keep human-readable text locally and structured output elsewhere — a per-profile difference, not a code difference.

Put variable data in fields, not interpolated into a sentence. A message whose text is constant and whose values are fields can be aggregated and alerted on; one that concatenates values into prose cannot.

## Never log

- Credentials, tokens, keys, session identifiers, or anything that grants access.
- Personal data beyond what is necessary, and only where retention is understood.
- Full request or response bodies by default.
- Anything a client sent that has not been validated, reflected verbatim.

Redact at the point of logging. A filter that redacts after the fact is a second chance, not the control.

## Log lines are an interface

Once an alert or a dashboard matches on a message, that message is a contract. Changing its wording silently breaks the alert. Treat the constant part of a frequently-matched message as an interface, and prefer structured fields, which can be renamed with a deprecation rather than silently.

## Verify

- No print statements, no string-concatenated messages, no method-entry lines.
- Every log statement with a caught throwable passes it as the final argument.
- Concurrent requests are distinguishable by correlation identifier.
- The diagnostic context is empty at the end of every request.
- No secret appears in output under any level, verified by a test that asserts a known secret is absent.
