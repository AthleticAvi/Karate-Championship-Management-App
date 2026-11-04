# Configuration

Applies when: reading a setting, adding a property, profiles, environment differences, secrets, startup validation
Status: enforced
Framework-first: typed binding, relaxed naming, profile layering, environment-variable override and startup validation are all provided. Loading a properties file by hand is a defect.

## Rules

- **Settings bind to a typed record**, one per cohesive group, validated at startup. Never read a raw key at the point of use.
- **Injected as a dependency**, through the constructor, like anything else. A configuration object constructed with `new` inside a class is outside the container: unvalidated, unmockable, and re-read per instance.
- **Rich types, not strings.** Durations, sizes and periods bind from suffixed values. Binding them as text and parsing by hand reintroduces a failure the framework already handles.
- **Fail at startup, not at first use.** Mark the type validated and constrain its components. A missing or malformed value must stop the context with a message naming the key.
- **No rule values in code.** Thresholds, timeouts, limits and durations come from configuration, always, including the default.
- **One key, one meaning, one place.** The same value defined in two files or two prefixes will diverge.

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

## Profiles

Layer profile-specific files over a shared base. The base holds what is genuinely common; a profile file holds only differences.

- Create a profile when something actually differs. An empty profile is noise.
- Default to the development profile locally; require the profile to be set explicitly elsewhere, so an unset environment fails rather than silently running development settings.
- Never branch on the active profile in code. Bind a value that differs per profile instead — the condition belongs in configuration, not in a method.

## Secrets

Never in a file under version control, in any profile, including development. Secrets arrive from the environment or a secret manager; configuration files may reference them but never contain them.

A default value for a secret is a way of shipping one. Leave it absent so startup validation fails when it is missing.

Confirm the ignore rules cover local override files before adding the first one.

## Precedence

Environment overrides file, and a more specific profile overrides the base. Rely on this rather than duplicating a value to force it. When a value is not what you expect at runtime, inspect the resolved configuration rather than guessing which file won.

## Local development

Every setting an application needs to start must have a working local default, or be documented in one place as required. A new contributor cloning the repository should reach a running application without being told a value privately.

Connection details for local infrastructure come from the environment with local defaults, never hardcoded.

## Verify

- Removing a required key fails startup with a message naming it.
- No class constructs its configuration with `new`.
- No secret appears in any committed file.
- No code branches on the active profile.
- A clean clone starts with documented steps only.

## Sources

- Fail Fast (Shore, *IEEE Software*, 2004; popularised by Fowler) — when a precondition for functioning is unmet, stop at startup rather than degrading into a later, unrelated failure.
