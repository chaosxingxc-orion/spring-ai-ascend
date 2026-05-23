#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 28k — javadoc_enforcer_citation_semantic_check. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 28k — javadoc_enforcer_citation_semantic_check (post-review fix
# plan F / P1-2, enforcer E33+ semantic widening).
#
# Phase 7 post-release review surfaced two test-class Javadocs citing the
# WRONG enforcer ID (S2cCallbackRoundTripIT cited #E83 but is actually E82;
# EngineRegistryBootValidationIT cited #E81 but is actually E84). Rule 28j
# checks `artifact: path#anchor` resolves; it does NOT cross-check that a
# test file citing `enforcers.yaml#E<n>` in its Javadoc actually corresponds
# to E<n>'s declared `artifact:` field.
#
# This rule scans *Test.java and *IT.java under agent-service/src/test/java
# and agent-service/src/test/java for Javadoc citations of the form
# `enforcers.yaml#E<n>` and asserts each cited E-row's `artifact:` field's
# file path (anchor stripped, path normalised) matches the source file
# path. Mis-citation is a Rule 25 truth violation.
# ---------------------------------------------------------------------------
_r28k_fail=0
# PR-Opt-rc22: load pre-parsed enforcers TSV into an associative array.
# Replaces the per-citation `awk` pass over the full enforcers.yaml (which
# was ~9-20s per gate run). The TSV is built once by gate/lib/scan_cache.sh
# as _SCAN_ENFORCERS_TSV with fields: e_id \t artifact_path \t kind.
declare -A _r28k_art_by_eid
if [[ -n "${_SCAN_ENFORCERS_TSV:-}" ]]; then
  while IFS=$'\t' read -r _r28k_eid_k _r28k_art_v _r28k_kind_v; do
    [[ -n "$_r28k_eid_k" ]] && _r28k_art_by_eid["$_r28k_eid_k"]="$_r28k_art_v"
  done <<< "$_SCAN_ENFORCERS_TSV"
fi

if [[ -f "$_efile" ]]; then
  # Perf fix (2026-05-23): the original per-file loop forked grep twice +
  # sed once per test file (~hundreds × 3 forks = thousands). On WSL/mnt/d
  # that was ~51s per gate. Replaced with a single python pass that reads
  # each in-scope file once and consults the pre-parsed _SCAN_ENFORCERS_TSV
  # (or falls back to parsing enforcers.yaml directly when the cache is
  # disabled). Same semantics: at-least-one-match required.
  _r28k_violations=$(GATE_R28K_EFILE="$_efile" "${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, sys
from pathlib import Path

efile = os.environ['GATE_R28K_EFILE']
tsv = os.environ.get('_SCAN_ENFORCERS_TSV', '')

# Build {eid -> artifact_path} map. Prefer the pre-parsed TSV; fall back
# to a one-shot awk-equivalent over enforcers.yaml.
art_by_eid: dict[str, str] = {}
if tsv:
    for row in tsv.splitlines():
        parts = row.split('\t')
        if len(parts) >= 2 and parts[0]:
            art_by_eid[parts[0]] = parts[1]
else:
    cur_id = None
    for line in Path(efile).read_text(encoding='utf-8', errors='replace').splitlines():
        m = re.match(r'^- id: (E\d+)$', line)
        if m:
            cur_id = m.group(1)
            continue
        if cur_id:
            m = re.match(r'^\s+artifact:\s*(.+)$', line)
            if m:
                p = m.group(1).split('#', 1)[0].strip()
                art_by_eid[cur_id] = p
                cur_id = None  # done with this row's artifact
            elif line.startswith('- id:'):
                cur_id = None

# Walk both test trees (the original double-listed agent-service/src/test/java
# for typo-tolerance; we deduplicate via a set).
roots = {'agent-service/src/test/java'}
test_files: list[str] = []
for root in roots:
    if not os.path.isdir(root):
        continue
    for dirpath, _, files in os.walk(root):
        for fn in files:
            if fn.endswith('Test.java') or fn.endswith('IT.java'):
                test_files.append(os.path.join(dirpath, fn))

strict_re = re.compile(r'enforcers\.yaml#E\d+')
eid_re = re.compile(r'#E(\d+)')
viol = []
for src in sorted(test_files):
    try:
        txt = Path(src).read_text(encoding='utf-8', errors='replace')
    except OSError:
        continue
    if not strict_re.search(txt):
        continue
    eids = sorted({m.group(0)[1:] for m in eid_re.finditer(txt)})
    if not eids:
        continue
    src_norm = src.removeprefix('./')
    any_match = False
    missing_eids = []
    collected = []
    for eid in eids:
        art = art_by_eid.get(eid, '')
        if not art:
            missing_eids.append(eid)
            continue
        art_norm = art.removeprefix('./')
        collected.append(f'{eid}:{art_norm}')
        if src_norm == art_norm:
            any_match = True
    for me in missing_eids:
        viol.append(f"MISSING\t{src}\t{me}\t")
    if not any_match and not missing_eids:
        viol.append(f"NOMATCH\t{src}\t\t{' '.join(collected)}")

for line in viol:
    print(line)
PYEOF
)
  if [[ -n "$_r28k_violations" ]]; then
    while IFS=$'\t' read -r _r28k_kind _r28k_src _r28k_eid _r28k_collected; do
      [[ -z "$_r28k_kind" ]] && continue
      case "$_r28k_kind" in
        MISSING)
          fail_rule "javadoc_enforcer_citation_semantic_check" "$_r28k_src cites enforcers.yaml#$_r28k_eid but no such row in $_efile (Rule 28k / post-review plan F)"
          ;;
        NOMATCH)
          fail_rule "javadoc_enforcer_citation_semantic_check" "$_r28k_src cites enforcers.yaml#E<n> rows but NONE of their artifact: paths match this file. Cited: $_r28k_collected. Per Rule 28k / post-review plan F."
          ;;
      esac
      _r28k_fail=1
    done <<< "$_r28k_violations"
  fi
fi
if [[ $_r28k_fail -eq 0 ]]; then pass_rule "javadoc_enforcer_citation_semantic_check"; fi

# ---------------------------------------------------------------------------
