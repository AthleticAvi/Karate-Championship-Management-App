# Line endings

Applies when: a Windows clone, core.autocrlf, CRLF, a multi-line text match or replacement finds nothing, a script edits files, .gitattributes
Status: enforced

## Index

- [crlf-working-tree] A multi-line match finds nothing on a Windows clone
- [verify] Verify

## [crlf-working-tree] A multi-line match finds nothing on a Windows clone

- With `core.autocrlf=true`, the default on a typical Windows install, a text file with no `eol`
  attribute is LF in the repository and CRLF in the working tree.
- A match or replacement that spans lines and is written with `\n` finds nothing in the working
  tree, and a script that edits by such a match silently changes nothing. Single-line matches still
  work, so the failure looks like a typo in the text.
- `git ls-files --eol <file>` shows both: `i/lf w/crlf` is the case above.
- Match line breaks as `\r?\n`, or read the file with its line endings normalised. Check that the
  replacement happened: count the matches before writing.
- An in-place stream edit can rewrite every line ending, not only the line it changes: a Unix
  `sed -i` on Windows may write the file back as LF. The diff then shows the whole file, and a
  formatter check fails the build on it, which hides the edit's real effect. Keep the endings with
  binary mode, such as `sed -b -i`, and check that the diff shows only the intended lines.
- A file a tool or test compares byte for byte is pinned with `text eol=lf` in `.gitattributes`, so
  it reads the same on every platform.

## [verify] Verify

- An edit made by a multi-line match shows in `git diff`.
- `git ls-files --eol` on a file that must be LF shows `w/lf`.
