# converters

## Index

- [purpose] Purpose
- [owns] Owns
- [facts] Project facts

## [purpose] Purpose

Data layer code that reads older stored forms.

## [owns] Owns

`LegacyWinnerConverter`, bound to the `winner` property with `@ValueConverter`.

## [facts] Project facts

Documents written before `winner` became a `PlayerColor` stay readable. The converter reads the old
display sentence (`"RED player: Kenji"` to `RED`, `"Pending game ending"` to `null`) and always
writes the enum name, so each document migrates itself on its next save. Without it,
`Enum.valueOf` fails and the whole match becomes unloadable.

`LegacyWinnerIT` inserts raw pre-change BSON to prove this, because every other test writes its
fixtures through the current mapping and can never see the old form.

Delete the converter once no legacy documents remain.
