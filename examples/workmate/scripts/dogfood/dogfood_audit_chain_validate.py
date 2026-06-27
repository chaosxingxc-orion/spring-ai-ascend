#!/usr/bin/env python3
"""Offline validator for W31 audit hash chain integrity (mirrors AuditChainVerifier)."""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path
from typing import Any


GENESIS_HASH = "0" * 64


def sha256_hex(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def canonical_payload(
    session_id: str,
    run_id: str,
    seq_global: int,
    event_name: str,
    payload_hash: str,
    created_at: str,
) -> str:
    return (
        "{"
        f'"sessionId":"{session_id}",'
        f'"runId":"{run_id}",'
        f'"seqGlobal":{seq_global},'
        f'"eventName":"{event_name}",'
        f'"payloadHash":"{payload_hash}",'
        f'"createdAt":"{created_at}"'
        "}"
    )


def compute_entry_hash(
    prev_hash: str,
    session_id: str,
    run_id: str,
    seq_global: int,
    event_name: str,
    payload_hash: str,
    created_at: str,
) -> str:
    payload = canonical_payload(session_id, run_id, seq_global, event_name, payload_hash, created_at)
    return sha256_hex(prev_hash + payload)


def verify_entries(entries: list[dict[str, Any]]) -> tuple[bool, list[str]]:
    info: list[str] = []
    if not entries:
        info.append("OK: empty chain")
        return True, info

    sorted_entries = sorted(entries, key=lambda row: int(row["seqGlobal"]))
    prev_hash = GENESIS_HASH
    for row in sorted_entries:
        seq = int(row["seqGlobal"])
        if row.get("prevHash") != prev_hash:
            info.append(
                f"FAIL: seq={seq} prev_hash expected={prev_hash} actual={row.get('prevHash')}"
            )
            return False, info
        expected = compute_entry_hash(
            row["prevHash"],
            row["sessionId"],
            row["runId"],
            seq,
            row["eventName"],
            row["payloadHash"],
            row["createdAt"],
        )
        if row.get("entryHash") != expected:
            info.append(
                f"FAIL: seq={seq} entry_hash expected={expected} actual={row.get('entryHash')}"
            )
            return False, info
        prev_hash = row["entryHash"]

    info.append(f"OK: verified through seq={sorted_entries[-1]['seqGlobal']}")
    return True, info


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: dogfood_audit_chain_validate.py <audit-chain.json>", file=sys.stderr)
        return 2
    entries = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    ok, info = verify_entries(entries)
    for line in info:
        print(line)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
