#!/usr/bin/env python3
"""Tests for the dual-track understanding-map gate helper.

Covers ``gate/lib/check_ai_understanding_map.py``: the map invariants (derived
``traverses``, no Feature/value-axis ownership of a Frame, module-only
``contains``, well-typed axes), the allowlist suppression, the three ratchet
modes (advisory / changed-files-blocking / full-blocking) including the
shipped-vs-not-shipped distinction for NON-DERIVED-TRAVERSE, the greenfield /
vacuity posture, and the fail-closed config errors. The helper is run both
in-process (unit-level projection of the merged DSL) and via subprocess
(end-to-end exit codes), mirroring the sibling ``gate/test_ai_reading_path.py``
/ ``gate/test_feature_readiness.py`` style.

Authority: ADR-0157 (EngineeringFrame Ontology -- the dual-track map).
"""
from __future__ import annotations

import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
GATE_LIB = REPO_ROOT / "gate" / "lib"
HELPER = GATE_LIB / "check_ai_understanding_map.py"

FEATURES_REL = "architecture/features/features.dsl"
FUNCTION_POINTS_REL = "architecture/features/function-points.dsl"
ENGINEERING_FRAMES_REL = "architecture/features/engineering-frames.dsl"
ALLOWLIST_REL = "gate/ai-understanding-map-allowlist.txt"


def _import_helper():
    sys.path.insert(0, str(GATE_LIB))
    try:
        return __import__("check_ai_understanding_map")
    finally:
        sys.path.pop(0)


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(text).lstrip(), encoding="utf-8")


def _run(root: Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(HELPER), "--repo", str(root), *args],
        text=True,
        capture_output=True,
        check=False,
    )


# ---------------------------------------------------------------------------
# Synthetic DSL fixtures. A CLEAN dual-track map:
#   Module genModule_demo --contains--> efShipped, efPlanned
#   efShipped --anchors--> fpAlpha
#   featShipped (shipped) --requires--> fpAlpha   --traverses--> efShipped (derived)
#   featShipped            --traverses--> efPlanned (efPlanned anchors nothing -> vacuous)
# Negative tests mutate exactly one obligation from this clean materialization.
# ---------------------------------------------------------------------------
def _frame(var: str, sid: str, status: str, extra: str = "") -> str:
    return textwrap.dedent(f"""
        {var} = element "{sid} Frame" "EngineeringFrame" "demo frame" "SAA EngineeringFrame" {{
            properties {{
                "saa.id" "{sid}"
                "saa.kind" "engineering_frame"
                "saa.level" "L1"
                "saa.view" "logical"
                "saa.status" "{status}"
                "saa.owner" "demo"
                "saa.sourceAdr" "ADR-0157"
                "saa.structuralAxis" "true"{extra}
            }}
        }}
    """)


def _feature(var: str, sid: str, status: str) -> str:
    return textwrap.dedent(f"""
        {var} = element "{sid} Feature" "Feature" "demo feature" "SAA Feature" {{
            properties {{
                "saa.id" "{sid}"
                "saa.kind" "feature"
                "saa.level" "L1"
                "saa.view" "scenarios"
                "saa.status" "{status}"
                "saa.owner" "demo"
                "saa.sourceAdr" "ADR-0157"
                "saa.productClaim" "PC-001"
                "saa.requirement" "REQ-001"
            }}
        }}
    """)


def _fp(var: str, sid: str) -> str:
    return textwrap.dedent(f"""
        {var} = element "{sid} FP" "FunctionPoint" "demo fp" "SAA FunctionPoint" {{
            properties {{
                "saa.id" "{sid}"
                "saa.kind" "function_point"
                "saa.level" "L1"
                "saa.view" "scenarios"
                "saa.status" "shipped"
                "saa.owner" "demo"
                "saa.sourceAdr" "ADR-0157"
            }}
        }}
    """)


def _edge(src: str, dst: str, rel: str) -> str:
    return textwrap.dedent(f"""
        {src} -> {dst} "{rel} edge" "SAA Relationship" {{
            properties {{
                "saa.rel" "{rel}"
            }}
        }}
    """)


def _features_dsl() -> str:
    return (
        _feature("featShipped", "FEAT-SHIPPED", "shipped")
        + _edge("featShipped", "fpAlpha", "requires")
    )


