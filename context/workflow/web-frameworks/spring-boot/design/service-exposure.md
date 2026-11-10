# Spring Boot service exposure

Applies when: ETag in Spring, If-None-Match, 304 Not Modified, checkNotModified, ShallowEtagHeaderFilter, conditional GET
Status: enforced
Builds on: `design/service-exposure.md`

## Index

- [conditional-reads] Conditional reads: decide the tag in the handler
- [verify] Verify
- [sources] Sources

## [conditional-reads] Conditional reads: decide the tag in the handler

- **Use `WebRequest.checkNotModified(etag)` in the handler,** or return
  `ResponseEntity.ok().eTag(etag)`. Compute the tag from the versions the response depends on and
  check it before building the body: when it returns true, return immediately and the framework
  answers 304.
- **`ShallowEtagHeaderFilter` hashes the finished body.** The response is still rendered, so it
  saves bandwidth, not server work. It ignores `If-Match` and `If-Unmodified-Since`, and its own
  documentation points to `checkNotModified` instead. Choose it only when the tag cannot be known
  before rendering.
- **Do not combine the timestamp and tag overloads of `checkNotModified` in sequence.** A reported
  case leaves a stale 304 in place (spring-framework issue 29031). Use the tag alone.
- **Never build the tag from the body by hand.** Both mechanisms above are provided.

## [verify] Verify

- A slice test sends the current tag in `If-None-Match` and gets 304 with an empty body.
- Changing any record the response composes in changes the tag in a test.

## [sources] Sources

- Spring Framework 7.0 Javadoc, `ShallowEtagHeaderFilter` and `WebRequest.checkNotModified`.
- spring-framework issue 29031, combining `checkNotModified` overloads.
