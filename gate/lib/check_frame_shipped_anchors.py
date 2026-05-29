#!/usr/bin/env python3
"""
Shipped-frame anchor integrity checker (Rule G-23 / Rule 140 / enforcer E188).

Authority: ADR-0157 (EngineeringFrame Ontology) + ADR-0158 (transport-agnostic
EnginePort boundary). Closes the external review F8.3 defect class: an
EngineeringFrame that declares `saa.status "shipped"` but anchors no
FunctionPoint is a structural lie — a shipped responsibility slice with no
work attached to it.

Every `SAA EngineeringFrame` element whose `saa.status` is `shipped` MUST have
at least one `anchors` relationship edge to a FunctionPoint. Frame elements are
authored across BOTH `architecture/features/engineering-frames.dsl` and
`architecture/features/features.dsl` (the agent-service Layer features are
re-tagged as frames in features.dsl); the `anchors` edges all live in
engineering-frames.dsl.

ADR-backed exceptions are listed (one frame var or saa.id per line) in
`gate/frame-shipped-zero-anchor-allowlist.txt`; that file ships empty.

Usage:
    python3 gate/lib/check_frame_shipped_anchors.py            # working tree
    python3 gate/lib/check_frame_shipped_anchors.py --repo DIR # alternate root

Exit 0 = clean; exit 1 = one or more shipped frames lack an anchors edge.
On failure each offending frame is printed as `MISSING-ANCHOR: <id> (<var>)`.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ELEMENT_RE = re.compile(
    r'(\w+)\s*=\s*element\s+"[^"]*"\s+"EngineeringFrame"', re.MULTILINE
)
ID_RE = re.compile(r'"saa\.id"\s+"([^"]+)"')
STATUS_RE = re.compile(r'"saa\.status"\s+"([^"]+)"')
# A relationship edge whose properties block declares saa.rel = anchors.
ANCHOR_EDGE_RE = re.compile(
    r'(\w+)\s*->\s*\w+\s+"[^"]*"\s+"SAA Relationship"\s*\{[^}]*?"saa\.rel"\s+"anchors"',
    re.DOTALL,
)


def parse_frames(text: str) -> dict[str, tuple[str, str]]:
    """Return {var: (saa.id, saa.status)} for every EngineeringFrame element."""
    frames: dict[str, tuple[str, str]] = {}
    for m in ELEMENT_RE.finditer(text):
        var = m.group(1)
        # The properties block follows immediately; a 900-char window covers it.
        block = text[m.start(): m.start() + 900]
        sid = ID_RE.search(block)
        sst = STATUS_RE.search(block)
        frames[var] = (
            sid.group(1) if sid else "?",
            sst.group(1) if sst else "?",
        )
    return frames


def anchor_source_vars(text: str) -> set[str]:
    """Frame vars that are the source of at least one anchors edge."""
    return {m.group(1) for m in ANCHOR_EDGE_RE.finditer(text)}


def load_allowlist(path: Path) -> set[str]:
    allow: set[str] = set()
    if not path.is_file():
        return allow
    for line in path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if not s or s.startswith("#"):
            continue
        allow.add(s)
    return allow


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=None)
    args = ap.parse_args()

    repo = Path(args.repo) if args.repo else Path(__file__).resolve().parents[2]
    ef_path = repo / "architecture" / "features" / "engineering-frames.dsl"
    feat_path = repo / "architecture" / "features" / "features.dsl"
    allowlist_path = repo / "gate" / "frame-shipped-zero-anchor-allowlist.txt"

    if not ef_path.is_file():
        print(f"MISSING-FILE: {ef_path}")
        return 1

    ef_text = ef_path.read_text(encoding="utf-8")
    feat_text = feat_path.read_text(encoding="utf-8") if feat_path.is_file() else ""

    frames = {**parse_frames(feat_text), **parse_frames(ef_text)}
    # Anchors edges only live in engineering-frames.dsl.
    anchored = anchor_source_vars(ef_text)
    allow = load_allowlist(allowlist_path)

    failures: list[str] = []
    for var, (sid, status) in sorted(frames.items()):
        if status != "shipped":
            continue
        if var in anchored:
            continue
        if var in allow or sid in allow:
            continue
        failures.append(f"MISSING-ANCHOR: {sid} ({var})")

    if failures:
        for f in failures:
            print(f)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
