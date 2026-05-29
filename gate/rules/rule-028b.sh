#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 28b — high_cardinality_tag_guard. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 28b — high_cardinality_tag_guard (enforcer E19)
# No source in agent-*/src/main/java registers Tag.of("run_id"|"idempotency_key"|
# "jwt_sub"|"body", ...) on a metric. The TenantTagMeterFilter scrubs these
# at runtime; the gate rejects them at commit time.
# ---------------------------------------------------------------------------
_r28b_fail=0
_forbidden_tag_pattern='Tag\.of\(\s*"(run_id|idempotency_key|jwt_sub|body)"'
_28b_hits=$(grep -rnE "$_forbidden_tag_pattern" \
  agent-*/src/main/java 2>/dev/null || true)
if [[ -n "$_28b_hits" ]]; then
  fail_rule "high_cardinality_tag_guard" "Forbidden high-cardinality metric tag found:\n$_28b_hits\nPer Rule 28b / enforcer E19."
  _r28b_fail=1
fi
if [[ $_r28b_fail -eq 0 ]]; then pass_rule "high_cardinality_tag_guard"; fi

# ---------------------------------------------------------------------------
