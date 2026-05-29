#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 49 — deployment_plane_in_module_metadata. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 49 — deployment_plane_in_module_metadata (enforcer E68, Rule 39 / P-I)
#
# Every <module>/module-metadata.yaml MUST declare deployment_plane: with
# value in {edge, compute_control, bus_state, sandbox, evolution, none}.
# ---------------------------------------------------------------------------
_r49_fail=0
_r49_allowed_re='^(edge|compute_control|bus_state|sandbox|evolution|none)$'
while IFS= read -r _r49_meta; do
  [[ -z "$_r49_meta" ]] && continue
  _r49_plane="$(grep -E '^deployment_plane:' "$_r49_meta" | head -1 | sed -E 's/^deployment_plane:[[:space:]]*([A-Za-z_]+).*/\1/')"
  if [[ -z "$_r49_plane" ]]; then
    fail_rule "deployment_plane_in_module_metadata" "$_r49_meta missing deployment_plane: field"
    _r49_fail=1
  elif ! [[ "$_r49_plane" =~ $_r49_allowed_re ]]; then
    fail_rule "deployment_plane_in_module_metadata" "$_r49_meta declares deployment_plane: $_r49_plane (not in {edge, compute_control, bus_state, sandbox, evolution, none})"
    _r49_fail=1
  fi
done <<< "${_SCAN_MODULE_METADATA:-$(find . -maxdepth 3 -name module-metadata.yaml -not -path './target/*' -not -path './.claude/*' 2>/dev/null)}"
if [[ $_r49_fail -eq 0 ]]; then pass_rule "deployment_plane_in_module_metadata"; fi

# ---------------------------------------------------------------------------
