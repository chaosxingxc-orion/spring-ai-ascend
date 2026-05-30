#!/usr/bin/env python3
"""Gate check: layer purity — L2/code detail leaked into L0/L1 architecture prose.

Encodes the adjudicated verdict (the "L0/L1 contains L2/code detail" critique is
TRUE) as an executable, three-mode advisory gate. It reads the machine-readable
owns/forbids policy and the dated grandfather allow-list, scans the L0 + L1
architecture documents for leaked-category trigger patterns, and reports any
LEAKED block that is not redeemed by a still-open temporary-violation row.

Two policy surfaces drive this check (both authored separately; this script is
their executable consumer, it invents no id and no relationship and it never
outranks a generated fact):

  * ``docs/governance/layer-purity-policy.yaml`` — the owns/forbids category
    vocabulary. Fixes the closed category set (defensible D1..D3, leaked
    L1..L8), the layers each category is OWNED / FORBIDDEN at, and the
    authoritative home a leaked category must migrate to. Authority: ADR-0159.
  * ``docs/governance/layer-purity-temporary-violations.yaml`` — the closed,
    dated grandfather list. Each row freezes one known, not-yet-migrated leak in
    an L0/L1 document AND declares a per-entry ``sunset_date``. A leaked block
    whose file is covered by a still-open row of the matching category is
    TOLERATED (reported as a grandfathered advisory, never a finding); a leak in
    no row — or one whose ``sunset_date`` has passed (UTC) — is a finding.

What this check is NOT. It is not the migration itself, not a rule card, not an
enforcer row, and it does not edit any authority surface. It is a READABLE-
INTERPRETATION classifier over layer prose: a mechanical pattern match cannot
reproduce the scan's per-line semantic judgement, so its trigger library is
deliberately specific (drawn from the verdict's concrete trigger phrases) to keep
false positives low; the policy YAML remains the normative owns/forbids source
where the two disagree, and the dated allow-list remains the sole tolerance
gate.

Scope of the scan. Only ``architecture/docs/L0/**/*.md`` and
``architecture/docs/L1/**/*.md`` are scanned — those are the over-layers a leak
can sit in. ``architecture/docs/L2/`` and ``docs/contracts/`` are never scanned:
L2 OWNS every leaked category (it is the migration home), so detail there is
in-layer by construction. The L1 ``_template/`` skeletons are excluded (they are
placeholder scaffolding, not authored content).

Modes (``--mode``):

  advisory                 Scan every L0/L1 document, print findings +
                           grandfathered notes to stderr, and ALWAYS exit 0.
                           This is the soak posture: the check reports but never
                           blocks. Default until the policy promotes to blocking
                           per ADR-0159 §9.
  changed-files-blocking   Scan only the L0/L1 documents that changed relative to
                           a base ref (``--base``, default ``origin/main``). Exit
                           1 if any CHANGED document carries a non-grandfathered
                           leak; pre-existing leaks in untouched documents do not
                           block. This is the ratchet posture: a PR may not ADD a
                           leak, but is not blocked by debt it did not touch.
  full-blocking            Scan every L0/L1 document; exit 1 on any
                           non-grandfathered leak. This is the terminal posture
                           once the corpus is clean and every grandfather row has
                           been retired.

Usage:
    python3 gate/lib/check_layer_purity.py --mode advisory
    python3 gate/lib/check_layer_purity.py --mode changed-files-blocking
    python3 gate/lib/check_layer_purity.py --mode changed-files-blocking --base origin/main
    python3 gate/lib/check_layer_purity.py --mode full-blocking
    python3 gate/lib/check_layer_purity.py --mode full-blocking --repo /path/to/repo

Exit codes:
    0 — passed (always, in advisory mode); or no in-scope findings in a blocking
        mode
    1 — one or more in-scope, non-grandfathered findings in a blocking mode
        (printed to stderr)
    2 — usage / configuration error (bad mode, unreadable/empty policy file, no
        L0/L1 documents discovered, etc.)

Authority: ADR-0159 (Progressive Learning Curve and Authority Lanes) §7 + §9.
"""
from __future__ import annotations

import argparse
import datetime
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

# ---------------------------------------------------------------------------
# Canonical policy-surface + scan-scope locations (repo-relative, forward slash).
# ---------------------------------------------------------------------------
POLICY_REL = "docs/governance/layer-purity-policy.yaml"
VIOLATIONS_REL = "docs/governance/layer-purity-temporary-violations.yaml"

# The over-layers a leak can sit in. Each maps to its scanned document root.
# L2 is intentionally absent — it OWNS every leaked category.
SCANNED_LAYER_ROOTS = {
    "L0": "architecture/docs/L0",
    "L1": "architecture/docs/L1",
}

