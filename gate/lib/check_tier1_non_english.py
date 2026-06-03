#!/usr/bin/env python3
"""
Tier-1 non-English / mojibake lint (Rule G-25 / Rule 142 / enforcer E190).

Authority: ADR-0156. Closes the external review P1-3 defect class:
non-English text or UTF-8/GBK mojibake leaking into an always-loaded Tier-1
surface, which the model then ingests every session.

Every file that carries a NON-ZERO byte ceiling in `gate/always-loaded-budget.txt`
(the always-loaded Tier-1 set) MUST be free of:
  (a) CJK Unified Ideographs    -- code points U+4E00..U+9FFF
  (b) common UTF-8/GBK mojibake -- U+FFFD replacement char, and the literal
      double-decode sequences "Ã", "â€", "ï¿½"

Fails closed (a missing budget file or unreadable surface is a failure).

CRITICAL (per the review): this checker reports byte offset / line:column
ONLY. It MUST NOT echo the offending non-English text, so the gate log never
embeds non-English source. Each finding is printed as:
    NON-ENGLISH: <relpath>:<line>:<col> byte=<offset> kind=cjk|mojibake

Usage:
    python3 gate/lib/check_tier1_non_english.py            # working tree
    python3 gate/lib/check_tier1_non_english.py --repo DIR # alt root
    python3 gate/lib/check_tier1_non_english.py --budget F # alt budget file

Exit 0 = clean; exit 1 = at least one finding (or a fail-closed condition).
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

# CJK Unified Ideographs block.
CJK_LO = 0x4E00
CJK_HI = 0x9FFF
REPLACEMENT = "�"  # U+FFFD
# Literal double-decode mojibake markers (already-decoded text artefacts).
MOJIBAKE_SEQUENCES = ("Ã", "â€", "ï¿½")


def non_zero_budget_files(budget_path: Path) -> list[str]:
    files: list[str] = []
    for line in budget_path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if not s or s.startswith("#"):
            continue
        if "=" not in s:
            continue
        rel, _, cap = s.rpartition("=")
        rel = rel.strip()
        cap = cap.strip()
        try:
            if int(cap) > 0:
                files.append(rel)
        except ValueError:
            continue
    return files


def scan_file(text: str) -> list[tuple[int, int, int, str]]:
    """Return (line, col, byte_offset, kind) findings. Never returns the text."""
    findings: list[tuple[int, int, int, str]] = []
    byte_offset = 0
    for lineno, line in enumerate(text.splitlines(keepends=True), start=1):
        # Per-character CJK + replacement-char scan.
        col_bytes = 0
        for col, ch in enumerate(line, start=1):
            cp = ord(ch)
            if CJK_LO <= cp <= CJK_HI:
                findings.append((lineno, col, byte_offset + col_bytes, "cjk"))
            elif ch == REPLACEMENT:
                findings.append((lineno, col, byte_offset + col_bytes, "mojibake"))
            col_bytes += len(ch.encode("utf-8"))
        # Multi-char literal mojibake sequences (substring scan).
        for seq in MOJIBAKE_SEQUENCES:
            idx = line.find(seq)
            if idx >= 0:
                col = idx + 1
                pre_bytes = len(line[:idx].encode("utf-8"))
                findings.append((lineno, col, byte_offset + pre_bytes, "mojibake"))
        byte_offset += len(line.encode("utf-8"))
    return findings


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=None)
    ap.add_argument("--budget", default=None)
    args = ap.parse_args()

    repo = Path(args.repo) if args.repo else Path(__file__).resolve().parents[2]
    budget_path = (
        Path(args.budget) if args.budget else repo / "gate" / "always-loaded-budget.txt"
    )

    if not budget_path.is_file():
        print(f"MISSING-BUDGET: {budget_path}")
        return 1

    files = non_zero_budget_files(budget_path)
    if not files:
        # Non-vacuity guard: a budget that yields zero in-scope files is a
        # format-drift signal, not a clean pass. Fail closed.
        print(f"NO-SCOPE: {budget_path} produced zero non-zero-budget entries")
        return 1

    failures: list[str] = []
    for rel in files:
        p = repo / rel
        if not p.is_file():
            # A budgeted always-loaded surface that is missing is fail-closed.
            failures.append(f"MISSING-SURFACE: {rel}")
            continue
        try:
            text = p.read_text(encoding="utf-8", errors="replace")
        except OSError as exc:
            failures.append(f"UNREADABLE: {rel} ({exc.__class__.__name__})")
            continue
        for lineno, col, byte_off, kind in scan_file(text):
            failures.append(
                f"NON-ENGLISH: {rel}:{lineno}:{col} byte={byte_off} kind={kind}"
            )

    if failures:
        for f in failures:
            print(f)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
