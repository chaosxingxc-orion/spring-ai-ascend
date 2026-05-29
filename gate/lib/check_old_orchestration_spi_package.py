#!/usr/bin/env python3
"""
Old orchestration-SPI package-name ban (Rule G-24 / Rule 141 / enforcer E189).

Authority: ADR-0158 (transport-agnostic EnginePort boundary). The neutral
orchestration/engine SPI was re-homed from
`com.huawei.ascend.engine.orchestration.spi` (package path
`engine/orchestration/spi`) to `com.huawei.ascend.bus.spi.engine`. Closes the
external review F6.3 defect class: active authority surfaces naming the old
package as the CURRENT home — a stale instruction that points an AI agent at a
package that no longer exists.

Active authority surfaces MUST NOT name `engine.orchestration.spi` /
`engine/orchestration/spi` as the current home. Past-tense history (the
re-home / dissolution narrative) is legitimate and is skipped via per-line
historical markers; the ADR-0158 statement that declares the NEW home is also
allowed because it carries a historical marker on the same line.

In-scope surfaces (current authority):
    docs/governance/templates/**.j2   (templated source of rendered .md)
    architecture/docs/**              (rendered narrative)
    docs/dfx/**
    docs/quickstart.md
    docs/cross-cutting/oss-bill-of-materials.md
    docs/governance/enforcers.yaml
    docs/governance/architecture-status.yaml
    docs/contracts/contract-catalog.md
    architecture/features/**

Excluded (historical / generated / migration tooling):
    docs/logs/**, docs/adr/**, docs/governance/rule-history.md, docs/archive/**,
    scripts/**, architecture/generated/**, architecture/facts/generated/**

NOTE on the live tree: the rendered `architecture/docs/**.md` are re-rendered
from the fixed `.j2` sources in a later wave (W6). Until that render runs the
rendered Markdown still names the old package as the current home, so the
live-tree PASS of Rule 141 is validated POST-RENDER (W8). The `.j2` sources and
the non-templated inline surfaces are already clean.

Usage:
    python3 gate/lib/check_old_orchestration_spi_package.py            # working tree
    python3 gate/lib/check_old_orchestration_spi_package.py --repo DIR # alt root

Exit 0 = clean; exit 1 = at least one active surface names the old package as
current. Each offending line is printed as `OLD-PACKAGE: <relpath>:<lineno>`
(the offending text is NOT echoed to keep the gate log free of stale package
strings — line numbers only).
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# Matches the old package in dotted (engine.orchestration.spi) or path
# (engine/orchestration/spi) form.
OLD_PACKAGE_RE = re.compile(r"engine[./]orchestration[./]spi")

# Per-line markers that make a mention legitimate historical / re-home prose.
# These cover the dissolution / relocation narrative AND the ADR-0158 new-home
# statement (which says "re-homed ... to bus.spi.engine ... This is the current home").
HISTORICAL_MARKERS = (
    "re-homed",
    "re-home",
    "relocated here from",
    "relocated from",
    "relocated to",
    "transient",
    "transiently co-located",
    "co-located",
    "co-location",
    "co-locating",
    "dissolved",
    "dissolution",
    "ADR-0088",
    "ADR-0158",
    "previously",
    "former",
    "back-dep",
    "glossary synonym",
)

# (subdir-glob, recurse?) entries describing the in-scope surfaces.
SCOPE_FILES = [
    "docs/quickstart.md",
    "docs/cross-cutting/oss-bill-of-materials.md",
    "docs/governance/enforcers.yaml",
    "docs/governance/architecture-status.yaml",
    "docs/contracts/contract-catalog.md",
]
SCOPE_GLOBS = [
    ("docs/governance/templates", "**/*.j2"),
    ("architecture/docs", "**/*.md"),
    ("docs/dfx", "**/*"),
    ("architecture/features", "**/*"),
]

# Path prefixes excluded as historical / generated / migration tooling.
EXCLUDE_PREFIXES = (
    "docs/logs/",
    "docs/adr/",
    "docs/governance/rule-history.md",
    "docs/archive/",
    "scripts/",
    "architecture/generated/",
    "architecture/facts/generated/",
)


def is_excluded(rel: str) -> bool:
    return any(rel.startswith(p) or rel == p for p in EXCLUDE_PREFIXES)


def line_is_historical(line: str) -> bool:
    return any(marker in line for marker in HISTORICAL_MARKERS)


def iter_candidate_files(repo: Path):
    for f in SCOPE_FILES:
        p = repo / f
        if p.is_file():
            yield p
    for base, pattern in SCOPE_GLOBS:
        root = repo / base
        if not root.is_dir():
            continue
        for p in sorted(root.glob(pattern)):
            if p.is_file():
                yield p


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=None)
    args = ap.parse_args()

    repo = Path(args.repo) if args.repo else Path(__file__).resolve().parents[2]

    failures: list[str] = []
    seen: set[Path] = set()
    for p in iter_candidate_files(repo):
        if p in seen:
            continue
        seen.add(p)
        rel = p.relative_to(repo).as_posix()
        if is_excluded(rel):
            continue
        try:
            text = p.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for lineno, line in enumerate(text.splitlines(), start=1):
            if not OLD_PACKAGE_RE.search(line):
                continue
            if line_is_historical(line):
                continue
            failures.append(f"OLD-PACKAGE: {rel}:{lineno}")

    if failures:
        for f in failures:
            print(f)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