# Path fragments that are excluded from the scan even under a scanned root:
# the per-module template skeleton carries placeholder prose, not authored
# layer content, so it must not generate leaks.
EXCLUDED_PATH_FRAGMENTS = ("/_template/",)


def repo_root() -> Path:
    """Return the repository root (the directory two levels above this script)."""
    return Path(__file__).resolve().parent.parent.parent


def _rel(path: Path, root: Path) -> str:
    try:
        return str(path.relative_to(root)).replace("\\", "/")
    except ValueError:
        return str(path).replace("\\", "/")


def _utc_today() -> datetime.date:
    """Today's date in UTC.

    The freshness/sunset convention in this repo is UTC: a local +08 date can be
    one day ahead of the CI clock (which runs in UTC), so a sunset compared
    against ``date.today()`` could expire a row a day early on a local run and
    pass on CI. Anchoring on UTC keeps local and CI verdicts identical.
    """
    return datetime.datetime.now(datetime.timezone.utc).date()


# ===========================================================================
# YAML loading. PyYAML is a hard dependency of the gate (Rule 38 fails fast
# without it); a missing parser for a policy surface here is therefore a config
# error (exit 2) — we fail closed rather than pass vacuously without the schema.
# ===========================================================================
def _load_yaml(path: Path) -> tuple[object | None, str]:
    """Load ``path`` as YAML. Returns ``(data, error)``; ``error`` is '' on success."""
    if not path.is_file():
        return None, f"missing file {path}"
    try:
        import yaml  # type: ignore[import-not-found]
    except ImportError:
        return None, (
            "PyYAML is not installed; cannot parse "
            f"{path} (run: pip install -r gate/requirements.txt)"
        )
    try:
        with path.open("r", encoding="utf-8") as fh:
            return yaml.safe_load(fh), ""
    except yaml.YAMLError as exc:  # type: ignore[attr-defined]
        return None, f"{path} failed YAML parse: {exc}"
    except (OSError, ValueError) as exc:
        return None, f"cannot read {path}: {exc}"


# ===========================================================================
# Policy model — the typed projection of the two governance-policy surfaces.
# ===========================================================================
@dataclass
class Category:
    """One owns/forbids category row from layer-purity-policy.yaml."""

    id: str
    kind: str  # "defensible" | "leaked"
    title: str
    forbidden_at: set[str]
    home: str


@dataclass
class Violation:
    """One row from layer-purity-temporary-violations.yaml (a dated grandfather)."""

    id: str
    layer: str
    file: str  # repo-relative, forward slash
    categories: set[str]
    sunset_date: datetime.date | None
    raw_sunset: str

    def is_open(self, today: datetime.date) -> bool:
        """A row tolerates a leak only while its sunset is in the future (inclusive).

        A missing/unparseable sunset is treated as ALREADY-EXPIRED (it cannot
        prove it is still open), so a malformed allow-list row never silently
        suppresses a leak.
        """
        return self.sunset_date is not None and self.sunset_date >= today


@dataclass
class Policy:
    """Typed view over the two layer-purity policy surfaces."""

    categories: dict[str, Category]
    status: str
    violations: list[Violation] = field(default_factory=list)
    violations_status: str = ""
    violations_list_closed: bool = False

    def leaked_categories(self) -> dict[str, Category]:
        return {cid: c for cid, c in self.categories.items() if c.kind == "leaked"}


def _parse_sunset(value: object) -> tuple[datetime.date | None, str]:
    """Parse a sunset_date cell. Returns ``(date_or_None, raw_string)``."""
    raw = "" if value is None else str(value)
    if not raw.strip():
        return None, raw
    try:
        return datetime.date.fromisoformat(raw.strip()), raw
    except ValueError:
        return None, raw


