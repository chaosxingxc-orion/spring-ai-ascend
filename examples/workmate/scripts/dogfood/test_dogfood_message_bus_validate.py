#!/usr/bin/env python3
"""Offline test for dogfood_message_bus_validate.py using fixture audit log."""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
FIXTURE = ROOT / "fixtures" / "message-bus-run-events.jsonl"


def main() -> int:
    events = []
    seq = 1
    for line in FIXTURE.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        events.append({"seq": seq, "name": row["name"], "data": row["data"]})
        seq += 1
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
        json.dump(events, f)
        events_path = f.name
    sse_path = Path(tempfile.mkstemp(suffix=".sse")[1])
    sse_path.write_text("", encoding="utf-8")
    result = subprocess.run(
        [
            sys.executable,
            str(ROOT / "dogfood_message_bus_validate.py"),
            str(sse_path),
            events_path,
            "content-reactive-bus-team",
        ],
        capture_output=True,
        text=True,
    )
    print(result.stdout)
    if result.stderr:
        print(result.stderr, file=sys.stderr)
    sse_path.unlink(missing_ok=True)
    Path(events_path).unlink(missing_ok=True)
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
