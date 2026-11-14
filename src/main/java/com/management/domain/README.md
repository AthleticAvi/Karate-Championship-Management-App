# domain

## Index

- [purpose] Purpose
- [owns] Owns
- [contract] Contract
- [may-call] May call

## [purpose] Purpose

The failures every named identity shares, such as an athlete or a referee, so the shared handler
answers each kind the same way.

## [owns] Owns

`IdentityNotFoundException`, `IdentityChangedException` and `IdentityInUseException`, each abstract
and carrying the noun of its kind. Each feature keeps a subclass per failure, in its own `domain`,
which its service throws.

## [contract] Contract

`GlobalExceptionHandler` in `web` has one handler per failure and titles it with the noun:
`Athlete not found`, `Referee in use`. A changed failure answers with `current`, in the response
shape of its kind; a kind with no case there answers 500. See `[errors]` in
`src/main/java/com/management/web/README.md`.

## [may-call] May call

Nothing. Like every domain, no Spring type.