def load_policy(root: Path) -> tuple[Policy | None, list[str]]:
    """Load + validate both policy surfaces. Returns ``(policy, config_errors)``.

    A non-empty ``config_errors`` list means the policy could not be loaded; the
    caller MUST treat that as exit-2 (the schema is unavailable, so no document
    can be judged — failing closed rather than passing vacuously).
    """
    errors: list[str] = []

    policy_doc, perr = _load_yaml(root / POLICY_REL)
    if perr:
        errors.append(f"policy: {perr}")
    violations_doc, verr = _load_yaml(root / VIOLATIONS_REL)
    if verr:
        errors.append(f"temporary-violations: {verr}")
    if errors:
        return None, errors

    if not isinstance(policy_doc, dict):
        errors.append(f"policy: top-level of {POLICY_REL} must be a mapping")
    if not isinstance(violations_doc, dict):
        errors.append(f"temporary-violations: top-level of {VIOLATIONS_REL} must be a mapping")
    if errors:
        return None, errors

    # --- categories[] ------------------------------------------------------
    cat_rows = policy_doc.get("categories")
    if not isinstance(cat_rows, list) or not cat_rows:
        errors.append(f"policy: {POLICY_REL} missing non-empty 'categories' list")
        cat_rows = []
    categories: dict[str, Category] = {}
    for idx, row in enumerate(cat_rows):
        if not isinstance(row, dict):
            errors.append(f"policy: categories[{idx}] must be a mapping")
            continue
        cid = str(row.get("id", "")).strip()
        kind = str(row.get("kind", "")).strip()
        if not cid:
            errors.append(f"policy: categories[{idx}] missing 'id'")
            continue
        if kind not in ("defensible", "leaked"):
            errors.append(
                f"policy: category {cid!r} has kind {kind!r}; expected 'defensible' or 'leaked'"
            )
        forbidden_raw = row.get("forbidden_at", []) or []
        forbidden = {str(x).strip() for x in forbidden_raw} if isinstance(forbidden_raw, list) else set()
        categories[cid] = Category(
            id=cid,
            kind=kind,
            title=str(row.get("title", cid)).strip(),
            forbidden_at=forbidden,
            home=str(row.get("home", "")).strip(),
        )

    status = str(policy_doc.get("status", "")).strip()

    # --- violations[] ------------------------------------------------------
    viol_rows = violations_doc.get("violations")
    # An empty/absent violations list is legitimate (the corpus may be clean):
    # it is NOT a config error. Only a wrong TYPE is.
    if viol_rows is None:
        viol_rows = []
    if not isinstance(viol_rows, list):
        errors.append(f"temporary-violations: {VIOLATIONS_REL} 'violations' must be a list")
        viol_rows = []
    violations: list[Violation] = []
    for idx, row in enumerate(viol_rows):
        if not isinstance(row, dict):
            errors.append(f"temporary-violations: violations[{idx}] must be a mapping")
            continue
        vid = str(row.get("id", f"<row-{idx}>")).strip()
        layer = str(row.get("layer", "")).strip()
        vfile = str(row.get("file", "")).strip().replace("\\", "/")
        # A row may carry a single `category` or a `categories` list.
        cats: set[str] = set()
        if isinstance(row.get("categories"), list):
            cats.update(str(x).strip() for x in row["categories"] if str(x).strip())
        single = row.get("category")
        if single is not None and str(single).strip():
            cats.add(str(single).strip())
        sunset, raw_sunset = _parse_sunset(row.get("sunset_date"))
        # Cross-check each cited category id against the policy vocabulary so a
        # typo in the allow-list cannot silently over-suppress.
        for c in cats:
            if c not in categories:
                errors.append(
                    f"temporary-violations: row {vid!r} cites category {c!r} "
                    f"absent from {POLICY_REL} categories"
                )
        violations.append(
            Violation(
                id=vid,
                layer=layer,
                file=vfile,
                categories=cats,
                sunset_date=sunset,
                raw_sunset=raw_sunset,
            )
        )

    if errors:
        return None, errors

    return (
        Policy(
            categories=categories,
            status=status,
            violations=violations,
            violations_status=str(violations_doc.get("status", "")).strip(),
            violations_list_closed=bool(violations_doc.get("list_closed", False)),
        ),
        [],
    )


