#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 87 — status_yaml_allowed_claim_module_name_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 87 — status_yaml_allowed_claim_module_name_truth (enforcer E120)
#
# Every allowed_claim: text value in docs/governance/architecture-status.yaml
# MUST NOT contain current-tense agent-platform, agent-runtime, or
# agent-runtime-core (all three are now deleted-module names after rc13
# ADR-0088 dissolution) outside a historical marker within +/-3 lines.
# Operationalises rc6 post-response review P1-2 + rc13 dissolution closure.
# ---------------------------------------------------------------------------
_r87_fail=0
_r87_yaml="docs/governance/architecture-status.yaml"
if [[ ! -f "$_r87_yaml" ]]; then
  fail_rule "status_yaml_allowed_claim_module_name_truth" "$_r87_yaml missing -- Rule 87 / E120"
  _r87_fail=1
else
  _r87_marker_re='historical|pre-ADR-[0-9]{4}|pre-Phase-C|pre-rc[0-9]+|consolidated into|consolidated from|merged into|merged in|was rooted|formerly|superseded|deprecated|archived|moved|post-ADR-[0-9]{4}|dissolution|dissolved|relocated|relocate'
  _r87_allowed_re='^[[:space:]]+allowed_claim:[[:space:]]*(.*)$'
  _r87_stale_re='\b(agent-platform|agent-runtime|agent-runtime-core)\b'
  # Perf fix (2026-05-23): per-line `echo | grep -qE` + `echo | sed` +
  # `echo | grep -oE` + `sed | grep -qiE` (~6 forks per line × 1471 lines)
  # → mapfile + bash-native regex against the array. ~164s → ~1s.
  mapfile -t _r87_arr < "$_r87_yaml"
  _r87_n=${#_r87_arr[@]}
  shopt -q nocasematch; _r87_nocase_was=$?
  for ((_r87_i=0; _r87_i<_r87_n; _r87_i++)); do
    _r87_line="${_r87_arr[$_r87_i]}"
    [[ "$_r87_line" =~ $_r87_allowed_re ]] || continue
    _r87_value="${BASH_REMATCH[1]}"
    _r87_value="${_r87_value#\"}"
    _r87_value="${_r87_value%\"}"
    [[ "$_r87_value" =~ $_r87_stale_re ]] || continue
    _r87_stale="${BASH_REMATCH[1]}"
    _r87_lo=$((_r87_i > 3 ? _r87_i - 3 : 0))
    _r87_hi=$((_r87_i + 3 < _r87_n - 1 ? _r87_i + 3 : _r87_n - 1))
    _r87_marker_present=0
    shopt -s nocasematch
    for ((_r87_j=_r87_lo; _r87_j<=_r87_hi; _r87_j++)); do
      if [[ "${_r87_arr[$_r87_j]}" =~ $_r87_marker_re ]]; then
        _r87_marker_present=1; break
      fi
    done
    [[ $_r87_nocase_was -ne 0 ]] && shopt -u nocasematch
    [[ $_r87_marker_present -eq 1 ]] && continue
    _r87_lineno=$((_r87_i + 1))
    fail_rule "status_yaml_allowed_claim_module_name_truth" "$_r87_yaml:$_r87_lineno allowed_claim text contains current-tense '$_r87_stale' (pre-Phase-C module name) without historical/pre-ADR/consolidated marker in +/-3 lines -- Rule 87 / E120 (allowed_claim module name drift)"
    _r87_fail=1
  done
  [[ $_r87_nocase_was -ne 0 ]] && shopt -u nocasematch
fi
if [[ $_r87_fail -eq 0 ]]; then pass_rule "status_yaml_allowed_claim_module_name_truth"; fi

