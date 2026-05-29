#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 34 — module_metadata_present_and_complete. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 34 — module_metadata_present_and_complete (enforcer E52, ADR-0066)
#
# Every reactor module (every <module>/pom.xml) MUST have a sibling
# module-metadata.yaml declaring module, kind, version, semver_compatibility.
# Required by CLAUDE.md Rule 31.
# ---------------------------------------------------------------------------
_r34_fail=0
_required_keys=(module kind version semver_compatibility)
while IFS= read -r _pom; do
  [[ -z "$_pom" ]] && continue
  # Skip the root reactor pom — it's the reactor declaration, not a module
  if [[ "$_pom" == "./pom.xml" || "$_pom" == "pom.xml" ]]; then continue; fi
  _mod_dir="$(dirname "$_pom")"
  _meta="${_mod_dir}/module-metadata.yaml"
  if [[ ! -f "$_meta" ]]; then
    fail_rule "module_metadata_present_and_complete" "$_meta missing — required for ${_mod_dir} (CLAUDE.md Rule 31 / ADR-0066)"
    _r34_fail=1
    continue
  fi
  for _k in "${_required_keys[@]}"; do
    if ! grep -qE "^[[:space:]]*${_k}:" "$_meta" 2>/dev/null; then
      fail_rule "module_metadata_present_and_complete" "$_meta missing required key '${_k}'"
      _r34_fail=1
    fi
  done
done < <(find . -mindepth 2 -maxdepth 2 -name 'pom.xml' -type f 2>/dev/null | sort || true)
if [[ $_r34_fail -eq 0 ]]; then pass_rule "module_metadata_present_and_complete"; fi

# ---------------------------------------------------------------------------
