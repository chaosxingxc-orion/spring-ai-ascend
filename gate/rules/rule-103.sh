#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 103 — deploy_entrypoint_deleted_module_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 103 — deploy_entrypoint_deleted_module_truth (enforcer E145)
#
# Closes rc11 review P1-4 + P1-5 (K-δ family): Rule 94 / 98 scopes covered
# .md/.yaml/.java/ops but missed root Dockerfile + .github/workflows/*.yml
# + .puml + gate/run_operator_shape_smoke.sh — all active deploy-entrypoint
# surfaces. Rule 103 closes the gap for deploy/operator/visual surfaces.
# ---------------------------------------------------------------------------
_r103_fail=0
_r103_files=()
[[ -f Dockerfile ]] && _r103_files+=(Dockerfile)
for _r103_f in ops/Dockerfile* ops/compose*.yml ops/compose*.yaml; do
  [[ -f "$_r103_f" ]] && _r103_files+=("$_r103_f")
done
while IFS= read -r _r103_f; do
  [[ -f "$_r103_f" ]] && _r103_files+=("$_r103_f")
done < <(find .github/workflows -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) 2>/dev/null)
[[ -f gate/run_operator_shape_smoke.sh ]] && _r103_files+=(gate/run_operator_shape_smoke.sh)
while IFS= read -r _r103_f; do
  [[ -f "$_r103_f" ]] && _r103_files+=("$_r103_f")
done < <(find docs/architecture-views -type f -name '*.puml' 2>/dev/null)

_r103_markers_file="gate/active-corpus-name-exemption-markers.txt"
_r103_marker_re="$(grep -vE '^[[:space:]]*(#|$)' "$_r103_markers_file" 2>/dev/null | tr '\n' '|' | sed 's/|$//')"
[[ -z "$_r103_marker_re" ]] && _r103_marker_re='historical'

_r103_violations=""
for _r103_f in "${_r103_files[@]}"; do
  _r103_hits=$(awk -v markers="$_r103_marker_re" '
    { lines[NR] = $0 }
    END {
      for (i = 1; i <= NR; i++) {
        line = lines[i]
        # Check for agent-platform or agent-runtime (not -core variant)
        match_pf = (line ~ /\<agent-platform\>/)
        match_rt = (line ~ /agent-runtime[^-]/) || (line ~ /agent-runtime$/)
        if (!match_pf && !match_rt) continue
        # Build ±3 marker window
        lo = i - 3; if (lo < 1) lo = 1
        hi = i + 3; if (hi > NR) hi = NR
        window = ""
        for (j = lo; j <= hi; j++) window = window " " lines[j]
        if (window !~ markers) {
          print i ": " line
        }
      }
    }
  ' "$_r103_f" 2>/dev/null || true)
  if [[ -n "$_r103_hits" ]]; then
    _r103_violations="${_r103_violations}${_r103_f}:\n${_r103_hits}\n"
  fi
done

if [[ -n "$_r103_violations" ]]; then
  fail_rule "deploy_entrypoint_deleted_module_truth" "active deploy-entrypoint surface(s) reference deleted modules (agent-platform / agent-runtime) outside historical-marker window:\n${_r103_violations}-- Rule 103 / E145 (rc11 review P1-4 + P1-5 K-δ closure)"
  _r103_fail=1
fi
if [[ $_r103_fail -eq 0 ]]; then pass_rule "deploy_entrypoint_deleted_module_truth"; fi

# ---------------------------------------------------------------------------
