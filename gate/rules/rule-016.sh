#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 16 — http_contract_w1_tenant_and_cancel_consistency. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 16 — http_contract_w1_tenant_and_cancel_consistency
# ADR-0040: (a) no "replace.*X-Tenant-Id" in active docs; (b) http-api-contracts.md
# must not reference CREATED as initial status; (c) openapi-v1.yaml must not
# mention DELETE /v1/runs/{runId} as the cancel mechanism; (d) shipped W0 cancel
# run-owner mismatch must remain 404 not_found; (e) W1 idempotency must not
# promise W2 response replay while architecture-status says replay is deferred.
# ---------------------------------------------------------------------------
_r16_fail=0
# 16a: no forward-looking "will replace X-Tenant-Id" claim in active normative docs
# Exclude docs/adr/: ADRs may legitimately document rejected options and past wrong text.
while IFS= read -r _mdf16; do
  [[ -z "$_mdf16" ]] && continue
  if grep -qE 'TenantContextFilter[[:space:]]+(switches[[:space:]]+to|replaces?([[:space:]]+with)?[[:space:]]+JWT|moves[[:space:]]+to)[[:space:]]+JWT|will[[:space:]]+replace.*X-Tenant-Id|replace[[:space:]]+header-based.*with[[:space:]]+JWT|W1[[:space:]]+replaces.*X-Tenant-Id' "$_mdf16" 2>/dev/null; then
    fail_rule "http_contract_w1_tenant_and_cancel_consistency" "$_mdf16 contains a replacement-implying claim about X-Tenant-Id or TenantContextFilter. Per ADR-0040 W1 adds JWT cross-check; X-Tenant-Id is NOT replaced. Forbidden phrasings: 'switches to JWT', 'replaces with JWT', 'moves to JWT', 'will replace X-Tenant-Id'."
    _r16_fail=1
    break
  fi
done < <(find . -name '*.md' \
  ! -path './docs/archive/*' \
  ! -path './docs/logs/reviews/*' \
  ! -path './docs/adr/*' \
  ! -path './third_party/*' \
  ! -path './target/*' \
  ! -path './.git/*' \
  -type f 2>/dev/null | sort || true)
# 16b: http-api-contracts.md must not say CREATED as initial status
if [[ $_r16_fail -eq 0 ]] && [[ -f 'docs/contracts/http-api-contracts.md' ]]; then
  if grep -qE 'starts in CREATED|CREATED stage|status.*CREATED' 'docs/contracts/http-api-contracts.md' 2>/dev/null; then
    fail_rule "http_contract_w1_tenant_and_cancel_consistency" "docs/contracts/http-api-contracts.md references CREATED as initial run status. Per ADR-0040 initial status is PENDING."
    _r16_fail=1
  fi
fi
# 16c: openapi-v1.yaml must not mention DELETE /v1/runs/{runId} as cancel
if [[ $_r16_fail -eq 0 ]] && [[ -f 'docs/contracts/openapi-v1.yaml' ]]; then
  if grep -qE 'DELETE[[:space:]]*/v1/runs/\{runId\}|DELETE.*runId.*cancel' 'docs/contracts/openapi-v1.yaml' 2>/dev/null; then
    fail_rule "http_contract_w1_tenant_and_cancel_consistency" "docs/contracts/openapi-v1.yaml references DELETE /v1/runs/{runId} as cancel. Per ADR-0040 cancel is POST /v1/runs/{id}/cancel."
    _r16_fail=1
  fi
fi
# 16d: W0 shipped cancel run-owner mismatch collapses to 404 not_found.
if [[ $_r16_fail -eq 0 ]] && [[ -f 'docs/contracts/http-api-contracts.md' ]]; then
  if grep -qE 'cancel.*Returns 403 `tenant_mismatch` if the request tenant differs from `Run\.tenantId`|request tenant differs from `Run\.tenantId`.*403 `tenant_mismatch`' 'docs/contracts/http-api-contracts.md' 2>/dev/null; then
    fail_rule "http_contract_w1_tenant_and_cancel_consistency" "docs/contracts/http-api-contracts.md says cancel run-owner tenant mismatch returns 403. Per ADR-0108/0116 W0 shipped behavior is 404 not_found; 403 is only JWT/header mismatch today and W1-widening future state."
    _r16_fail=1
  fi
fi
# 16e: W1 idempotency claim-only behavior is 409, response replay is W2.
if [[ $_r16_fail -eq 0 ]] && grep -q 'Response replay deferred to W2' docs/governance/architecture-status.yaml 2>/dev/null; then
  for _r16_idem_file in docs/contracts/http-api-contracts.md docs/contracts/openapi-v1.yaml agent-service/src/test/resources/contracts/openapi-v1-pinned.yaml; do
    [[ -f "$_r16_idem_file" ]] || continue
    if grep -qiE 'same key[^.]*return(s|ed)? the original response|same key[^.]*return(s|ed)? the first response|replays with the same key \+ body return the original response|Reused keys with same body return the original response' "$_r16_idem_file" 2>/dev/null; then
      fail_rule "http_contract_w1_tenant_and_cancel_consistency" "$_r16_idem_file promises response replay for same-body idempotency, but architecture-status.yaml says response replay is deferred to W2. W1 contract is 409 idempotency_conflict for same hash and 409 idempotency_body_drift for different hash."
      _r16_fail=1
      break
    fi
  done
fi
if [[ $_r16_fail -eq 0 ]]; then pass_rule "http_contract_w1_tenant_and_cancel_consistency"; fi

# ---------------------------------------------------------------------------
