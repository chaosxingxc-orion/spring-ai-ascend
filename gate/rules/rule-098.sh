#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 98 — broad_corpus_deleted_module_name_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 98 — broad_corpus_deleted_module_name_truth (enforcer E137)
#
# Closes rc10 I-ε family: Rule 94 explicitly exempts docs/contracts/openapi-v1.yaml
# ("separate update plan"), all test fixtures ("pinned contract snapshots"), and
# narrowly scans only ARCHITECTURE.md + rule cards + test Javadocs. Deleted-module
# name leaks in ops/helm/**/*.yaml, docs/contracts/openapi-v1.yaml,
# **/module-metadata.yaml description fields survived rc9's prevention wave.
# Rule 98 widens the file-discovery scope using the SAME word-boundary regex
# and ±3-line marker exemption as Rule 94 — closing the Rule 94 implementation
# /kernel-claim gap where the kernel said "every active .md, .yaml, *.java
# file" but the implementation scanned a tiny subset.
# ---------------------------------------------------------------------------
_r98_fail=0
# Rule 98 reuses Rule 94's marker vocabulary (Wave 2 externalisation).
_r98_marker_vocab="gate/active-corpus-name-exemption-markers.txt"
if [[ ! -f "$_r98_marker_vocab" ]]; then
  fail_rule "broad_corpus_deleted_module_name_truth" "$_r98_marker_vocab missing -- Rule 98 / E137 (Wave 2 vocabulary externalisation)"
  _r98_fail=1
fi
_r98_markers="$(grep -vE '^[[:space:]]*(#|$)' "$_r98_marker_vocab" 2>/dev/null | tr '\n' '|' | sed 's/|$//')"
_r98_violations=""
while IFS= read -r _r98_file; do
  [[ -z "$_r98_file" ]] && continue
  # Rule 98 only scans ops/, docs/contracts/, **/module-metadata.yaml; the docs/logs/
  # and docs/archive/ partitions are NEVER reached by the find pipeline below, so no
  # per-file exemption needed beyond build-artefact paths (already excluded at find time).
  case "$_r98_file" in
    docs/archive/*|docs/logs/*) continue ;;
  esac
  _r98_hits=$(awk -v markers="$_r98_markers" '
    BEGIN {
      ap_re  = "(^|[^a-zA-Z0-9_-])agent-platform([^a-zA-Z0-9_-]|$)"
      ar_re  = "(^|[^a-zA-Z0-9_-])agent-runtime([^a-zA-Z0-9_-]|$)"
      arc_re = "(^|[^a-zA-Z0-9_-])agent-runtime-core([^a-zA-Z0-9_-]|$)"
    }
    { lines[NR] = $0 }
    END {
      in_code = 0
      for (i = 1; i <= NR; i++) {
        line = lines[i]
        if (line ~ /^[[:space:]]*```/) { in_code = 1 - in_code; continue }
        if (in_code) continue
        # rc11 widening (rc10 P1-2): YAML comment lines are NOT exempted — sidecar-mem0.yml
        # carried "(port 8001 avoids collision with agent-platform on 8080 / ...)" in a
        # comment that rc10 missed. The marker check below still allows historical-marked
        # comments to pass.
        # rc13 widening (ADR-0088): agent-runtime-core joins the deleted-module set.
        if (line ~ ap_re || (line ~ ar_re && line !~ arc_re) || line ~ arc_re) {
          lo = i - 3; if (lo < 1) lo = 1
          hi = i + 3; if (hi > NR) hi = NR
          window = ""
          for (j = lo; j <= hi; j++) window = window " " lines[j]
          if (window !~ markers) print i ":" line
        }
      }
    }
  ' "$_r98_file" 2>/dev/null || true)
  if [[ -n "$_r98_hits" ]]; then
    while IFS= read -r _r98_hit; do
      _r98_violations="${_r98_violations}${_r98_file}:${_r98_hit}\n"
    done <<< "$_r98_hits"
  fi
done < <(
  # rc10 widening: surfaces Rule 94 explicitly omitted but where deleted-module-name leaks were found.
  # rc11 widening (per ADR-0085): adds ops/**/*.md (operational runbooks) per rc10 post-corrective P1-2.
  {
    find ops -type f \( -name '*.yaml' -o -name '*.yml' -o -name '*.tpl' -o -name '*.md' \) 2>/dev/null | sed 's|^\./||'
    find docs/contracts -maxdepth 1 -type f -name '*.yaml' 2>/dev/null | sed 's|^\./||'
    find . -maxdepth 3 -type f -name 'module-metadata.yaml' -not -path './target/*' -not -path './*/target/*' -not -path './.git/*' -not -path './docs/archive/*' 2>/dev/null | sed 's|^\./||'
  } | sort -u
)
if [[ -n "$_r98_violations" ]]; then
  _r98_first=$(printf '%b' "$_r98_violations" | head -5 | tr '\n' '|')
  fail_rule "broad_corpus_deleted_module_name_truth" "broad corpus contains current-tense pre-Phase-C module name(s) without historical marker (first 5): ${_r98_first}-- Rule 98 / E137 (rc10 I-ε family closure; widens Rule 94 from ARCHITECTURE.md + rule cards + test Javadocs to ops/**, docs/contracts/*.yaml, **/module-metadata.yaml)"
  _r98_fail=1
fi
if [[ $_r98_fail -eq 0 ]]; then pass_rule "broad_corpus_deleted_module_name_truth"; fi

# ---------------------------------------------------------------------------
