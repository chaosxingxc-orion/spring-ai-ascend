#!/usr/bin/env python3
"""
Build a formal-release evidence bundle from the repository tree.

The bundle is intentionally derived from live files and command output rather
than hand-authored release prose. It is the mechanical input for formal release
notes and for gate/lib/check_formal_release_transaction.py.
"""

from __future__ import annotations

import argparse
import datetime as dt
import glob
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

import yaml


def repo_default() -> Path:
    return Path(__file__).resolve().parents[2]


def read_text(path: Path) -> str:
    if not path.is_file():
        return ""
    return path.read_text(encoding="utf-8", errors="replace")


def load_yaml(path: Path) -> Any:
    if not path.is_file():
        return None
    with path.open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle) or {}


def run(root: Path, args: list[str]) -> tuple[int, str, str]:
    proc = subprocess.run(
        args,
        cwd=root,
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=True,
        check=False,
    )
    return proc.returncode, proc.stdout.strip(), proc.stderr.strip()


def git_value(root: Path, args: list[str], fallback: str = "unknown") -> str:
    rc, stdout, _stderr = run(root, ["git", *args])
    return stdout if rc == 0 and stdout else fallback


def git_dirty(root: Path) -> bool:
    rc, stdout, _stderr = run(root, ["git", "status", "--short"])
    return rc == 0 and bool(stdout.strip())


def baseline_metrics(root: Path) -> dict[str, Any]:
    data = load_yaml(root / "docs" / "governance" / "architecture-status.yaml") or {}
    direct = data.get("architecture_sync_gate", {}).get("baseline_metrics")
    if isinstance(direct, dict):
        return direct
    nested = (
        data.get("capabilities", {})
        .get("architecture_sync_gate", {})
        .get("baseline_metrics")
    )
    return nested if isinstance(nested, dict) else {}


def count_active_engineering_rules(root: Path) -> int:
    # active_engineering_rules = rule cards (docs/governance/rules/rule-*.md) whose
    # status is active or active_advisory. Cards are the sole rule authority since
    # 2026-05-28 (Rule 68/69), so this counts cards, not CLAUDE.md kernel headings.
    rules_dir = root / "docs" / "governance" / "rules"
    if not rules_dir.is_dir():
        return 0
    return sum(
        1
        for card in rules_dir.glob("rule-*.md")
        if re.search(r"^status:\s*active", read_text(card), re.MULTILINE)
    )


def count_gate_rules(root: Path) -> int:
    # Use the same manifest extractor shape as Rule 91 when bash/awk are
    # available. This avoids a second Python-only interpretation of historical
    # separator mojibake in the monolithic gate file.
    em_dash = chr(8212)
    rc, stdout, _stderr = run(
        root,
        [
            "bash",
            "-lc",
            f"awk '/^# === END OF RULES ===$/{{exit}} /^# Rule [0-9]+.?[a-z]? {em_dash} /{{c++}} END{{print c+0}}' gate/check_architecture_sync.sh",
        ],
    )
    if rc == 0 and stdout.strip().isdigit():
        return int(stdout.strip())

    text = read_text(root / "gate" / "check_architecture_sync.sh")
    bounded_lines: list[str] = []
    for line in text.splitlines():
        if line.strip() == "# === END OF RULES ===":
            break
        bounded_lines.append(line)
    before_end = "\n".join(bounded_lines)
    return len(
        re.findall(
            r"^# Rule [0-9]+(?:\.[a-z])? " + em_dash + r" ",
            before_end,
            re.MULTILINE,
        )
    )


def count_enforcers(root: Path) -> int:
    text = read_text(root / "docs" / "governance" / "enforcers.yaml")
    return len(re.findall(r"^\s*-\s+id:\s+E[0-9]+\b", text, re.MULTILINE))


def count_adrs(root: Path) -> int:
    adr_dir = root / "docs" / "adr"
    if not adr_dir.is_dir():
        return 0

    def _count(directory: Path) -> int:
        if not directory.is_dir():
            return 0
        return len(
            [
                path
                for path in directory.iterdir()
                if path.is_file()
                and re.match(r"^[0-9]{4}-.*\.(yaml|md)$", path.name)
            ]
        )

    # baseline_metrics.adr_count is "active .yaml + locked foundational .md"
    return _count(adr_dir) + _count(adr_dir / "locked")


def count_recurring_families(root: Path) -> int:
    data = load_yaml(root / "docs" / "governance" / "recurring-defect-families.yaml") or {}
    families = data.get("families") or []
    return len(families) if isinstance(families, list) else 0


def graph_counts(root: Path) -> tuple[int | None, int | None]:
    data = load_yaml(root / "docs" / "governance" / "architecture-graph.yaml") or {}
    return data.get("node_count"), data.get("edge_count")


def parse_self_test_count_from_output(output: str) -> int | None:
    match = re.search(r"Tests passed:\s*[0-9]+\s*/\s*([0-9]+)", output)
    if match:
        return int(match.group(1))
    return None


