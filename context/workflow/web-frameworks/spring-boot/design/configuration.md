# Configuration

Applies when: reading a setting, adding a property, profiles, environment differences, secrets, startup validation, request size limits
Status: enforced
Framework-first: typed binding, relaxed naming, profile layering, environment-variable override and startup validation are all provided. Loading a properties file by hand is a defect.
Builds on: `web-frameworks/spring-boot/standards/standards.md`

## Index

- [rules] Rules
- [profiles] Profiles
- [request-limits] Request limits
- [secrets] Secrets
- [precedence] Precedence
- [local-development] Local development
- [verify] Verify
- [sources] Sources

## [rules] Rules

- **Settings bind to a typed record**, one per cohesive group, validated at startup. Never read a raw key at the point of use.
- **Injected as a dependency**, through the constructor, like anything else. A configuration object constructed with `new` inside a class is outside the container: unvalidated, unmockable, and re-read per instance.
- **Rich types, not strings.** Durations, sizes and periods bind from suffixed values. Binding them as text and parsing by hand reintroduces a failure the framework already handles.
- **Fail at startup, not at first use.** Mark the type validated and constrain its components. A missing or malformed value must stop the context with a message naming the key.
- **No rule values in code.** Thresholds, timeouts, limits and durations come from configuration, always, including the default.
- **One key, one meaning, one place.** The same value defined in two files or two prefixes will diverge.
- **A placeholder with no default does not fail on its own.** The binder leaves `${NAME}` as text, so a missing variable surfaces later, in whatever reads the value, under a message naming no setting. `Environment.getProperty` resolves strictly: read the key there at startup to get a failure that names it.

```java
@Validated
@ConfigurationProperties("widget")
public record WidgetProperties(
        @NotNull Duration defaultTimeout,
        @NotEmpty List<Duration> allowedTimeouts,
        @Positive int maxRetries) { }
```

```yaml
widget:
  default-timeout: 120s
  allowed-timeouts: [90s, 180s]
  max-retries: 3
```

## [profiles] Profiles

Layer profile-specific files over a shared base. The base holds what is genuinely common; a profile file holds only differences.

- Create a profile when something actually differs. An empty profile is noise.
- Default to the development profile locally; require the profile to be set explicitly elsewhere, so an unset environment fails rather than silently running development settings.
- The framework has no option that requires a profile: with none set it activates one named `default` and carries on. `spring.profiles.default` in the base file reaches the packaged artifact too, so set the local profile through the build tool's run goal, and refuse an empty profile list in a context initializer.
- Set the build tool's profile through its property, not a plugin configuration entry, which a command line override cannot replace.
- Before adding a profile file, check the ignore rules. An entry written to keep secrets out silently drops a file that holds only placeholders, and so does a broad entry such as `META-INF/`.
- Never branch on the active profile in code. Bind a value that differs per profile instead: the condition belongs in configuration, not in a method.

## [request-limits] Request limits

The servlet container limits headers and forms, not a JSON body, and an unread body costs little but a read one has no ceiling.

- Limit JSON through the parser: `spring.jackson.factory.constraints.read.max-document-length`. It is exact, and holds for chunked bodies, where a `Content-Length` check sees nothing.
- It covers only what is read as JSON. The form content filter reads a form `PUT` whole, and a `String` or `byte[]` body has no limit, so turn off the readers no endpoint uses.
- The limit surfaces as a 400 parse failure until mapped. Every parser limit raises one exception type, so only the message separates size from depth: pin it with a test.
- Headers past the limit are refused by the container before the framework runs, so the answer is its own page and not the application's error format.

## [secrets] Secrets

Never in a file under version control, in any profile, including development. Secrets arrive from the environment or a secret manager; configuration files may reference them but never contain them.

A default value for a secret is a way of shipping one. Leave it absent so startup validation fails when it is missing.

Confirm the ignore rules cover local override files before adding the first one.

## [precedence] Precedence

Environment overrides file, and a more specific profile overrides the base. Rely on this rather than duplicating a value to force it. When a value is not what you expect at runtime, inspect the resolved configuration rather than guessing which file won.

## [local-development] Local development

Every setting an application needs to start must have a working local default, or be documented in one place as required. A new contributor cloning the repository should reach a running application without being told a value privately.

Connection details for local infrastructure come from the environment with local defaults, never hardcoded.

## [verify] Verify

- Removing a required key fails startup with a message naming it.
- No class constructs its configuration with `new`.
- No secret appears in any committed file.
- No code branches on the active profile.
- A clean clone starts with documented steps only.

## [sources] Sources

- Fail Fast (Shore, *IEEE Software*, 2004; popularised by Fowler): when a precondition for functioning is unmet, stop at startup rather than degrading into a later, unrelated failure.