# ===========================================================================
# Leaked-category trigger library.
#
# Each leaked category id (L1..L8) maps to a list of (compiled regex, label)
# probes derived from the verdict's concrete trigger phrases (recorded in
# docs/governance/remediation-inventory/layer-purity-scan.md and mirrored in the
# policy `definition` fields). The probes are deliberately specific — they target
# the mechanism (SQL keyword, HTTP status code, @Order value, sequenceDiagram
# fence, traceparent header, JVM-shape sentence) rather than the topic — so that
# a one-line SPI boundary identity (D1) or a package-decomposition sentence (D2)
# does not trip them. A line that matches several probes is reported once, under
# the first category in policy order, with the matching label.
#
# The category ids MUST exist in layer-purity-policy.yaml; load_policy() rejects
# any probe category absent from the policy vocabulary (fail-closed on drift).
# ===========================================================================
TRIGGERS: dict[str, list[tuple[re.Pattern[str], str]]] = {
    # L1 — Method call chains: inter-method dispatch sequences "a -> b", named
    # method-to-method chains, "invoked via", "atomic CAS via <method>(...)".
    "L1-method-call-chain": [
        (re.compile(r"\b[A-Za-z_]\w*\([^)]*\)\s*->\s*[A-Za-z_]\w*\s*\("), "method-call arrow chain a(...) -> b("),
        (re.compile(r"\bupdateIfNotTerminal\s*\("), "named CAS dispatch updateIfNotTerminal(...)"),
        (re.compile(r"\bwithStatus\s*\([^)]*\)\s*MUST\s+invoke\b", re.IGNORECASE), "withStatus(...) MUST invoke ... call chain"),
        (re.compile(r"\b(?:invoked|invoke[ds]?)\s+via\b.*\b[A-Za-z_]\w*\s*\(", re.IGNORECASE), "'invoked via <method>(...)' dispatch"),
        (re.compile(r"\b[A-Za-z_]\w*\.[A-Za-z_]\w*\s*\([^)]*\)\s*(?:->|=>)\s*[A-Za-z_]"), "Type.method(...) -> ... dispatch"),
    ],
    # L2 — Runtime sequences: sequence diagrams, race winner/loser narratives,
    # CAS-ordering ("CAS WINS"/"LOSES", "post-CAS re-read", "no-op").
    "L2-runtime-sequence": [
        (re.compile(r"\bsequenceDiagram\b"), "mermaid sequenceDiagram fence"),
        (re.compile(r"\bCAS\s+(?:WINS|LOSES|WIN|LOSE)\b", re.IGNORECASE), "CAS winner/loser race narrative"),
        (re.compile(r"\bpost-CAS\s+re-?read\b", re.IGNORECASE), "post-CAS re-read race ordering"),
        (re.compile(r"\bcancel-race\b", re.IGNORECASE), "cancel-race runtime ordering"),
        (re.compile(r"\(\s*CAS\s+no-?op\s*\)", re.IGNORECASE), "(CAS no-op) transition ordering"),
        (re.compile(r"\bwinner\s*/\s*loser\b", re.IGNORECASE), "winner/loser sequence"),
    ],
    # L3 — SQL / RLS / GUC / persistence: SET LOCAL, ON CONFLICT, WHERE ...
    # NOT IN, RLS policy wiring, migration-file names, CHECK-constraint enums.
    "L3-sql-rls-persistence": [
        (re.compile(r"\bSET\s+LOCAL\b", re.IGNORECASE), "SET LOCAL GUC statement"),
        (re.compile(r"\bON\s+CONFLICT\b", re.IGNORECASE), "SQL ON CONFLICT clause"),
        (re.compile(r"\bWHERE\s+status\s+NOT\s+IN\b", re.IGNORECASE), "WHERE status NOT IN (...) CAS predicate"),
        (re.compile(r"\bDO\s+NOTHING\b", re.IGNORECASE), "SQL DO NOTHING upsert"),
        (re.compile(r"\bCHECK\s+constraint\b", re.IGNORECASE), "CHECK-constraint enum detail"),
        (re.compile(r"\bRLS\s+(?:policy|policies|migration|layer|coverage|SELECT)\b", re.IGNORECASE), "RLS policy/migration wiring"),
        (re.compile(r"\benable\s+(?:Postgres\s+)?RLS\b", re.IGNORECASE), "enable Postgres RLS"),
        (re.compile(r"\bapp\.tenant_id\b"), "app.tenant_id GUC key"),
        (re.compile(r"\bV\??\d*_*_[A-Za-z0-9_]*\.sql\b"), "Flyway migration-file name (V__*.sql)"),
        (re.compile(r"\bidempotency_dedup\b"), "named idempotency_dedup table"),
    ],
    # L4 — HTTP status / route-verb / header behaviour: bare 2xx/4xx/5xx status
    # codes, "POST ... not DELETE", header parse/emit, route x verb tables.
    "L4-http-status-route-verb": [
        (re.compile(r"(?<![\w./-])(?:200|201|400|403|404|409|422|500)(?![\w%./-])"), "concrete HTTP status code"),
        (re.compile(r"\b(?:not|never)\s+DELETE\b", re.IGNORECASE), "route-verb semantics (POST not DELETE)"),
        (re.compile(r"\bPOST\s+/v\d+/"), "concrete route x verb (POST /vN/...)"),
        (re.compile(r"\bGET\s+/v\d+/"), "concrete route x verb (GET /vN/...)"),
        (re.compile(r"\b(?:Idempotency-Key|X-Tenant-Id|traceparent|traceresponse)\b"), "named HTTP header parse/emit"),
        (re.compile(r"\billegal_state_transition\b|\bidempotency_conflict\b|\bidempotency_body_drift\b|\btenant_mismatch\b"), "wire error-code token"),
    ],
    # L5 — Filter ordering: numeric @Order/order values, chain-position rules,
    # tie-break-by-Class.getName(), LIFO unwind.
    "L5-filter-ordering": [
        (re.compile(r"@Order\b"), "@Order annotation ordering"),
        (re.compile(r"\border\s+\d+\b", re.IGNORECASE), "numeric filter order value"),
        (re.compile(r"\bClass\.getName\(\)"), "Class.getName() tie-break"),
        (re.compile(r"\bLIFO\s+unwind\b", re.IGNORECASE), "LIFO unwind ordering"),
        (re.compile(r"\bfilter\s+chain\s+order\b", re.IGNORECASE), "filter chain order rule"),
        (re.compile(r"\bregistration\s+order\b", re.IGNORECASE), "registration-order dispatch rule"),
    ],
    # L6 — Wire formats: OTLP/HTTP, attribute namespaces gen_ai.*/langfuse.*,
    # buffer sizes + overflow strategy, sampling rates, traceparent grammar.
    "L6-wire-format": [
        (re.compile(r"\bOTLP/HTTP\b", re.IGNORECASE), "OTLP/HTTP wire encoding"),
        (re.compile(r"\bgen_ai\.\*|\blangfuse\.\*"), "telemetry attribute namespace"),
        (re.compile(r"\bDROP_OLDEST\b"), "buffer overflow strategy DROP_OLDEST"),
        (re.compile(r"\bsampling\b.*\b\d+\s*%", re.IGNORECASE), "numeric sampling rate"),
        (re.compile(r"\b\d+\s*events?\b.*\boverflow\b", re.IGNORECASE), "bounded buffer size + overflow"),
        (re.compile(r"\b00-<trace_id>-<span_id>-\d+\b"), "traceparent/traceresponse wire grammar"),
        (re.compile(r"\bW3C\s+version-\d+\s+traceparent\b", re.IGNORECASE), "W3C traceparent header grammar"),
    ],
    # L7 — Method signatures: parameter lists / return types / JVM shapes,
    # enumerated SPI method lists, interface-vs-record classification, forbidden
    # method names, canonical field names. (Distinct from D1: a one-line SPI
    # IDENTITY is allowed; a parameter/return inventory is not. Distinct from D3:
    # an "X is forbidden (Rule R-...)" structural CONSTRAINT is a defensible
    # enforcer citation; only a forbidden METHOD NAME — `name()` ... forbidden —
    # is the L7 leak, so the probe requires the method-call token, not bare
    # "is forbidden".)
    "L7-method-signature": [
        (re.compile(r"\b[A-Za-z_]\w*\([^)]*\)\s*->\s*[A-Z]\w+"), "method signature args -> ReturnType"),
        (re.compile(r"\bclassified\s+as\s+(?:an?\s+)?[`'\"]?(?:interface|record)\b", re.IGNORECASE), "interface-vs-record classification"),
        (re.compile(r"\b[A-Za-z_]\w*\(\)\s+(?:is|are)\s+forbidden\b", re.IGNORECASE), "forbidden method-name rule (name() is forbidden)"),
        (re.compile(r"\bcanonical\s+field\s+name\b", re.IGNORECASE), "canonical field-name detail"),
        (re.compile(r"\bsubset\s+of\s+the\s+canonical\s+interface\b", re.IGNORECASE), "enumerated SPI method-subset rule"),
        # A parenthesised parameter list with >=2 JVM-typed args, or a single
        # JVM-typed arg paired with a return arrow — a parameter/return inventory,
        # not a one-line identity.
        (re.compile(r"\(\s*(?:UUID|Instant|String|List<[^>]+>|Map<[^>]+>|StateDelta|TaskMetadata|InjectedContext|ChildFailurePolicy|SessionContext)\b[^)]*,[^)]*\)"), "JVM-typed parameter inventory (multi-arg)"),
    ],
    # L8 — Test-class inventories: named test classes carrying their ASSERTED
    # runtime behaviour, embedded in layer prose — a test catalogue. NOT an
    # ArchUnit enforcer citation: an architecture-test (`*ArchTest` / `*PurityTest`)
    # or a test named inside an "enforced by ..." / "ArchUnit (...)" clause is a
    # D3-defensible MECHANISM citation and is filtered out post-match (see
    # _is_d3_enforcer_citation). The positive signal is a test class enumerated
    # as inventory: a markdown table row, a bullet-list entry, or a test name
    # paired with a behaviour verb.
    "L8-test-class-inventory": [
        # Markdown table row whose cells name an integration/unit/spec test.
        (re.compile(r"^\s*\|.*`[A-Z]\w+(?:IT|Test|Spec)`"), "test-inventory table row (named test in a table)"),
        # Bullet-list entry naming a test class (FQN or simple name).
        (re.compile(r"^\s*[-*]\s+`?(?:[a-z][\w.]*\.)?[A-Z]\w+(?:IT|Test|Spec)`?\s*$"), "test-inventory bullet (enumerated test class)"),
        # A test class paired with a behaviour clause (em-dash/colon + verb).
        (re.compile(r"\b[A-Z]\w+(?:IT|Test|Spec)\b[^\n]{0,40}?(?:[-—:]\s*)(?:asserts?|verif(?:y|ies)|checks?|arms?|covers?|ensures?|exercises?|proves?)\b", re.IGNORECASE), "named test + asserted-behaviour clause"),
        (re.compile(r"\b@(?:Test|SpringBootTest|DataR2dbcTest|WebFluxTest)\b"), "test annotation in prose"),
    ],
}

