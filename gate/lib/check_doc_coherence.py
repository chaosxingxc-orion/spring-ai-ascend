#!/usr/bin/env python3
"""
Cross-doc coherence sweep for the 7 entry surfaces of the architecture-design system.

Authority: ADR-0150 (Wave 8 docs consolidation) Step 11.7. ADVISORY at Wave 8;
promotable to BLOCKING in a future sub-wave once the rhetorical-stance + reading-path
discipline has soaked.

Checks (all should PASS — failures are reported as ADVISORY warnings; exit 0 either way):

1. README.md contains a `## Reading path` section.
2. SESSION-START-CONTEXT.md reading-order table covers the same surfaces as README#Reading-path.
3. AGENTS.md contains `## For AI assistants` + `## Rhetorical stance of each top-level doc`.
4. ARCHITECTURE.md contains §0.6 (Rhetorical stance) + §0.7 (Constraint &lt;-&gt; Rule cross-reference).
5. CLAUDE.md contains `## Rhetorical stance` block + `## Constraint &lt;-&gt; Rule mapping` section.
6. docs/contracts/contract-catalog.md contains `## Rhetorical stance` block.

Each check is a simple regex/substring grep with a clear pass/fail line.

Usage:
    python3 gate/lib/check_doc_coherence.py
    # exit 0 always at W8; exit 1 only on missing files (genuine I/O error).
"""
from __future__ import annotations

import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

CHECKS = [
    {
        "name": "README.md has '## Reading path'",
        "path": REPO / "README.md",
        "must_contain": "## Reading path",
    },
    {
        "name": "SESSION-START-CONTEXT.md reading-order table aligns",
        "path": REPO / "docs" / "governance" / "SESSION-START-CONTEXT.md",
        "must_contain": "architecture/workspace.dsl` + `architecture/README.md",
    },
    {
        "name": "AGENTS.md '## For AI assistants — load this set'",
        "path": REPO / "AGENTS.md",
        "must_contain": "## For AI assistants",
    },
    {
        "name": "AGENTS.md rhetorical-stance table",
        "path": REPO / "AGENTS.md",
        "must_contain": "## Rhetorical stance of each top-level doc",
    },
    {
        "name": "ARCHITECTURE.md §0.6 Rhetorical stance",
        "path": REPO / "ARCHITECTURE.md",
        "must_contain": "## 0.6 Rhetorical stance of this document",
    },
    {
        "name": "ARCHITECTURE.md §0.7 Constraint &lt;-&gt; Rule cross-reference",
        "path": REPO / "ARCHITECTURE.md",
        "must_contain": "## 0.7 Constraint",
    },
    {
        "name": "CLAUDE.md '## Rhetorical stance'",
        "path": REPO / "CLAUDE.md",
        "must_contain": "## Rhetorical stance",
    },
    {
        "name": "CLAUDE.md '## Constraint &lt;-&gt; Rule mapping'",
        "path": REPO / "CLAUDE.md",
        "must_contain": "## Constraint",
    },
    {
        "name": "docs/contracts/contract-catalog.md '## Rhetorical stance'",
        "path": REPO / "docs" / "contracts" / "contract-catalog.md",
        "must_contain": "## Rhetorical stance",
    },
    {
        "name": "architecture/README.md '## Reading path'",
        "path": REPO / "architecture" / "README.md",
        "must_contain": "## Reading path",
    },
    {
        "name": "architecture/docs/L1/README.md exists",
        "path": REPO / "architecture" / "docs" / "L1" / "README.md",
        "must_contain": "L1 Module Design Index",
    },
]


def main() -> int:
    print("Cross-doc coherence sweep (advisory; W8 ADR-0150 step 11.7)")
    print("=" * 64)
    passed = 0
    advisory = 0
    missing = 0
    for check in CHECKS:
        name = check["name"]
        path = check["path"]
        needle = check["must_contain"]
        if not path.is_file():
            print(f"  MISSING: {name}  -- {path.relative_to(REPO)} does not exist")
            missing += 1
            continue
        text = path.read_text(encoding="utf-8")
        if needle in text:
            print(f"  PASS:    {name}")
            passed += 1
        else:
            print(f"  ADVISORY (missing marker): {name}  -- expected substring {needle!r}")
            advisory += 1

    print("=" * 64)
    print(f"Summary: {passed} pass / {advisory} advisory / {missing} missing-file")
    if missing > 0:
        print("FAIL: missing required files (file-level break, not advisory)", file=sys.stderr)
        return 1
    # Advisory failures do not block at W8 per the migration plan.
    return 0


if __name__ == "__main__":
    sys.exit(main())
