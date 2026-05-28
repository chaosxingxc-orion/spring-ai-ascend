#!/usr/bin/env python3
"""Gate check: workspace baseline parity (Round-3 Wave Alpha sweep defect 17).

The recurring `F-numeric-drift` family has produced a workspace-counts
drift in every fact-layer review round. `architecture-status.yaml#
baseline_metrics.workspace_elements / workspace_relationships` claims
canonical baseline values that nothing automatically compares against
the live workspace projection at
`docs/governance/architecture-workspace-graph.yaml` (header fields
`node_count` / `edge_count`).

This script closes the gap: it parses both surfaces and fails closed
when they disagree. Invoked from `gate/check_architecture_workspace.sh`
after the projection is re-emitted, so every gate run compares baseline
prose against live machine-generated values.

Authority: ADR-0154 (Fact-Layer Authority); Rule G-8 (Cross-Authority
Parity, sub-clause .a — graph baseline parity, extended here to
workspace_elements / workspace_relationships).

Usage:
    python3 gate/lib/check_workspace_baseline_parity.py            # default check
    python3 gate/lib/check_workspace_baseline_parity.py --repo DIR # explicit root

Exit codes:
    0 — baseline matches live projection
    1 — drift detected (prints diff to stderr)
    2 — input files missing or malformed (configuration error)
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

_BASELINE_RE = re.compile(
    r"^\s*(workspace_elements|workspace_relationships):\s*(\d+)",
    re.MULTILINE,
)
_HEADER_RE = re.compile(
    r"^(node_count|edge_count):\s*(\d+)",
    re.MULTILINE,
)


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent.parent


def parse_baseline(yaml_path: Path) -> dict[str, int]:
    text = yaml_path.read_text(encoding="utf-8")
    out: dict[str, int] = {}
    for match in _BASELINE_RE.finditer(text):
        out[match.group(1)] = int(match.group(2))
    return out


def parse_projection(yaml_path: Path) -> dict[str, int]:
    text = yaml_path.read_text(encoding="utf-8")
    out: dict[str, int] = {}
    for match in _HEADER_RE.finditer(text):
        out[match.group(1)] = int(match.group(2))
    return out


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Workspace baseline parity check.")
    parser.add_argument("--repo", default=None, help="Repository root (defaults to derived).")
    args = parser.parse_args(argv)
    root = Path(args.repo) if args.repo else repo_root()

    baseline_path = root / "docs" / "governance" / "architecture-status.yaml"
    projection_path = root / "docs" / "governance" / "architecture-workspace-graph.yaml"

    if not baseline_path.is_file():
        print(f"workspace_baseline_parity: missing {baseline_path}", file=sys.stderr)
        return 2
    if not projection_path.is_file():
        print(f"workspace_baseline_parity: missing {projection_path}", file=sys.stderr)
        return 2

    baseline = parse_baseline(baseline_path)
    projection = parse_projection(projection_path)

    findings: list[str] = []
    pairs = (
        ("workspace_elements", "node_count"),
        ("workspace_relationships", "edge_count"),
    )
    for yaml_key, proj_key in pairs:
        b_val = baseline.get(yaml_key)
        p_val = projection.get(proj_key)
        if b_val is None:
            findings.append(
                f"workspace_baseline_parity: baseline missing {yaml_key} in {baseline_path.name}"
            )
            continue
        if p_val is None:
            findings.append(
                f"workspace_baseline_parity: projection missing {proj_key} in {projection_path.name}"
            )
            continue
        if b_val != p_val:
            findings.append(
                f"workspace_baseline_parity: "
                f"architecture-status.yaml#baseline_metrics.{yaml_key}={b_val} "
                f"but architecture-workspace-graph.yaml#{proj_key}={p_val} "
                f"-- Rule G-8 (Cross-Authority Parity) extended to workspace counts"
            )

    if findings:
        for f in findings:
            print(f, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
