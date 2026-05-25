#!/usr/bin/env python3
"""Validate contract-catalog SPI counts against the latest release note."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


PROMOTED_NAMES = ("Skill", "AgentRegistry")


def latest_release(root: Path) -> Path | None:
    release_dir = root / "docs" / "logs" / "releases"
    files = sorted(release_dir.glob("*.md")) if release_dir.is_dir() else []

    def key(path: Path) -> tuple[int, str]:
        match = re.search(r"rc([0-9]+)", path.name)
        return int(match.group(1)) if match else 0, path.name

    return sorted(files, key=key)[-1] if files else None


def active_spi_total(catalog: str) -> int | None:
    match = re.search(r"\*\*Active SPI interfaces \(([0-9]+) total\):\*\*", catalog)
    return int(match.group(1)) if match else None


def module_sum(catalog: str) -> int:
    total = 0
    in_section = False
    for line in catalog.splitlines():
        if line.strip() == "**Count by module:**" or line.startswith("**SPI count by module"):
            in_section = True
            continue
        if in_section and line.startswith("## "):
            break
        if not in_section:
            continue
        match = re.match(r"\| `[^`]+` \| ([0-9]+) \(", line)
        if match:
            total += int(match.group(1))
    return total


def latest_release_spi_total(text: str) -> int | None:
    match = re.search(r"Active SPI interfaces:\s*([0-9]+)\s+total", text)
    return int(match.group(1)) if match else None


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=".", help="Repository root")
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    catalog_path = root / "docs" / "contracts" / "contract-catalog.md"
    catalog = catalog_path.read_text(encoding="utf-8", errors="replace")
    failures: list[str] = []

    total = active_spi_total(catalog)
    if total is None:
        failures.append("contract catalog missing Active SPI interfaces total")
    else:
        summed = module_sum(catalog)
        if summed != total:
            failures.append(f"contract catalog module count sum {summed} != active total {total}")

    if "**Design-named SPIs (deferred W2+):**" in catalog:
        stale_text = catalog.split("**Design-named SPIs (deferred W2+):**", 1)[-1]
        for name in PROMOTED_NAMES:
            if re.search(rf"`{name}`[^\\n]*\\|[^\\n]*(W2|post-W4)", stale_text):
                failures.append(f"promoted SPI {name} must not be listed as deferred design-only")

    release = latest_release(root)
    if release is not None and total is not None:
        release_text = release.read_text(encoding="utf-8", errors="replace")
        release_total = latest_release_spi_total(release_text)
        if release_total is not None and release_total != total:
            failures.append(
                f"{release.relative_to(root)} active SPI total {release_total} != catalog total {total}"
            )

    if failures:
        print("; ".join(failures))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
