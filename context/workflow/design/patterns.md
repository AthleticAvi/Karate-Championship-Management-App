# Design patterns

Applies when: choosing a design pattern, replacing a conditional, wrapping an outside system, varying behaviour, building complex objects
Status: enforced

## Index

- [choose] Choosing a pattern
- [table] Which pattern for which problem
- [smells] Signs a pattern is wrong
- [verify] Verify

## [choose] Choosing a pattern

- Start from the problem, never from the pattern. Name the problem in the plan.
- Check the framework first: most patterns already exist there. See the stack's `design/patterns.md`.
- No pattern is the default. Plain code that works beats a pattern that does nothing yet.
- Use the pattern's name in the type name only when it helps a reader.

## [table] Which pattern for which problem

| Problem | Pattern | Use when | Avoid when |
|---|---|---|---|
| Behaviour varies by type or rule | Strategy | Two or more real variants | One variant exists |
| Several rules may each answer, in order | Chain of responsibility | Rules are independent and ordered | Rules depend on each other |
| Legal behaviour depends on current state | State | Transitions matter and are enforced | A simple flag is enough |
| Creating objects needs a decision | Factory | The caller must not know the concrete type | A constructor is enough |
| Many optional values to construct | Builder | Test fixtures, wide objects | Few required values |
| An outside system has the wrong shape | Adapter | Wrapping a tool, API or library | You own both sides |
| A subsystem is too wide for its callers | Facade | Callers need one simple entry | It only forwards calls |
| Storage must stay out of business code | Repository | Any persisted aggregate | Never bypass it |
| Layers exchange different shapes | Mapper | Crossing a boundary | Types are in one layer |
| Others react to something, unknown to it | Observer or events | Reactions must not couple back | The caller needs the result |
| Add behaviour around a call | Decorator | Cross-cutting, composable additions | A framework hook exists |

## [smells] Signs a pattern is wrong

- It has one implementation and no planned second.
- A reader needs its name explained before the code makes sense.
- The class exists only to forward calls.
- It was added for flexibility nobody asked for.

## [verify] Verify

- The plan names the problem the pattern solves.
- Removing the pattern would make the code worse, not just shorter.
