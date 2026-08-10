# Slice Testing

Applies when: testing controllers, HTTP status codes, JSON serialisation, request binding, repository queries
Status: enforced
Framework-first: the framework provides a slice annotation per layer. Never assemble a partial context by hand.

## What a slice is

A slice starts one layer of the container and nothing else. It is the correct layer for everything that only exists because the framework is present: routing, content negotiation, serialisation, status codes, parameter binding, validation triggering, query derivation.

Two things follow. Beans outside the slice are absent, so collaborators are supplied as test doubles. And the slice is the *only* place these concerns can be tested — a unit test cannot exercise routing, and an integration test is too expensive to enumerate every status code.

## Web slice

Starts controllers, the exception handler, serialisation, and validation. Does not start services or repositories.

```java
@WebMvcTest(WidgetController.class)
class WidgetControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean WidgetService service;   // @MockBean before framework 6.2 / Boot 3.4

    @Test
    void get_whenFound_returnsWidgetJson() throws Exception {
        given(service.find("w-1")).willReturn(new Widget("w-1", "gadget", 3));

        mvc.perform(get("/api/widgets/w-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("w-1"))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.internalRevision").doesNotExist());

    }

    @Test
    void get_whenMissing_returnsProblemDetail() throws Exception {
        given(service.find("gone")).willThrow(new WidgetNotFoundException("gone"));

        mvc.perform(get("/api/widgets/gone"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void create_whenNameBlank_returns400() throws Exception {
        mvc.perform(post("/api/widgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"name": "  "}
                                 """))
                .andExpect(status().isBadRequest());
    }
}
```

Name the controller in the annotation. Omitting it loads every controller, which couples the test to unrelated changes and multiplies context builds.

## Rules

- **Assert the JSON body, not the returned object.** The wire contract is the thing under test. Object-level assertions pass while field names, formats and nesting change underneath.
- **Assert absence too.** At least one test per response type asserts that internal fields are *not* present. This is the only automated defence against a persistence type leaking through the boundary.
- **Every status code the layer can produce gets a test**, including each handler in the exception mapping. An error path with no test is an error path that has never run.
- **Pin the format of dates, times and durations** with an explicit body assertion. These have several plausible serialisations and the default can shift under a dependency upgrade with no code change and no other failing test.
- **Validation failures are tested here**, not in a unit test — the constraint only fires because the container is present.

## Persistence slice

Starts the mapping layer and repositories against real infrastructure supplied by the test framework. Use it for derived queries, custom queries, index behaviour, and mapping round trips.

The mapping round trip is the point: save, load through the repository, assert every field survived — including fields the constructor does not take. A type whose only constructor also assigns defaults will silently reset them on load if the mapping layer cannot populate them afterwards.

```java
@DataMongoTest
class WidgetRepositoryTest {

    @Container @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired WidgetRepository repository;

    @Test
    void save_thenFindById_preservesEveryField() {
        Widget saved = repository.save(new Widget(null, "gadget", 3));

        Widget loaded = repository.findById(saved.id()).orElseThrow();

        assertThat(loaded).usingRecursiveComparison().isEqualTo(saved);
    }
}
```

Recursive comparison rather than field-by-field, so a newly added field is covered without editing the test.

## Don't

- Do not start the full container to test a status code.
- Do not mock the serialisation layer; that is the thing under test.
- Do not vary properties or mocked beans per class without cause — each distinct configuration is a separate cached context.
- Do not use an in-memory substitute for the real datastore. It agrees with production until precisely the moment it matters.

## Verify

- Every endpoint has a success assertion on its serialised body.
- Every exception handler has a test asserting status and content type.
- At least one test asserts an internal field is absent from a response.
- Persistence tests run against the real engine, and a save/load cycle asserts full fidelity.
