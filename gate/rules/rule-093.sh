#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 93 — dfx_stem_matches_module. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 93 — dfx_stem_matches_module (enforcer E127)
#
# Closes rc8 post-corrective review P0-3: `docs/dfx/agent-platform.yaml`
# remained on disk after ADR-0078 deleted the agent-platform module.
# Rule 93 asserts that every `docs/dfx/<stem>.yaml` (not under archive/) has
# a stem matching some `<module>` entry in root `pom.xml`.
# ---------------------------------------------------------------------------
_r93_fail=0
_r93_dfx_dir="docs/dfx"
_r93_pom="pom.xml"
if [[ ! -d "$_r93_dfx_dir" ]] || [[ ! -f "$_r93_pom" ]]; then
  fail_rule "dfx_stem_matches_module" "$_r93_dfx_dir or $_r93_pom missing — Rule 93 / E127"
  _r93_fail=1
else
  _r93_pom_modules=$(grep -oE '<module>[^<]+</module>' "$_r93_pom" | sed -E 's|</?module>||g' | sort -u)
  _r93_orphans=""
  for _r93_dfx in "$_r93_dfx_dir"/*.yaml; do
    [[ -e "$_r93_dfx" ]] || continue
    _r93_stem=$(basename "$_r93_dfx" .yaml)
    if ! echo "$_r93_pom_modules" | grep -qxF "$_r93_stem"; then
      _r93_orphans="${_r93_orphans}${_r93_stem} "
    fi
  done
  if [[ -n "$_r93_orphans" ]]; then
    fail_rule "dfx_stem_matches_module" "$_r93_dfx_dir has DFX files for non-existent modules: ${_r93_orphans}-- Rule 93 / E127 (delete the orphan DFX file or archive it; closes rc8 post-corrective P0-3)"
    _r93_fail=1
  fi
fi
if [[ $_r93_fail -eq 0 ]]; then pass_rule "dfx_stem_matches_module"; fi

# ---------------------------------------------------------------------------