def _function_points_dsl() -> str:
    return _fp("fpAlpha", "FP-ALPHA") + _fp("fpBeta", "FP-BETA")


def _frames_dsl() -> str:
    return (
        _frame("efShipped", "EF-SHIPPED", "shipped")
        + _frame("efPlanned", "EF-PLANNED", "design_only")
        + _edge("genModule_demo", "efShipped", "contains")
        + _edge("genModule_demo", "efPlanned", "contains")
        + _edge("efShipped", "fpAlpha", "anchors")
        + _edge("featShipped", "efShipped", "traverses")
        + _edge("featShipped", "efPlanned", "traverses")
    )


def _materialize_clean(root: Path) -> None:
    write(root / FEATURES_REL, _features_dsl())
    write(root / FUNCTION_POINTS_REL, _function_points_dsl())
    write(root / ENGINEERING_FRAMES_REL, _frames_dsl())


# ===========================================================================
# In-process projection tests.
# ===========================================================================
class MapModelTests(unittest.TestCase):
    def setUp(self) -> None:
        self.mod = _import_helper()
        self.text = "\n".join((_features_dsl(), _function_points_dsl(), _frames_dsl()))

    def test_build_collects_all_kinds_and_edges(self) -> None:
        m = self.mod.build_map(self.text)
        self.assertEqual(set(m.features), {"featShipped"})
        self.assertEqual(set(m.frames), {"efShipped", "efPlanned"})
        self.assertEqual(set(m.function_points), {"fpAlpha", "fpBeta"})
        self.assertIn(("featShipped", "fpAlpha"), m.requires)
        self.assertIn(("efShipped", "fpAlpha"), m.anchors)
        self.assertIn(("featShipped", "efShipped"), m.traverses)
        self.assertIn(("genModule_demo", "efShipped"), m.contains)

    def test_clean_map_has_no_findings(self) -> None:
        m = self.mod.build_map(self.text)
        all_findings = (
            self.mod.check_derived_traverse(m)
            + self.mod.check_no_feature_owns_frame(m)
            + self.mod.check_only_module_contains_frame(m)
            + self.mod.check_frame_carries_no_value_axis(m)
            + self.mod.check_well_typed_axes(m)
        )
        self.assertEqual(all_findings, [], [f.line() for f in all_findings])

    def test_planned_frame_traverse_is_vacuous(self) -> None:
        # efPlanned anchors nothing -> a traverse onto it is not a finding.
        m = self.mod.build_map(self.text)
        findings = self.mod.check_derived_traverse(m)
        subjects = {f.subject for f in findings}
        self.assertNotIn("featShipped->efPlanned", subjects)