# A line whose only test-ish token is a D3-defensible ArchUnit-enforcer citation
# MUST NOT be flagged L8: the verdict keeps "citing an ArchUnit enforcer as the
# mechanism" at L0/L1. Two signals mark such a citation:
#   * the token is an ArchUnit architecture-test name (suffix ArchTest / ArchUnitTest
#     / PurityTest) — these ARE enforcers, not behaviour catalogues; or
#   * the test token sits inside an explicit mechanism clause ("enforced by ...",
#     "ArchUnit `...`", "(enforcer E<n>)").
_D3_ARCHUNIT_TOKEN_RE = re.compile(r"\b[A-Z]\w*(?:ArchTest|ArchUnitTest|PurityTest)\b")
_D3_MECHANISM_CLAUSE_RE = re.compile(r"enforced by|ArchUnit|\(enforcer\s+E\d+\)", re.IGNORECASE)
# A non-ArchUnit behaviour test (e.g. RunHttpContractIT, S2cCallbackRoundTripIT)
# whose presence means the line is a genuine inventory even if it also cites a
# mechanism — so the D3 exemption applies only when NO behaviour-test token is
# present alongside the ArchUnit/mechanism citation.
_NON_ARCHUNIT_TEST_TOKEN_RE = re.compile(r"\b[A-Z]\w+(?:IT|Test|Spec)\b")


