#!/usr/bin/env python3
"""Move test classes whose physical path doesn't match their `package` declaration
post-rc13 package rename. Idempotent; safe to re-run.
"""
from pathlib import Path
import re

ROOTS = [
    Path("agent-service/src/test/java"),
    Path("agent-execution-engine/src/test/java"),
    Path("agent-bus/src/test/java"),
]

fixed = 0
for root in ROOTS:
    if not root.exists():
        continue
    for f in list(root.rglob("*.java")):
        body = f.read_bytes()
        m = re.search(rb"^\s*package\s+([\w.]+)\s*;", body[:500], re.MULTILINE)
        if not m:
            continue
        decl_pkg = m.group(1).decode()
        expected_path = Path(*decl_pkg.split("."))
        actual_parent = f.parent.relative_to(root)
        if actual_parent.as_posix() == expected_path.as_posix():
            continue
        new_dir = root / expected_path
        new_dir.mkdir(parents=True, exist_ok=True)
        new_file = new_dir / f.name
        f.rename(new_file)
        fixed += 1
        print(f"MOVED {f} -> {new_file}")

print(f"Total moved: {fixed}")
