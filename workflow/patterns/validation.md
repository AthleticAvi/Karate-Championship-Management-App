# Validation

Applies when: checking request input, constraint annotations, rejecting bad data, domain invariants, required fields
Status: enforced
Framework-first: Jakarta Bean Validation is on the classpath via a starter and fires automatically. Hand-written null and emptiness checks in a service are a defect.

## Two kinds, two places

| Kind | Question | Where | Failure |
|---|---|---|---|
| Input validation | is this request well-formed | boundary, declaratively | 400 |
| Domain invariant | is this operation legal for this thing | domain type | 409 or 422 |

Confusing them produces both failure modes: invariants leak into request types where they cannot see the current state, and shape checks scatter through services where each new endpoint can forget them.

## Input validation

Constraints are declared on the request type and triggered by annotating the bound parameter. Nothing else is needed — no per-endpoint code, no service-level guard.

```java
public record CreateWidgetRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull @Positive Integer timeoutSeconds,
        @NotEmpty List<@NotBlank String> tags) { }

@PostMapping
ResponseEntity<WidgetResponse> create(@Valid @RequestBody CreateWidgetRequest request) { ... }
```

Rules:

- **Type the field as what it is.** A numeric value typed as text defers the failure to a hand parse, which throws a parsing exception that surfaces as a server error. Typing it correctly makes the framework reject it as a client error before any code runs. Choosing the right type removes more validation code than adding constraints does.
- **Constrain collection elements**, not only the collection. `@NotEmpty List<String>` permits a list of blanks.
- **Validate nested types** by marking the nested field, or nesting is skipped silently.
- **Never hand-write a null or emptiness check** that a constraint expresses.
- **Delete hand-rolled validation as constraints replace it.** Leaving both means two sources of truth that will disagree.

## What constraints cannot express

An annotation sees one request in isolation. It cannot see the database, other records, or the current state of the thing being changed. Anything requiring that context is a domain invariant, not input validation.

Composition rules — "exactly one of each role must be present", "these two fields are mutually exclusive" — are a middle case. A custom class-level constraint handles them cleanly when the rule depends only on the request. When the rule depends on stored state, it belongs in the domain.

## Domain invariants

Enforce them where the state lives, so no caller can bypass them. A type that can be constructed or mutated into an illegal state will eventually be, by a path nobody anticipated.

Prefer making illegal states unrepresentable over checking for them: a type that cannot be built wrong needs no guard. Where a check is genuinely needed, put it in the constructor or the mutating method, not in the service that happens to call it today.

## Failure reporting

Validation failures are reported as a structured body listing every field that failed and why — not the first failure, and not a single flattened sentence. A caller fixing three fields should need one round trip.

The framework raises a dedicated exception for a failed argument binding; mapping it is covered in `patterns/error-handling.md`. Do not catch validation failures in a controller.

## Trust boundary

Everything from a client is untrusted, including path and query parameters, headers, and values the client obtained from a previous response. Validation is not a convenience for well-behaved callers; it is the boundary of what the system accepts.

Never reflect an unvalidated client value into a message, a log line, or a persisted field.

## Verify

- A request with several invalid fields returns 400 listing all of them.
- A well-formed request that is illegal for the resource's current state returns 409 or 422, not 400.
- No service contains a null or emptiness check expressible as a constraint.
- Removing a constraint makes a test fail.

## Sources

- Jakarta Bean Validation 3.0 (formerly JSR-380) — the constraint annotations and the validation lifecycle. Hibernate Validator is the reference implementation supplied by the framework's validation starter.
