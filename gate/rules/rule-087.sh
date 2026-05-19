#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 87 — status_yaml_allowed_claim_module_name_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

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
  _r87_lineno=0
  while IFS= read -r _r87_line || [[ -n "$_r87_line" ]]; do
    _r87_lineno=$((_r87_lineno + 1))
    if ! echo "$_r87_line" | grep -qE '^[[:space:]]+allowed_claim:[[:space:]]*'; then continue; fi
    _r87_value=$(echo "$_r87_line" | sed -E 's/^[[:space:]]+allowed_claim:[[:space:]]*//')
    _r87_value="${_r87_value#\"}"
    _r87_value="${_r87_value%\"}"
    _r87_stale=$(echo "$_r87_value" | grep -oE '\bagent-platform\b|\bagent-runtime\b|\bagent-runtime-core\b' | head -1)
    if [[ -z "$_r87_stale" ]]; then continue; fi
    _r87_lo=$((_r87_lineno > 3 ? _r87_lineno - 3 : 1))
    _r87_hi=$((_r87_lineno + 3))
    if sed -n "${_r87_lo},${_r87_hi}p" "$_r87_yaml" 2>/dev/null | grep -qiE "$_r87_marker_re"; then continue; fi
    fail_rule "status_yaml_allowed_claim_module_name_truth" "$_r87_yaml:$_r87_lineno allowed_claim text contains current-tense '$_r87_stale' (pre-Phase-C module name) without historical/pre-ADR/consolidated marker in +/-3 lines -- Rule 87 / E120 (allowed_claim module name drift)"
    _r87_fail=1
  done < "$_r87_yaml"
fi
if [[ $_r87_fail -eq 0 ]]; then pass_rule "status_yaml_allowed_claim_module_name_truth"; fi

