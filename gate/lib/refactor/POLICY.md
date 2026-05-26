# AST-Aware Refactor Tooling Policy

> Authority: ADR-0119 + rc51 Wave G2 (closes recurring-defect family
> `F-bulk-scrub-orphan-syntax`).

## Rule

**Broad renames, package moves, and any cross-file textual rewrite over
production source MUST use AST-aware tooling, not `sed` / `find -exec` /
`perl -pi -e` / bulk regex.**

Bulk regex is correct on a single line in a known shape; it is wrong on a
file because it cannot tell a string literal from an identifier, a
multi-line Javadoc continuation from a top-level statement, or a
matching brace from a Markdown table separator. Every multi-rc-wave
occurrence in `F-bulk-scrub-orphan-syntax` traces back to a bulk-regex
rewrite that produced orphan punctuation (`} ,)` / lonely `*` /
truncated Javadoc / split string literals).

## Tooling

| Language | Tool | Wrapper |
|---|---|---|
| Java | [JavaParser](https://javaparser.org/) | `gate/lib/refactor/java_rename.py` |
| Python | [libCST](https://libcst.readthedocs.io/) | `gate/lib/refactor/python_rename.py` |
| Shell | [shfmt](https://github.com/mvdan/sh) | (operator-driven; no Python wrapper) |
| Markdown | hand-edit + render-idempotency check | (no AST tooling needed) |

## Acceptance criteria for a "broad rename" wave

1. The rename is performed by the AST-aware wrapper, NOT by `sed -i`.
2. The output passes `git diff --check` (no whitespace errors).
3. The output passes `bash gate/check_architecture_sync.sh` AND
   `./mvnw -T 1C clean verify` (or `pytest` for Python touch).
4. If the rename produces zero hits on a file, that file's mtime MUST
   NOT change (idempotency).
5. The rc release note declares the AST-aware tool that was used.

## Backlog: existing bulk-scrub scripts that should migrate

These scripts predate the policy and use bulk regex. Migrate them
incrementally as part of any wave that touches them:

- `gate/lib/scrub_deleted_module_names.sh` (uses `grep -rE` + `sed -i`).
- `gate/lib/renumber_rules.sh` (uses `sed -i`).
- Any future "bulk-fix Javadoc" or "bulk-update import order" task.

Migration pattern: replace `sed -i` with a one-shot call to the
appropriate wrapper, passing the source paths via `--paths` and the
identifier mapping via `--mapping FILE`.
