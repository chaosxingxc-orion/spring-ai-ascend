#!/usr/bin/env python3
"""Tests for the formal release transaction tooling."""

from __future__ import annotations

import subprocess
import sys
import tempfile
import textwrap
import unittest
import sqlite3
from pathlib import Path

import yaml


REPO_ROOT = Path(__file__).resolve().parents[1]
BUILDER = REPO_ROOT / "gate" / "lib" / "build_release_evidence.py"
NODEGRAPH_BUILDER = REPO_ROOT / "gate" / "lib" / "build_codegraph_nodegraph_evidence.py"
VALIDATOR = REPO_ROOT / "gate" / "lib" / "check_formal_release_transaction.py"


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(text).lstrip(), encoding="utf-8")


def create_minimal_release_repo(
    root: Path,
    *,
    formal_release: bool = False,
    evidence_bundle: str | None = None,
    release_candidate_commit: str | None = None,
) -> None:
    write(
        root / "docs" / "governance" / "architecture-status.yaml",
        """
        capabilities:
          architecture_sync_gate:
            baseline_metrics:
              active_engineering_rules: 2
              gate_executable_test_cases: 3
              active_gate_checks: 2
              enforcer_rows: 1
              adr_count: 2
              maven_tests_green: 7
              architecture_graph_nodes: 4
              architecture_graph_edges: 5
              recurring_defect_families: 1
        """,
    )
    write(
        root / "docs" / "governance" / "enforcers.yaml",
        """
        enforcers:
          - id: E1
            rule: Rule D-1
        """,
    )
    write(
        root / "docs" / "governance" / "recurring-defect-families.yaml",
        """
        schema_version: 1
        last_updated: 2026-05-25
        families:
          - id: F-test
            title: Test Family
            first_observed_rc: rc1
            last_observed_rc: rc1
            occurrences: [rc1]
            root_cause: test
            surfaces: [docs/]
            prevention_rules: [Rule 1]
            cleanup_status: partial
            open_residual: test
        """,
    )
    write(
        root / "docs" / "governance" / "architecture-graph.yaml",
        """
        node_count: 4
        edge_count: 5
        nodes: []
        edges: []
        """,
    )
    write(root / "docs" / "adr" / "0001-test.yaml", "id: ADR-0001\n")
    write(root / "docs" / "adr" / "0002-test.md", "# ADR-0002\n")
    write(
        root / "CLAUDE.md",
        """
        #### Rule D-1
        First rule.

        #### Rule D-2
        Second rule.
        """,
    )
    write(
        root / "gate" / "check_architecture_sync.sh",
        """
        #!/usr/bin/env bash
        # Rule 1 \u2014 first_rule
        # Rule 2 \u2014 second_rule
        # === END OF RULES ===
        """,
    )
    write(
        root / "gate" / "test_architecture_sync_gate.sh",
        """
        #!/usr/bin/env bash
        echo "Tests passed: 3/3"
        """,
    )
    write(root / "gate" / "lib" / "build_release_evidence.py", "# placeholder\n")
    write(root / "gate" / "lib" / "check_formal_release_transaction.py", "# placeholder\n")
    write(root / "gate" / "check_formal_release_transaction.sh", "#!/usr/bin/env bash\n")
    write(
        root / "docs" / "governance" / "release-readiness" / "release-readiness.schema.yaml",
        """
        schema_version: 1
        models:
          ReleaseCandidate: {}
          EvidenceBundle: {}
          AuthoritySurface: {}
          CurrentForwardClaim: {}
          DefectFamilyClosure: {}
        """,
    )
    write(
        root / "docs" / "governance" / "release-readiness" / "formal-release-note-template.en.md",
        "# Formal Release Note Template\n",
    )
    write(
        root / ".claude" / "skills" / "formal-release-transaction.md",
        """
        ---
        name: formal-release-transaction
        description: Formal release transaction workflow.
        ---
        # Formal Release Transaction
        """,
    )
    frontmatter_lines = [f"formal_release: {str(formal_release).lower()}"]
    if evidence_bundle is not None:
        frontmatter_lines.append(f"evidence_bundle: {evidence_bundle}")
    if release_candidate_commit is not None:
        frontmatter_lines.append(f"release_candidate_commit: {release_candidate_commit}")
    frontmatter = "\n".join(frontmatter_lines) + "\n"
    write(
        root / "docs" / "logs" / "releases" / "2026-05-25-l0-rc1-test.en.md",
        "---\n" + frontmatter + "---\n# Test release\n",
    )


