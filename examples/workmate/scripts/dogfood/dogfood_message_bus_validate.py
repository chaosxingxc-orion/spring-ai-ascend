#!/usr/bin/env python3
"""Validate message-bus team dogfood: SSE stream + run_events audit log."""
from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


def parse_sse_file(path: Path) -> list[dict[str, Any]]:
    events: list[dict[str, Any]] = []
    if not path.exists():
        return events
    text = path.read_text(encoding="utf-8")
    parts = text.split("\n\n")
    for part in parts:
        if not part.strip():
            continue
        name = "message"
        data_lines: list[str] = []
        for line in part.split("\n"):
            if line.startswith("event:"):
                name = line[6:].strip()
            elif line.startswith("data:"):
                data_lines.append(line[5:].strip())
        if not data_lines:
            continue
        raw = "\n".join(data_lines)
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            data = raw
        events.append({"name": name, "data": data})
    return events


def load_run_events(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    return json.loads(path.read_text(encoding="utf-8"))


def event_names(events: list[dict[str, Any]]) -> list[str]:
    return [e.get("name", "") for e in events]


def merge_audit(sse_events: list[dict[str, Any]], run_events: list[dict[str, Any]]) -> list[dict[str, Any]]:
    merged: list[dict[str, Any]] = []
    for e in sse_events:
        merged.append({"name": e["name"], "data": e.get("data")})
    for e in run_events:
        merged.append({"name": e.get("name", ""), "data": e.get("data")})
    return merged


def bus_published(events: list[dict[str, Any]]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for e in events:
        if e.get("name") != "team.bus.published":
            continue
        data = e.get("data")
        if isinstance(data, dict):
            out.append(data)
    return out


def tool_starts(events: list[dict[str, Any]]) -> list[str]:
    names: list[str] = []
    for e in events:
        if e.get("name") != "tool.start":
            continue
        data = e.get("data")
        if isinstance(data, dict) and isinstance(data.get("toolName"), str):
            names.append(data["toolName"])
    return names


def validate(
    sse_path: Path,
    run_events_path: Path,
    team_expert: str,
) -> tuple[bool, list[str], list[str]]:
    ok = True
    info: list[str] = []
    warnings: list[str] = []

    sse_events = parse_sse_file(sse_path)
    run_events = load_run_events(run_events_path)
    all_events = merge_audit(sse_events, run_events)
    names = event_names(all_events)

    required = [
        "team.started",
        "team.bus.subscribed",
        "team.bus.published",
        "team.member.started",
        "team.completed",
    ]
    for req in required:
        if req not in names:
            ok = False
            info.append(f"MISS: event {req}")

    started = [
        e for e in all_events if e.get("name") == "team.started" and isinstance(e.get("data"), dict)
    ]
    if started:
        data = started[0]["data"]
        pattern = data.get("pattern")
        if pattern != "message-bus":
            ok = False
            info.append(f"FAIL: team.started pattern={pattern}")
        else:
            info.append(f"OK: pattern=message-bus busMode={data.get('busMode')}")
        if data.get("topicBusProvider"):
            info.append(f"OK: topicBusProvider={data.get('topicBusProvider')}")
        team_id = data.get("teamId")
        if team_id != team_expert:
            warnings.append(f"teamId={team_id} (expected {team_expert})")
    else:
        ok = False

    publishes = bus_published(all_events)
    if len(publishes) < 2:
        ok = False
        info.append(f"FAIL: team.bus.published count={len(publishes)} (need >=2)")
    else:
        info.append(f"OK: team.bus.published count={len(publishes)}")
        topics = {p.get("topic") for p in publishes}
        info.append(f"    topics: {sorted(t for t in topics if t)}")
        sources = {p.get("publishSource") for p in publishes if p.get("publishSource")}
        if sources:
            info.append(f"    publishSource: {sorted(sources)}")
        ingress = [p for p in publishes if p.get("topic") == "ingress"]
        if not ingress:
            ok = False
            info.append("FAIL: no ingress publish")
        elif ingress[0].get("publishSource") != "orchestrator":
            warnings.append("ingress publishSource != orchestrator (older API?)")

    member_starts = names.count("team.member.started")
    if member_starts < 2:
        warnings.append(f"member runs={member_starts} (expected >=2 for 2-member team)")

    completed = [
        e for e in all_events if e.get("name") == "team.completed" and isinstance(e.get("data"), dict)
    ]
    if completed:
        c = completed[-1]["data"]
        info.append(
            f"OK: team.completed busEntryCount={c.get('busEntryCount')} "
            f"converged={c.get('converged')} iterations={c.get('iterationsCompleted')}"
        )

    bus_tools = [n for n in tool_starts(all_events) if "team_bus_publish" in n]
    if bus_tools:
        info.append(f"OK: mid-run tool calls={len(bus_tools)}")
    else:
        warnings.append("no workmate_team_bus_publish tool.start (LLM may skip mid-run publish)")

    return ok, info, warnings


def main() -> int:
    if len(sys.argv) != 4:
        print("Usage: dogfood_message_bus_validate.py <sse.log> <run-events.json> <team-expert-id>", file=sys.stderr)
        return 2
    sse_path = Path(sys.argv[1])
    run_path = Path(sys.argv[2])
    team_expert = sys.argv[3]
    ok, info, warnings = validate(sse_path, run_path, team_expert)
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
