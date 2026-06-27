#!/usr/bin/env python3
"""Offline test for dogfood_audit_chain_validate.py (W31 tamper detection)."""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
VALIDATOR = ROOT / "dogfood_audit_chain_validate.py"


def build_good_chain() -> list[dict]:
    # Import hash helpers from validator module path
    sys.path.insert(0, str(ROOT))
    from dogfood_audit_chain_validate import GENESIS_HASH, compute_entry_hash

    session_id = "11111111-1111-1111-1111-111111111111"
    payload_hash = "a" * 64
    entry1_hash = compute_entry_hash(
        GENESIS_HASH, session_id, "run-1", 1, "tool.start", payload_hash, "2026-06-20T10:00:00Z"
    )
    entry2_hash = compute_entry_hash(
        entry1_hash, session_id, "run-1", 2, "tool.end", payload_hash, "2026-06-20T10:00:01Z"
    )
    return [
        {
            "seqGlobal": 1,
            "sessionId": session_id,
            "runId": "run-1",
            "eventName": "tool.start",
            "payloadHash": payload_hash,
            "prevHash": GENESIS_HASH,
            "entryHash": entry1_hash,
            "createdAt": "2026-06-20T10:00:00Z",
        },
        {
            "seqGlobal": 2,
            "sessionId": session_id,
            "runId": "run-1",
            "eventName": "tool.end",
            "payloadHash": payload_hash,
            "prevHash": entry1_hash,
            "entryHash": entry2_hash,
            "createdAt": "2026-06-20T10:00:01Z",
        },
    ]


def run_validator(entries: list[dict]) -> int:
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as handle:
        json.dump(entries, handle)
        path = handle.name
    result = subprocess.run(
        [sys.executable, str(VALIDATOR), path],
        capture_output=True,
        text=True,
    )
    print(result.stdout, end="")
    if result.stderr:
        print(result.stderr, file=sys.stderr, end="")
    Path(path).unlink(missing_ok=True)
    return result.returncode


def main() -> int:
    good = build_good_chain()
    print("=== good chain ===")
    if run_validator(good) != 0:
        print("FAIL: expected valid chain", file=sys.stderr)
        return 1

    tampered = [dict(good[0]), dict(good[1])]
    tampered[0]["entryHash"] = "deadbeef" * 8
    print("=== tampered chain ===")
    if run_validator(tampered) == 0:
        print("FAIL: expected tampered chain to fail", file=sys.stderr)
        return 1

    print("OK: tamper detection fixture passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
