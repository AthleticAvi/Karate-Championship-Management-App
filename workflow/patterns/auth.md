# Authentication and Authorization

Applies when: securing endpoints, filter chain, JWT, tokens, roles, permissions, CORS, securing management endpoints
Status: target — apply when security is introduced; do not retrofit partially
Framework-first: the security module provides the filter chain, token validation, password hashing, method-level authorization and test support. Writing a token parser, a credential check, or a role filter by hand is a defect and a vulnerability.

## Shape

Security is configured by declaring a filter chain bean using the builder's lambda form. The older base class to extend is removed; a configuration class returning the chain replaces it.

```java
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/widgets/**").hasAuthority("SCOPE_widget.read")
                        .anyRequest().authenticated())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(CsrfConfigurer::disable)
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .build();
    }
}
```

Several chains may coexist, each scoped by a matcher — the first matching chain wins, so order from most specific to least.

## Rules

- **Validate tokens with the framework's resource-server support**, not a hand-written filter. It verifies signature, expiry, issuer and audience, handles key rotation and caching, and has been attacked far more than anything written locally. A hand-rolled verifier that forgets to check the algorithm claim accepts forged tokens.
- **Stateless for an API.** No session creation. A stateless API that quietly creates sessions carries the cost and the fixation risk of sessions with none of the benefit.
- **Disabling CSRF is only correct for a stateless, token-authenticated API.** If any browser-driven, cookie-authenticated endpoint exists, CSRF protection stays on for it.
- **Deny by default.** End the matcher list with a catch-all requiring authentication. A permissive fallthrough means every new endpoint is public until someone remembers.
- **Authorize on the smallest meaningful unit.** Path rules for coarse boundaries, method-level annotations where the rule depends on the operation or the record. Never scatter the same rule across both.
- **Ownership checks belong where the record is loaded.** A path rule cannot express "only the owner may modify this" — that needs the record, so it is a domain check.
- **Never log or return a token, a credential, or a hash.** See `patterns/logging.md`.
- **Passwords, if ever stored, use the framework's adaptive hashing.** Never a general-purpose digest, never a hand-rolled salt scheme.

## 401 versus 403

Missing or invalid credentials is 401. Valid credentials without permission is 403. Returning 403 for an unauthenticated request tells a client to stop retrying when it should authenticate; returning 401 for an authorization failure invites a credential retry loop.

Neither response reveals whether the resource exists, when existence is itself sensitive.

## Management endpoints

Health, metrics and environment endpoints are secured before anything is reachable outside a developer machine. Expose only what is needed, and treat detailed health output as privileged — it enumerates dependencies and their status.

A liveness probe may be public; anything describing internals may not.

## CORS

Browsers block cross-origin calls unless the API permits them. This is invisible until a browser-based client exists, then blocks it immediately with an error that looks like a network fault.

- Configure centrally, not per endpoint.
- Allowed origins come from configuration and differ per environment. Never a wildcard where credentials are permitted — browsers reject the combination, and it is unsafe regardless.
- Allow only the methods and headers actually used.
- Verify from a real browser. A command-line client does not enforce cross-origin rules and will succeed regardless.

## Testing

The security module provides test support for authenticating a request in a slice test. Use it rather than disabling security in tests — a suite that runs with security off proves nothing about the secured application, and the first test written against the real chain will fail for reasons nobody has seen.

Cover: an unauthenticated request is rejected; an authenticated request without the required authority is rejected; the permitted case succeeds.

## Verify

- Every endpoint is deny-by-default; adding one without a rule makes it inaccessible, not public.
- No hand-written token parsing or credential comparison exists.
- Management endpoints require authentication outside local development.
- Tests cover unauthenticated, unauthorized and permitted paths.
- No credential or token appears in any log or response body.
