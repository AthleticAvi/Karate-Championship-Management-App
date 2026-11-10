# Spring Boot patterns

Applies when: applying a design pattern in Spring Boot, injecting a list of beans, ordering rules, application events, cross-cutting behaviour
Status: enforced
Builds on: `design/patterns.md`

## Index

- [framework-provides] The framework already provides these
- [add-a-rule] Adding a rule means adding a class
- [enum-strategy] Strategies attached to enum constants
- [verify] Verify

## [framework-provides] The framework already provides these

| Pattern | Use this | Never hand-roll |
|---|---|---|
| Strategy | Inject `List<Interface>` of beans | A registry map filled by hand |
| Chain of responsibility | `List<Interface>` sorted by `@Order` | A hardcoded sequence of calls |
| Factory | `@Bean` methods, or a static factory on the type | A service locator |
| Repository | Spring Data repository interfaces | Driver calls in a service |
| Observer | `ApplicationEventPublisher` and `@EventListener` | A listener list on a service |
| Decorator | AOP through framework annotations, such as `@Retryable` | Wrapper classes around every bean |
| Retry | Spring Framework 7's own `@Retryable` (`org.springframework.resilience.annotation`), enabled by `@EnableResilientMethods` | A retry loop, or adding the separate spring-retry dependency |
| Adapter | A bean wrapping `RestClient` behind an interface | Client calls in a service |

**Two `@Retryable` annotations exist.** Spring Framework 7 has its own; spring-retry has one in
`org.springframework.retry.annotation`, enabled by `@EnableRetry`. Check the import. The limit
attribute differs: the framework's `maxRetries` counts retries only, spring-retry's `maxAttempts`
counts the first call too. Retry advice always runs outside `@Transactional`. The annotation works
only through the proxy: a call from inside the same bean is not retried, so put the retried method
on a separate bean.

## [add-a-rule] Adding a rule means adding a class

An injected list gives open/closed for free: a new `@Component` joins the list and no existing
class changes. Use it when a set of rules or handlers will grow.

## [enum-strategy] Strategies attached to enum constants

Attach the strategy to the constant when the variants are fixed and closed. Use injected beans
when a variant needs dependencies or will be added over time.

## [verify] Verify

- No pattern is hand-built where the table names a framework form.
- A new variant adds a class and edits none.
