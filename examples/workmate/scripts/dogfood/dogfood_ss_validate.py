#!/usr/bin/env python3
"""Validate shared-state team dogfood via run_events."""
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
        "team.iteration.started",
        "team.member.started",
        "team.state.progress",
        "team.completed",
    ):
        if req not in names:
            ok = False
            info.append(f"MISS: {req}")

    if "team.memory" not in names:
        warnings.append("no team.memory events")

    started = next(
        (e for e in events if e.get("name") == "team.started" and isinstance(e.get("data"), dict)),
        None,
    )
    if started:
        data = started["data"]
        if data.get("pattern") != "shared-state":
            ok = False
            info.append(f"FAIL: pattern={data.get('pattern')}")
        else:
            info.append("OK: pattern=shared-state maxIterations={}".format(data.get("maxIterations")))
    else:
        ok = False

    completed = next(
        (e for e in events if e.get("name") == "team.completed" and isinstance(e.get("data"), dict)),
        None,
    )
    if completed:
        c = completed["data"]
        info.append(
            f"OK: team.completed converged={c.get('converged')} iterations={c.get('iterationsCompleted')}"
        )

    return ok, info, warnings


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: dogfood_ss_validate.py <run-events.json> <team-expert-id>", file=sys.stderr)
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