def _is_d3_enforcer_citation(line: str) -> bool:
    """Return True when an L8 match on ``line`` is a D3-defensible enforcer citation.

    The exemption holds only when every test token on the line is an ArchUnit
    architecture-test (or the line is purely a mechanism citation) AND no
    behaviour-test (``*IT`` / non-Arch ``*Test`` / ``*Spec``) is enumerated. This
    keeps a real integration-test inventory a leak even when it sits beside an
    "enforced by" clause, while sparing pure ArchUnit-rule citations.
    """
    tokens = _NON_ARCHUNIT_TEST_TOKEN_RE.findall(line)
    if not tokens:
        # No behaviour-test token at all (e.g. only a `@Test` annotation match or
        # an ArchTest handled below) — defer to the mechanism/ArchUnit signals.
        return bool(_D3_ARCHUNIT_TOKEN_RE.search(line) or _D3_MECHANISM_CLAUSE_RE.search(line))
    # Every enumerated test token is an ArchUnit architecture-test -> D3.
    archunit_tokens = set(_D3_ARCHUNIT_TOKEN_RE.findall(line))
    if archunit_tokens and all(t in archunit_tokens for t in tokens):
        return True
    return False


# ===========================================================================
# Document discovery + changed-file scoping.
# ===========================================================================
def discover_documents(root: Path) -> list[Path]:
    """Return every scanned L0/L1 markdown document, sorted, template excluded."""
    out: list[Path] = []
    for layer_root in SCANNED_LAYER_ROOTS.values():
        base = root / layer_root
        if not base.is_dir():
            continue
        for md in base.rglob("*.md"):
            rel = _rel(md, root)
            if any(frag in "/" + rel for frag in EXCLUDED_PATH_FRAGMENTS):
                continue
            out.append(md)
    return sorted(out, key=lambda p: _rel(p, root))


def layer_of(rel_path: str) -> str | None:
    """Return the layer id (L0/L1) a repo-relative path belongs to, or None."""
    norm = rel_path.replace("\\", "/")
    for layer, layer_root in SCANNED_LAYER_ROOTS.items():
        if norm == layer_root or norm.startswith(layer_root + "/"):
            return layer
    return None


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