# ===========================================================================
# Greenfield / vacuity.
# ===========================================================================
class GreenfieldTests(unittest.TestCase):
    def test_absent_map_is_vacuously_clean(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for mode in ("advisory", "full-blocking", "changed-files-blocking"):
                result = _run(root, "--mode", mode)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("greenfield", result.stderr)


# ===========================================================================
# Happy path (subprocess).
# ===========================================================================
class HappyPathTests(unittest.TestCase):
    def test_clean_repo_full_blocking_is_clean(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _materialize_clean(root)
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("0 finding(s)", result.stderr)


# ===========================================================================
# Each invariant -- one mutation from the clean model.
# ===========================================================================
class InvariantFindingTests(unittest.TestCase):
    def _assert_finding(self, mutate, code: str, *, returncode: int = 1,
                        mode: str = "full-blocking") -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _materialize_clean(root)
            mutate(root)
            result = _run(root, "--mode", mode)
            self.assertEqual(result.returncode, returncode,
                             f"{code}: rc={result.returncode}\n{result.stderr}")
            self.assertIn(code, result.stderr, f"expected {code}:\n{result.stderr}")

    def test_non_derived_traverse_from_shipped_feature_blocks(self) -> None:
        # Give efPlanned an anchor to a DIFFERENT fp the shipped feature does not
        # require -> the featShipped->efPlanned traverse becomes non-derivable.
        def mutate(r: Path) -> None:
            (r / ENGINEERING_FRAMES_REL).write_text(
                _frames_dsl() + _edge("efPlanned", "fpBeta", "anchors"),
                encoding="utf-8",
            )
        self._assert_finding(mutate, "NON-DERIVED-TRAVERSE")

    def test_non_derived_traverse_from_non_shipped_feature_is_advisory_only(self) -> None:
        # Same shape, but the traversing feature is NOT shipped -> reported but
        # never blocking, even under full-blocking (exit 0).
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / FEATURES_REL,
                  _feature("featDraft", "FEAT-DRAFT", "design_only")
                  + _edge("featDraft", "fpAlpha", "requires"))
            write(root / FUNCTION_POINTS_REL, _function_points_dsl())
            write(root / ENGINEERING_FRAMES_REL,
                  _frame("efPop", "EF-POP", "shipped")
                  + _edge("genModule_demo", "efPop", "contains")
                  + _edge("efPop", "fpBeta", "anchors")          # anchors fpBeta
                  + _edge("featDraft", "efPop", "traverses"))    # but feat requires fpAlpha
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("NON-DERIVED-TRAVERSE", result.stderr)
            self.assertIn("[advisory]", result.stderr)

    def test_feature_contains_frame_is_ownership(self) -> None:
        self._assert_finding(
            lambda r: (r / ENGINEERING_FRAMES_REL).write_text(
                _frames_dsl() + _edge("featShipped", "efShipped", "contains"),
                encoding="utf-8",
            ),
            "FEATURE-OWNS-FRAME",
        )

    def test_feature_anchors_frame_is_ownership(self) -> None:
        self._assert_finding(
            lambda r: (r / ENGINEERING_FRAMES_REL).write_text(
                _frames_dsl() + _edge("featShipped", "efPlanned", "anchors"),
                encoding="utf-8",
            ),
            "FEATURE-OWNS-FRAME",
        )

    def test_non_module_contains_frame(self) -> None:
        # A bare (non-genModule_) var contains a frame.
        self._assert_finding(
            lambda r: (r / ENGINEERING_FRAMES_REL).write_text(
                _frames_dsl() + _edge("someOtherThing", "efPlanned", "contains"),
                encoding="utf-8",
            ),
            "NON-MODULE-CONTAINS-FRAME",
        )

    def test_frame_carrying_product_claim_is_value_ownership(self) -> None:
        def mutate(r: Path) -> None:
            frames = (
                _frame("efShipped", "EF-SHIPPED", "shipped",
                       extra='\n                "saa.productClaim" "PC-001"')
                + _frame("efPlanned", "EF-PLANNED", "design_only")
                + _edge("genModule_demo", "efShipped", "contains")
                + _edge("genModule_demo", "efPlanned", "contains")
                + _edge("efShipped", "fpAlpha", "anchors")
                + _edge("featShipped", "efShipped", "traverses")
                + _edge("featShipped", "efPlanned", "traverses")
            )
            (r / ENGINEERING_FRAMES_REL).write_text(frames, encoding="utf-8")
        self._assert_finding(mutate, "FRAME-OWNS-VALUE")

    def test_frame_carrying_requirement_is_value_ownership(self) -> None:
        def mutate(r: Path) -> None:
            frames = (
                _frame("efShipped", "EF-SHIPPED", "shipped",
                       extra='\n                "saa.requirement" "REQ-001"')
                + _frame("efPlanned", "EF-PLANNED", "design_only")
                + _edge("genModule_demo", "efShipped", "contains")
                + _edge("genModule_demo", "efPlanned", "contains")
                + _edge("efShipped", "fpAlpha", "anchors")
                + _edge("featShipped", "efShipped", "traverses")
                + _edge("featShipped", "efPlanned", "traverses")
            )
            (r / ENGINEERING_FRAMES_REL).write_text(frames, encoding="utf-8")
        self._assert_finding(mutate, "FRAME-OWNS-VALUE")

    def test_malformed_anchors_target_not_fp(self) -> None:
        # efShipped anchors efPlanned (a Frame, not an FP).
        self._assert_finding(
            lambda r: (r / ENGINEERING_FRAMES_REL).write_text(
                _frames_dsl() + _edge("efShipped", "efPlanned", "anchors"),
                encoding="utf-8",
            ),
            "MALFORMED-EDGE",
        )


# ===========================================================================
# Allowlist suppression.
# ===========================================================================
class AllowlistTests(unittest.TestCase):
    def _materialize_with_value_owning_frame(self, root: Path) -> None:
        write(root / FEATURES_REL, _features_dsl())
        write(root / FUNCTION_POINTS_REL, _function_points_dsl())
        write(root / ENGINEERING_FRAMES_REL,
              _frame("efShipped", "EF-SHIPPED", "shipped",
                     extra='\n                "saa.productClaim" "PC-001"')
              + _frame("efPlanned", "EF-PLANNED", "design_only")
              + _edge("genModule_demo", "efShipped", "contains")
              + _edge("genModule_demo", "efPlanned", "contains")
              + _edge("efShipped", "fpAlpha", "anchors")
              + _edge("featShipped", "efShipped", "traverses")
              + _edge("featShipped", "efPlanned", "traverses"))

    def test_allowlist_by_var_suppresses(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._materialize_with_value_owning_frame(root)
            # Sanity: it fails without the allowlist.
            self.assertEqual(_run(root, "--mode", "full-blocking").returncode, 1)
            write(root / ALLOWLIST_REL, "efShipped  # ADR-XXXX demo exception\n")
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("0 finding(s)", result.stderr)

    def test_allowlist_by_saa_id_suppresses(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._materialize_with_value_owning_frame(root)
            write(root / ALLOWLIST_REL, "EF-SHIPPED\n")
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 0, result.stderr)


# ===========================================================================
# Ratchet modes.
# ===========================================================================
class ModeTests(unittest.TestCase):
    def _materialize_with_one_blocking_finding(self, root: Path) -> None:
        # A Feature directly contains a Frame -> one FEATURE-OWNS-FRAME finding.
        _materialize_clean(root)
        (root / ENGINEERING_FRAMES_REL).write_text(
            _frames_dsl() + _edge("featShipped", "efPlanned", "contains"),
            encoding="utf-8",
        )

    def test_advisory_always_exits_zero(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._materialize_with_one_blocking_finding(root)
            result = _run(root, "--mode", "advisory")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("[advisory]", result.stderr)
            self.assertNotIn("0 finding(s)", result.stderr)

    def test_default_mode_is_advisory(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._materialize_with_one_blocking_finding(root)
            result = _run(root)  # no --mode
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("[advisory]", result.stderr)

    def test_full_blocking_exits_one(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._materialize_with_one_blocking_finding(root)
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn("BLOCKING", result.stderr)

    def test_changed_files_blocking_blocks_when_untracked(self) -> None:
        # A non-git temp dir -> base ref unresolved -> the whole map is in scope.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._materialize_with_one_blocking_finding(root)
            result = _run(root, "--mode", "changed-files-blocking", "--base", "origin/main")
            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn("BLOCKING", result.stderr)


# ===========================================================================
# Config errors (fail-closed).
# ===========================================================================
class ConfigErrorTests(unittest.TestCase):
    def test_unreadable_repo_dir_exits_two(self) -> None:
        result = subprocess.run(
            [sys.executable, str(HELPER), "--repo",
             str(REPO_ROOT / "no-such-dir-xyz"), "--mode", "advisory"],
            text=True, capture_output=True, check=False,
        )
        self.assertEqual(result.returncode, 2)
        self.assertIn("is not a directory", result.stderr)

    def test_bad_mode_exits_two(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = _run(Path(tmp), "--mode", "no-such-mode")
            self.assertEqual(result.returncode, 2)


# ===========================================================================
# Live-corpus smoke (advisory must always pass; the real map is clean).
# ===========================================================================
class LiveCorpusTests(unittest.TestCase):
    def test_advisory_against_repo_root_exits_zero_and_clean(self) -> None:
        result = subprocess.run(
            [sys.executable, str(HELPER), "--repo", str(REPO_ROOT), "--mode", "advisory"],
            text=True, capture_output=True, check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("ai-understanding-map [advisory]", result.stderr)
        # The live dual-track map satisfies every invariant.
        self.assertNotIn("FEATURE-OWNS-FRAME", result.stderr)
        self.assertNotIn("FRAME-OWNS-VALUE", result.stderr)
        self.assertNotIn("NON-MODULE-CONTAINS-FRAME", result.stderr)
        self.assertNotIn("MALFORMED-EDGE", result.stderr)

    def test_full_blocking_against_repo_root_is_clean(self) -> None:
        result = subprocess.run(
            [sys.executable, str(HELPER), "--repo", str(REPO_ROOT), "--mode", "full-blocking"],
            text=True, capture_output=True, check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("0 finding(s)", result.stderr)


if __name__ == "__main__":
    unittest.main()
