#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 78 — dfx_spi_packages_match_module_metadata. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 78 — dfx_spi_packages_match_module_metadata (enforcer E111)
#
# For every module with kind ∈ {platform, domain}, the dfx yaml at
# docs/dfx/<module>.yaml MUST declare a top-level `spi_packages:` block
# whose entries are an order-insensitive set match with the module's
# module-metadata.yaml#spi_packages (placeholder entries excluded — see
# Rule 75). Catches the 2026-05-18 root cause where dfx yamls omitted,
# mis-nested (under observability), or under-declared spi packages
# relative to module-metadata.yaml.
#
# Placeholder filter: lines whose inline comment contains BOTH "placeholder"
# AND "ADR-NNNN" are excluded from both sides of the comparison so deferred
# SPI work declared symmetrically (or asymmetrically) in metadata only does
# not force a noisy dfx declaration before the real SPI lands.
# ---------------------------------------------------------------------------
_r78_fail=0
# Perf fix (2026-05-23): replaced per-metadata × per-line grep/sed/tr loop
# (~8 modules × 2 files × ~5 lines × ~5 forks = ~400 forks, ~13s) with a
# single python pass. Same placeholder filter (`# ... placeholder ... ADR-NNNN`).
_r78_violations="$("${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, glob
from pathlib import Path

DFX_REQUIRED_KINDS = {'platform', 'domain'}

def extract_real_spi(path: str) -> list[str]:
    try: lines = Path(path).read_text(encoding='utf-8', errors='replace').splitlines()
    except OSError: return []
    in_block = False; out: list[str] = []
    placeholder_re = re.compile(r'placeholder')
    adr_re = re.compile(r'ADR-\d{4}')
    for line in lines:
        if re.match(r'^spi_packages:', line):
            in_block = True; continue
        if not in_block: continue
        if re.match(r'^[a-zA-Z_]', line):
            in_block = False; continue
        m = re.match(r'^\s*-\s+', line)
        if not m: continue
        # Placeholder filter: comment containing both 'placeholder' AND 'ADR-NNNN'.
        if '#' in line and placeholder_re.search(line) and adr_re.search(line):
            continue
        # Strip leading dash + token-and-onwards.
        v = re.sub(r'^\s*-\s*', '', line)
        v = re.sub(r'[\s#].*$', '', v)
        v = v.strip('"\'')
        if v: out.append(v)
    return sorted(set(out))

metas = sorted(set(glob.glob('*/module-metadata.yaml') + glob.glob('*/*/module-metadata.yaml')))
for meta in metas:
    try: text = Path(meta).read_text(encoding='utf-8', errors='replace')
    except OSError: continue
    km = re.search(r'^\s*kind:\s*([A-Za-z_]+)', text, re.MULTILINE)
    if not km or km.group(1) not in DFX_REQUIRED_KINDS: continue
    mm = re.search(r'^\s*module:\s*([A-Za-z0-9_-]+)', text, re.MULTILINE)
    if not mm: continue
    mod = mm.group(1)
    dfx = f'docs/dfx/{mod}.yaml'
    if not os.path.isfile(dfx): continue  # Rule 35 reports missing-dfx
    meta_spi = extract_real_spi(meta)
    dfx_spi = extract_real_spi(dfx)
    if not meta_spi: continue
    if not dfx_spi:
        print(f"MISSING\t{meta}\t{dfx}\t\t")
        continue
    if meta_spi != dfx_spi:
        print(f"MISMATCH\t{meta}\t{dfx}\t{','.join(meta_spi)}\t{','.join(dfx_spi)}")
PYEOF
)"
if [[ -n "$_r78_violations" ]]; then
  while IFS=$'\t' read -r _r78_kind _r78_meta _r78_dfx _r78_meta_one _r78_dfx_one; do
    [[ -z "$_r78_kind" ]] && continue
    case "$_r78_kind" in
      MISSING)
        fail_rule "dfx_spi_packages_match_module_metadata" "$_r78_dfx missing top-level 'spi_packages:' block (must mirror non-placeholder entries of $_r78_meta) — Rule 78 / E111"
        ;;
      MISMATCH)
        fail_rule "dfx_spi_packages_match_module_metadata" "$_r78_meta non-placeholder spi_packages={${_r78_meta_one}} but $_r78_dfx declares {${_r78_dfx_one}} — Rule 78 / E111"
        ;;
    esac
    _r78_fail=1
  done <<< "$_r78_violations"
fi
if [[ $_r78_fail -eq 0 ]]; then pass_rule "dfx_spi_packages_match_module_metadata"; fi

# ===========================================================================
# 2026-05-18 beyond-SDD review response wave -- Rule 79
# Authority: docs/governance/rules/rule-79.md
#            + D:/.claude/plans/d-chao-workspace-spring-ai-ascend-docs-shimmering-milner.md
# Operationalises the "Telemetry-First Debugging" remediation proposal from
# docs/logs/reviews/spring-ai-ascend-beyond-sdd-en.md by requiring an executable
# debug-sequence runbook to exist on disk, cited by the rule card, with the
# canonical title string present (so the file cannot drift to a different
# topic while still passing the gate by name alone).
# ===========================================================================

