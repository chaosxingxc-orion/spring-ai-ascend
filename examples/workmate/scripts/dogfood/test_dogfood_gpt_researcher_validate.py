#!/usr/bin/env python3
"""Offline test for dogfood_gpt_researcher_validate.py."""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
FIXTURE = ROOT / "fixtures" / "gpt-researcher-run-events.jsonl"


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
        path = f.name
    result = subprocess.run(
        [sys.executable, str(ROOT / "dogfood_gpt_researcher_validate.py"), path, "gpt-researcher-team"],
        capture_output=True,
        text=True,
    )
    print(result.stdout)
    if result.stderr:
        print(result.stderr, file=sys.stderr)
    Path(path).unlink(missing_ok=True)
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
