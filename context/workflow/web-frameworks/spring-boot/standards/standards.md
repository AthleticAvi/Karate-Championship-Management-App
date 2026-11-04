# Spring Boot Coding Standards

Applies when: adding a bean, wiring a dependency, reading configuration, choosing a library, structuring packages
Status: enforced
Scope: framework level only. Language rules live in `standards/java.md`.

## Framework-first

Before writing any mechanism, establish that the framework does not already provide it. A hand-rolled equivalent of a framework feature is rejected in review regardless of quality — it duplicates behaviour the framework will keep maintaining, and it will not receive that maintenance.

| Need | Use this | Never hand-roll |
|---|---|---|
| Read external settings | `@ConfigurationProperties` + relaxed binding | `Properties` + classloader |
| Validate request input | Jakarta Bean Validation + `@Valid` | null checks in a service |
| Map exception to HTTP response | `@ControllerAdvice` + `ProblemDetail` | status codes assembled per controller |
| Environment differences | profiles | `if (env.equals("prod"))` |
| Health / metrics | Actuator | a custom `/ping` endpoint |
| Connection details in tests | Testcontainers `@ServiceConnection` | `@DynamicPropertySource` string wiring |
| Optimistic concurrency | `@Version` on the document | a hand-maintained revision field |
| Scheduled work | `@Scheduled` | a raw `Thread` or `Timer` |
| Retry | framework retry support | a hand-written loop with a counter |
| Object mapping to persistence | the mapping layer's creator resolution | manual field copying |

Where the framework offers a choice, take the one the reference documentation shows first. Reaching for a third-party library when a starter exists is a project-wide decision, not a per-change one.

## Rules

- **Constructor injection only.** No `@Autowired` on fields. A single constructor needs no annotation at all. Dependencies are `final`.
- **Circular dependencies are a design defect, not a wiring problem.** `spring.main.allow-circular-references` stays unset. `@Lazy` to break a cycle is a temporary measure that must carry a linked owner and a removal plan; it is never the resolution.
- **Configuration binds to a typed record**, validated at startup with `@Validated` and constraint annotations. A malformed value fails the context, not the first request.
- **Persistence types never cross the HTTP boundary** in either direction. Requests bind to a request type, responses are built from a response type. See `patterns/service-exposure.md`.
- **Controllers contain no logic.** Accept, delegate, return. No mapping, no branching on domain state, no persistence calls.
- **The application class sits at the root of the package tree.** Component scanning and repository discovery both derive from its package. Explicit `scanBasePackages` or `basePackages` arguments are a symptom of a misplaced application class — move the class rather than widening the scan.
- **One starter, not a pile of coordinates.** Depend on the starter; let the parent manage versions. Never pin a version the parent already manages.
- **Bean methods are package-private or private where possible.** Public is not required and widens the surface.
- **No business logic in a `@Configuration` class.** It wires; it does not decide.

## Do

```java
@Service
public class WidgetService {

    private final WidgetRepository repository;
    private final WidgetProperties properties;

    // single constructor: no @Autowired needed
    public WidgetService(WidgetRepository repository, WidgetProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }
}

@Validated
@ConfigurationProperties("widget")
public record WidgetProperties(
        @NotNull Duration defaultTimeout,
        @NotEmpty List<Duration> allowedTimeouts) { }
```

```yaml
widget:
  default-timeout: 120s
  allowed-timeouts: [90s, 180s]
```

Durations, periods and data sizes bind from suffixed strings. Parsing them by hand is a defect.

## Don't

```java
@Service
public class WidgetService {
    @Autowired private WidgetRepository repository;          // hides the dependency surface
    private final Config config = new Config();              // outside the container: unmockable,
                                                             // reloaded per instance, unvalidated
    @Autowired @Lazy private OtherService other;             // cycle papered over
}
```

A field initialised inline and marked `final` cannot be replaced by a test double — mock injection does not write to final fields. Any class built this way is untestable in the dimension that matters.

## Package layout

```
<root>                    application class, nothing else
<root>.<feature>.web      controllers, request/response types
<root>.<feature>.domain   entities, value objects, domain services
<root>.<feature>.data     repositories
<root>.config             typed configuration records
```

Package by feature above package by layer once a second feature exists. Layer-only packaging stops scaling at the point where two features share a layer and neither owns it.

## Verify

- Application starts with no circular-reference support enabled.
- Every service can be constructed in a test with `new` and test doubles.
- No `@Autowired` field, no `new` on a type that should be a bean.
- Removing a configuration key fails startup with a message naming the key.
