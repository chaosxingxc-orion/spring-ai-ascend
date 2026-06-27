#!/usr/bin/env python3
"""Validate openjiuwen TeamAgent orchestrator dogfood via run_events."""
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

    for req in ("team.started", "team.completed"):
        if req not in names:
            ok = False
            info.append(f"MISS: {req}")

    started = next(
        (e for e in events if e.get("name") == "team.started" and isinstance(e.get("data"), dict)),
        None,
    )
    if started:
        data = started["data"]
        if data.get("teamId") != team_expert:
            ok = False
            info.append(f"FAIL: teamId={data.get('teamId')}")
        else:
            info.append(f"OK: teamId={team_expert}")
        if data.get("pattern") != "orchestrator":
            ok = False
            info.append(f"FAIL: pattern={data.get('pattern')}")
        else:
            info.append("OK: pattern=orchestrator")
        runtime = data.get("teamRuntime")
        if runtime != "openjiuwen-team":
            ok = False
            info.append(f"FAIL: teamRuntime={runtime!r} (expected openjiuwen-team)")
        else:
            info.append("OK: teamRuntime=openjiuwen-team")
        members = data.get("members")
        member_count = data.get("memberCount")
        if not isinstance(members, list) or len(members) < 2:
            ok = False
            info.append("FAIL: team.started members roster too small")
        elif isinstance(member_count, int) and member_count < len(members):
            ok = False
            info.append("FAIL: memberCount < roster size")
        else:
            info.append(f"OK: roster size={len(members)} memberCount={member_count}")
    else:
        ok = False

    completed = next(
        (e for e in events if e.get("name") == "team.completed" and isinstance(e.get("data"), dict)),
        None,
    )
    if completed:
        data = completed["data"]
        if data.get("anyMemberFailed") is True:
            warnings.append("team.completed anyMemberFailed=true")
        info.append("OK: team.completed present")
    elif "team.completed" in names:
        ok = False
        info.append("FAIL: team.completed missing data payload")

    if "team.memory" in names:
        info.append("OK: team.memory emitted")
    else:
        warnings.append("no team.memory events (blackboard may be empty)")

    member_starts = names.count("team.member.started")
    member_done = names.count("team.member.completed")
    info.append(f"INFO: member started={member_starts} completed={member_done}")
    if member_starts == 0:
        warnings.append("no team.member.started — hybrid lead may not have spawned yet")

    if "team.lead.synthesizing" in names:
        warnings.append("team.lead.synthesizing present (legacy orchestrator signal; optional on TeamAgent path)")

    return ok, info, warnings


def main() -> int:
    if len(sys.argv) != 3:
        print(
            "Usage: dogfood_teamagent_validate.py <run-events.json> <team-expert-id>",
            file=sys.stderr,
        )
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
