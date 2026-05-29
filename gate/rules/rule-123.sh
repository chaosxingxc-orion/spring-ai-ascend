#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 123 — proposal_engine_package_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 123 — proposal_engine_package_truth (enforcer E171)
#
# Proposal FQNs must respect current package authority unless explicitly marked
# proposed/future on the same line. Current authority is:
#   - engine-owned SPI/runtime under com.huawei.ascend.engine.*
#   - service-owned StatelessEngine under com.huawei.ascend.service.engine.spi
# ---------------------------------------------------------------------------
_r123_fail=0
for _r123_file in docs/logs/reviews/*proposal*.md; do
  [[ -f "$_r123_file" ]] || continue
  _r123_hits=$(grep -nE 'com\.huawei\.ascend\.agent\.engine|StatelessEngineExecutor' "$_r123_file" 2>/dev/null \
    | grep -viE 'proposed|future|candidate|exploratory|not current' || true)
  if [[ -n "$_r123_hits" ]]; then
    fail_rule "proposal_engine_package_truth" "$_r123_file contains engine/service FQN or signature claims not marked proposed: ${_r123_hits//$'\n'/; } -- Rule G-8 / E171"
    _r123_fail=1
  fi
done
[[ $_r123_fail -eq 0 ]] && pass_rule "proposal_engine_package_truth"

# ---------------------------------------------------------------------------
