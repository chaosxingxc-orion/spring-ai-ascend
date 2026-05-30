#!/usr/bin/env python3
"""Gate check: L2-detail-sink — implementation detail that leaked into L0/L1 prose.

Authority: docs/governance/rules/rule-G-27.md (kernel Rule G-27), enforcer E195,
gate Rule 145 (advisory). Encodes the adjudicated layer-purity VERDICT: an
L0 / L1 architecture document is a STRUCTURAL boundary surface and MUST NOT
carry runtime L2 implementation detail. The detail belongs in
architecture/docs/L2/ (when those land) and the contract surfaces under
docs/contracts/ + the generated facts under architecture/facts/generated/.

This is the *document-prose* analog of Rule G-28's per-ADR altitude control:
G-28 rejects an L2-altitude `decision_type` in an L0/L1 normalized ADR view;
this rule reports L2-altitude *prose* (SQL/RLS/GUC, HTTP status+verb behaviour,
on-wire formats, method signatures + call chains, filter ordering, concrete
test-class inventories) in an L0/L1 ARCHITECTURE / view markdown.

VERDICT split (the keep-list is NOT reported — only the leak-list is):

  DEFENSIBLE (stays at L0/L1, never flagged):
    * naming a public SPI *type* as a boundary identity (a noun: `Orchestrator`,
      `Checkpointer`, `ResilienceContract`);
    * development-view package decomposition (`com.huawei.ascend..`,
      `<module>/src/main/java/..`);
    * citing an ArchUnit / enforcer mechanism (`enforcer E160`, `Rule R-C.e`,
      `*ArchTest`).

  LEAKED (belongs at L2 / contracts, reported here):
    * SQL / RLS / GUC / persistence DDL + semantics;
    * HTTP status code + route-verb + header runtime behaviour;
    * on-wire formats (OTLP, attribute namespaces, envelope field shapes);
    * Java method signatures + call chains (`A.b() -> C.d()`, CAS arg lists);
    * filter / interceptor ordering;
    * concrete test-class inventories used as evidence in L0/L1 prose.

Scope: architecture/docs/L0/*.md and architecture/docs/L1/**/*.md, EXCLUDING the
`_template/` scaffolds (placeholder docs, not authority). Fenced code blocks are
scanned too — a `SET LOCAL app.tenant_id` inside a fenced "L2 Boundary Contract"
zone is still leaked L2 detail per the VERDICT (a sanctioned *forward-declaration
heading* does not launder the detail it contains). A single finding can be
suppressed in place with an HTML comment on the same line or the line directly
above it:

    <!-- l2-detail-sink-allow: <reason> -->

(mirrors the `secret-allowlist:` inline opt-out convention used by Rule 28c).

Ratchet (mode):
  advisory               -- report every finding to stderr, always exit 0.
  changed-files-blocking -- exit 1 only if a finding lands on a file passed via
                            --changed (a PR may not add/worsen a leak on a file
                            it touches); other findings stay advisory.
  blocking               -- exit 1 if any finding exists (terminal rung, once the
                            L0/L1 corpus has been swept clean).

Usage:
    python3 gate/lib/check_l2_detail_sink.py                       # advisory
    python3 gate/lib/check_l2_detail_sink.py --mode blocking
    python3 gate/lib/check_l2_detail_sink.py --changed architecture/docs/L0/ARCHITECTURE.md \
        --mode changed-files-blocking
    python3 gate/lib/check_l2_detail_sink.py --repo /path/to/repo

Exit codes:
    0 -- mode satisfied (advisory always; *-blocking when no blocking finding)
    1 -- a blocking finding under the active mode, OR a fatal config error
"""
from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path

VALID_MODES = ("advisory", "changed-files-blocking", "blocking")

# In-line suppression token. A finding on a line is dropped when this token
# appears on that line or on the line immediately above it.
ALLOW_TOKEN = "l2-detail-sink-allow:"


