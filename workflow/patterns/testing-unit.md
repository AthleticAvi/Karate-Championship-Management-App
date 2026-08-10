# Unit Testing

Applies when: testing a single class, using test doubles, mocking, stubbing, verifying interactions
Status: enforced
Framework-first: no container. If a test needs the container to run, it is not a unit test — see `patterns/testing-slice.md`.

## Rules

- **Construct the subject with `new`.** Pass test doubles as constructor arguments. Reflection-based injection into a unit test is a smell that the subject cannot be built normally, which is a design defect in the subject.
- **Use the framework's JUnit extension for mock lifecycle.** Never open mocks manually. Manual initialisation in both a field initialiser and a setup method is a double-initialisation leak that also silently discards the first set.
- **Strict stubbing on.** An unused stub means the test does not exercise what its author believed. Let it fail.
- **Mock only what you own and what has behaviour** — collaborators across a boundary. Never mock value objects, records, collections, or types from the standard library; construct the real thing.
- **Prefer state assertions to interaction assertions.** Verifying that a collaborator was called asserts how the subject works. Assert the outcome instead, and verify interactions only when the interaction *is* the outcome, such as a message being dispatched.
- **A test double must not be more capable than the real thing.** See round-trip fidelity in `patterns/testing-strategy.md`.
- **No container annotations.** If one appears, the test has changed layer and belongs in a different file.

## The final-field trap

Mock injection frameworks do not write to `final` fields, and a field initialised inline is already assigned before any injection runs. A class that constructs its own collaborator in a `final` field cannot receive a double:

```java
public class WidgetService {
    private final Config config = new Config();   // no double can replace this, ever
}
```

A declared mock of that type will appear in the test, be stubbed, and never reach the subject. The test passes, exercising the real collaborator, and the author believes otherwise. There is no warning.

The fix is in the subject, not the test: take the collaborator as a constructor argument. See `standards/spring-boot.md`.

Where a mock is declared and stubbed but the subject builds its own instance, the stub is silently dead. Treat an unused-stub failure as a signal to check for this, not as noise to suppress.

## Do

```java
@ExtendWith(MockitoExtension.class)
class WidgetServiceTest {

    @Mock WidgetRepository repository;

    WidgetService service;

    @BeforeEach
    void setUp() {
        service = new WidgetService(repository, new WidgetProperties(Duration.ofSeconds(120), List.of()));
    }

    @Test
    void rename_whenWidgetExists_persistsNewName() {
        given(repository.findById("w-1")).willReturn(Optional.of(new Widget("w-1", "old")));

        service.rename("w-1", "new");

        ArgumentCaptor<Widget> saved = ArgumentCaptor.forClass(Widget.class);
        then(repository).should().save(saved.capture());
        assertThat(saved.getValue().name()).isEqualTo("new");
    }

    @Test
    void rename_whenWidgetMissing_throws() {
        given(repository.findById("gone")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.rename("gone", "new"))
                .isInstanceOf(WidgetNotFoundException.class)
                .hasMessageContaining("gone");
    }
}
```

Real configuration object, not a mock — it is a value type with no behaviour to stub.

## Don't

```java
class WidgetServiceTest {
    @Mock WidgetRepository repository;
    @InjectMocks WidgetService service;

    private AutoCloseable mocks = MockitoAnnotations.openMocks(this);  // runs at construction

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);   // runs again, first set leaked
    }

    @Test
    void save_returnsSameInstance() {
        given(repository.save(any())).willAnswer(i -> i.getArgument(0));   // no round trip:
        ...                                                                // hides every
    }                                                                      // persistence defect
}
```

## Testing time-dependent logic

Inject a clock; never sleep, never call a static "now" inside the subject. A test that must observe elapsed time advances a controllable clock. Code that cannot be tested without sleeping has a missing dependency, not a hard-to-test behaviour.

## Verify

- Every test constructs its subject with `new`.
- Strict stubbing enabled and passing.
- No sleep, no ambient time, no ambient locale or timezone.
- Deleting a branch in the subject turns exactly one test red.
