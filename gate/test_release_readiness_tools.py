#!/usr/bin/env python3
"""Tests for the formal release transaction tooling."""

from __future__ import annotations

import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

import yaml


REPO_ROOT = Path(__file__).resolve().parents[1]
BUILDER = REPO_ROOT / "gate" / "lib" / "build_release_evidence.py"
VALIDATOR = REPO_ROOT / "gate" / "lib" / "check_formal_release_transaction.py"


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(text).lstrip(), encoding="utf-8")


def create_minimal_release_repo(root: Path, *, formal_release: bool = False) -> None:
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
    frontmatter = "formal_release: true\n" if formal_release else "formal_release: false\n"
    write(
        root / "docs" / "logs" / "releases" / "2026-05-25-l0-rc1-test.en.md",
        f"""
        ---
        {frontmatter}
        ---
        # Test release
        """,
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


if __name__ == "__main__":
    unittest.main()