def repo_root() -> Path:
    """Repository root — two directories above this script (gate/lib/..)."""
    return Path(__file__).resolve().parent.parent.parent


# ---------------------------------------------------------------------------
# Leak-signal corpus.
#
# Each family is (id, human_label, [compiled patterns]). Patterns are anchored
# to runtime-behaviour shapes, NOT to bare type/package names, so the
# VERDICT keep-list (SPI nouns, package paths, enforcer citations) is not
# reported. Patterns are intentionally conservative: a false negative (a leak
# that slips through) is preferable to a false positive on defensible prose,
# because this is an advisory ratchet that tightens over successive sweeps.
# ---------------------------------------------------------------------------
def _compile(*patterns: str) -> list[re.Pattern[str]]:
    return [re.compile(p, re.IGNORECASE) for p in patterns]


LEAK_FAMILIES: list[tuple[str, str, list[re.Pattern[str]]]] = [
    (
        "sql_persistence",
        "SQL / RLS / GUC / persistence DDL or semantics (belongs at L2 + Flyway)",
        _compile(
            r"\bCREATE\s+TABLE\b",
            r"\bALTER\s+TABLE\b",
            r"\bSET\s+LOCAL\b",
            # Postgres GUC tenant keys (the RLS session variable).
            r"\bapp\.(current_)?tenant(_id)?\b",
            # RLS policy DDL / enablement.
            r"\b(ENABLE\s+ROW\s+LEVEL\s+SECURITY|CREATE\s+POLICY|ROW\s+LEVEL\s+SECURITY)\b",
            # Flyway migration filenames (V<n>__name.sql / V?__name.sql).
            r"\bV\?*\d*__[A-Za-z0-9_]+\.sql\b",
            # SQL DML predicate on tenant_id (a query shape, not a constraint name).
            r"\bSELECT\b[^.\n]{0,80}\bWHERE\b[^.\n]{0,40}\btenant_id\b",
        ),
    ),
    (
        "http_runtime",
        "HTTP status + route-verb + header runtime behaviour (belongs at L2 + OpenAPI)",
        _compile(
            # A verb of producing a status code + a 3-digit code (runtime behaviour).
            r"\b(returns?|respond(s|ing)?|repl(y|ies|ying)|emit(s|ting)?|send(s|ing)?)\b[^.\n]{0,40}\bHTTP\s*[1-5]\d\d\b",
            r"\b(returns?|respond(s|ing)?|repl(y|ies|ying)|status(\s+code)?(\s+is)?)\b[^.\n]{0,30}\b[1-5]\d\d\s+(OK|Created|Accepted|No\s+Content|Bad\s+Request|Unauthorized|Forbidden|Not\s+Found|Conflict|Unprocessable|Too\s+Many\s+Requests|Internal\s+Server\s+Error)\b",
            # "within 200ms returns 202" timing+status runtime SLA prose.
            r"\b[1-5]\d\d\b[^.\n]{0,25}\bwithin\b[^.\n]{0,15}\bms\b",
            # Header-rewrite runtime semantics at the edge.
            r"\b(replace|overwrite|strip|rewrite)s?\b[^.\n]{0,25}\b(X-[A-Za-z-]+|header)\b",
        ),
    ),
    (
        "wire_format",
        "On-wire format / attribute namespace / envelope field shape (belongs at L2 + AsyncAPI/contracts)",
        _compile(
            r"\bOTLP/?(HTTP|gRPC)?\b",
            # Telemetry attribute namespaces as wire-level keys.
            r"\b(gen_ai|langfuse|otel|opentelemetry)\.[A-Za-z_.*]+",
            # W3C trace header on the wire.
            r"\btraceparent\b",
            # JSON wire envelope field-level shape callouts.
            r"\bwire\s+(format|shape|envelope)\b",
        ),
    ),
    (
        "method_signature",
        "Java method signature / call chain / CAS arg list (belongs at L2 + code facts)",
        _compile(
            # Method-call arrow chain: `foo() -> bar()` or `Foo.bar() → Baz.qux()`.
            r"\b[A-Za-z_][A-Za-z0-9_]*\s*\([^)\n]*\)\s*(->|→)\s*[A-Za-z_][A-Za-z0-9_]*\s*\(",
            # Compare-and-set runtime primitive with an arg list.
            r"\bcompareAndSet\s*\(",
            r"\b(expected|witness)\s*,\s*(update|next|new)\s*\)",
            # Atomic CAS phrased as method-level (the agent-service Run CAS leak).
            r"\bCAS\b[^.\n]{0,30}\b(fromStatus|expectedStatus|update)\b",
        ),
    ),
    (
        "filter_ordering",
        "Filter / interceptor ordering (belongs at L2 + code facts)",
        _compile(
            r"\bfilter\s+(order|ordering|chain\s+order|position)\b",
            r"\b@Order\s*\(",
            r"\b[A-Za-z][A-Za-z0-9]*Filter\b[^.\n]{0,30}\b(runs|executes|ordered)\b[^.\n]{0,15}\b(before|after)\b",
            r"\bFilterChain\b[^.\n]{0,25}\b(order|position|before|after)\b",
        ),
    ),
    (
        "test_inventory",
        "Concrete test-class inventory used as L0/L1 evidence (belongs at L2 + test facts)",
        _compile(
            # Three or more concrete test-class names listed inline.
            r"(\b[A-Z][A-Za-z0-9]*(Test|IT)\b[^.\n]{0,8}[,;)][^.\n]{0,8}){2,}\b[A-Z][A-Za-z0-9]*(Test|IT)\b",
        ),
    ),
]

