#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 35 — dfx_yaml_present_and_wellformed. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 35 — dfx_yaml_present_and_wellformed (enforcer E53, ADR-0067)
#
# Every module with kind ∈ {platform, domain} in its module-metadata.yaml
# MUST have a docs/dfx/<module>.yaml covering five DFX dimensions:
# releasability, resilience, availability, vulnerability, observability.
# DFX is OPTIONAL for kind ∈ {bom, starter, sample}.
# Required by CLAUDE.md Rule 32.
# ---------------------------------------------------------------------------
_r35_fail=0
_dfx_required_kinds_re='^(platform|domain)$'
while IFS= read -r _meta; do
  [[ -z "$_meta" ]] && continue
  _kind="$(grep -E '^[[:space:]]*kind:' "$_meta" 2>/dev/null | head -1 | sed -E 's/^[[:space:]]*kind:[[:space:]]*([A-Za-z_]+).*/\1/')"
  [[ ! "$_kind" =~ $_dfx_required_kinds_re ]] && continue
  _mod_name="$(grep -E '^[[:space:]]*module:' "$_meta" 2>/dev/null | head -1 | sed -E 's/^[[:space:]]*module:[[:space:]]*([A-Za-z0-9_-]+).*/\1/')"
  _dfx="docs/dfx/${_mod_name}.yaml"
  if [[ ! -f "$_dfx" ]]; then
    fail_rule "dfx_yaml_present_and_wellformed" "$_dfx missing — required for kind=${_kind} module '${_mod_name}' (CLAUDE.md Rule 32 / ADR-0067)"
    _r35_fail=1
    continue
  fi
  for _d in releasability resilience availability vulnerability observability; do
    if ! grep -qE "^[[:space:]]*${_d}:" "$_dfx" 2>/dev/null; then
      fail_rule "dfx_yaml_present_and_wellformed" "$_dfx missing required DFX dimension '${_d}'"
      _r35_fail=1
    fi
  done
done < <(find . -mindepth 2 -maxdepth 2 -name 'module-metadata.yaml' -type f 2>/dev/null | sort || true)
if [[ $_r35_fail -eq 0 ]]; then pass_rule "dfx_yaml_present_and_wellformed"; fi

# ---------------------------------------------------------------------------
