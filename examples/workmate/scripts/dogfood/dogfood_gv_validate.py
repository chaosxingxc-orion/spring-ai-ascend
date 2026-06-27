#!/usr/bin/env python3
"""Validate generator-verifier team dogfood via run_events."""
from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


def load_events(path: Path) -> list[dict[str, Any]]:
    return json.loads(path.read_text(encoding="utf-8"))


def validate(events: list[dict[str, Any]], team_expert: str) -> tuple[bool, list[str], list[str]]:
    ok = True
    info: list[str] = []
    warnings: list[str] = []
    names = [e.get("name", "") for e in events]

    for req in ("team.started", "team.iteration.started", "team.member.started", "team.completed"):
        if req not in names:
            ok = False
            info.append(f"MISS: {req}")

    if "team.verify.started" not in names:
        ok = False
        info.append("MISS: team.verify.started")

    started = next(
        (e for e in events if e.get("name") == "team.started" and isinstance(e.get("data"), dict)),
        None,
    )
    if started:
        data = started["data"]
        if data.get("pattern") != "generator-verifier":
            ok = False
            info.append(f"FAIL: pattern={data.get('pattern')}")
        else:
            info.append("OK: pattern=generator-verifier")
    else:
        ok = False

    verify_events = [n for n in names if n.startswith("team.verify.")]
    info.append(f"OK: verify events={verify_events}")
    if "team.verify.accepted" not in names and "team.verify.rejected" not in names:
        warnings.append("no team.verify.accepted/rejected (may still be running)")

    iterations = names.count("team.iteration.started")
    info.append(f"OK: iterations={iterations}")

    return ok, info, warnings


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: dogfood_gv_validate.py <run-events.json> <team-expert-id>", file=sys.stderr)
        return 2
    events = load_events(Path(sys.argv[1]))
    ok, info, warnings = validate(events, sys.argv[2])
    for line in info:
        print(line)
    for line in warnings:
        print(f"WARN: {line}")
    if ok:
        print("VALIDATION: PASS")
        return 0
    print("VALIDATION: FAIL")
    return 1


if __name__ == "__main__":
    sys.exit(main())
