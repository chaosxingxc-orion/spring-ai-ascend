#!/usr/bin/env python3
"""Gate check: Rule G-28 (ADR Normalization) — normalized-ADR view validator.

Validates every normalized ADR view (``docs/adr/normalized/ADR-NNNN.yaml``)
against the two hand-authored governance-policy surfaces that together pin the
shape and altitude of a normalized view:

  * ``docs/governance/adr-taxonomy.yaml`` — the decision-level taxonomy. Fixes
    the closed ``decision_level`` key set, the per-level ``decision_type``
    allow / forbid lists (the layer-purity altitude control), and the closed
    ``affected_level_vocabulary``. Authority: ADR-0160 decision (4).
  * ``docs/governance/adr-governance-policy.yaml`` — the normalized-view schema.
    Fixes the required-field list, the closed five-value ``current_state`` set,
    and the per-state field-presence invariants. Authority: ADR-0160 decision (2).

A normalized view is valid only when it satisfies BOTH surfaces. This script is
the executable form of that joint obligation. It invents no ADR ids and no
relationships and it never outranks a generated fact: it reads the two policy
files as the schema and the views as the data, and reports altitude / shape /
state violations. The single source of truth for WHICH ADRs exist is the raw
``docs/adr/`` sources and their extracted projection
``architecture/facts/generated/adrs.json`` (ADR-0154); the complete-coverage
obligation (every raw ADR has a ledger entry, every accepted ADR has a
normalized view) is a separate check (``check_historical_adr_governance.py``).

Modes (``--mode``):

  advisory                 Validate every normalized view, print findings to
                           stderr, and ALWAYS exit 0. This is the soak posture:
                           the check reports but never blocks.
  changed-files-blocking   Validate only the normalized views that changed
                           relative to a base ref (``--base``, default
                           ``origin/main``). Exit 1 if any CHANGED view has a
                           finding; pre-existing findings on untouched views do
                           not block. This is the ratchet posture: a PR may not
                           ADD or WORSEN a violation, but is not blocked by debt
                           it did not touch.
  full-blocking            Validate every normalized view; exit 1 on any
                           finding. This is the terminal posture once the corpus
                           is clean.

Usage:
    python3 gate/lib/check_adr_taxonomy.py --mode advisory
    python3 gate/lib/check_adr_taxonomy.py --mode changed-files-blocking
    python3 gate/lib/check_adr_taxonomy.py --mode changed-files-blocking --base origin/main
    python3 gate/lib/check_adr_taxonomy.py --mode full-blocking
    python3 gate/lib/check_adr_taxonomy.py --mode full-blocking --repo /path/to/repo

Exit codes:
    0 — passed (always, in advisory mode); or no findings in a blocking mode
    1 — one or more in-scope findings in a blocking mode (printed to stderr)
    2 — usage / configuration error (bad mode, unreadable policy file, etc.)

Authority: ADR-0160 (ADR Governance Model). Consumed by Rule G-28.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# Canonical policy-surface locations (repo-relative).
# ---------------------------------------------------------------------------
TAXONOMY_REL = "docs/governance/adr-taxonomy.yaml"
POLICY_REL = "docs/governance/adr-governance-policy.yaml"
NORMALIZED_DIR_REL = "docs/adr/normalized"

# A normalized view's filename — docs/adr/normalized/ADR-NNNN.yaml.
NORMALIZED_NAME_RE = re.compile(r"^ADR-\d{4}\.yaml$")

# The ADR-0068 view axis. A normalized view's `view` field draws from this set
# OR is an explicit authority-surface label (any other non-empty token), so we
# do not hard-fail on a non-axis token — we only assert the field is present
# and non-empty. The closed axis is documented here for traceability and to
# anchor a future tightening of the rule.
VIEW_AXIS = ("logical", "development", "process", "physical", "scenarios")


def repo_root() -> Path:
    """Return the repository root (the directory two levels above this script)."""
    return Path(__file__).resolve().parent.parent.parent


# ---------------------------------------------------------------------------
# YAML loading. Mirrors the fallback posture of sibling gate helpers: PyYAML is
# the authority, but its absence must not silently pass — for the policy files a
# missing parser is a hard config error (exit 2) because we cannot validate
# without the schema; for an individual view a missing parser is reported as an
# (unresolvable) finding rather than a vacuous pass.
# ---------------------------------------------------------------------------
def _load_yaml(path: Path) -> tuple[object | None, str]:
    """Load ``path`` as YAML. Returns ``(data, error)``; ``error`` is '' on success."""
    if not path.is_file():
        return None, f"missing file {path}"
    try:
        import yaml  # type: ignore[import-not-found]
    except ImportError:
        return None, (
            "PyYAML is not installed; cannot parse "
            f"{path} (run: pip install pyyaml)"
        )
    try:
        with path.open("r", encoding="utf-8") as fh:
            return yaml.safe_load(fh), ""
    except yaml.YAMLError as exc:  # type: ignore[attr-defined]
        return None, f"{path} failed YAML parse: {exc}"
    except (OSError, ValueError) as exc:
        return None, f"cannot read {path}: {exc}"


# ---------------------------------------------------------------------------
# Policy model — the typed projection of the two governance-policy surfaces.
# ---------------------------------------------------------------------------
class Policy:
    """Typed view over adr-taxonomy.yaml + adr-governance-policy.yaml."""

    def __init__(
        self,
        decision_levels: dict[str, dict[str, list[str]]],
        affected_vocabulary: set[str],
        required_fields: list[str],
        current_states: set[str],
        state_rules: dict[str, dict[str, bool]],
    ) -> None:
        self.decision_levels = decision_levels
        self.affected_vocabulary = affected_vocabulary
        self.required_fields = required_fields
        self.current_states = current_states
        self.state_rules = state_rules

    def allowed_types(self, level: str) -> set[str]:
        return set(self.decision_levels.get(level, {}).get("allowed_decision_types", []))

    def forbidden_types(self, level: str) -> set[str]:
        return set(self.decision_levels.get(level, {}).get("forbidden_decision_types", []))


def load_policy(root: Path) -> tuple[Policy | None, list[str]]:
    """Load + validate the two policy surfaces. Returns ``(policy, config_errors)``.

    A non-empty ``config_errors`` list means the policy could not be loaded; the
    caller MUST treat that as exit-2 (the schema is unavailable, so no view can
    be judged — failing closed rather than passing vacuously).
    """
    errors: list[str] = []

    taxonomy, terr = _load_yaml(root / TAXONOMY_REL)
    if terr:
        errors.append(f"taxonomy: {terr}")
    policy_doc, perr = _load_yaml(root / POLICY_REL)
    if perr:
        errors.append(f"governance-policy: {perr}")
    if errors:
        return None, errors

    if not isinstance(taxonomy, dict):
        errors.append(f"taxonomy: top-level of {TAXONOMY_REL} must be a mapping")
    if not isinstance(policy_doc, dict):
        errors.append(f"governance-policy: top-level of {POLICY_REL} must be a mapping")
    if errors:
        return None, errors

    decision_levels = taxonomy.get("decision_levels")
    if not isinstance(decision_levels, dict) or not decision_levels:
        errors.append(
            f"taxonomy: {TAXONOMY_REL} missing non-empty 'decision_levels' mapping"
        )
        decision_levels = {}
    # Normalize each level's allow/forbid lists to list[str].
    norm_levels: dict[str, dict[str, list[str]]] = {}
    for level, body in decision_levels.items():
        if not isinstance(body, dict):
            errors.append(f"taxonomy: decision_levels.{level} must be a mapping")
            continue
        allowed = body.get("allowed_decision_types", []) or []
        forbidden = body.get("forbidden_decision_types", []) or []
        if not isinstance(allowed, list) or not isinstance(forbidden, list):
            errors.append(
                f"taxonomy: decision_levels.{level} allow/forbid lists must be sequences"
            )
            allowed, forbidden = [], []
        norm_levels[str(level)] = {
            "allowed_decision_types": [str(x) for x in allowed],
            "forbidden_decision_types": [str(x) for x in forbidden],
        }

    affected_vocab_raw = taxonomy.get("affected_level_vocabulary", []) or []
    if not isinstance(affected_vocab_raw, list) or not affected_vocab_raw:
        errors.append(
            f"taxonomy: {TAXONOMY_REL} missing non-empty 'affected_level_vocabulary' list"
        )
        affected_vocab_raw = []
    affected_vocabulary = {str(x) for x in affected_vocab_raw}

    required_fields_raw = policy_doc.get("normalized_adr_required_fields", []) or []
    if not isinstance(required_fields_raw, list) or not required_fields_raw:
        errors.append(
            f"governance-policy: {POLICY_REL} missing non-empty "
            "'normalized_adr_required_fields' list"
        )
        required_fields_raw = []
    required_fields = [str(x) for x in required_fields_raw]

    current_states_raw = policy_doc.get("current_states", {}) or {}
    if not isinstance(current_states_raw, dict) or not current_states_raw:
        errors.append(
            f"governance-policy: {POLICY_REL} missing non-empty 'current_states' mapping"
        )
        current_states_raw = {}
    current_states = {str(k) for k in current_states_raw}

    state_rules_raw = policy_doc.get("state_rules", {}) or {}
    if not isinstance(state_rules_raw, dict):
        errors.append(f"governance-policy: {POLICY_REL} 'state_rules' must be a mapping")
        state_rules_raw = {}
    state_rules: dict[str, dict[str, bool]] = {}
    for state, body in state_rules_raw.items():
        if isinstance(body, dict):
            state_rules[str(state)] = {str(k): bool(v) for k, v in body.items()}

    if errors:
        return None, errors

    return (
        Policy(
            decision_levels=norm_levels,
            affected_vocabulary=affected_vocabulary,
            required_fields=required_fields,
            current_states=current_states,
            state_rules=state_rules,
        ),
        [],
    )


# ---------------------------------------------------------------------------
# View-level validation.
# ---------------------------------------------------------------------------
def _is_nonempty(value: object) -> bool:
    """A field counts as 'present and non-empty' if it is a non-blank scalar or
    a non-empty collection. ``None`` and empty list/dict/str are empty."""
    if value is None:
        return False
    if isinstance(value, str):
        return value.strip() != ""
    if isinstance(value, (list, dict, tuple, set)):
        return len(value) > 0
    return True


def validate_view(path: Path, policy: Policy, root: Path) -> list[str]:
    """Validate one normalized ADR view. Returns a list of finding strings.

    The view's own ``adr`` field SHOULD agree with its filename; a mismatch is a
    finding so the readable-interpretation layer cannot drift from its id.
    """
    rel = _rel(path, root)
    findings: list[str] = []

    data, err = _load_yaml(path)
    if err:
        return [f"G-28 {rel}: {err}"]
    if not isinstance(data, dict):
        return [f"G-28 {rel}: top-level must be a mapping (got {type(data).__name__})"]

    # 1) Required fields present (key must exist; emptiness is governed per-state).
    for field in policy.required_fields:
        if field not in data:
            findings.append(f"G-28 {rel}: missing required field '{field}'")

    # 2) Filename / adr-id agreement.
    expected_id = path.stem  # ADR-NNNN
    adr_id = data.get("adr")
    if isinstance(adr_id, str) and adr_id.strip() and adr_id.strip() != expected_id:
        findings.append(
            f"G-28 {rel}: 'adr' field {adr_id!r} disagrees with filename id {expected_id!r}"
        )

    # 3) decision_level must be a known level.
    level = data.get("decision_level")
    level_str = str(level).strip() if level is not None else ""
    level_known = level_str in policy.decision_levels
    if level is None:
        # already reported as missing if it was absent; if present-but-null, flag.
        if "decision_level" in data:
            findings.append(f"G-28 {rel}: 'decision_level' is null")
    elif not level_known:
        findings.append(
            f"G-28 {rel}: decision_level {level_str!r} is not one of "
            f"{sorted(policy.decision_levels)}"
        )

    # 4) decision_type must be ALLOWED at the level and MUST NOT be a forbidden
    #    lower-altitude leak (the layer-purity invariant). Only checkable once
    #    the level is known.
    decision_type = data.get("decision_type")
    dtype_str = str(decision_type).strip() if decision_type is not None else ""
    if level_known and dtype_str:
        forbidden = policy.forbidden_types(level_str)
        allowed = policy.allowed_types(level_str)
        if dtype_str in forbidden:
            findings.append(
                f"G-28 {rel}: decision_type {dtype_str!r} is FORBIDDEN at "
                f"decision_level {level_str!r} (lower-altitude leakage — push this "
                f"detail down to L2/contracts/facts)"
            )
        elif dtype_str not in allowed:
            findings.append(
                f"G-28 {rel}: decision_type {dtype_str!r} is not in the allowed set "
                f"for decision_level {level_str!r} ({sorted(allowed)})"
            )

    # 5) affected_levels must be a list drawn from the closed vocabulary.
    affected = data.get("affected_levels")
    if affected is not None:
        if not isinstance(affected, list):
            findings.append(
                f"G-28 {rel}: 'affected_levels' must be a list (got "
                f"{type(affected).__name__})"
            )
        else:
            for token in affected:
                tok = str(token).strip()
                if tok not in policy.affected_vocabulary:
                    findings.append(
                        f"G-28 {rel}: affected_levels entry {tok!r} not in "
                        f"affected_level_vocabulary {sorted(policy.affected_vocabulary)}"
                    )

    # 6) view present + non-empty (axis token or authority-surface label).
    if "view" in data and not _is_nonempty(data.get("view")):
        findings.append(f"G-28 {rel}: 'view' must be a non-empty token")

    # 7) current_state must be exactly one of the closed set; then apply the
    #    per-state field-presence invariants.
    state = data.get("current_state")
    state_str = str(state).strip() if state is not None else ""
    if "current_state" in data and not state_str:
        findings.append(f"G-28 {rel}: 'current_state' must be non-empty")
    elif state_str and state_str not in policy.current_states:
        findings.append(
            f"G-28 {rel}: current_state {state_str!r} is not one of "
            f"{sorted(policy.current_states)}"
        )
    elif state_str:
        findings.extend(_check_state_invariants(rel, data, state_str, policy))

    return findings


def _check_state_invariants(
    rel: str, data: dict, state: str, policy: Policy
) -> list[str]:
    """Apply the per-state field-presence invariants from the governance policy.

    The invariant keys are interpreted generically so the rule tracks the policy
    file rather than hard-coding the matrix:

      * ``<field>_required: true``  -> ``<field>`` MUST be present and non-empty.
      * ``<field>_allowed: false``  -> ``<field>`` MUST be empty / absent.
      * keys suffixed ``_when_*``   -> conditional; only the unconditional
        portion is enforced mechanically here (the conditional clause needs
        semantic context a structural gate cannot see), so they are recorded as
        advisory and not turned into hard findings.
    """
    findings: list[str] = []
    rules = policy.state_rules.get(state, {})
    for key, flag in rules.items():
        # Conditional invariants (e.g. gate_refs_required_when_gate_change_claimed)
        # cannot be mechanically adjudicated by a structural gate; skip them.
        if "_when_" in key:
            continue
        if key.endswith("_required"):
            field = key[: -len("_required")]
            if flag and not _is_nonempty(data.get(field)):
                findings.append(
                    f"G-28 {rel}: current_state {state!r} requires non-empty "
                    f"'{field}' (state invariant {key})"
                )
        elif key.endswith("_allowed"):
            field = key[: -len("_allowed")]
            if (not flag) and _is_nonempty(data.get(field)):
                findings.append(
                    f"G-28 {rel}: current_state {state!r} forbids a non-empty "
                    f"'{field}' (state invariant {key})"
                )
    return findings


# ---------------------------------------------------------------------------
# View discovery + changed-file scoping.
# ---------------------------------------------------------------------------
def discover_views(root: Path) -> list[Path]:
    """Return all normalized ADR view files, sorted. May be empty (no views yet)."""
    norm_dir = root / NORMALIZED_DIR_REL
    if not norm_dir.is_dir():
        return []
    return sorted(p for p in norm_dir.glob("ADR-*.yaml") if NORMALIZED_NAME_RE.match(p.name))


def _git_run(args: list[str], cwd: Path) -> tuple[int, str]:
    """Run git; return ``(returncode, stdout)``. Forces UTF-8 (Windows GBK-safe)."""
    try:
        result = subprocess.run(
            ["git", *args],
            cwd=str(cwd),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=False,
        )
        return result.returncode, (result.stdout or "")
    except OSError:
        return 1, ""


def changed_views(root: Path, base: str) -> tuple[list[Path] | None, str]:
    """Return the normalized views changed vs ``base``, or ``(None, reason)``.

    Scope = (committed diff base...HEAD) UNION (uncommitted working-tree +
    staged changes). A ``None`` return means git could not resolve the base ref
    (e.g. shallow clone without the base, or not a git repo); the caller decides
    how to treat that (fail-closed in a blocking mode).
    """
    rc, _ = _git_run(["rev-parse", "--git-dir"], root)
    if rc != 0:
        return None, "not a git repository (cannot scope changed files)"

    # Resolve the merge-base so renames/branch divergence are handled; fall back
    # to a two-dot diff if no common ancestor (e.g. unrelated histories).
    diff_spec = base
    rc, mb = _git_run(["merge-base", base, "HEAD"], root)
    if rc == 0 and mb.strip():
        diff_spec = mb.strip()
    else:
        # Verify the base ref exists at all; if not, fail-closed via None.
        rc_verify, _ = _git_run(["rev-parse", "--verify", "--quiet", base], root)
        if rc_verify != 0:
            return None, f"base ref {base!r} is not resolvable in this clone"

    names: set[str] = set()
    # Committed changes base...HEAD.
    rc, out = _git_run(["diff", "--name-only", f"{diff_spec}", "HEAD"], root)
    if rc == 0:
        names.update(line.strip() for line in out.splitlines() if line.strip())
    # Uncommitted (tracked, staged + unstaged) changes.
    rc, out = _git_run(["diff", "--name-only", "HEAD"], root)
    if rc == 0:
        names.update(line.strip() for line in out.splitlines() if line.strip())
    # Untracked files (new normalized views not yet added).
    rc, out = _git_run(["ls-files", "--others", "--exclude-standard", NORMALIZED_DIR_REL], root)
    if rc == 0:
        names.update(line.strip() for line in out.splitlines() if line.strip())

    prefix = NORMALIZED_DIR_REL + "/"
    selected: list[Path] = []
    for name in sorted(names):
        norm = name.replace("\\", "/")
        if norm.startswith(prefix) and NORMALIZED_NAME_RE.match(Path(norm).name):
            candidate = root / norm
            if candidate.is_file():
                selected.append(candidate)
    return selected, ""


# ---------------------------------------------------------------------------
# CLI.
# ---------------------------------------------------------------------------
def _rel(path: Path, root: Path) -> str:
    try:
        return str(path.relative_to(root)).replace("\\", "/")
    except ValueError:
        return str(path)


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Rule G-28 — ADR Normalization: validate normalized ADR views "
        "against adr-taxonomy.yaml + adr-governance-policy.yaml.",
    )
    parser.add_argument(
        "--mode",
        choices=("advisory", "changed-files-blocking", "full-blocking"),
        default="advisory",
        help="advisory (report, never block); changed-files-blocking (block only "
        "on changed views); full-blocking (block on any view). Default: advisory.",
    )
    parser.add_argument(
        "--base",
        default="origin/main",
        help="Base ref for changed-files-blocking scope. Default: origin/main.",
    )
    parser.add_argument(
        "--repo",
        default=None,
        help="Repository root. Defaults to script-derived root.",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    root = Path(args.repo) if args.repo else repo_root()
    if not root.is_dir():
        print(f"G-28: --repo {root} is not a directory", file=sys.stderr)
        return 2

    policy, config_errors = load_policy(root)
    if policy is None:
        for err in config_errors:
            print(f"G-28 config error: {err}", file=sys.stderr)
        # Fail-closed: without the schema we cannot judge views.
        return 2

    all_views = discover_views(root)

    if args.mode == "changed-files-blocking":
        scoped, reason = changed_views(root, args.base)
        if scoped is None:
            # Cannot scope reliably -> fail-closed in a blocking mode by falling
            # back to validating ALL views (a superset is safe: it can only block
            # MORE, never let a changed-view defect through).
            print(
                f"G-28: changed-file scoping unavailable ({reason}); "
                f"falling back to full-corpus validation",
                file=sys.stderr,
            )
            views = all_views
        else:
            views = scoped
    else:
        views = all_views

    findings: list[str] = []
    for view in views:
        findings.extend(validate_view(view, policy, root))

    if findings:
        for finding in findings:
            print(finding, file=sys.stderr)

    summary = (
        f"G-28 [{args.mode}]: validated {len(views)} normalized ADR view(s)"
        + (f" (corpus total {len(all_views)})" if len(views) != len(all_views) else "")
        + f"; {len(findings)} finding(s)"
    )
    print(summary, file=sys.stderr)

    if args.mode == "advisory":
        # Advisory: report but never block.
        return 0
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
