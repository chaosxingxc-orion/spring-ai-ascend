#!/usr/bin/env python3
"""Validate gpt-researcher-team TeamAgent dogfood via run_events."""
from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

from dogfood_teamagent_validate import validate as validate_teamagent


def load_events(path: Path) -> list[dict[str, Any]]:
    return json.loads(path.read_text(encoding="utf-8"))


def handback_recipient(args: dict[str, Any]) -> str:
    """Resolve send_message recipient from args, including redacted preview blobs."""
    for key in ("to", "recipient"):
        value = args.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip().lower()
        if isinstance(value, dict):
            preview = value.get("preview")
            if isinstance(preview, str) and preview.strip():
                return preview.strip().lower()
    preview = args.get("preview")
    if isinstance(preview, str) and '"to"' in preview:
        import re

        match = re.search(r'"to"\s*:\s*"([^"]+)"', preview)
        if match:
            return match.group(1).strip().lower()
    return ""


def validate_gpt_researcher(events: list[dict[str, Any]], team_expert: str) -> tuple[bool, list[str], list[str]]:
    ok, info, warnings = validate_teamagent(events, team_expert)

    started = next(
        (e for e in events if e.get("name") == "team.started" and isinstance(e.get("data"), dict)),
        None,
    )
    if started:
        members = started["data"].get("members")
        if isinstance(members, list) and members:
            first_id = members[0].get("memberId")
            if first_id == "topic-researcher":
                info.append("OK: roster UI order starts with topic-researcher")
            else:
                ok = False
                info.append(f"FAIL: first roster member={first_id!r} (expected topic-researcher)")

    names = [e.get("name", "") for e in events]
    phase_started = names.count("team.phase.started")
    phase_completed = names.count("team.phase.completed")
    info.append(f"INFO: team.phase.started={phase_started} team.phase.completed={phase_completed}")
    if phase_started == 0:
        warnings.append("no team.phase.started — lead may not have emitted progress markers")

    member_starts = [
        e["data"].get("memberId")
        for e in events
        if e.get("name") == "team.member.started" and isinstance(e.get("data"), dict)
    ]
    if member_starts:
        info.append(f"INFO: member spawn order={member_starts}")
        if member_starts[0] != "topic-researcher":
            warnings.append(
                f"first spawn was {member_starts[0]!r} not topic-researcher (Phase 1 SOP)"
            )
    else:
        warnings.append("no team.member.started — hybrid lead may not have spawned yet")

    completed = next(
        (e for e in events if e.get("name") == "team.completed" and isinstance(e.get("data"), dict)),
        None,
    )
    if completed:
        data = completed["data"]
        member_count = data.get("memberCount")
        members_completed = data.get("membersCompleted")
        if (
            isinstance(member_count, int)
            and member_count >= 6
            and isinstance(members_completed, int)
            and members_completed >= member_count
            and len(member_starts) == 0
        ):
            ok = False
            info.append("FAIL: all members marked complete without any spawn (false sequential complete)")

    if "question.required" in names:
        info.append("OK: question.required present (Phase 2 gate available)")
    else:
        warnings.append("no question.required (acceptable in 快速模式 dogfood)")

    member_handback_starts = []
    for e in events:
        if e.get("name") != "tool.start" or not isinstance(e.get("data"), dict):
            continue
        data = e["data"]
        if not data.get("memberId"):
            continue
        tool_name = str(data.get("toolName", "")).lower()
        if "send_message" not in tool_name or tool_name.startswith("team."):
            continue
        args = data.get("args") if isinstance(data.get("args"), dict) else {}
        to = handback_recipient(args)
        member_handback_starts.append((data.get("memberId"), to))

    if member_handback_starts:
        info.append(f"OK: member send_message handbacks={len(member_handback_starts)} {member_handback_starts[:3]}")
        if not any(to in ("team-lead", "lead", "leader", "main", "__lead__") for _, to in member_handback_starts):
            ok = False
            info.append("FAIL: member send_message missing to=team-lead (handback protocol)")
    elif member_starts:
        ok = False
        info.append("FAIL: members ran but no member-scoped send_message to team-lead (implicit handback only)")

    return ok, info, warnings


def main() -> int:
    if len(sys.argv) != 3:
        print(
            "Usage: dogfood_gpt_researcher_validate.py <run-events.json> <team-expert-id>",
            file=sys.stderr,
        )
        return 2
    events = load_events(Path(sys.argv[1]))
    ok, info, warnings = validate_gpt_researcher(events, sys.argv[2])
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
