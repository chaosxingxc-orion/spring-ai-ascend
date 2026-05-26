#!/usr/bin/env python3
"""AST-aware Python identifier rename via libCST.

This wrapper is the canonical entry point for broad Python renames per
the rc51 Wave G2 policy (gate/lib/refactor/POLICY.md). It enforces that
identifier rewrites happen on the concrete syntax tree, not via `sed -i`.

Authority: ADR-0119 + rc51 Wave G2 (closes recurring-defect family
`F-bulk-scrub-orphan-syntax`).

Usage:
    python3 gate/lib/refactor/python_rename.py \
        --paths gate/lib gate/build_architecture_graph.py \
        --mapping rename-mapping.tsv \
        [--dry-run]

`rename-mapping.tsv` has one `old<TAB>new` pair per line; comment lines
starting with `#` are skipped.

Exit codes:
  0 — dry-run completed (or rename successful with --no-dry-run);
  1 — bad CLI arguments / mapping file malformed;
  2 — libCST not installed (run `pip install libcst`);
  3 — at least one file would change but `--dry-run` was set.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Iterable

REPO_DEFAULT = Path(__file__).resolve().parents[3]


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument("--paths", nargs="+", required=True, help="Python files / directories to rewrite")
    parser.add_argument("--mapping", required=True, type=Path, help="TSV file with old<TAB>new identifier pairs")
    parser.add_argument("--dry-run", action="store_true", default=True, help="Default; report would-change files without rewriting")
    parser.add_argument("--no-dry-run", dest="dry_run", action="store_false", help="Actually rewrite files in place")
    parser.add_argument("--repo", default=REPO_DEFAULT, type=Path, help="Repository root")
    return parser.parse_args(argv)


def load_mapping(path: Path) -> dict[str, str]:
    if not path.is_file():
        print(f"FATAL: mapping file missing: {path}", file=sys.stderr)
        sys.exit(1)
    mapping: dict[str, str] = {}
    for line_no, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) != 2 or not parts[0] or not parts[1]:
            print(f"FATAL: {path}:{line_no}: malformed mapping line: {raw!r}", file=sys.stderr)
            sys.exit(1)
        mapping[parts[0]] = parts[1]
    return mapping


def gather_python_files(roots: Iterable[Path]) -> list[Path]:
    files: list[Path] = []
    for root in roots:
        if root.is_file() and root.suffix == ".py":
            files.append(root)
        elif root.is_dir():
            files.extend(p for p in root.rglob("*.py") if not any(part.startswith(".") for part in p.parts))
        else:
            print(f"FATAL: source path does not exist or is not .py: {root}", file=sys.stderr)
            sys.exit(1)
    return files


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    mapping = load_mapping(args.mapping)
    repo = args.repo.resolve()
    source_roots = [(repo / p).resolve() for p in args.paths]
    files = gather_python_files(source_roots)

    try:
        import libcst as cst
        from libcst.metadata import ScopeProvider
    except ImportError:
        msg = (
            "FATAL: libCST is not installed. Install with:\n"
            "  pip install libcst\n"
            "(rc51 Wave G2 ships the policy + wrapper skeleton; pinning libcst to\n"
            " the project Python toolchain lands alongside the first non-trivial\n"
            " AST-aware Python rename.)\n"
        )
        print(msg, file=sys.stderr)
        return 2

    class RenameTransformer(cst.CSTTransformer):
        METADATA_DEPENDENCIES = (ScopeProvider,)

        def leave_Name(self, original_node: cst.Name, updated_node: cst.Name) -> cst.Name:
            if original_node.value in mapping:
                return updated_node.with_changes(value=mapping[original_node.value])
            return updated_node

    changed: list[Path] = []
    for src in files:
        try:
            tree = cst.parse_module(src.read_text(encoding="utf-8"))
        except cst.ParserSyntaxError as exc:
            print(f"WARN: skipping {src} (libcst parse error): {exc}", file=sys.stderr)
            continue
        wrapper = cst.MetadataWrapper(tree)
        new_tree = wrapper.visit(RenameTransformer())
        if new_tree.code != tree.code:
            changed.append(src)
            if not args.dry_run:
                src.write_text(new_tree.code, encoding="utf-8")

    if not changed:
        print("PASS: no files needed renaming.")
        return 0

    print(f"{'DRY-RUN' if args.dry_run else 'RENAMED'}: {len(changed)} file(s):")
    for path in changed:
        print(f"  {path.relative_to(repo)}")
    return 3 if args.dry_run else 0


if __name__ == "__main__":
    sys.exit(main())