# Defensible-content guards: a candidate line that matches one of these is a
# false-positive risk from the keep-list and is dropped *only* when the sole
# evidence on the line is a keep-list shape. Applied per-pattern below.
ENFORCER_CITATION_RE = re.compile(r"\b(enforcer\s+E\d+|Rule\s+[A-Z]-[\w.]+|[A-Z][A-Za-z0-9]*ArchTest)\b")
PACKAGE_PATH_RE = re.compile(r"\bcom\.huawei\.ascend\b|/src/main/java/")


@dataclass(frozen=True)
class Finding:
    path: str          # repo-relative POSIX path
    line_no: int       # 1-based
    family: str
    label: str
    excerpt: str       # trimmed matching line


def _iter_target_files(root: Path) -> list[Path]:
    """L0 + L1 markdown authority docs, excluding _template scaffolds."""
    docs = root / "architecture" / "docs"
    out: list[Path] = []
    for level in ("L0", "L1"):
        base = docs / level
        if not base.is_dir():
            continue
        for md in sorted(base.rglob("*.md")):
            # _template/ docs are scaffolds, not authority — never scanned.
            if "_template" in md.relative_to(root).parts:
                continue
            out.append(md)
    return out


def _line_is_suppressed(lines: list[str], idx: int) -> bool:
    """True when an allow-token sits on this line or the line directly above."""
    if ALLOW_TOKEN in lines[idx]:
        return True
    if idx > 0 and ALLOW_TOKEN in lines[idx - 1]:
        return True
    return False


def _excerpt(line: str, limit: int = 160) -> str:
    trimmed = line.strip()
    if len(trimmed) > limit:
        return trimmed[: limit - 1] + "…"
    return trimmed


