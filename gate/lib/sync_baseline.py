#!/usr/bin/env python3
"""Derive drift-prone baseline_metrics counts from the living repository.

Resurrects the guard retired with Rule 82 (baseline_metrics_single_source). A
small, EXPLICIT allowlist of baseline_metrics fields whose canonical count is
unambiguous is computed from source, then either checked (--check, the CI guard)
or written in place (--write).

Fields with subtle/unsettled counting definitions are deliberately OUT of scope
(see DERIVABLE comment). The architecture graph node/edge counts stay owned by
gate Rule 106 (cross_authority_parity), not by this tool.

  python gate/lib/sync_baseline.py --check   # exit 1 if any derivable field drifted
  python gate/lib/sync_baseline.py --write    # rewrite drifted fields in place
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_release_evidence as bre  # noqa: E402

ROOT = bre.repo_default()
STATUS = ROOT / "docs" / "governance" / "architecture-status.yaml"


def _count_glob(rel: str, pattern: str) -> int:
    directory = ROOT / rel
    return len(list(directory.glob(pattern))) if directory.is_dir() else 0


# field -> canonical counter. ONLY fields with an unambiguous, source-of-truth
# definition belong here. Deliberately EXCLUDED because their definition is not
# yet settled enough to derive safely (a wrong counter writes a wrong baseline):
#   phase_loading_skills (a curated subset: excludes formal-release-transaction.md),
#   section_4_constraints (numbered-constraint parse), feature_corpus_size,
#   l1_modules_with_canonical_directory, maven_tests_green (needs Maven reports),
#   gate_executable_test_cases (needs a self-test harness run).
# Owned by OTHER enforcers, so not duplicated here: architecture_graph_nodes/edges
# (gate Rule 106), workspace_elements/relationships (check_workspace_baseline_parity.py).
DERIVABLE = {
    "active_gate_checks": lambda: bre.count_gate_rules(ROOT),
    "enforcer_rows": lambda: bre.count_enforcers(ROOT),
    "adr_count": lambda: bre.count_adrs(ROOT),
    "active_governing_principles": lambda: _count_glob(
        "docs/governance/principles", "P-*.md"
    ),
    "active_engineering_rules": lambda: bre.count_active_engineering_rules(ROOT),
    "phase_contracts": lambda: _count_glob("docs/governance/contracts", "*.md"),
    "recurring_defect_families": lambda: bre.count_recurring_families(ROOT),
}


def canonical() -> dict[str, int]:
    return {field: counter() for field, counter in DERIVABLE.items()}


def current(text: str) -> dict[str, int | None]:
    found: dict[str, int | None] = {}
    for field in DERIVABLE:
        match = re.search(
            rf"^\s+{re.escape(field)}:\s+([0-9]+)", text, re.MULTILINE
        )
        found[field] = int(match.group(1)) if match else None
    return found


def drift() -> list[tuple[str, int | None, int]]:
    found = current(STATUS.read_text(encoding="utf-8"))
    want = canonical()
    return [(f, found[f], want[f]) for f in DERIVABLE if found[f] != want[f]]


def write(changes: list[tuple[str, int | None, int]]) -> None:
    text = STATUS.read_text(encoding="utf-8")
    for field, _old, new in changes:
        text = re.sub(
            rf"^(\s+{re.escape(field)}:\s+)[0-9]+",
            rf"\g<1>{new}",
            text,
            count=1,
            flags=re.MULTILINE,
        )
    STATUS.write_text(text, encoding="utf-8")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument(
        "--check", action="store_true", help="exit 1 if any derivable field drifted"
    )
    mode.add_argument(
        "--write", action="store_true", help="rewrite drifted fields in place"
    )
    args = parser.parse_args(argv)

    changes = drift()

    if args.check:
        if changes:
            print("baseline_metrics DRIFT (derivable fields):")
            for field, old, new in changes:
                print(f"  {field}: baseline={old} canonical={new}")
            print("fix: python gate/lib/sync_baseline.py --write")
            return 1
        print("baseline_metrics: all derivable fields match canonical counts.")
        return 0

    if changes:
        write(changes)
        for field, old, new in changes:
            print(f"synced {field}: {old} -> {new}")
    else:
        print("baseline_metrics: already in sync; nothing to write.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
