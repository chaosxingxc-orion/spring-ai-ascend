#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 9 — openapi_path_consistency. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 9 — openapi_path_consistency
# /v3/api-docs must appear in the agent-platform ARCHITECTURE.md documenting
# the security permit path.
# ---------------------------------------------------------------------------
_r9_fail=0
_plat_arch='agent-service/ARCHITECTURE.md'
if [[ -f "$_plat_arch" ]]; then
  if ! grep -q '/v3/api-docs' "$_plat_arch" 2>/dev/null; then
    fail_rule "openapi_path_consistency" "$_plat_arch does not document /v3/api-docs exposure. Document it or remove the security permitAll."
    _r9_fail=1
  fi
fi
if [[ $_r9_fail -eq 0 ]]; then pass_rule "openapi_path_consistency"; fi

# ---------------------------------------------------------------------------
