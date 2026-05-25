#!/usr/bin/env python3
"""Validate the latest release note does not publish placeholder evidence."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


PLACEHOLDER_PATTERNS = (
    "pending-formal-validator-run",
    "TO BE GENERATED",
    "TBD",
    "TODO",
)


def latest_release(root: Path) -> Path | None:
    release_dir = root / "docs" / "logs" / "releases"
    files = sorted(release_dir.glob("*.md")) if release_dir.is_dir() else []

    def key(path: Path) -> tuple[int, str]:
        match = re.search(r"rc([0-9]+)", path.name)
        return int(match.group(1)) if match else 0, path.name

    return sorted(files, key=key)[-1] if files else None


def frontmatter(text: str) -> dict[str, str]:
    match = re.match(r"^---\s*\n(.*?)\n---\s*\n", text, re.DOTALL)
    if not match:
        return {}
    result: dict[str, str] = {}
    for line in match.group(1).splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        result[key.strip()] = value.strip().strip("'\"")
    return result


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=".", help="Repository root")
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    release = latest_release(root)
    if release is None:
        print("no release notes found", file=sys.stderr)
        return 1

    text = release.read_text(encoding="utf-8", errors="replace")
    fm = frontmatter(text)
    if fm.get("superseded_by"):
        return 0
    status = fm.get("status", "")
    releaseish = (
        fm.get("formal_release") == "true"
        or "ship" in status
        or "release" in status
        or "Release Decision" in text
    )
    if not releaseish:
        return 0

    hits = [pattern for pattern in PLACEHOLDER_PATTERNS if pattern in text]
    if hits:
        rel = release.relative_to(root).as_posix()
        print(f"{rel}: current release note contains placeholder evidence tokens: {', '.join(hits)}")
        return 1

    candidate = fm.get("release_candidate_commit", "")
    if not re.fullmatch(r"[0-9a-f]{40}", candidate):
        rel = release.relative_to(root).as_posix()
        print(f"{rel}: release_candidate_commit must be a 40-character git SHA")
        return 1

    if fm.get("formal_release") == "true" and not fm.get("evidence_bundle"):
        rel = release.relative_to(root).as_posix()
        print(f"{rel}: formal_release true requires evidence_bundle")
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
