# Spring Boot service exposure

Applies when: ETag in Spring, If-None-Match, 304 Not Modified, checkNotModified, ShallowEtagHeaderFilter, conditional GET, JsonView, two views of one response, Cache-Control no-store
Status: enforced
Builds on: `design/service-exposure.md`

## Index

- [conditional-reads] Conditional reads: decide the tag in the handler
- [json-views] Two views of one response
- [verify] Verify
- [sources] Sources

## [conditional-reads] Conditional reads: decide the tag in the handler

- **Use `WebRequest.checkNotModified(etag)` in the handler.** Compute the tag from the versions
  the response depends on and check it before building the body: when it returns true, return
  immediately and the framework answers 304.
- **`ResponseEntity.ok().eTag(etag)` compares after the body is built.** It still answers 304,
  but every poll pays for the full read and mapping. Use it only on reads that are cheap to build.
- **`ShallowEtagHeaderFilter` hashes the finished body.** The response is still rendered, so it
  saves bandwidth, not server work. It ignores `If-Match` and `If-Unmodified-Since`, and its own
  documentation points to `checkNotModified` instead. Choose it only when the tag cannot be known
  before rendering.
- **Do not combine the timestamp and tag overloads of `checkNotModified` in sequence.** A reported
  case leaves a stale 304 in place (spring-framework issue 29031). Use the tag alone.
- **Never build the tag from the body by hand.** Both mechanisms above are provided.
- **A browser keeps nothing to revalidate while Spring Security answers `no-store`.** Its default
  headers send `Cache-Control: no-cache, no-store` on every answer, unless the handler sets its own
  `Cache-Control`. A browser then never sends `If-None-Match` by itself. Script on another origin
  can send it only when CORS allows that request header, and read the tag only when CORS exposes
  `ETag`. Decide both when a browser is meant to use the tag.
- **Spring Security writes no `Cache-Control` on a 304.** A handler that sets its own policy with
  `ResponseEntity.cacheControl` sets it on the 304 as well, because a cache replaces the headers it
  kept with the 304's. `CacheControl.empty()` writes nothing and leaves Spring Security's default
  in place.
- **MockMvc sends no `Date`.** The container adds it, on a 304 too. A slice test of a header the
  client reads for the server's time passes while proving nothing: test it over real HTTP.
- **A tag built from stored versions alone survives a redeploy.** If a deploy can change what the
  same stored data reads as, such as configuration or derived values, add something that changes
  with each start, such as the application context's start date.

## [json-views] Two views of one response

- **Use `@JsonView` on the handler** to leave fields out of one view of a shared response record,
  rather than a second family of records. A handler that names no view writes every field.
- **Jackson 3 leaves out every unmarked field under a view.** `MapperFeature.DEFAULT_VIEW_INCLUSION`
  is false by default, unlike Jackson 2, so a view that marks only the fields it drops answers
  `{}`. Set `spring.jackson.mapper.default-view-inclusion=true`, or mark every record the view
  reaches. Symptom: an empty object from the handler with the view.
- **springdoc writes a second schema per record a view reaches,** named with the view's simple
  name after an underscore, such as `OrderResponse_Public`. A test that expects one schema per
  record has to allow for them.

## [verify] Verify

- A slice test sends the current tag in `If-None-Match` and gets 304 with an empty body.
- Changing any record the response composes in changes the tag in a test.
- A strict whole-body test pins each view, so a field the view drops by mistake fails it.

## [sources] Sources

- Spring Framework 7.0 Javadoc, `ShallowEtagHeaderFilter` and `WebRequest.checkNotModified`.
- spring-framework issue 29031, combining `checkNotModified` overloads.