def count_gate_self_tests(root: Path, run_command: bool) -> int | None:
    script = root / "gate" / "test_architecture_sync_gate.sh"
    if run_command and script.is_file():
        rc, stdout, stderr = run(root, ["bash", script.relative_to(root).as_posix()])
        output = f"{stdout}\n{stderr}"
        count = parse_self_test_count_from_output(output)
        if rc == 0 and count is not None:
            return count
    text = read_text(script)
    literal = parse_self_test_count_from_output(text)
    if literal is not None:
        return literal
    return None


def count_maven_tests_from_reports(root: Path, include_reports: bool) -> int | None:
    if not include_reports:
        return None
    total = 0
    found = False
    for pattern in (
        "**/target/surefire-reports/TEST-*.xml",
        "**/target/failsafe-reports/TEST-*.xml",
    ):
        for name in glob.glob(str(root / pattern), recursive=True):
            text = read_text(Path(name))
            match = re.search(r'\btests="([0-9]+)"', text)
            if match:
                total += int(match.group(1))
                found = True
    return total if found else None


def latest_release(root: Path) -> dict[str, Any]:
    release_dir = root / "docs" / "logs" / "releases"
    files = sorted(release_dir.glob("*.md")) if release_dir.is_dir() else []

    def key(path: Path) -> tuple[int, str]:
        match = re.search(r"rc([0-9]+)", path.name)
        rc = int(match.group(1)) if match else 0
        return rc, path.name

    if not files:
        return {"path": None, "formal_release": False, "evidence_bundle": None}
    path = sorted(files, key=key)[-1]
    frontmatter = parse_frontmatter(read_text(path))
    return {
        "path": path.relative_to(root).as_posix(),
        "formal_release": bool(frontmatter.get("formal_release", False)),
        "evidence_bundle": frontmatter.get("evidence_bundle"),
    }


def parse_frontmatter(text: str) -> dict[str, Any]:
    match = re.match(r"^---\s*\n(.*?)\n---\s*\n", text, re.DOTALL)
    if not match:
        return {}
    data = yaml.safe_load(match.group(1)) or {}
    return data if isinstance(data, dict) else {}


def live_metrics(root: Path, run_self_tests: bool, include_maven_reports: bool) -> dict[str, Any]:
    nodes, edges = graph_counts(root)
    metrics: dict[str, Any] = {
        "active_engineering_rules": count_active_engineering_rules(root),
        "active_gate_checks": count_gate_rules(root),
        "enforcer_rows": count_enforcers(root),
        "adr_count": count_adrs(root),
        "recurring_defect_families": count_recurring_families(root),
    }
    self_tests = count_gate_self_tests(root, run_self_tests)
    if self_tests is not None:
        metrics["gate_executable_test_cases"] = self_tests
    maven_tests = count_maven_tests_from_reports(root, include_maven_reports)
    if maven_tests is not None:
        metrics["maven_tests_green"] = maven_tests
    if nodes is not None:
        metrics["architecture_graph_nodes"] = nodes
    if edges is not None:
        metrics["architecture_graph_edges"] = edges
    return metrics


def compare_metrics(baseline: dict[str, Any], live: dict[str, Any]) -> dict[str, Any]:
    comparison: dict[str, Any] = {}
    for key in sorted(set(baseline) | set(live)):
        baseline_value = baseline.get(key)
        live_value = live.get(key)
        comparison[key] = {
            "baseline": baseline_value,
            "live": live_value,
            "matches": None if live_value is None else baseline_value == live_value,
        }
    return comparison


def build_evidence(root: Path, run_self_tests: bool, include_maven_reports: bool) -> dict[str, Any]:
    baseline = baseline_metrics(root)
    live = live_metrics(root, run_self_tests, include_maven_reports)
    return {
        "schema_version": 1,
        "generated_at_utc": dt.datetime.now(dt.UTC).replace(microsecond=0).isoformat(),
        "repository": {
            "root": str(root),
            "commit_sha": git_value(root, ["rev-parse", "HEAD"]),
            "branch": git_value(root, ["rev-parse", "--abbrev-ref", "HEAD"]),
            "dirty": git_dirty(root),
        },
        "latest_release": latest_release(root),
        "baseline_metrics": baseline,
        "live_metrics": live,
        "baseline_comparison": compare_metrics(baseline, live),
        "release_transaction": {
            "generated_by": "gate/lib/build_release_evidence.py",
            "validator": "gate/check_formal_release_transaction.sh",
            "requires_current_forward_claims": True,
            "requires_defect_family_closures": True,
            "requires_generated_surface_refresh": True,
        },
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(repo_default()), help="Repository root")
    parser.add_argument("--output", help="Write bundle to this file instead of stdout")
    parser.add_argument(
        "--run-self-tests",
        action="store_true",
        help="Run gate/test_architecture_sync_gate.sh to derive the self-test count",
    )
    parser.add_argument(
        "--include-maven-reports",
        action="store_true",
        help="Read existing Surefire/Failsafe XML reports for maven_tests_green",
    )
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    evidence = build_evidence(root, args.run_self_tests, args.include_maven_reports)
    rendered = yaml.safe_dump(evidence, sort_keys=False, allow_unicode=False)

    if args.output:
        output = Path(args.output)
        if not output.is_absolute():
            output = root / output
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(rendered, encoding="utf-8")
    else:
        sys.stdout.write(rendered)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
