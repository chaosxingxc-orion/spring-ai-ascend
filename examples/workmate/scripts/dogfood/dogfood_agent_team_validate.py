#!/usr/bin/env python3
"""Validate agent-team (parallel fan-out) team dogfood via run_events."""
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

    for req in (
        "team.started",
        "team.parallel.started",
        "team.member.started",
        "team.lead.synthesizing",
        "team.completed",
    ):
        if req not in names:
            ok = False
            info.append(f"MISS: {req}")

    started = next(
        (e for e in events if e.get("name") == "team.started" and isinstance(e.get("data"), dict)),
        None,
    )
    if started:
        data = started["data"]
        if data.get("pattern") != "agent-team":
            ok = False
            info.append(f"FAIL: pattern={data.get('pattern')}")
        else:
            info.append("OK: pattern=agent-team")
    else:
        ok = False

    member_starts = names.count("team.member.started")
    member_done = names.count("team.member.completed")
    info.append(f"OK: member runs started={member_starts} completed={member_done}")
    if member_starts < 2:
        warnings.append("expected >=2 parallel team.member.started")

    return ok, info, warnings


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: dogfood_agent_team_validate.py <run-events.json> <team-expert-id>", file=sys.stderr)
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
