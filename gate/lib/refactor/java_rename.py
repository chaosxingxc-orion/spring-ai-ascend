#!/usr/bin/env python3
"""AST-aware Java identifier rename via JavaParser-CLI.

This wrapper is the canonical entry point for broad Java renames per the
rc51 Wave G2 policy (gate/lib/refactor/POLICY.md). It enforces that
identifier rewrites happen on the AST, not via `sed -i`.

Authority: ADR-0119 + rc51 Wave G2 (closes recurring-defect family
`F-bulk-scrub-orphan-syntax`).

Usage:
    python3 gate/lib/refactor/java_rename.py \
        --paths agent-middleware/src/main/java \
        --mapping rename-mapping.tsv \
        [--dry-run]

`rename-mapping.tsv` has one `old<TAB>new` pair per line. Each pair maps
a fully-qualified or simple identifier (class / method / field / package
segment). Comment lines starting with `#` are skipped.

Implementation strategy
-----------------------
JavaParser is a Java library, so the wrapper either:

1. (preferred) shells out to a pre-built `javaparser-cli.jar` shipped
   under `gate/lib/refactor/jars/` (not committed at rc51; W2
   investment lands the jar alongside the first non-trivial rename);
2. (fallback) refuses to run with a clear error pointing at (1).

Exit codes:
  0 — dry-run completed (or rename successful with --no-dry-run);
  1 — bad CLI arguments / mapping file malformed;
  2 — JavaParser CLI jar not present (W2 follow-up to land it);
  3 — at least one file would change but `--dry-run` was set.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Iterable

REPO_DEFAULT = Path(__file__).resolve().parents[3]
JAR_DEFAULT = REPO_DEFAULT / "gate" / "lib" / "refactor" / "jars" / "javaparser-cli.jar"


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument("--paths", nargs="+", required=True, help="Java source roots to rewrite")
    parser.add_argument("--mapping", required=True, type=Path, help="TSV file with old<TAB>new identifier pairs")
    parser.add_argument("--dry-run", action="store_true", default=True, help="Default; report would-change files without rewriting")
    parser.add_argument("--no-dry-run", dest="dry_run", action="store_false", help="Actually rewrite files in place")
    parser.add_argument("--jar", default=JAR_DEFAULT, type=Path, help="Path to javaparser-cli.jar (W2 deliverable)")
    parser.add_argument("--repo", default=REPO_DEFAULT, type=Path, help="Repository root (used to resolve relative paths)")
    return parser.parse_args(argv)


def load_mapping(path: Path) -> list[tuple[str, str]]:
    if not path.is_file():
        print(f"FATAL: mapping file missing: {path}", file=sys.stderr)
        sys.exit(1)
    pairs: list[tuple[str, str]] = []
    for line_no, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) != 2 or not parts[0] or not parts[1]:
            print(f"FATAL: {path}:{line_no}: malformed mapping line: {raw!r}", file=sys.stderr)
            sys.exit(1)
        pairs.append((parts[0], parts[1]))
    return pairs


def resolve_paths(repo: Path, raw_paths: Iterable[str]) -> list[Path]:
    resolved: list[Path] = []
    for p in raw_paths:
        candidate = (repo / p).resolve()
        if not candidate.exists():
            print(f"FATAL: source path does not exist: {candidate}", file=sys.stderr)
            sys.exit(1)
        resolved.append(candidate)
    return resolved


def check_jar_present(jar: Path) -> None:
    if not jar.is_file():
        msg = (
            f"FATAL: JavaParser CLI jar not present at {jar}.\n"
            "rc51 Wave G2 ships the policy + wrapper skeleton; the jar lands\n"
            "alongside the first non-trivial AST-aware rename (W2 investment).\n"
            "Migrate one of the bulk-scrub backlog scripts under\n"
            "gate/lib/refactor/POLICY.md#backlog to bind a concrete jar version.\n"
        )
        print(msg, file=sys.stderr)
        sys.exit(2)


def run_rename(jar: Path, source_roots: list[Path], pairs: list[tuple[str, str]], dry_run: bool) -> int:
    cmd = [
        "java",
        "-jar",
        str(jar),
        "rename",
        "--dry-run" if dry_run else "--in-place",
    ]
    for old, new in pairs:
        cmd.extend(["--map", f"{old}={new}"])
    for root in source_roots:
        cmd.append(str(root))
    if not shutil.which("java"):
        print("FATAL: `java` is not on PATH; install a JDK 21+ before running this wrapper.", file=sys.stderr)
        sys.exit(2)
    return subprocess.call(cmd)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    pairs = load_mapping(args.mapping)
    source_roots = resolve_paths(args.repo.resolve(), args.paths)
    check_jar_present(args.jar)
    return run_rename(args.jar, source_roots, pairs, args.dry_run)


if __name__ == "__main__":
    sys.exit(main())