def write_minimal_release_evidence(
    root: Path,
    *,
    path: str = "gate/release-ci-evidence/test-evidence.yaml",
    repository_commit: str = "abc123",
    repository_dirty: bool = False,
    latest_release_path: str = "docs/logs/releases/2026-05-25-l0-rc1-test.en.md",
    latest_formal_release: bool = True,
    latest_evidence_bundle: str = "gate/release-ci-evidence/test-evidence.yaml",
    baseline_matches: bool = True,
) -> Path:
    evidence_path = root / path
    write(
        evidence_path,
        f"""
        schema_version: 1
        repository:
          commit_sha: {repository_commit}
          dirty: {str(repository_dirty).lower()}
        latest_release:
          path: {latest_release_path}
          formal_release: {str(latest_formal_release).lower()}
          evidence_bundle: {latest_evidence_bundle}
        baseline_metrics: {{}}
        live_metrics: {{}}
        baseline_comparison:
          active_gate_checks:
            matches: {str(baseline_matches).lower()}
        release_transaction: {{}}
        """,
    )
    return evidence_path


def create_minimal_codegraph_db(root: Path) -> None:
    db_path = root / ".codegraph" / "codegraph.db"
    db_path.parent.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(db_path) as con:
        con.executescript(
            """
            create table files (
              path text primary key,
              language text not null
            );
            create table nodes (
              id text primary key,
              kind text not null,
              name text not null
            );
            create table edges (
              id integer primary key,
              source text not null,
              target text not null,
              kind text not null
            );
            create table unresolved_refs (
              id integer primary key,
              name text not null
            );
            create table schema_versions (
              version integer primary key,
              description text
            );
            insert into files(path, language) values
              ('agent-service/src/main/java/App.java', 'java'),
              ('docs/governance/architecture-status.yaml', 'yaml');
            insert into nodes(id, kind, name) values
              ('n1', 'class', 'App'),
              ('n2', 'method', 'invoke'),
              ('n3', 'interface', 'ModelGateway');
            insert into edges(id, source, target, kind) values
              (1, 'n1', 'n2', 'contains'),
              (2, 'n2', 'n3', 'calls');
            insert into unresolved_refs(id, name) values (1, 'MissingSymbol');
            insert into schema_versions(version, description) values (4, 'test schema');
            """
        )