def scan_file(root: Path, path: Path) -> list[Finding]:
    """Return all (un-suppressed) leak findings for one markdown file."""
    try:
        text = path.read_text(encoding="utf-8")
    except OSError:
        return []
    rel = path.relative_to(root).as_posix()
    lines = text.splitlines()
    findings: list[Finding] = []
    for idx, line in enumerate(lines):
        # Markdown links and reference paths frequently embed contract/spi file
        # names; a leak claim must be in *prose*, so skip pure link-definition
        # lines (those whose only content is a `[..](..)` link or a bare path).
        stripped = line.strip()
        if not stripped:
            continue
        if _line_is_suppressed(lines, idx):
            continue
        for family, label, patterns in LEAK_FAMILIES:
            matched = any(p.search(line) for p in patterns)
            if not matched:
                continue
            # Keep-list guard: if the line's only structural signal is an
            # enforcer citation or a package path AND nothing else in the
            # leak corpus is present beyond that token, treat it as
            # defensible. We re-test the leak match after blanking the
            # keep-list tokens; if the leak no longer matches, it was a
            # citation/package false-positive.
            blanked = PACKAGE_PATH_RE.sub(" ", ENFORCER_CITATION_RE.sub(" ", line))
            if not any(p.search(blanked) for p in patterns):
                continue
            findings.append(
                Finding(
                    path=rel,
                    line_no=idx + 1,
                    family=family,
                    label=label,
                    excerpt=_excerpt(stripped),
                )
            )
            break  # one finding per line is enough to flag it
    return findings


def _normalize_changed(root: Path, changed: list[str]) -> set[str]:
    """Normalize --changed args to repo-relative POSIX paths for comparison."""
    out: set[str] = set()
    for raw in changed:
        raw = raw.strip()
        if not raw:
            continue
        p = Path(raw)
        try:
            if p.is_absolute():
                out.add(p.resolve().relative_to(root).as_posix())
            else:
                out.add((root / p).resolve().relative_to(root).as_posix())
        except ValueError:
            # Path outside the repo — keep the raw form so an exact string
            # match can still work if the caller passed a repo-relative path.
            out.add(Path(raw).as_posix())
    return out


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="L2-detail-sink — L2 implementation detail leaked into L0/L1 prose (Rule G-27 / E195)"
    )
    parser.add_argument(
        "--mode",
        default="advisory",
        choices=VALID_MODES,
        help="Ratchet rung: advisory (default), changed-files-blocking, or blocking.",
    )
    parser.add_argument(
        "--changed",
        action="append",
        default=[],
        help="A changed file (repeatable). Only consulted in changed-files-blocking mode.",
    )
    parser.add_argument(
        "--repo",
        default=None,
        help="Repository root. Defaults to the script-derived root.",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    root = Path(args.repo).resolve() if args.repo else repo_root()
    if not root.is_dir():
        print(f"ERROR: --repo {root} is not a directory", file=sys.stderr)
        return 1

    targets = _iter_target_files(root)
    all_findings: list[Finding] = []
    for path in targets:
        all_findings.extend(scan_file(root, path))

    # Emit a deterministic, grep-friendly report to stderr.
    for f in all_findings:
        print(
            f"L2-DETAIL-SINK {f.path}:{f.line_no} [{f.family}] {f.label} :: {f.excerpt}",
            file=sys.stderr,
        )

    # Family-level summary line (consumed by the gate's advisory grep on
    # 'finding(s)'); printed even at zero so the gate can confirm the helper ran.
    by_family: dict[str, int] = {}
    for f in all_findings:
        by_family[f.family] = by_family.get(f.family, 0) + 1
    if by_family:
        breakdown = ", ".join(f"{fam}={n}" for fam, n in sorted(by_family.items()))
        summary = (
            f"{len(all_findings)} L2-detail-sink finding(s) across "
            f"{len({f.path for f in all_findings})} L0/L1 doc(s): {breakdown}"
        )
    else:
        summary = "0 L2-detail-sink finding(s): L0/L1 prose is altitude-clean"
    print(summary, file=sys.stderr)

    # Mode-dependent exit.
    if args.mode == "advisory":
        return 0
    if args.mode == "blocking":
        return 1 if all_findings else 0
    # changed-files-blocking: block only on a finding in a changed file.
    changed = _normalize_changed(root, args.changed)
    blocking = [f for f in all_findings if f.path in changed]
    if blocking:
        print(
            f"BLOCKING: {len(blocking)} L2-detail-sink finding(s) on changed file(s); "
            "migrate the implementation detail to architecture/docs/L2/ + the contract surface",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
