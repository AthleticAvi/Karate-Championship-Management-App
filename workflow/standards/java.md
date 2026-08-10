# Java Coding Standards

Applies when: writing or reviewing any Java source; naming; exceptions; immutability; equality; encoding
Status: enforced
Scope: language level only. Framework rules live in `standards/spring-boot.md`.

## Rules

- **Data carriers are records.** Any type that only carries values — request payloads, response payloads, value objects, config bindings — is a `record`. Hand-written getter/setter classes are rejected in review.
- **Fields are `final` unless mutation is a stated requirement.** A non-final field is a claim that something reassigns it; the reviewer will ask what.
- **Constructor-assign every dependency.** No setter injection, no post-construction wiring for collaborators.
- **Never use exceptions as control flow.** Parse once and branch on the result; do not call a `boolean`-returning probe that swallows an exception and then repeat the work.
- **Never call `printStackTrace()`.** It writes to stderr, bypassing the log pipeline. Log with the throwable as the final argument.
- **Never swallow an exception into a degraded default.** Either handle it meaningfully or let it propagate. A `catch` block that logs and continues with empty state converts a clear failure into a distant one.
- **Custom exceptions extend `RuntimeException`** unless the caller is genuinely expected to recover, which is rare in a request-scoped service.
- **`equals`, `hashCode`, `toString` on every type placed in a collection or logged.** Identity-based equality for entities with a persistent id; value equality for value objects; records give all three free.
- **A type with a persistent identifier compares on that identifier alone.** Decide explicitly what equality means before the id is assigned — normally "equal only to itself".
- **`Optional` is a return type, never a field or a parameter.** For "absent", return `Optional`; do not accept one.
- **No magic strings for absent state.** A sentinel like `"pending"` in a value field forces string comparison on every consumer. Use `null`, an `Optional` return, or an enum.
- **Static utility classes get a private throwing constructor**, and hold no state.
- **Enums carry behaviour.** Attach the per-constant strategy to the constant rather than branching on the enum at each call site.
- **All I/O declares its charset.** Never rely on the platform default.

## Do

```java
public record WidgetResponse(String id, String name, int count) { }

public enum Grade {
    HIGH(new HighScoring()),
    LOW(new LowScoring());

    private final ScoringStrategy strategy;
    Grade(ScoringStrategy strategy) { this.strategy = strategy; }
    public ScoringStrategy strategy() { return strategy; }
}

// parse once, branch on the outcome
private static Optional<Grade> parseGrade(String raw) {
    if (raw == null) { return Optional.empty(); }
    try {
        return Optional.of(Grade.valueOf(raw.toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
        return Optional.empty();
    }
}

public static Grade requireGrade(String raw) {
    return parseGrade(raw).orElseThrow(() -> new InvalidGradeException("Unknown grade: " + raw));
}
```

## Don't

```java
// two lookups, exception as a boolean, and the null case still throws NPE
public static Grade map(String raw) {
    if (!isGrade(raw)) { throw new NotFoundException(raw); }   // lookup 1, wrong exception type
    return Grade.valueOf(raw.toUpperCase());                   // lookup 2
}
public static boolean isGrade(String raw) {
    try { Grade.valueOf(raw.toUpperCase()); return true; }
    catch (IllegalArgumentException e) { return false; }
}

// failure converted into a delayed, unrelated failure
try {
    props.load(in);
} catch (IOException ex) {
    ex.printStackTrace();       // never reaches the log pipeline
}                               // caller now reads empty config and fails somewhere else
```

## Encoding

Source and resources are UTF-8. Set it on the build rather than trusting a default, and read files with an explicit decoder in any tooling script.

- Build property: `project.build.sourceEncoding=UTF-8`.
- PowerShell 5.1 decodes a BOM-less file with the ANSI codepage. `Get-Content -Raw` silently corrupts non-ASCII; use `[IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)`. Keep `.ps1` files pure ASCII, because the parser has the same defect on the script itself.
- Anything that round-trips text through a shell must be verified on the far side, not on the console that wrote it.

## Naming

| Element | Form |
|---|---|
| Logger field | `private static final Logger log` |
| Constant | `UPPER_SNAKE`, `private static final` |
| Boolean accessor | `isX` / `hasX` |
| Test method | `methodUnderTest_condition_expectedOutcome` |

## Third-party code generation

Prefer records and the language over an annotation processor. Introducing one (Lombok or similar) is a project-wide decision with a build-tooling cost, not something a single change adopts.

## Verify

- Compiles with no warnings introduced.
- No new `printStackTrace`, no new `public` mutable static, no new getter/setter data class.
- Non-ASCII characters survive a round trip through any script that touches the file.
