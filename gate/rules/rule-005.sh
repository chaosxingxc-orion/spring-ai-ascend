#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 5 — required_files_present. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 5 — required_files_present
# These 2 files must exist: docs/contracts/contract-catalog.md and
# docs/contracts/openapi-v1.yaml.
# ---------------------------------------------------------------------------
_r5_fail=0
for _req in "docs/contracts/contract-catalog.md" "docs/contracts/openapi-v1.yaml"; do
  if [[ ! -f "$_req" ]]; then
    fail_rule "required_files_present" "$_req not found"
    _r5_fail=1
  fi
done
if [[ $_r5_fail -eq 0 ]]; then pass_rule "required_files_present"; fi

# ---------------------------------------------------------------------------
