# Test proof

Applies when: writing any new test, in any stack, proving a test tests real behaviour
Status: enforced

## Index

- [proves-behaviour] Proving a test tests behaviour
- [no-fitting] Code never fits a test

## [proves-behaviour] Proving a test tests behaviour

A passing test proves nothing until it has failed for the right reason. For every new test:

1. Break the behaviour it covers: change the line, or remove it.
2. Run the test and confirm that test fails. A red build proves nothing on its own: a linter or
   formatter can stop the build on the deliberate break, so the test never ran.
3. Restore the code. Record what was broken in the PR. A restore that keeps the file's older
   timestamp leaves the broken build in place: an incremental build sees nothing newer and keeps
   the output compiled from the break. Touch the file or clean the build after restoring.

If the test still passes, it is testing nothing. Fix the test, not the code.

## [no-fitting] Code never fits a test

Code must never be written to satisfy a test. A test describes behaviour, so if the behaviour
drifts, the test fails.