def changed_documents(root: Path, base: str) -> tuple[list[Path] | None, str]:
    """Return the scanned L0/L1 documents changed vs ``base``, or ``(None, reason)``.

    Scope = (committed diff merge-base...HEAD) UNION (uncommitted tracked
    changes) UNION (untracked files under the scanned roots). A ``None`` return
    means git could not resolve the base ref (shallow clone without it, or not a
    git repo); the caller fails closed in a blocking mode.
    """
    rc, _ = _git_run(["rev-parse", "--git-dir"], root)
    if rc != 0:
        return None, "not a git repository (cannot scope changed files)"

    diff_spec = base
    rc, mb = _git_run(["merge-base", base, "HEAD"], root)
    if rc == 0 and mb.strip():
        diff_spec = mb.strip()
    else:
        rc_verify, _ = _git_run(["rev-parse", "--verify", "--quiet", base], root)
        if rc_verify != 0:
            return None, f"base ref {base!r} is not resolvable in this clone"

    names: set[str] = set()
    rc, out = _git_run(["diff", "--name-only", diff_spec, "HEAD"], root)
    if rc == 0:
        names.update(line.strip() for line in out.splitlines() if line.strip())
    rc, out = _git_run(["diff", "--name-only", "HEAD"], root)
    if rc == 0:
        names.update(line.strip() for line in out.splitlines() if line.strip())
    for layer_root in SCANNED_LAYER_ROOTS.values():
        rc, out = _git_run(["ls-files", "--others", "--exclude-standard", layer_root], root)
        if rc == 0:
            names.update(line.strip() for line in out.splitlines() if line.strip())

    selected: list[Path] = []
    for name in sorted(names):
        norm = name.replace("\\", "/")
        if layer_of(norm) is None:
            continue
        if not norm.endswith(".md"):
            continue
        if any(frag in "/" + norm for frag in EXCLUDED_PATH_FRAGMENTS):
            continue
        candidate = root / norm
        if candidate.is_file():
            selected.append(candidate)
    return selected, ""


# ===========================================================================
# Scanning.
# ===========================================================================
@dataclass
class Leak:
    """One detected leaked-category hit in a layer document."""

    rel_path: str
    layer: str
    line_no: int
    category_id: str
    label: str
    excerpt: str


def _strip_inline_code_noise(line: str) -> str:
    """Return the line as-is.

    We intentionally do NOT strip code spans: the verdict treats inlined
    mechanism (a backticked ``SET LOCAL`` statement, a ``sequenceDiagram`` fence,
    a backticked ``@Order``) as exactly the leak. Kept as a single hook so the
    policy on code spans lives in one place if it ever needs to change.
    """
    return line


def scan_document(path: Path, root: Path, leaked: dict[str, Category]) -> list[Leak]:
    """Scan one L0/L1 document line-by-line for leaked-category triggers.

    A line is reported at most once — under the first leaked category (in policy
    order) whose probe matches — to avoid double-counting a block that mixes
    several leaked categories. The per-category allow-list suppression is applied
    later by the caller (so the raw scan stays policy-pure and testable).
    """
    rel = _rel(path, root)
    layer = layer_of(rel) or "?"
    try:
        text = path.read_text(encoding="utf-8")
    except OSError:
        # An unreadable in-scope document is itself a problem; surface it as a
        # synthetic finding rather than skipping silently.
        return [
            Leak(
                rel_path=rel,
                layer=layer,
                line_no=0,
                category_id="<unreadable>",
                label="document could not be read",
                excerpt="",
            )
        ]

    leaks: list[Leak] = []
    for line_no, raw_line in enumerate(text.splitlines(), start=1):
        line = _strip_inline_code_noise(raw_line)
        if not line.strip():
            continue
        for category_id in leaked:  # dict preserves policy order
            matched_label = None
            for pattern, label in TRIGGERS.get(category_id, ()):  # type: ignore[arg-type]
                if pattern.search(line):
                    matched_label = label
                    break
            if matched_label is None:
                continue
            # D3-defensible carve-out: an L8 match that is really an ArchUnit /
            # enforcer MECHANISM citation is in-layer at L0/L1 — skip it and let a
            # genuinely-leaked lower-priority category (if any) still report.
            if category_id == "L8-test-class-inventory" and _is_d3_enforcer_citation(line):
                continue
            excerpt = line.strip()
            if len(excerpt) > 160:
                excerpt = excerpt[:157] + "..."
            leaks.append(
                Leak(
                    rel_path=rel,
                    layer=layer,
                    line_no=line_no,
                    category_id=category_id,
                    label=matched_label,
                    excerpt=excerpt,
                )
            )
            break  # one (leaked, non-exempt) category per line
    return leaks


def suppressing_row(leak: Leak, policy: Policy, today: datetime.date) -> Violation | None:
    """Return the open grandfather row that tolerates ``leak``, or None.

    A row suppresses a leak when ALL hold:
      * the row's ``file`` equals the leak's document (repo-relative path), and
      * the row's ``layer`` equals the leak's layer (defence in depth — the file
        match already implies the layer), and
      * the row cites the leak's leaked category, and
      * the row is still open (sunset_date is today or later, UTC).

    Expired rows do not suppress (the migration deadline has passed); unknown or
    malformed sunsets are treated as expired by ``Violation.is_open``.
    """
    for row in policy.violations:
        if row.file != leak.rel_path:
            continue
        if row.layer and row.layer != leak.layer:
            continue
        if leak.category_id not in row.categories:
            continue
        if row.is_open(today):
            return row
    return None


