# Test proof

Applies when: writing any new test, in any stack, proving a test tests real behaviour
Status: enforced

## Index

- [proves-behaviour] Proving a test tests behaviour
- [no-fitting] Code never fits a test

## [proves-behaviour] Proving a test tests behaviour

A passing test proves nothing until it has failed for the right reason. For every new test:

1. Break the behaviour it covers: change the line, or remove it.
2. Run the test and confirm it fails.
3. Restore the code. Record what was broken in the PR.

If the test still passes, it is testing nothing. Fix the test, not the code.

## [no-fitting] Code never fits a test

Code must never be written to satisfy a test. A test describes behaviour, so if the behaviour
drifts, the test fails.
