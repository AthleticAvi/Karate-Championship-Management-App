# data

## Index

- [purpose] Purpose
- [owns] Owns
- [may-call] May call
- [facts] Project facts

## [purpose] Purpose

The storage every named identity shares, such as an athlete or a referee, so a fix to it is made
once.

## [owns] Owns

- `IdentityDocument`: the stored fields every named identity has, `id`, `version`, `schemaVersion`,
  `name` and the two instants, and the rule for when a document's `CURRENT_SCHEMA_VERSION` rises.
- `IdentityRepository`: the delete by id and the name search. Not a bean: each feature's repository
  extends it for its own document.

## [may-call] May call

Spring Data only. Like every data layer, never a service, a rule or a gateway.

## [facts] Project facts

- **A field added to `IdentityDocument` changes every stored identity,** so each document that
  extends it raises its `CURRENT_SCHEMA_VERSION`.
- **A new named identity must have a name:** the shared list sorts and searches by it.
- **Unmarked for nulls,** like every feature's data package: see `[facts]` in
  `src/main/java/com/management/kumitegame/data/README.md`.
