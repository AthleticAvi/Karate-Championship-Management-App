# Integration and End-to-End Testing

Applies when: testing a full flow, wiring, real infrastructure, containers, end-to-end scenarios, test data isolation
Status: enforced
Framework-first: connection details for a containerised dependency are wired by the framework's service-connection support. Never hand-wire host and port into properties.

## Purpose

Integration tests answer one question: does the assembled application work against real infrastructure. They are not where behaviour is enumerated — that belongs in cheaper layers. Keep a deliberate, small set covering the flows that would embarrass you if broken.

Pick them by consequence: the primary flow a user performs end to end, plus any flow where several components must agree. Not one per endpoint.

## Real infrastructure, never a substitute

Test against the same engine and major version as production, supplied as a container by the test framework. In-memory or embedded substitutes diverge from the real engine in exactly the areas that cause production defects: query semantics, type coercion, index behaviour, constraint enforcement, transaction availability.

A substitute that behaves better than production converts a real defect into a passing test.

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class WidgetFlowIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired TestRestTemplate rest;
    @Autowired WidgetRepository repository;

    @AfterEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void createThenIncrementThenRead() {
        ResponseEntity<WidgetResponse> created = rest.postForEntity(
                "/api/widgets", new CreateWidgetRequest("gadget"), WidgetResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String id = created.getBody().id();
        rest.put("/api/widgets/" + id + "/increment", null);

        WidgetResponse read = rest.getForObject("/api/widgets/" + id, WidgetResponse.class);
        assertThat(read.count()).isEqualTo(1);
    }
}
```

`@ServiceConnection` derives every connection property from the container. The older approach of writing property values by hand couples the test to property names and silently breaks when they change.

## Rules

- **Declare the container `static`.** A non-static container restarts per test method and dominates suite time.
- **One container definition, shared.** Put it on a base class or a shared configuration so every integration test joins the same cached context and the same container. Divergent definitions multiply both.
- **Start the shared container yourself; do not let the test framework own its lifecycle.** A framework-managed container is tied to the class that declares it and is stopped when that class finishes, so the second class to run finds it dead. Start it once in a static initialiser and let the container runtime's reaper remove it at process exit.
- **Exactly one file should name a container type.** Container classes move between packages across major versions of the container library, so every file that names one is a file the next upgrade has to edit. Concentrating the reference in the shared base makes that a one-line change however many integration tests exist.
- **Never disable shared infrastructure to simulate failure.** Stopping the shared datastore to assert that a health check reports failure breaks every test that runs afterwards, and forces a fixed method order to hide it — order dependence introduced deliberately. Point a separate, clearly-labelled context at an address nothing is listening on instead: it is faster, unambiguous, and has no blast radius. Lower the client's connection timeout, or the test spends the default timeout waiting to be told what it already knows.
- **Never mock inside an integration test.** Substituting a bean invalidates the only thing this layer proves. If a collaborator must be faked — a third-party endpoint — fake it at the network boundary with a stub server, not by replacing a bean.
- **Clean before, not after.** Each test wipes the datastore as it starts, rather than tidying as it finishes. Cleaning afterwards depends on teardown actually running: a test that crashes, times out, or is killed leaves its state behind for whatever runs next, which reintroduces the order dependence the cleaning was supposed to remove. Cleaning first cannot be skipped, and it leaves a failed test's data in place to be inspected. Do not rely on transaction rollback where the engine does not provide it.
- **Never assert against data another test created.** Every test creates what it needs. Shared seed data creates order dependence that appears only under parallel execution or reordering.
- **Assert through the public interface**, over HTTP, the way a client would. Reaching into a repository to assert is acceptable for setup and for confirming persistence, but the primary assertion should be the response.
- **Name them distinctly** from unit tests so the build can run the fast suite alone.

## Concurrency scenarios

Behaviour under simultaneous access can only be observed here. Where two callers may act on the same record, one integration test should drive genuinely concurrent requests and assert that no update is lost — the failure mode is silent, so nothing else will catch it.

Use a latch to release both requests at the same instant. A loop of sequential calls does not reproduce it.

## What belongs at this layer, and what does not

| Concern | Layer |
|---|---|
| Every branch of a calculation | unit |
| Every status code | slice |
| Serialisation format | slice |
| Query correctness | persistence slice |
| Does the wiring hold together | integration |
| Does the primary flow work end to end | integration |
| Lost updates under concurrency | integration |
| Transaction and consistency behaviour | integration |

## Verify

- The suite passes from a clean checkout with no pre-existing data.
- Tests pass in reverse order and under parallel execution.
- Stopping the container makes the tests fail rather than silently fall back to a substitute.
- The fast suite can be run without starting any container.
