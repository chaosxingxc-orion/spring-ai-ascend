#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 22 — lowercase_metrics_in_contract_docs. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 22 — lowercase_metrics_in_contract_docs (widened per ADR-0043/ADR-0045)
# The full ACTIVE_NORMATIVE_DOCS corpus must not contain SPRINGAI_ASCEND_<lowercase>
# metric name patterns. grep -E is case-sensitive by default (LC_ALL=C set above).
# ---------------------------------------------------------------------------
_r22_fail=0
while IFS= read -r _af22; do
  [[ -z "$_af22" ]] && continue
  if grep -qE 'SPRINGAI_ASCEND_[a-z]' "$_af22" 2>/dev/null; then
    fail_rule "lowercase_metrics_in_contract_docs" "$_af22 contains uppercase metric namespace 'SPRINGAI_ASCEND_<lowercase>'. Per ADR-0043 Gate Rule 22 (widened) metric names must use lowercase springai_ascend_ prefix."
    _r22_fail=1
  fi
done < <(find . -name '*.md' -o -name '*.yaml' | grep -v '/docs/archive/' | grep -v '/docs/logs/reviews/' | \
  grep -v '/docs/adr/' | grep -v '/docs/delivery/' | grep -v '/docs/v6-rationale/' | \
  grep -v '/docs/plans/' | grep -v '/third_party/' | grep -v '/target/' | grep -v '/.git/' | sort 2>/dev/null || true)
if [[ $_r22_fail -eq 0 ]]; then pass_rule "lowercase_metrics_in_contract_docs"; fi

# ---------------------------------------------------------------------------