# ===========================================================================
# CLI.
# ===========================================================================
def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Layer purity — detect L2/code detail leaked into L0/L1 "
        "architecture prose, honouring the dated grandfather allow-list "
        "(ADR-0159).",
    )
    parser.add_argument(
        "--mode",
        choices=("advisory", "changed-files-blocking", "full-blocking"),
        default="advisory",
        help="advisory (report, never block); changed-files-blocking (block only "
        "on changed L0/L1 docs); full-blocking (block on any). Default: advisory.",
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
        print(f"layer-purity: --repo {root} is not a directory", file=sys.stderr)
        return 2

    policy, config_errors = load_policy(root)
    if policy is None:
        for err in config_errors:
            print(f"layer-purity config error: {err}", file=sys.stderr)
        return 2

    leaked = policy.leaked_categories()
    if not leaked:
        print(
            f"layer-purity config error: {POLICY_REL} defines no leaked categories",
            file=sys.stderr,
        )
        return 2
    # Every leaked category MUST have a trigger probe set, else the gate is
    # silently blind to it (non-vacuity guard for an auto-discovering rule).
    missing_probes = [cid for cid in leaked if not TRIGGERS.get(cid)]
    if missing_probes:
        print(
            "layer-purity config error: no trigger probes defined for leaked "
            f"categ/ies {sorted(missing_probes)} (gate would be blind to them)",
            file=sys.stderr,
        )
        return 2

    all_documents = discover_documents(root)
    # Non-vacuity guard: this rule auto-discovers its inputs by globbing the L0/L1
    # roots. If it discovers zero documents the scan is vacuous and a path/format
    # drift would pass silently — fail closed.
    if not all_documents:
        print(
            "layer-purity config error: discovered zero L0/L1 documents under "
            f"{sorted(SCANNED_LAYER_ROOTS.values())} (path drift? scan would be vacuous)",
            file=sys.stderr,
        )
        return 2

    if args.mode == "changed-files-blocking":
        scoped, reason = changed_documents(root, args.base)
        if scoped is None:
            print(
                f"layer-purity: changed-file scoping unavailable ({reason}); "
                "falling back to full-corpus scan",
                file=sys.stderr,
            )
            documents = all_documents
        else:
            documents = scoped
    else:
        documents = all_documents

    today = _utc_today()

    findings: list[Leak] = []
    grandfathered: list[tuple[Leak, Violation]] = []
    for doc in documents:
        for leak in scan_document(doc, root, leaked):
            if leak.category_id == "<unreadable>":
                findings.append(leak)
                continue
            row = suppressing_row(leak, policy, today)
            if row is not None:
                grandfathered.append((leak, row))
            else:
                findings.append(leak)

    # --- report ------------------------------------------------------------
    for leak in findings:
        cat = policy.categories.get(leak.category_id)
        home = f" -> migrate to {cat.home}" if cat and cat.home else ""
        title = cat.title if cat else leak.category_id
        print(
            f"layer-purity {leak.rel_path}:{leak.line_no} [{leak.layer}] "
            f"{leak.category_id} ({title}): {leak.label}{home}\n"
            f"    > {leak.excerpt}",
            file=sys.stderr,
        )

    for leak, row in grandfathered:
        print(
            f"layer-purity GRANDFATHERED {leak.rel_path}:{leak.line_no} [{leak.layer}] "
            f"{leak.category_id}: tolerated by {row.id} (sunset {row.raw_sunset})",
            file=sys.stderr,
        )

    # --- expired-row advisory ---------------------------------------------
    # An allow-list row whose sunset has passed AND that no longer matches any
    # live leak is dead weight; surface it so the list can be pruned. This never
    # affects the exit code (it is housekeeping), but it keeps the closed,
    # dated grandfather list honest.
    matched_row_ids = {row.id for _, row in grandfathered}
    expired_unused = [
        row
        for row in policy.violations
        if row.sunset_date is not None
        and row.sunset_date < today
        and row.id not in matched_row_ids
    ]
    for row in sorted(expired_unused, key=lambda r: r.id):
        print(
            f"layer-purity NOTE: grandfather row {row.id} expired "
            f"({row.raw_sunset}) and matched no live leak; remove it from "
            f"{VIOLATIONS_REL}",
            file=sys.stderr,
        )

    scanned_n = len(documents)
    corpus_n = len(all_documents)
    summary = (
        f"layer-purity [{args.mode}] (policy status: {policy.status or 'unknown'}): "
        f"scanned {scanned_n} L0/L1 document(s)"
        + (f" (corpus total {corpus_n})" if scanned_n != corpus_n else "")
        + f"; {len(findings)} finding(s), {len(grandfathered)} grandfathered"
    )
    print(summary, file=sys.stderr)

    if args.mode == "advisory":
        return 0
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
