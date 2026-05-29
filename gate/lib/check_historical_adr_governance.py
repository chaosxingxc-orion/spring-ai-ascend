#!/usr/bin/env python3
"""Historical-ADR governance checker (Rule G-28 — ADR Normalization).

Authority: ADR-0160 (ADR Governance Model). Two assertions over the historical
ADR corpus, both keyed off the apex authority for the raw-ADR set
(``architecture/facts/generated/adrs.json``, a generated fact projection — see
the Rule G-15 / ADR-0154 authority cascade ``generated facts > DSL > Card/prose``):

  1. LEDGER TOTALITY — every raw ADR enumerated in ``adrs.json`` has a matching
     entry (by ``adr`` id) in ``docs/governance/adr-remediation-ledger.yaml``.
     A raw ADR with no ledger entry is an un-governed decision: the review
     authority (the normalized view) has nowhere to hang.

  2. NORMALIZED-VIEW COVERAGE — every ADR whose ``adrs.json`` status is
     ``accepted`` has a normalized view file at
     ``docs/adr/normalized/ADR-NNNN.yaml``. An accepted ADR cited as current
     authority with no normalized view is the review-blocking condition
     ADR-0160 names: future architecture reviews read the normalized view, not
     raw historical prose.

This helper INVENTS NO ADR ids and NO relationships — it only cross-references
the raw-ADR set (adrs.json), the ledger, and the on-disk normalized views.

Three-mode ratchet (ADR-0160 consequences: ships advisory first, then
changed-files-blocking, then full-blocking — the ADR-0151 -> ADR-0153 /
ADR-0156 ladder). This script implements the two ends:

  --mode advisory   (default) report findings on stdout as ``ADVISORY:`` lines
                    and ALWAYS exit 0. The gate wires this mode, so a not-yet-
                    populated ledger / empty normalized dir never blocks the
                    build while the dedicated normalization wave back-fills.
  --mode blocking   report findings and exit 1 when any finding exists; used by
                    the later ratchet step once coverage is complete.

``changed-files`` is accepted as a recognized mode value (the middle rung of the
ratchet) and behaves like ``advisory`` here: the per-file diff scoping that
distinguishes it lives in the calling gate, not in this enumerator.

Usage:
    python3 gate/lib/check_historical_adr_governance.py --repo . --mode advisory
    python3 gate/lib/check_historical_adr_governance.py --mode blocking

Exit codes:
    0 — advisory mode (always), OR blocking mode with no findings.
    1 — blocking mode with >=1 finding, OR a structural/IO error (e.g. adrs.json
        unreadable) regardless of mode.

Output lines (machine-greppable prefixes):
    ADVISORY: <summary>                       (advisory mode, one per finding kind)
    MISSING-LEDGER-ENTRY: <ADR-id>            (one per raw ADR absent from ledger)
    MISSING-NORMALIZED-VIEW: <ADR-id> (<path>)  (one per accepted ADR with no view)
    OK: <summary>                             (when a check has zero findings)
    ERROR: <message>                          (structural/IO failure; always exit 1)
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ADRS_JSON_REL = "architecture/facts/generated/adrs.json"
LEDGER_REL = "docs/governance/adr-remediation-ledger.yaml"
NORMALIZED_DIR_REL = "docs/adr/normalized"

# ADR ids look like ADR-0001 .. ADR-9999 (zero-padded to >=4 digits in the corpus).
ADR_ID_RE = re.compile(r"^ADR-\d{4,}$")


def repo_root() -> Path:
    """Repository root (two directories above this script: gate/lib/ -> repo)."""
    return Path(__file__).resolve().parents[2]


# ---------------------------------------------------------------------------
# Raw-ADR set (the apex authority for "which ADRs exist"): adrs.json facts.
# ---------------------------------------------------------------------------
def load_raw_adrs(root: Path) -> tuple[list[dict[str, str]], str | None]:
    """Return ([{id, status}, ...], error).

    The list preserves adrs.json order. ``error`` is non-None on an IO/parse
    failure (the caller turns that into a hard ERROR + exit 1 in every mode,
    since a missing apex authority is never an advisory condition).
    """
    path = root / ADRS_JSON_REL
    if not path.is_file():
        return [], f"missing raw-ADR fact file: {ADRS_JSON_REL}"
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return [], f"cannot read {ADRS_JSON_REL}: {exc}"
    facts = payload.get("facts") if isinstance(payload, dict) else None
    if not isinstance(facts, list):
        return [], f"{ADRS_JSON_REL} has no top-level 'facts' list"
    out: list[dict[str, str]] = []
    for fact in facts:
        if not isinstance(fact, dict):
            continue
        observed = fact.get("observed_value")
        if not isinstance(observed, dict):
            continue
        adr_id = observed.get("id")
        status = observed.get("status", "")
        if isinstance(adr_id, str) and ADR_ID_RE.match(adr_id):
            out.append({"id": adr_id, "status": str(status)})
    return out, None


# ---------------------------------------------------------------------------
# Ledger membership: docs/governance/adr-remediation-ledger.yaml entries[].adr.
# PyYAML-optional, matching the gate's Windows-host discipline. The fallback is
# a deliberately narrow regex over the flat ledger schema (a top-level
# `entries:` list of `- adr: ADR-NNNN` mapping items); it never tries to be a
# general YAML parser.
# ---------------------------------------------------------------------------
def load_ledger_adr_ids(root: Path) -> tuple[set[str], str | None]:
    """Return (set_of_adr_ids_in_ledger, error_if_unreadable)."""
    path = root / LEDGER_REL
    if not path.is_file():
        # A missing ledger is itself a finding (every raw ADR is then un-entried),
        # not an error: report it via the empty set so advisory mode can surface
        # the gap while the normalization wave authors the file.
        return set(), None
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        return set(), f"cannot read {LEDGER_REL}: {exc}"

    parsed = _ledger_ids_via_pyyaml(text)
    if parsed is not None:
        return parsed, None
    return _ledger_ids_via_regex(text), None


def _ledger_ids_via_pyyaml(text: str) -> set[str] | None:
    """Strict parse via PyYAML when importable; None signals 'use the fallback'."""
    try:
        import yaml  # type: ignore[import-not-found]
    except ImportError:
        return None
    try:
        doc = yaml.safe_load(text)
    except yaml.YAMLError:  # type: ignore[attr-defined]
        # Malformed YAML: hand off to the regex fallback rather than crashing.
        return None
    if not isinstance(doc, dict):
        return set()
    entries = doc.get("entries")
    if not isinstance(entries, list):
        return set()
    ids: set[str] = set()
    for entry in entries:
        if isinstance(entry, dict):
            adr = entry.get("adr")
            if isinstance(adr, str) and ADR_ID_RE.match(adr.strip()):
                ids.add(adr.strip())
    return ids


# A list item that opens an entry mapping with an `adr:` key on the same line:
#   - adr: ADR-0001
# The leading `-` distinguishes an entry's own id from any nested `adr:`-named
# field, and anchors the match to top-level list items of `entries:`.
_LEDGER_ENTRY_ADR_RE = re.compile(
    r"^\s*-\s*adr:\s*[\"']?(ADR-\d{4,})[\"']?\s*$", re.MULTILINE
)


def _ledger_ids_via_regex(text: str) -> set[str]:
    """Fallback extraction of entries[].adr ids without a YAML library."""
    return {m.group(1) for m in _LEDGER_ENTRY_ADR_RE.finditer(text)}


# ---------------------------------------------------------------------------
# Normalized-view presence: docs/adr/normalized/<ADR-id>.yaml on disk.
# ---------------------------------------------------------------------------
def normalized_view_path(root: Path, adr_id: str) -> Path:
    return root / NORMALIZED_DIR_REL / f"{adr_id}.yaml"


def find_findings(root: Path) -> tuple[list[str], list[str], str | None]:
    """Compute (missing_ledger_ids, missing_view_ids, fatal_error).

    ``missing_ledger_ids`` — raw ADRs (adrs.json order) with no ledger entry.
    ``missing_view_ids``   — accepted raw ADRs with no normalized view file.
    ``fatal_error``        — non-None when the apex authority is unreadable.
    """
    raw_adrs, err = load_raw_adrs(root)
    if err is not None:
        return [], [], err
    ledger_ids, ledger_err = load_ledger_adr_ids(root)
    if ledger_err is not None:
        return [], [], ledger_err

    missing_ledger: list[str] = []
    missing_view: list[str] = []
    for adr in raw_adrs:
        adr_id = adr["id"]
        if adr_id not in ledger_ids:
            missing_ledger.append(adr_id)
        if adr["status"] == "accepted" and not normalized_view_path(root, adr_id).is_file():
            missing_view.append(adr_id)
    return missing_ledger, missing_view, None


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Rule G-28 — historical-ADR governance (ledger totality + normalized-view coverage)"
    )
    parser.add_argument(
        "--repo",
        default=None,
        help="Repository root. Defaults to the script-derived root.",
    )
    parser.add_argument(
        "--mode",
        default="advisory",
        choices=("advisory", "changed-files", "blocking"),
        help="advisory (default, always exit 0) | changed-files (advisory here) | blocking (exit 1 on findings).",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    root = Path(args.repo) if args.repo else repo_root()
    if not root.is_dir():
        print(f"ERROR: --repo {root} is not a directory", file=sys.stderr)
        return 1

    missing_ledger, missing_view, fatal = find_findings(root)
    if fatal is not None:
        print(f"ERROR: {fatal}")
        return 1

    blocking = args.mode == "blocking"

    # Per-item lines (greppable). Capped so a fully-unpopulated corpus does not
    # flood the gate log; the summary line always carries the true total.
    for adr_id in missing_ledger[:20]:
        print(f"MISSING-LEDGER-ENTRY: {adr_id}")
    for adr_id in missing_view[:20]:
        path = normalized_view_path(root, adr_id).relative_to(root).as_posix()
        print(f"MISSING-NORMALIZED-VIEW: {adr_id} ({path})")

    # Summary lines.
    if missing_ledger:
        msg = (
            f"{len(missing_ledger)} raw ADR(s) in {ADRS_JSON_REL} have no entry in "
            f"{LEDGER_REL} (first: {missing_ledger[0]})"
        )
        print(f"{'BLOCKING' if blocking else 'ADVISORY'}: {msg}")
    else:
        print(f"OK: every raw ADR in {ADRS_JSON_REL} has a ledger entry")

    if missing_view:
        msg = (
            f"{len(missing_view)} accepted ADR(s) have no normalized view under "
            f"{NORMALIZED_DIR_REL}/ (first: {missing_view[0]})"
        )
        print(f"{'BLOCKING' if blocking else 'ADVISORY'}: {msg}")
    else:
        print(f"OK: every accepted ADR has a normalized view under {NORMALIZED_DIR_REL}/")

    if blocking and (missing_ledger or missing_view):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
