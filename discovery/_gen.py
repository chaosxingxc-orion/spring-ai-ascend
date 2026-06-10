#!/usr/bin/env python3
"""Phase A Wave 3 discovery-index generator.

Generates discovery/adr-index.md, rule-index.md, contract-index.md as
Tier-2 progressive disclosure surfaces. Read-only over source corpora;
output is byte-deterministic per current corpus state.

This script is generation-only — it does NOT modify source files.
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
DISCOVERY = REPO / "discovery"

YAML_FRONT_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)
ADR_ID_FROM_FILENAME = re.compile(r"^(\d{4})")


def read(p: Path) -> str:
    try:
        return p.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return p.read_text(encoding="utf-8", errors="replace")


def yaml_scalar(text: str, key: str) -> str | None:
    """Pull a top-level scalar from YAML or YAML-frontmatter text. Handles
    quoted, unquoted, and block-scalar (>|) values on a single returned line.
    Strips trailing inline `#` comments on plain (non-quoted) values.
    """
    m = re.search(rf"(?m)^{re.escape(key)}\s*:\s*(.*)$", text)
    if not m:
        return None
    val = m.group(1).strip()
    # strip trailing inline yaml comment from PLAIN values (before quote-strip)
    if not (val.startswith('"') or val.startswith("'")):
        c = re.search(r"\s+#\s", val)
        if c:
            val = val[: c.start()].rstrip()
    # strip surrounding quotes
    if (val.startswith('"') and val.endswith('"')) or (val.startswith("'") and val.endswith("'")):
        val = val[1:-1]
    if val in ("|", ">", ">|", "|-", ">-"):
        # block scalar — read next indented line
        lines = text.splitlines()
        for i, ln in enumerate(lines):
            if re.match(rf"^{re.escape(key)}\s*:", ln):
                if i + 1 < len(lines):
                    return lines[i + 1].strip()
        return None
    return val


def first_md_h1_title(text: str) -> str | None:
    """For markdown ADRs lacking frontmatter — pull `# NNNN. Title`."""
    m = re.search(r"^#\s+(\d+\.\s*)?(.+?)\s*$", text, re.MULTILINE)
    if m:
        return m.group(2).strip()
    return None


def first_md_status(text: str) -> str | None:
    m = re.search(r"\*\*Status:\*\*\s*([A-Za-z _\-]+)", text)
    if m:
        return m.group(1).strip().lower()
    m = re.search(r"^##\s*Status\s*\n+\s*([A-Za-z _\-]+)", text, re.MULTILINE)
    if m:
        return m.group(1).strip().lower()
    return None


# ----------------------------- ADR index ------------------------------------
def collect_adrs():
    """Returns dict: adr_id (int) -> (id_str, rel_path, title, status, product_claim).

    Precedence when the same ADR id appears in multiple source folders:
      docs/adr/*.yaml > docs/adr/*.md > docs/adr/locked/*.md > architecture/decisions/*.{md,yaml}
    The earliest source wins; later duplicates are silently dropped.
    """
    out: dict[int, tuple] = {}

    def add(num: int, idstr: str, rel: str, title: str, status: str, pc: str):
        if num in out:
            return
        out[num] = (idstr, rel, title, status, pc)

    # 1) docs/adr/*.yaml (rich frontmatter)
    for p in sorted((REPO / "docs/adr").glob("*.yaml")):
        m = ADR_ID_FROM_FILENAME.match(p.name)
        if not m:
            continue
        num = int(m.group(1))
        text = read(p)
        idstr = yaml_scalar(text, "id") or f"ADR-{num:04d}"
        title = yaml_scalar(text, "title") or p.stem
        status = yaml_scalar(text, "status") or "unknown"
        pc = yaml_scalar(text, "product_claim") or "placeholder"
        add(num, idstr, p.relative_to(REPO).as_posix(), title, status, pc)

    # 2) docs/adr/*.md
    for p in sorted((REPO / "docs/adr").glob("*.md")):
        m = ADR_ID_FROM_FILENAME.match(p.name)
        if not m:
            continue
        num = int(m.group(1))
        if num in out:
            continue
        text = read(p)
        idstr = f"ADR-{num:04d}"
        title = first_md_h1_title(text) or p.stem
        status = first_md_status(text) or "unknown"
        # md ADRs rarely have product_claim — check frontmatter then fallback
        fm = YAML_FRONT_RE.match(text)
        pc = "placeholder"
        if fm:
            pc = yaml_scalar(fm.group(1), "product_claim") or "placeholder"
        add(num, idstr, p.relative_to(REPO).as_posix(), title, status, pc)

    # 3) docs/adr/locked/*.md
    locked_dir = REPO / "docs/adr/locked"
    if locked_dir.is_dir():
        for p in sorted(locked_dir.glob("*.md")):
            m = ADR_ID_FROM_FILENAME.match(p.name)
            if not m:
                continue
            num = int(m.group(1))
            if num in out:
                continue
            text = read(p)
            idstr = f"ADR-{num:04d}"
            title = first_md_h1_title(text) or p.stem
            status = first_md_status(text) or "locked"
            add(num, idstr, p.relative_to(REPO).as_posix(), title, status, "placeholder")

    # 4) architecture/decisions/
    arch_dir = REPO / "architecture/decisions"
    if arch_dir.is_dir():
        for p in sorted(list(arch_dir.glob("*.yaml")) + list(arch_dir.glob("*.md"))):
            m = ADR_ID_FROM_FILENAME.match(p.name)
            if not m:
                continue
            num = int(m.group(1))
            if num in out:
                continue
            text = read(p)
            if p.suffix == ".yaml":
                idstr = yaml_scalar(text, "id") or f"ADR-{num:04d}"
                title = yaml_scalar(text, "title") or p.stem
                status = yaml_scalar(text, "status") or "unknown"
                pc = yaml_scalar(text, "product_claim") or "placeholder"
            else:
                idstr = f"ADR-{num:04d}"
                title = first_md_h1_title(text) or p.stem
                status = first_md_status(text) or "unknown"
                pc = "placeholder"
            add(num, idstr, p.relative_to(REPO).as_posix(), title, status, pc)

    return out


def shorten_path(rel: str) -> str:
    """Strip the conventional `docs/adr/` prefix so links are terser. Locked
    and architecture/decisions paths keep their distinguishing prefix."""
    if rel.startswith("docs/adr/locked/"):
        return rel[len("docs/adr/"):]  # → locked/NNNN.md
    if rel.startswith("docs/adr/"):
        return rel[len("docs/adr/"):]
    if rel.startswith("architecture/decisions/"):
        return "arch/" + rel[len("architecture/decisions/"):]
    return rel


def shorten_title(t: str, cap: int = 70) -> str:
    t = re.sub(r"\s+", " ", t).strip()
    # drop common boilerplate suffixes
    t = re.sub(r"\s+—\s+rc\d+.*$", "", t)
    if len(t) > cap:
        t = t[: cap - 1].rstrip() + "…"
    return t


def write_adr_index():
    adrs = collect_adrs()
    lines = []
    lines.append("---")
    lines.append("index_id: DISCOVERY-ADR-INDEX")
    lines.append("governance_infra: true")
    lines.append("generated_at: 2026-05-28")
    lines.append('generator: "spring-ai-ascend Phase A Wave 3"')
    lines.append('purpose: "Tier-2 progressive disclosure index — auto-loaded with summary lines; full bodies loaded on demand by phase-contract skills."')
    lines.append("---")
    lines.append("")
    lines.append("# ADR Discovery Index")
    lines.append("")
    lines.append(f"schema_version: 1 | last_updated: 2026-05-28 | count: {len(adrs)}")
    lines.append("")
    lines.append("Tier-2 progressive-disclosure index over the ADR corpus. Each row: id link, title, `status:`. Sources: `docs/adr/*.yaml`, `docs/adr/*.md`, `docs/adr/locked/*.md`, `architecture/decisions/*` (precedence in that order on id collision).")
    lines.append("")
    lines.append("## Index")
    lines.append("")
    for num in sorted(adrs.keys()):
        idstr, rel, title, status, _ = adrs[num]
        title_s = shorten_title(title, cap=80)
        status_s = re.sub(r"\s+", " ", status).strip()
        lines.append(f"- [{idstr}]({rel}) — {title_s} — {status_s}")
    lines.append("")
    (DISCOVERY / "adr-index.md").write_text("\n".join(lines), encoding="utf-8")
    return len(adrs)


# ----------------------------- Rule index -----------------------------------
def rule_sort_key(rule_id: str):
    """Sort like D-1, D-2, ..., G-1, G-1.1, G-2, ..., M-1, M-2, R-A, R-B, ..."""
    m = re.match(r"^([A-Z]+)-([A-Za-z0-9.]+)$", rule_id)
    if not m:
        return (rule_id, )
    ns, suf = m.group(1), m.group(2)
    # numeric? letter?
    parts = []
    for chunk in suf.split("."):
        if chunk.isdigit():
            parts.append((0, int(chunk), ""))
        else:
            # letter-suffixed like "1a" or pure letter "A"
            num_m = re.match(r"^(\d+)([A-Za-z]*)$", chunk)
            if num_m:
                parts.append((0, int(num_m.group(1)), num_m.group(2)))
            else:
                # purely alphabetic suffix like 'A' in 'R-A'
                parts.append((1, 0, chunk))
    return (ns, tuple(parts))


def collect_rules():
    """Returns list of dicts: {id, path, title, status, product_claim}."""
    out = []
    rules_dir = REPO / "docs/governance/rules"
    for p in sorted(rules_dir.glob("rule-*.md")):
        text = read(p)
        fm_m = YAML_FRONT_RE.match(text)
        if fm_m:
            fm = fm_m.group(1)
        else:
            fm = text[:2000]
        rid = yaml_scalar(fm, "rule_id")
        if not rid:
            # filename-derived
            mm = re.match(r"^rule-([A-Za-z0-9.\-]+)\.md$", p.name)
            rid = mm.group(1) if mm else p.stem
        title = yaml_scalar(fm, "title") or p.stem
        status = yaml_scalar(fm, "status") or "unknown"
        out.append({
            "id": rid,
            "path": p.relative_to(REPO).as_posix(),
            "title": title,
            "status": status,
        })
    out.sort(key=lambda r: rule_sort_key(r["id"]))
    return out


def write_rule_index():
    rules = collect_rules()
    lines = []
    lines.append("---")
    lines.append("index_id: DISCOVERY-RULE-INDEX")
    lines.append("governance_infra: true")
    lines.append("generated_at: 2026-05-28")
    lines.append('generator: "spring-ai-ascend Phase A Wave 3"')
    lines.append('purpose: "Tier-2 progressive disclosure index — auto-loaded with summary lines; full bodies loaded on demand by phase-contract skills."')
    lines.append("---")
    lines.append("")
    lines.append("# Rule Discovery Index")
    lines.append("")
    lines.append(f"- **schema_version**: 1")
    lines.append(f"- **last_updated**: 2026-05-28")
    lines.append(f"- **count**: {len(rules)}")
    lines.append("")
    lines.append("## Usage")
    lines.append("")
    lines.append("This file is a Tier-2 progressive-disclosure index over the governance rule cards under `docs/governance/rules/`. Each line names one rule by id (D-/G-/R-/M- namespace), its canonical card path, and the rule's current `status:` value.")
    lines.append("")
    lines.append("Load this index to find rules by id, topic, or status without scanning every card. Each rule's full body (motivation, details, enforcers, exit criteria) lives behind the linked card and is loaded on demand by the `/design-mode`, `/impl-mode`, `/verify-mode`, `/commit-mode`, and `/review-mode` phase-contract skills declared in `CLAUDE.md`. Sort order: namespace (D, G, M, R) then numeric/letter suffix.")
    lines.append("")
    lines.append("## Index")
    lines.append("")
    for r in rules:
        title = re.sub(r"\s+", " ", r["title"]).strip()
        status = re.sub(r"\s+", " ", r["status"]).strip()
        lines.append(f"- [Rule {r['id']}]({r['path']}) — {title} — {status}")
    lines.append("")
    (DISCOVERY / "rule-index.md").write_text("\n".join(lines), encoding="utf-8")
    return len(rules)


# --------------------------- Contract index ---------------------------------
def _strip_comment_paragraph(lines: list[str], start: int) -> tuple[str, int]:
    """Starting at lines[start] which begins a comment paragraph (line begins
    with '#' and has content), gather contiguous '# ...' lines until a blank
    line (`#` alone or empty) or non-comment terminates the paragraph.

    Returns (joined-paragraph-text, end-index-exclusive).
    """
    buf: list[str] = []
    i = start
    while i < len(lines):
        ln = lines[i]
        if not ln.startswith("#"):
            break
        stripped = ln.lstrip("#").strip()
        if not stripped:
            i += 1
            break
        buf.append(stripped)
        i += 1
    return " ".join(buf), i


def contract_purpose(text: str) -> str:
    """Extract a one-line purpose from contract yaml.

    Strategy:
      1. yaml top-level `purpose:` (block scalar), then plain `title:` / `description:`
      2. OpenAPI `info.title:`
      3. Leading comment block — Purpose paragraph
      4. Leading comment block — first content paragraph that isn't Authority/Wave
    """
    # 1a. block scalar purpose
    bm = re.search(r"^purpose\s*:\s*[|>][-+]?\s*\n((?:[ \t]+.*\n)+)", text, re.MULTILINE)
    if bm:
        body = bm.group(1)
        joined = " ".join(ln.strip() for ln in body.splitlines() if ln.strip())
        if joined:
            return joined
    # 1b. plain inline scalars
    for key in ("purpose", "title", "description"):
        val = yaml_scalar(text, key)
        if val and val not in ("|", ">", ">|", "|-", ">-") and len(val) > 3:
            return val
    # 2. OpenAPI info.title
    m = re.search(r"^info\s*:\s*\n(?:[ \t]+.*\n)*?[ \t]+title\s*:\s*(.+)$", text, re.MULTILINE)
    if m:
        return m.group(1).strip().strip('"').strip("'")
    # 3 / 4. Leading comment-paragraph scan
    lines = text.splitlines()
    # If file opens with a YAML frontmatter block (--- ... ---), skip past it
    i = 0
    if i < len(lines) and lines[i].strip() == "---":
        j = i + 1
        while j < len(lines) and lines[j].strip() != "---":
            j += 1
        if j < len(lines):
            i = j + 1  # past the closing ---
    # collect comment paragraphs from the next block (stop at first non-comment, non-empty)
    paragraphs: list[str] = []
    while i < len(lines):
        ln = lines[i]
        if ln.startswith("#"):
            stripped = ln.lstrip("#").strip()
            if not stripped:
                i += 1
                continue
            para, i = _strip_comment_paragraph(lines, i)
            if para:
                paragraphs.append(para)
        elif ln.strip() == "":
            i += 1
        else:
            break
    # First, look for an explicit "Purpose: ..." paragraph
    for para in paragraphs:
        pm = re.match(r"Purpose\s*:\s*(.+)", para, re.IGNORECASE)
        if pm:
            return pm.group(1).strip().rstrip(".")
    # Otherwise return the first content paragraph that isn't a header tag
    for para in paragraphs:
        low = para.lower()
        if low.startswith(("authority", "wave", "schema", "driver review", "schema:")):
            continue
        if len(para) < 8:
            continue
        return para.rstrip(".")
    return "(no purpose declared)"


def contract_status(text: str) -> str:
    val = yaml_scalar(text, "status")
    if val:
        return val
    runtime = yaml_scalar(text, "runtime_enforced")
    if runtime is not None:
        return "runtime_enforced" if runtime.strip().lower() == "true" else "design_only"
    return "unknown"


def collect_contracts():
    out = []
    for p in sorted((REPO / "docs/contracts").glob("*.yaml")):
        text = read(p)
        purpose = contract_purpose(text)
        status = contract_status(text)
        out.append({
            "name": p.name,
            "path": p.relative_to(REPO).as_posix(),
            "purpose": purpose,
            "status": status,
        })
    return out


def write_contract_index():
    contracts = collect_contracts()
    lines = []
    lines.append("---")
    lines.append("index_id: DISCOVERY-CONTRACT-INDEX")
    lines.append("governance_infra: true")
    lines.append("generated_at: 2026-05-28")
    lines.append('generator: "spring-ai-ascend Phase A Wave 3"')
    lines.append('purpose: "Tier-2 progressive disclosure index — auto-loaded with summary lines; full bodies loaded on demand by phase-contract skills."')
    lines.append("---")
    lines.append("")
    lines.append("# Contract Discovery Index")
    lines.append("")
    lines.append(f"- **schema_version**: 1")
    lines.append(f"- **last_updated**: 2026-05-28")
    lines.append(f"- **count**: {len(contracts)}")
    lines.append("")
    lines.append("## Usage")
    lines.append("")
    lines.append("This file is a Tier-2 progressive-disclosure index over the runtime / SPI / protocol contract schemas under `docs/contracts/`. Each line names one schema file, its one-line purpose, and its current `status:` (or `runtime_enforced:` derivative).")
    lines.append("")
    lines.append("Load this index to locate contract schemas by name, purpose, or status. The full schema body lives behind the linked file. Catalog cross-reference: `docs/contracts/contract-catalog.md`. Each contract's authority ADR is named in its leading comment block per Rule M-2 sub-clause .b.")
    lines.append("")
    lines.append("## Index")
    lines.append("")
    for c in contracts:
        purpose = re.sub(r"\s+", " ", c["purpose"]).strip()
        # cap purpose at ~200 chars to keep lines tight
        if len(purpose) > 240:
            purpose = purpose[:237].rstrip() + "..."
        status = re.sub(r"\s+", " ", c["status"]).strip()
        lines.append(f"- [{c['name']}]({c['path']}) — {purpose} — {status}")
    lines.append("")
    (DISCOVERY / "contract-index.md").write_text("\n".join(lines), encoding="utf-8")
    return len(contracts)


def main():
    DISCOVERY.mkdir(exist_ok=True)
    n_adr = write_adr_index()
    n_rule = write_rule_index()
    n_contract = write_contract_index()
    print(f"adr-index.md      — {n_adr} entries")
    print(f"rule-index.md     — {n_rule} entries")
    print(f"contract-index.md — {n_contract} entries")
    # report sizes
    for name in ("adr-index.md", "rule-index.md", "contract-index.md"):
        p = DISCOVERY / name
        print(f"  {name:24s} {p.stat().st_size:8d} bytes")


if __name__ == "__main__":
    main()
