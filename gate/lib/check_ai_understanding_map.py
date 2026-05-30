#!/usr/bin/env python3
"""Gate check: the dual-track understanding map keeps its axes derived, never owned.

Authority: ADR-0157 (EngineeringFrame Ontology — the dual-track value/structure
map) read through the authored Structurizr fragments
``architecture/features/features.dsl`` (Feature elements + Feature->FunctionPoint
``requires`` edges, the VALUE axis), ``architecture/features/function-points.dsl``
(FunctionPoint elements), and ``architecture/features/engineering-frames.dsl``
(EngineeringFrame elements + Module->Frame ``contains``, Frame->FunctionPoint
``anchors``, and Feature->Frame ``traverses`` edges, the STRUCTURE axis). The
agent-service EngineeringFrame ELEMENTS are authored in features.dsl while their
``contains`` / ``anchors`` edges live in engineering-frames.dsl, so all three
files are merged before the map is read (mirroring
``check_frame_shipped_anchors.py`` / ``check_frame_card_consistency.py``).

The understanding map is a dual-track graph (ADR-0157 §2):

  * VALUE axis     ProductClaim -> Requirement -> Feature -> FunctionPoint, with
                   the Feature -> FunctionPoint hop carried by a ``requires`` edge.
  * STRUCTURE axis Module -> EngineeringFrame -> FunctionPoint, with the
                   Module -> Frame hop carried by ``contains`` and the
                   Frame -> FunctionPoint hop carried by ``anchors``.
  * The two axes meet ONLY at the FunctionPoint and ONLY through a DERIVED
    Feature -> EngineeringFrame ``traverses`` edge: a value demand is read as a
    route across the structural map. A ``traverses`` edge is a derived view of
    "this Feature's work lands in that Frame", NEVER a statement that the Feature
    (or its ProductClaim / Requirement) OWNS the Frame. Frames are
    CLAIM-AGNOSTIC; product value binds to the value axis (Feature), not to the
    structural slice it crosses.

This check is a READABLE-INTERPRETATION guard over the authored DSL: it invents
no id and no relationship, asserts none of its own authority, and never outranks
a surface it reads (cascade: generated facts > DSL > Card/prose). It only asserts
that the map the DSL already declares satisfies the two map invariants:

  1. DERIVED TRAVERSE. Every ``Feature --traverses--> Frame`` edge MUST be
     DERIVABLE from a shared FunctionPoint: the Feature ``requires`` some
     FunctionPoint that the Frame ``anchors``. A Frame that anchors NO
     FunctionPoint yet (a still-``design_only`` structural placeholder) has
     nothing to derive from, so a traverse onto it is vacuously permitted — but
     the instant a Frame anchors any FunctionPoint, a Feature that traverses it
     MUST share one, so the value<->structure link can never be invented over a
     populated Frame. A break is a ``NON-DERIVED-TRAVERSE`` finding. The blocking
     ratchet scopes this to ``shipped`` Features (a shipped value thread whose
     structural route is fabricated is a downstream-correctness lie); traverses
     from not-yet-shipped Features are reported but advisory-only even under
     ``full-blocking`` (their structure is still settling).

  2. NO OWNERSHIP OF A FRAME. A Frame is owned (``contains``) by exactly one
     Module and by nothing else. Concretely:
       * ``FEATURE-OWNS-FRAME``       — a Feature is the source of a ``contains``
                                        / ``anchors`` / ``owns`` edge into a
                                        Frame (a Feature may ONLY ``traverses`` a
                                        Frame, never own it).
       * ``NON-MODULE-CONTAINS-FRAME``— a ``contains`` edge into a Frame whose
                                        source is not a Module element
                                        (``genModule_*``).
       * ``FRAME-OWNS-VALUE``         — a Frame element carries a value-axis
                                        property (``saa.productClaim`` or
                                        ``saa.requirement``). ProductClaim and
                                        Requirement are value-axis IDENTIFIERS,
                                        not graph elements, so the only way one
                                        can "own" a Frame is by appearing as a
                                        Frame property; forbidding that property
                                        on Frames is the structural form of
                                        "no ProductClaim / Requirement owns a
                                        Frame".

  3. WELL-TYPED AXES. The hops that the derivation relies on must connect the
     declared kinds: an ``anchors`` edge goes Frame -> FunctionPoint and a
     ``requires`` edge goes Feature -> FunctionPoint. A mis-wired hop is a
     ``MALFORMED-EDGE`` finding (it would silently corrupt the derivation).

ADR-backed exceptions are listed (one ``feat*`` / ``ef*`` var or ``FEAT-*`` /
``EF-*`` saa.id, or a ``src->dst`` edge pair, per line) in
``gate/ai-understanding-map-allowlist.txt``; that file ships empty. An allowlist
entry suppresses any finding whose subject equals the entry.

Modes (``--mode``), mirroring the sibling reading-path / layer-purity ratchet:

  advisory                 Evaluate the whole map, print findings to stderr, and
                           ALWAYS exit 0. The landing posture (first cleanup wave).
  changed-files-blocking   Exit 1 on any in-scope finding ONLY when one of the
                           three authoring DSL files changed relative to a base
                           ref (``--base``, default ``origin/main``, else
                           ``HEAD``); the map is a single shared surface, so a
                           change to any of the three re-scopes it. Otherwise
                           advisory.
  full-blocking            Exit 1 on any in-scope finding — the terminal posture
                           once the map is clean. NON-DERIVED-TRAVERSE findings
                           from not-yet-shipped Features stay advisory even here.

The three DSL files are the only required inputs. When NONE of them exists the
check is vacuously clean in every mode (the map has not been authored yet —
greenfield). The instant any exists it MUST be readable, or the check fails
closed (exit 2) in EVERY mode including advisory — a missing authority is never
an advisory condition.

Usage:
    python3 gate/lib/check_ai_understanding_map.py --mode advisory
    python3 gate/lib/check_ai_understanding_map.py --mode changed-files-blocking
    python3 gate/lib/check_ai_understanding_map.py --mode changed-files-blocking --base origin/main
    python3 gate/lib/check_ai_understanding_map.py --mode full-blocking
    python3 gate/lib/check_ai_understanding_map.py --mode full-blocking --repo /path/to/repo

Exit codes:
    0 — passed (always, in advisory mode); or no in-scope findings in a blocking
        mode; or none of the three map DSL files exists yet (greenfield)
    1 — one or more in-scope findings in a blocking mode (printed to stderr)
    2 — usage / configuration error (bad mode, an existing DSL file is
        unreadable, --repo not a directory, etc.)
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

# ---------------------------------------------------------------------------
# Canonical surface locations (repo-relative, forward slash).
# ---------------------------------------------------------------------------
FEATURES_REL = "architecture/features/features.dsl"
FUNCTION_POINTS_REL = "architecture/features/function-points.dsl"
ENGINEERING_FRAMES_REL = "architecture/features/engineering-frames.dsl"
ALLOWLIST_REL = "gate/ai-understanding-map-allowlist.txt"

# All three authoring files; a change to any re-scopes the whole map.
MAP_DSL_RELS = (FEATURES_REL, FUNCTION_POINTS_REL, ENGINEERING_FRAMES_REL)

VALID_MODES = ("advisory", "changed-files-blocking", "full-blocking")

# Module element vars own (contain) Frames; the authored convention is the
# `genModule_<module>` naming emitted by modules.dsl. A `contains` edge into a
# Frame whose source does not match this is NON-MODULE-CONTAINS-FRAME.
MODULE_VAR_RE = re.compile(r"^genModule_[A-Za-z0-9_]+$")

# Ownership-shaped relationship verbs: a Feature that is the source of any of
# these into a Frame is claiming structural ownership it must not have.
OWNERSHIP_RELS = ("contains", "anchors", "owns")

# ---------------------------------------------------------------------------
# DSL element / edge regexes (mirror check_frame_shipped_anchors.py).
# ---------------------------------------------------------------------------
# `<var> = element "<name>" "<Kind>" ...` — captures the var for a given kind.
_ELEMENT_TMPL = r'(\w+)\s*=\s*element\s+"[^"]*"\s+"{kind}"'
# A relationship edge whose properties block declares saa.rel = <rel>.
_EDGE_TMPL = (
    r'(\w+)\s*->\s*(\w+)\s+"[^"]*"\s+"SAA Relationship"\s*\{{[^}}]*?'
    r'"saa\.rel"\s+"{rel}"'
)
_PROP_TMPL = r'"{key}"\s+"([^"]*)"'


def _element_re(kind: str) -> re.Pattern[str]:
    return re.compile(_ELEMENT_TMPL.format(kind=re.escape(kind)), re.MULTILINE)


def _edge_re(rel: str) -> re.Pattern[str]:
    return re.compile(_EDGE_TMPL.format(rel=re.escape(rel)), re.DOTALL)


def _prop(block: str, key: str) -> str | None:
    m = re.search(_PROP_TMPL.format(key=re.escape(key)), block)
    return m.group(1) if m else None


# ===========================================================================
# Repo + path helpers.
# ===========================================================================
def repo_root() -> Path:
    """Return the repository root (the directory two levels above this script)."""
    return Path(__file__).resolve().parent.parent.parent


def _norm(rel: str) -> str:
    return rel.replace("\\", "/").rstrip("/")


# ===========================================================================
# Findings + errors.
# ===========================================================================
@dataclass(frozen=True)
class Finding:
    code: str  # NON-DERIVED-TRAVERSE / FEATURE-OWNS-FRAME / NON-MODULE-CONTAINS-FRAME / FRAME-OWNS-VALUE / MALFORMED-EDGE
    subject: str  # the var / edge / saa.id the finding is about (also the allowlist key)
    detail: str
    advisory_only: bool = False  # never blocks, even under full-blocking (e.g. non-shipped traverse)

    def line(self) -> str:
        return f"ai-understanding-map [{self.code}] {self.subject}: {self.detail}"


class ConfigError(Exception):
    """A required authority is missing/unreadable while the map exists (exit 2)."""


# ===========================================================================
# Map model.
# ===========================================================================
@dataclass
class UnderstandingMap:
    # var -> element block text, per kind.
    features: dict[str, str] = field(default_factory=dict)
    frames: dict[str, str] = field(default_factory=dict)
    function_points: dict[str, str] = field(default_factory=dict)
    # edge sets as (src_var, dst_var) tuples, per relationship verb.
    requires: set[tuple[str, str]] = field(default_factory=set)   # Feature -> FunctionPoint
    anchors: set[tuple[str, str]] = field(default_factory=set)    # Frame   -> FunctionPoint
    traverses: set[tuple[str, str]] = field(default_factory=set)  # Feature -> Frame
    contains: set[tuple[str, str]] = field(default_factory=set)   # Module  -> Frame (+ noise)
    owns: set[tuple[str, str]] = field(default_factory=set)       # (none authored today)

    def feature_id(self, var: str) -> str:
        return _prop(self.features.get(var, ""), "saa.id") or var

    def frame_id(self, var: str) -> str:
        return _prop(self.frames.get(var, ""), "saa.id") or var

    def feature_status(self, var: str) -> str | None:
        return _prop(self.features.get(var, ""), "saa.status")

    def feature_required_fps(self, var: str) -> set[str]:
        return {dst for src, dst in self.requires if src == var}

    def frame_anchored_fps(self, var: str) -> set[str]:
        return {dst for src, dst in self.anchors if src == var}


def _collect_elements(text: str, kind: str) -> dict[str, str]:
    """Map every `<var> = element ... "<kind>"` to its brace-balanced block."""
    out: dict[str, str] = {}
    for m in _element_re(kind).finditer(text):
        var = m.group(1)
        start = m.start()
        brace = text.find("{", start)
        if brace == -1:
            out[var] = text[start:start + 600]
            continue
        depth = 0
        j = brace
        while j < len(text):
            ch = text[j]
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        out[var] = text[start:j + 1]
    return out


def _collect_edges(text: str, rel: str) -> set[tuple[str, str]]:
    return {(m.group(1), m.group(2)) for m in _edge_re(rel).finditer(text)}


def build_map(text: str) -> UnderstandingMap:
    """Project the merged DSL text into the dual-track map model."""
    m = UnderstandingMap()
    m.features = _collect_elements(text, "Feature")
    m.frames = _collect_elements(text, "EngineeringFrame")
    m.function_points = _collect_elements(text, "FunctionPoint")
    m.requires = _collect_edges(text, "requires")
    m.anchors = _collect_edges(text, "anchors")
    m.traverses = _collect_edges(text, "traverses")
    m.contains = _collect_edges(text, "contains")
    m.owns = _collect_edges(text, "owns")
    return m


# ===========================================================================
# Invariant checks.
# ===========================================================================
def check_derived_traverse(m: UnderstandingMap) -> list[Finding]:
    """Every Feature->Frame traverse must share a FunctionPoint with the Frame
    (unless the Frame anchors none yet). Shipped-Feature breaks block; others are
    advisory-only."""
    findings: list[Finding] = []
    for src, dst in sorted(m.traverses):
        if src not in m.features:
            # A traverse whose source is not a Feature is a mis-wired axis.
            findings.append(
                Finding("MALFORMED-EDGE", f"{src}->{dst}",
                        f"traverses source {src} is not a Feature element")
            )
            continue
        if dst not in m.frames:
            findings.append(
                Finding("MALFORMED-EDGE", f"{src}->{dst}",
                        f"traverses target {dst} is not an EngineeringFrame element")
            )
            continue
        frame_fps = m.frame_anchored_fps(dst)
        if not frame_fps:
            # Design-only structural placeholder with nothing to derive from yet.
            continue
        if m.feature_required_fps(src) & frame_fps:
            continue  # derivable: they share a FunctionPoint
        shipped = m.feature_status(src) == "shipped"
        findings.append(
            Finding(
                "NON-DERIVED-TRAVERSE",
                f"{src}->{dst}",
                f"Feature {m.feature_id(src)} traverses Frame {m.frame_id(dst)} but "
                f"shares no FunctionPoint with it (the Frame anchors "
                f"{sorted(frame_fps)}; the Feature requires "
                f"{sorted(m.feature_required_fps(src))}) — the value<->structure "
                f"link is invented, not derived"
                + ("" if shipped else " [source Feature not shipped]"),
                advisory_only=not shipped,
            )
        )
    return findings


def check_no_feature_owns_frame(m: UnderstandingMap) -> list[Finding]:
    """A Feature may only `traverses` a Frame, never own it (contains/anchors/owns)."""
    findings: list[Finding] = []
    edge_sets = {
        "contains": m.contains,
        "anchors": m.anchors,
        "owns": m.owns,
    }
    for rel in OWNERSHIP_RELS:
        for src, dst in sorted(edge_sets[rel]):
            if src in m.features and dst in m.frames:
                findings.append(
                    Finding(
                        "FEATURE-OWNS-FRAME",
                        f"{src}->{dst}",
                        f"Feature {m.feature_id(src)} is the source of a '{rel}' edge "
                        f"into Frame {m.frame_id(dst)}; a Feature may only "
                        f"'traverses' a Frame (the value axis NEVER owns the "
                        f"structure axis)",
                    )
                )
    return findings


def check_only_module_contains_frame(m: UnderstandingMap) -> list[Finding]:
    """Every `contains` edge into a Frame must originate at a Module element."""
    findings: list[Finding] = []
    for src, dst in sorted(m.contains):
        if dst not in m.frames:
            continue  # contains edges into non-frames are out of scope here
        if MODULE_VAR_RE.match(src):
            continue
        # A Feature source is the sharper FEATURE-OWNS-FRAME finding; avoid the
        # duplicate and let that check own it.
        if src in m.features:
            continue
        findings.append(
            Finding(
                "NON-MODULE-CONTAINS-FRAME",
                f"{src}->{dst}",
                f"Frame {m.frame_id(dst)} is contained by '{src}', which is not a "
                f"Module element ({MODULE_VAR_RE.pattern}); only a Module may own "
                f"(contain) an EngineeringFrame",
            )
        )
    return findings


def check_frame_carries_no_value_axis(m: UnderstandingMap) -> list[Finding]:
    """A Frame must not carry a value-axis property (productClaim / requirement)."""
    findings: list[Finding] = []
    for var, block in sorted(m.frames.items()):
        offenders = [
            key for key in ("saa.productClaim", "saa.requirement")
            if _prop(block, key) is not None
        ]
        if offenders:
            findings.append(
                Finding(
                    "FRAME-OWNS-VALUE",
                    var,
                    f"Frame {m.frame_id(var)} declares value-axis "
                    f"{', '.join(offenders)}; EngineeringFrames are claim-agnostic "
                    f"(a ProductClaim / Requirement is a value-axis identifier and "
                    f"may NEVER own a structural Frame)",
                )
            )
    return findings


def check_well_typed_axes(m: UnderstandingMap) -> list[Finding]:
    """anchors must go Frame->FunctionPoint; requires must go Feature->FunctionPoint."""
    findings: list[Finding] = []
    for src, dst in sorted(m.anchors):
        if src not in m.frames:
            findings.append(
                Finding("MALFORMED-EDGE", f"{src}->{dst}",
                        f"anchors source {src} is not an EngineeringFrame element")
            )
        if dst not in m.function_points:
            findings.append(
                Finding("MALFORMED-EDGE", f"{src}->{dst}",
                        f"anchors target {dst} is not a FunctionPoint element")
            )
    for src, dst in sorted(m.requires):
        if src not in m.features:
            findings.append(
                Finding("MALFORMED-EDGE", f"{src}->{dst}",
                        f"requires source {src} is not a Feature element")
            )
        if dst not in m.function_points:
            findings.append(
                Finding("MALFORMED-EDGE", f"{src}->{dst}",
                        f"requires target {dst} is not a FunctionPoint element")
            )
    return findings


# ===========================================================================
# Allowlist.
# ===========================================================================
def load_allowlist(path: Path) -> set[str]:
    """One entry per line. A `#` starts a comment (full-line or trailing the
    entry — the header mandates a trailing ADR citation), so the stored key is
    the text before the first `#`, stripped."""
    allow: set[str] = set()
    if not path.is_file():
        return allow
    for line in path.read_text(encoding="utf-8").splitlines():
        entry = line.split("#", 1)[0].strip()
        if entry:
            allow.add(entry)
    return allow


def _allowed(finding: Finding, allow: set[str], m: UnderstandingMap) -> bool:
    """A finding is suppressed when its subject (var, edge `src->dst`, or the
    saa.id of either endpoint) appears in the allowlist."""
    if finding.subject in allow:
        return True
    # Edge subjects are `src->dst`; also honour an allowlist entry on either
    # endpoint's var or saa.id so an ADR exception can name the element.
    if "->" in finding.subject:
        src, _, dst = finding.subject.partition("->")
        for var in (src, dst):
            if var in allow:
                return True
            if var in m.features and m.feature_id(var) in allow:
                return True
            if var in m.frames and m.frame_id(var) in allow:
                return True
    else:
        var = finding.subject
        if var in m.frames and m.frame_id(var) in allow:
            return True
        if var in m.features and m.feature_id(var) in allow:
            return True
    return False


# ===========================================================================
# Orchestration.
# ===========================================================================
def _read_merged(root: Path) -> str:
    """Read + concatenate the three map DSL files. Fails closed (ConfigError) if
    a file that EXISTS cannot be read. Absent files contribute empty text."""
    parts: list[str] = []
    for rel in MAP_DSL_RELS:
        p = root / rel
        if not p.exists():
            continue
        try:
            parts.append(p.read_text(encoding="utf-8"))
        except OSError as exc:
            raise ConfigError(f"cannot read {rel}: {exc}") from exc
    return "\n".join(parts)


def evaluate(root: Path) -> tuple[UnderstandingMap, list[Finding]]:
    """Load the merged DSL and run every invariant check."""
    text = _read_merged(root)
    m = build_map(text)
    allow = load_allowlist(root / ALLOWLIST_REL)

    findings: list[Finding] = []
    findings += check_derived_traverse(m)
    findings += check_no_feature_owns_frame(m)
    findings += check_only_module_contains_frame(m)
    findings += check_frame_carries_no_value_axis(m)
    findings += check_well_typed_axes(m)

    findings = [f for f in findings if not _allowed(f, allow, m)]
    return m, findings


def any_map_dsl_exists(root: Path) -> bool:
    return any((root / rel).exists() for rel in MAP_DSL_RELS)


# ===========================================================================
# Changed-file scoping (changed-files-blocking mode).
# ===========================================================================
def _git_changed(root: Path, base: str) -> set[str] | None:
    """Repo-relative paths changed vs `base` (committed + uncommitted + untracked).

    Returns None when git cannot resolve the base ref (caller then treats the
    map as in-scope — the safe superset on a clone without the base)."""
    try:
        ok = subprocess.run(
            ["git", "-C", str(root), "rev-parse", "--verify", base],
            capture_output=True, text=True, check=False,
        )
        if ok.returncode != 0:
            return None
    except (OSError, FileNotFoundError):
        return None

    changed: set[str] = set()
    for cmd in (
        ["git", "-C", str(root), "diff", "--name-only", f"{base}", "HEAD"],
        ["git", "-C", str(root), "diff", "--name-only", "HEAD"],
        ["git", "-C", str(root), "ls-files", "--others", "--exclude-standard"],
    ):
        try:
            out = subprocess.run(cmd, capture_output=True, text=True, check=False)
        except OSError:
            continue
        for ln in out.stdout.splitlines():
            ln = ln.strip()
            if ln:
                changed.add(ln.replace("\\", "/"))
    return changed


def map_in_scope(root: Path, base: str) -> bool:
    """True when any of the three map DSL files changed vs base (or scope unknown)."""
    changed = _git_changed(root, base)
    if changed is None:
        return True  # base unresolved -> full scope (safe superset)
    authoring = {_norm(r) for r in MAP_DSL_RELS}
    return bool(authoring & changed)


# ===========================================================================
# CLI.
# ===========================================================================
def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("--mode", default="advisory", choices=VALID_MODES)
    ap.add_argument("--base", default="origin/main",
                    help="base ref for changed-files-blocking scope (default origin/main, else HEAD)")
    ap.add_argument("--repo", default=None,
                    help="repository root (default: two levels above this script)")
    args = ap.parse_args(argv)

    root = Path(args.repo).resolve() if args.repo else repo_root()
    if not root.is_dir():
        print(f"ai-understanding-map: config error: --repo {root} is not a directory",
              file=sys.stderr)
        return 2

    # Greenfield: none of the three map DSL files exists -> vacuously clean.
    if not any_map_dsl_exists(root):
        print(f"ai-understanding-map [{args.mode}]: no map DSL "
              f"({', '.join(MAP_DSL_RELS)}) present (greenfield) — 0 finding(s)",
              file=sys.stderr)
        return 0

    try:
        m, findings = evaluate(root)
    except ConfigError as exc:
        print(f"ai-understanding-map: config error: {exc}", file=sys.stderr)
        return 2

    # Decide blocking from mode + scope. advisory_only findings never block.
    blockable = [f for f in findings if not f.advisory_only]
    blocking = False
    if args.mode == "full-blocking":
        blocking = bool(blockable)
    elif args.mode == "changed-files-blocking":
        if blockable and map_in_scope(root, args.base):
            blocking = True

    for f in findings:
        if f.advisory_only:
            marker = "advisory"
        else:
            marker = "BLOCKING" if blocking else "advisory"
        print(f"{f.line()}  [{marker}]", file=sys.stderr)

    print(
        f"ai-understanding-map [{args.mode}]: {len(findings)} finding(s) "
        f"({len(blockable)} blockable) over {len(m.features)} Feature(s) / "
        f"{len(m.frames)} Frame(s) / {len(m.function_points)} FunctionPoint(s) / "
        f"{len(m.traverses)} traverse edge(s)",
        file=sys.stderr,
    )

    return 1 if blocking else 0


if __name__ == "__main__":
    sys.exit(main())
