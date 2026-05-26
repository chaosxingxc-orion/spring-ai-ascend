#!/usr/bin/env python3
"""
Validate formal-release transaction scaffolding and optional evidence bundles.

Default mode validates that the repository has the skill, model schema, template,
and tooling needed for a formal release transaction. If the latest release note
declares `formal_release: true`, the validator also requires an evidence bundle.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Any

import yaml


REQUIRED_MODEL_NAMES = {
    "ReleaseCandidate",
    "EvidenceBundle",
    "AuthoritySurface",
    "CurrentForwardClaim",
    "DefectFamilyClosure",
}

REQUIRED_SCAFFOLD_FILES = [
    ".claude/skills/formal-release-transaction.md",
    "docs/governance/release-readiness/release-readiness.schema.yaml",
    "docs/governance/release-readiness/formal-release-note-template.en.md",
    "gate/lib/build_release_evidence.py",
    "gate/lib/check_formal_release_transaction.py",
    "gate/check_formal_release_transaction.sh",
]


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


def parse_frontmatter(path: Path) -> dict[str, Any]:
    text = read_text(path)
    match = re.match(r"^---\s*\n(.*?)\n---\s*\n", text, re.DOTALL)
    if not match:
        return {}
    data = yaml.safe_load(match.group(1)) or {}
    return data if isinstance(data, dict) else {}


def latest_release_path(root: Path) -> Path | None:
    release_dir = root / "docs" / "logs" / "releases"
    files = sorted(release_dir.glob("*.md")) if release_dir.is_dir() else []
    if not files:
        return None

    def key(path: Path) -> tuple[int, str]:
        match = re.search(r"rc([0-9]+)", path.name)
        rc = int(match.group(1)) if match else 0
        return rc, path.name

    return sorted(files, key=key)[-1]


class Reporter:
    def __init__(self) -> None:
        self.failures: list[str] = []

    def fail(self, slug: str, message: str) -> None:
        self.failures.append(f"FAIL: {slug} -- {message}")

    def pass_(self, slug: str, message: str) -> None:
        print(f"PASS: {slug} -- {message}")

    def emit_failures(self) -> None:
        for failure in self.failures:
            print(failure)


def validate_scaffold(root: Path, reporter: Reporter) -> None:
    for rel in REQUIRED_SCAFFOLD_FILES:
        if not (root / rel).is_file():
            reporter.fail("formal_release_transaction_missing_scaffold", f"{rel} is missing")

    schema_path = root / "docs" / "governance" / "release-readiness" / "release-readiness.schema.yaml"
    schema = load_yaml(schema_path) or {}
    models = schema.get("models") or {}
    missing = sorted(REQUIRED_MODEL_NAMES - set(models.keys()))
    if missing:
        reporter.fail(
            "formal_release_transaction_schema_incomplete",
            f"{schema_path.relative_to(root).as_posix()} missing model(s): {', '.join(missing)}",
        )


def validate_latest_release(root: Path, reporter: Reporter) -> Path | None:
    latest = latest_release_path(root)
    if latest is None:
        return None
    frontmatter = parse_frontmatter(latest)
    if bool(frontmatter.get("formal_release", False)) and not frontmatter.get("evidence_bundle"):
        reporter.fail(
            "formal_release_without_evidence_bundle",
            f"{latest.relative_to(root).as_posix()} declares formal_release: true but has no evidence_bundle frontmatter field",
        )
    if bool(frontmatter.get("formal_release", False)) and not frontmatter.get("release_candidate_commit"):
        reporter.fail(
            "formal_release_without_release_candidate_commit",
            f"{latest.relative_to(root).as_posix()} declares formal_release: true but has no release_candidate_commit frontmatter field",
        )
    evidence_bundle = frontmatter.get("evidence_bundle")
    if evidence_bundle:
        bundle_path = root / str(evidence_bundle)
        if not bundle_path.is_file():
            reporter.fail(
                "formal_release_evidence_bundle_missing",
                f"{latest.relative_to(root).as_posix()} references missing evidence bundle {evidence_bundle}",
            )
    return latest


def normalize_rel_path(root: Path, path: Path | str) -> str:
    candidate = Path(path)
    if candidate.is_absolute():
        try:
            return candidate.resolve().relative_to(root).as_posix()
        except ValueError:
            return candidate.as_posix()
    return candidate.as_posix()


def validate_evidence_bundle(root: Path, evidence_path: Path, latest: Path | None, reporter: Reporter) -> None:
    if not evidence_path.is_absolute():
        evidence_path = root / evidence_path
    evidence = load_yaml(evidence_path)
    if not isinstance(evidence, dict):
        reporter.fail("formal_release_evidence_unreadable", f"{evidence_path} is not readable YAML")
        return
    for key in (
        "schema_version",
        "repository",
        "latest_release",
        "baseline_metrics",
        "live_metrics",
        "baseline_comparison",
        "release_transaction",
    ):
        if key not in evidence:
            reporter.fail("formal_release_evidence_missing_key", f"{evidence_path} missing {key}")

    comparison = evidence.get("baseline_comparison") or {}
    if isinstance(comparison, dict):
        mismatches = [
            key
            for key, value in comparison.items()
            if isinstance(value, dict) and value.get("matches") is False
        ]
        if mismatches:
            reporter.fail(
                "formal_release_evidence_baseline_mismatch",
                f"{evidence_path} has baseline/live mismatches: {', '.join(sorted(mismatches))}",
            )

    if latest is None:
        return
    frontmatter = parse_frontmatter(latest)
    if not bool(frontmatter.get("formal_release", False)):
        return

    repository = evidence.get("repository") or {}
    latest_release = evidence.get("latest_release") or {}
    if isinstance(repository, dict) and repository.get("dirty") is not False:
        reporter.fail(
            "formal_release_evidence_dirty_repository",
            f"{evidence_path} was generated from a dirty repository",
        )

    expected_commit = frontmatter.get("release_candidate_commit")
    actual_commit = repository.get("commit_sha") if isinstance(repository, dict) else None
    if expected_commit and actual_commit != expected_commit:
        reporter.fail(
            "formal_release_evidence_candidate_mismatch",
            f"{evidence_path} commit {actual_commit!r} does not match release_candidate_commit {expected_commit!r}",
        )

    expected_bundle = frontmatter.get("evidence_bundle")
    actual_bundle = latest_release.get("evidence_bundle") if isinstance(latest_release, dict) else None
    expected_release_path = latest.relative_to(root).as_posix()
    actual_release_path = latest_release.get("path") if isinstance(latest_release, dict) else None
    evidence_describes_current_release = actual_release_path == expected_release_path
    if evidence_describes_current_release:
        if isinstance(latest_release, dict) and latest_release.get("formal_release") is not True:
            reporter.fail(
                "formal_release_evidence_latest_release_not_formal",
                f"{evidence_path} latest_release.formal_release is not true",
            )
        if expected_bundle and actual_bundle != expected_bundle:
            reporter.fail(
                "formal_release_evidence_bundle_mismatch",
                f"{evidence_path} latest_release.evidence_bundle {actual_bundle!r} does not match {expected_bundle!r}",
            )

    evidence_rel = normalize_rel_path(root, evidence_path)
    if expected_bundle and evidence_rel != expected_bundle:
        reporter.fail(
            "formal_release_evidence_bundle_argument_mismatch",
            f"validated evidence path {evidence_rel!r} does not match release evidence_bundle {expected_bundle!r}",
        )


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(repo_default()), help="Repository root")
    parser.add_argument("--evidence", help="Optional evidence bundle to validate")
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    reporter = Reporter()
    validate_scaffold(root, reporter)
    latest = validate_latest_release(root, reporter)
    if args.evidence:
        validate_evidence_bundle(root, Path(args.evidence), latest, reporter)

    if reporter.failures:
        reporter.emit_failures()
        print("GATE: FAIL")
        return 1
    reporter.pass_("formal_release_transaction", "scaffold and release-frontmatter checks passed")
    print("GATE: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
