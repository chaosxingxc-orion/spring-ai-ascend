#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 124 — unsupported_absolute_claim_guard. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 124 — unsupported_absolute_claim_guard (enforcer E172)
#
# Security/performance absolutes in proposal docs invite false release claims.
# The terms below are allowed only when the same line points at evidence such as
# a benchmark, threat model, measurement, or acceptance criterion.
# ---------------------------------------------------------------------------
_r124_fail=0
for _r124_file in docs/logs/reviews/*proposal*.md; do
  [[ -f "$_r124_file" ]] || continue
  _r124_hits=$(grep -nEi 'bulletproof|zero-day safety|zero downtime|sub-millisecond|sub-milliseconds' "$_r124_file" 2>/dev/null \
    | grep -viE 'benchmark|threat model|measured|measurement|acceptance criteria|acceptance criterion|deferred' || true)
  if [[ -n "$_r124_hits" ]]; then
    fail_rule "unsupported_absolute_claim_guard" "$_r124_file contains unsupported absolute claim(s): ${_r124_hits//$'\n'/; } -- Rule G-2 / E172"
    _r124_fail=1
  fi
done
[[ $_r124_fail -eq 0 ]] && pass_rule "unsupported_absolute_claim_guard"

# ---------------------------------------------------------------------------