class ReleaseReadinessToolTests(unittest.TestCase):
    def test_gate_self_test_harness_source_has_no_post_pass_shell_diagnostics(self) -> None:
        harness = (REPO_ROOT / "gate" / "test_architecture_sync_gate.sh").read_text(encoding="utf-8")

        self.assertNotIn("`rc<N> Wave <M>`", harness)
        self.assertNotIn("$_fixtures_root", harness)

    def test_evidence_builder_derives_metrics_from_repository_tree(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            create_minimal_release_repo(root)

            result = subprocess.run(
                [sys.executable, str(BUILDER), "--root", str(root)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
            evidence = yaml.safe_load(result.stdout)
            self.assertEqual(evidence["schema_version"], 1)
            self.assertEqual(evidence["live_metrics"]["active_gate_checks"], 2)
            self.assertEqual(evidence["live_metrics"]["active_engineering_rules"], 2)
            self.assertEqual(evidence["live_metrics"]["gate_executable_test_cases"], 3)
            self.assertEqual(evidence["live_metrics"]["recurring_defect_families"], 1)
            self.assertTrue(evidence["baseline_comparison"]["active_gate_checks"]["matches"])
            self.assertTrue(evidence["baseline_comparison"]["adr_count"]["matches"])
            self.assertTrue(evidence["latest_release"]["path"].endswith("2026-05-25-l0-rc1-test.en.md"))

    def test_validator_rejects_formal_release_without_evidence_bundle(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            create_minimal_release_repo(root, formal_release=True)

            result = subprocess.run(
                [sys.executable, str(VALIDATOR), "--root", str(root)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("formal_release_without_evidence_bundle", result.stdout)

    def test_validator_accepts_nonformal_release_scaffolding(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            create_minimal_release_repo(root, formal_release=False)

            result = subprocess.run(
                [sys.executable, str(VALIDATOR), "--root", str(root)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
            self.assertIn("PASS: formal_release_transaction", result.stdout)

    def test_validator_rejects_dirty_formal_release_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            create_minimal_release_repo(
                root,
                formal_release=True,
                evidence_bundle="gate/release-ci-evidence/test-evidence.yaml",
                release_candidate_commit="abc123",
            )
            evidence = write_minimal_release_evidence(root, repository_dirty=True)

            result = subprocess.run(
                [sys.executable, str(VALIDATOR), "--root", str(root), "--evidence", str(evidence)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("formal_release_evidence_dirty_repository", result.stdout)

    def test_validator_rejects_release_candidate_commit_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            create_minimal_release_repo(
                root,
                formal_release=True,
                evidence_bundle="gate/release-ci-evidence/test-evidence.yaml",
                release_candidate_commit="abc123",
            )
            evidence = write_minimal_release_evidence(root, repository_commit="def456")

            result = subprocess.run(
                [sys.executable, str(VALIDATOR), "--root", str(root), "--evidence", str(evidence)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("formal_release_evidence_candidate_mismatch", result.stdout)

    def test_validator_rejects_nonformal_latest_release_in_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            create_minimal_release_repo(
                root,
                formal_release=True,
                evidence_bundle="gate/release-ci-evidence/test-evidence.yaml",
                release_candidate_commit="abc123",
            )
            evidence = write_minimal_release_evidence(root, latest_formal_release=False)

            result = subprocess.run(
                [sys.executable, str(VALIDATOR), "--root", str(root), "--evidence", str(evidence)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("formal_release_evidence_latest_release_not_formal", result.stdout)

    def test_validator_rejects_evidence_bundle_path_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            create_minimal_release_repo(
                root,
                formal_release=True,
                evidence_bundle="gate/release-ci-evidence/test-evidence.yaml",
                release_candidate_commit="abc123",
            )
            evidence = write_minimal_release_evidence(
                root,
                latest_evidence_bundle="gate/release-ci-evidence/other.yaml",
            )

            result = subprocess.run(
                [sys.executable, str(VALIDATOR), "--root", str(root), "--evidence", str(evidence)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("formal_release_evidence_bundle_mismatch", result.stdout)

    def test_validator_accepts_evidence_from_frozen_candidate_before_release_note(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            create_minimal_release_repo(
                root,
                formal_release=True,
                evidence_bundle="gate/release-ci-evidence/test-evidence.yaml",
                release_candidate_commit="abc123",
            )
            evidence = write_minimal_release_evidence(
                root,
                latest_release_path="docs/logs/releases/2026-05-25-l0-previous.en.md",
                latest_formal_release=True,
                latest_evidence_bundle="gate/release-ci-evidence/previous.yaml",
            )

            result = subprocess.run(
                [sys.executable, str(VALIDATOR), "--root", str(root), "--evidence", str(evidence)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("PASS: formal_release_transaction", result.stdout)

    def test_nodegraph_evidence_builder_derives_counts_from_codegraph_db(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            create_minimal_codegraph_db(root)

            result = subprocess.run(
                [sys.executable, str(NODEGRAPH_BUILDER), "--root", str(root)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
            evidence = yaml.safe_load(result.stdout)
            self.assertEqual(evidence["schema_version"], 1)
            self.assertEqual(evidence["tool"], "codegraph")
            self.assertEqual(evidence["nodegraph"]["files"], 2)
            self.assertEqual(evidence["nodegraph"]["nodes"], 3)
            self.assertEqual(evidence["nodegraph"]["edges"], 2)
            self.assertEqual(evidence["nodegraph"]["unresolved_refs"], 1)
            self.assertEqual(evidence["nodegraph"]["schema_versions"], [4])
            self.assertEqual(evidence["nodegraph"]["nodes_by_kind"]["class"], 1)
            self.assertEqual(evidence["nodegraph"]["nodes_by_kind"]["interface"], 1)
            self.assertEqual(evidence["nodegraph"]["nodes_by_kind"]["method"], 1)

    def test_nodegraph_evidence_builder_reports_clean_git_status_as_false(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            create_minimal_codegraph_db(root)
            subprocess.run(["git", "init"], cwd=root, text=True, capture_output=True, check=True)
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.name", "Test User"], cwd=root, check=True)
            subprocess.run(["git", "add", ".codegraph/codegraph.db"], cwd=root, check=True)
            subprocess.run(["git", "commit", "-m", "seed codegraph db"], cwd=root, text=True, capture_output=True, check=True)

            result = subprocess.run(
                [sys.executable, str(NODEGRAPH_BUILDER), "--root", str(root)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
            evidence = yaml.safe_load(result.stdout)
            self.assertIs(evidence["repository"]["dirty"], False)


if __name__ == "__main__":
    unittest.main()
