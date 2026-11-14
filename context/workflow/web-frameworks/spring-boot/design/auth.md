# Authentication and Authorization

Applies when: securing endpoints, filter chain, JWT, tokens, roles, permissions, CORS, securing management endpoints
Status: target. Apply when security is introduced; do not retrofit partially
Framework-first: the security module provides the filter chain, token validation, password hashing, method-level authorization and test support. Writing a token parser, a credential check, or a role filter by hand is a defect and a vulnerability.
Builds on: `web-frameworks/spring-boot/standards/standards.md`

## Index

- [shape] Shape
- [rules] Rules
- [401-versus-403] 401 versus 403
- [management-endpoints] Management endpoints
- [cors] CORS
- [testing] Testing
- [verify] Verify

## [shape] Shape

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

Several chains may coexist, each scoped by a matcher: the first matching chain wins, so order from most specific to least.

## [rules] Rules

- **Validate tokens with the framework's resource-server support**, not a hand-written filter. It verifies signature, expiry, issuer and audience, handles key rotation and caching, and has been attacked far more than anything written locally. A hand-rolled verifier that forgets to check the algorithm claim accepts forged tokens.
- **Stateless for an API.** No session creation. A stateless API that quietly creates sessions carries the cost and the fixation risk of sessions with none of the benefit.
- **Disabling CSRF is only correct for a stateless, token-authenticated API.** If any browser-driven, cookie-authenticated endpoint exists, CSRF protection stays on for it.
- **Deny by default.** End the matcher list with a catch-all requiring authentication. A permissive fallthrough means every new endpoint is public until someone remembers.
- **Authorize on the smallest meaningful unit.** Path rules for coarse boundaries, method-level annotations where the rule depends on the operation or the record. Never scatter the same rule across both.
- **Ownership checks belong where the record is loaded.** A path rule cannot express "only the owner may modify this": that needs the record, so it is a domain check.
- **Never log or return a token, a credential, or a hash.** See `design/logging.md`.
- **For Basic credentials, set the entry point on the Basic configurer.** It answers wrong credentials itself, and exception handling takes it as the default for no credentials. Set on exception handling alone, wrong credentials still answer in the framework's form. Test both paths.
- **A challenge on a 401 makes a browser resend credentials by itself.** With CSRF off, an API whose client sends `Authorization` itself sends no `WWW-Authenticate: Basic`, and records why.
- **Turn logout off on a stateless API.** The builder adds it by default, and a `POST /logout` then answers a redirect to a login page that is not there.
- **A failed credential lookup is not a wrong password.** An outage in the account store arrives as an authentication exception, so an entry point answers 401 and sends the client to check a good password. Tell the service exception apart and answer it as a server fault.
- **The firewall answers before the chain.** A `;` in a path or a non-standard method is refused through the error dispatch, so permit that dispatch or it turns into a 401.
- **A deny-by-default catch-all also answers unknown paths and unlisted methods.** A 404 or a 405 becomes a 401, and `HEAD` is not `GET` to a method matcher.
- **Passwords, if ever stored, use the framework's adaptive hashing.** Never a general-purpose digest, never a hand-rolled salt scheme.

## [401-versus-403] 401 versus 403

Missing or invalid credentials is 401. Valid credentials without permission is 403. Returning 403 for an unauthenticated request tells a client to stop retrying when it should authenticate; returning 401 for an authorization failure invites a credential retry loop.

Neither response reveals whether the resource exists, when existence is itself sensitive.

## [management-endpoints] Management endpoints

Health, metrics and environment endpoints are secured before anything is reachable outside a developer machine. Expose only what is needed, and treat detailed health output as privileged: it enumerates dependencies and their status.

A liveness probe may be public; anything describing internals may not.

## [cors] CORS

Browsers block cross-origin calls unless the API permits them. This is invisible until a browser-based client exists, then blocks it immediately with an error that looks like a network fault.

- Configure centrally, not per endpoint.
- Allowed origins come from configuration and differ per environment. Never a wildcard where credentials are permitted: browsers reject the combination, and it is unsafe regardless.
- Allow only the methods and headers actually used.
- Expose what a browser client must read. It hides every response header outside a short list, including `Location` and any correlation header. `ETag` and `Date` are outside it too; `Cache-Control` is inside.
- A conditional header set by code, such as `If-None-Match`, is not one a browser sends freely: each such request needs an allowed header and a preflight. The browser's own revalidation from its cache needs neither.
- CORS runs before authentication, because a preflight carries no credentials. Exactly one `UrlBasedCorsConfigurationSource` bean makes Spring Security add its own CORS filter, with no call in the chain, and a second source turns that off. A bean named `corsFilter` must be a plain `CorsFilter`.
- A CORS filter registered ahead of the chain already covers it, so turn the chain's off: it sees every path and refuses a preflight on any path outside the source.
- `CorsFilter` ends every preflight it sees, even for a path with no configuration. Limit it in its servlet mapping, or it changes the answer on paths meant to stay out of CORS.
- Set a preflight max age from configuration. Without one a browser asks again after 5 seconds, and `CorsConfiguration` built with `new` sends none. Browsers cap it: Safari at 10 minutes, Chromium at 2 hours, Firefox at 24 hours.
- A cached preflight costs on narrowing, not widening. A refused preflight is never kept, so a header or method newly allowed works at once. The CORS processor checks origin and method on every request but request headers on the preflight only, so a header taken off the list passes until the cached answer expires.
- The server enforces the rules too: a refused origin gets a 403 before anything runs. Prove that with HTTP tests that send `Origin`. A browser adds what the server cannot show, so a browser check is a one-off, not the proof. Headless Chrome's `--dump-dom` dumps at load and ignores pending timers: for a check that needs real time to pass, such as a preflight cache, leave the browser running and read the server's request log.
- Same-origin is decided from the address the server sees, so a TLS proxy with forwarded headers off makes a same-origin page look cross-origin.

## [testing] Testing

The security module provides test support for authenticating a request in a slice test. Use it rather than disabling security in tests: a suite that runs with security off proves nothing about the secured application, and the first test written against the real chain will fail for reasons nobody has seen.

A web slice applies the framework's default chain unless the real configuration class is imported, because a configuration class is not part of the slice. The default looks like security working, so nothing fails to say so. Actuator matchers match nothing in a slice, which has no actuator, so those rules need an integration test.

Keep the role table in the test as a literal, and compare it with the real mappings. A test that reads the rules from the code under test only proves the code agrees with itself.

Cover: an unauthenticated request is rejected; an authenticated request without the required authority is rejected; the permitted case succeeds.

## [verify] Verify

- Every endpoint is deny-by-default; adding one without a rule makes it inaccessible, not public.
- No hand-written token parsing or credential comparison exists.
- Management endpoints require authentication outside local development.
- Tests cover unauthenticated, unauthorized and permitted paths.
- No credential or token appears in any log or response body.
