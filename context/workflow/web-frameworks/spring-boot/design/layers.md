# Spring Boot layers

Applies when: placing a class in a Spring Boot service, controller, service, repository, what may call what
Status: enforced
Builds on: `design/layers.md`, `design/service-interaction.md`

## Index

- [mapping] Mapping the responsibilities
- [contract] Layer contract
- [stereotypes] Wiring annotations in business code
- [verify] Verify

## [mapping] Mapping the responsibilities

| Responsibility | Spring Boot form |
|---|---|
| Inbound gateway | `@RestController`, request and response types, mappers, `@RestControllerAdvice` |
| Outbound gateway | A client wrapped in an adapter, such as `RestClient` |
| Business | `@Service` classes and domain types |
| Data | Repository interfaces and persistence mapping |

## [contract] Layer contract

| Layer | May call | May not |
|---|---|---|
| Web | application services | persistence, other web components |
| Application service | domain services, persistence, other application services (acyclically) | web |
| Domain | value objects, domain services | persistence, web, framework types |
| Persistence | the datastore | services |

## [stereotypes] Wiring annotations in business code

`@Service`, `@Component` and `@Order` wire a class. They add no behaviour the class depends on, so
they are allowed in business code. Web and persistence types are not.

## [verify] Verify

- No controller references a repository.
- No service references a web type.
- Every service can be constructed with `new` and test doubles.
