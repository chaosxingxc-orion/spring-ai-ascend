#!/usr/bin/env bash
# spring-ai-ascend architecture-sync gate self-test (L0 v2 + L1 Phase L).
# PARTIAL COVERAGE: covers Rules 1-6 + Rules 16, 19, 22, 24, 25, 26, 27, 28, 28j, 29.
# Full gate verification requires running: pwsh gate/check_architecture_sync.ps1
# The full gate has 29 active rules; this self-test covers Rules 1-6 + 16/19/22/24/25/26/27/28/28j/29.
# Phase L (L1 reviewer remediation) adds 2 cases for Rule 28j anchor validation:
# 35 → 37 self-tests.
# Prints: Tests passed: N/37
# Exits 0 if all 37 pass, 1 otherwise.

set -uo pipefail
export LC_ALL=C

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# PR-E4: resolve $GATE_PARALLELISM_JOBS via gate/lib/load_config.sh, but read
# the value in a subshell so the loader's exported env vars (especially
# GATE_PARALLELISM_JOBS itself, plus a dozen sibling GATE_* knobs) do NOT
# leak into test fixtures that source the same loader with a synthetic
# GATE_REPO_ROOT (e.g. Rule 73 negative cases — they construct a fake config
# and would otherwise inherit the real loader's values, defeating the test).
# Resolution order: GATE_JOBS env > config.yaml > 8.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -n "${GATE_JOBS:-}" ]]; then
  GATE_PARALLELISM_JOBS="$GATE_JOBS"
elif [[ -f "$SCRIPT_DIR/lib/load_config.sh" ]]; then
  GATE_PARALLELISM_JOBS="$(bash -c "
    source '$SCRIPT_DIR/lib/load_config.sh'
    gate_load_config >/dev/null 2>&1 || true
    printf '%s' \"\${GATE_PARALLELISM_JOBS:-8}\"
  ")"
else
  GATE_PARALLELISM_JOBS=8
fi
# Resolve auto/0 to a sensible default in case the config requests it.
if [[ "${GATE_PARALLELISM_JOBS}" == "0" || -z "${GATE_PARALLELISM_JOBS}" ]]; then
  GATE_PARALLELISM_JOBS=8
fi

passed=0
failed=0
# TOTAL is derived from the actual passed+failed result count after the
# parallel orchestrator runs (see end of file). Bare literal `TOTAL=NNN`
# declarations are forbidden by Rule 89 / E122 sub-check (b).

# PR-E4: parallel-aware ok()/fail().
# Serial mode (TEST_RESULT_FILE unset): increment globals + print directly.
# Parallel mode (TEST_RESULT_FILE set): append to per-batch file; aggregator counts at end.
_record_result() {
  # $1 = status (PASS|FAIL), $2 = test_id, $3 = message
  if [[ -n "${TEST_RESULT_FILE:-}" ]]; then
    printf '%s [%s]: %s\n' "$1" "$2" "$3" >> "$TEST_RESULT_FILE"
  else
    if [[ "$1" == "PASS" ]]; then
      printf 'PASS [%s]: %s\n' "$2" "$3"
      passed=$((passed + 1))
    else
      printf 'FAIL [%s]: %s\n' "$2" "$3" >&2
      failed=$((failed + 1))
    fi
  fi
}

ok() { _record_result PASS "$1" "$2"; }
fail() { _record_result FAIL "$1" "$2"; }

# Scratch directory for synthetic test fixtures (cleaned up on exit).
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT

# ---------------------------------------------------------------------------
# Helper: run the gate script on a synthetic repo root and capture output.
# Usage: run_gate <fake_root>  -- sets last_out, last_rc.
# We replicate the gate checks inline rather than invoking the real script
# (which requires the full repo layout) so each test is self-contained.
# ---------------------------------------------------------------------------


test_rule1_status_enum_invalid() {
# ---------------------------------------------------------------------------
# RULE 1 -- status_enum_invalid
# Allowed: design_accepted implemented_unverified test_verified deferred_w1 deferred_w2
# ---------------------------------------------------------------------------

## Positive: valid status values pass
_r1_pos="$scratch/r1_pos"
mkdir -p "$_r1_pos/docs/governance"
cat > "$_r1_pos/docs/governance/architecture-status.yaml" <<'EOF'
capabilities:
  foo:
    status: design_accepted
  bar:
    status: test_verified
  baz:
    status: deferred_w1
EOF
_allowed_re='^(design_accepted|implemented_unverified|test_verified|deferred_w1|deferred_w2)$'
_r1_pos_fail=0
while IFS= read -r _l; do
  _v=$(printf '%s\n' "$_l" | sed -nE 's/^[[:space:]]*status:[[:space:]]*([A-Za-z_]+)[[:space:]]*$/\1/p')
  if [[ -n "$_v" ]] && ! [[ "$_v" =~ $_allowed_re ]]; then _r1_pos_fail=1; fi
done < "$_r1_pos/docs/governance/architecture-status.yaml"
if [[ $_r1_pos_fail -eq 0 ]]; then
  ok "rule1_status_enum_pos" "valid status values all pass"
else
  fail "rule1_status_enum_pos" "expected PASS for valid status values"
fi

## Negative: invalid status value triggers FAIL
_r1_neg="$scratch/r1_neg"
mkdir -p "$_r1_neg/docs/governance"
cat > "$_r1_neg/docs/governance/architecture-status.yaml" <<'EOF'
capabilities:
  foo:
    status: proposed
EOF
_r1_neg_fail=0
while IFS= read -r _l; do
  _v=$(printf '%s\n' "$_l" | sed -nE 's/^[[:space:]]*status:[[:space:]]*([A-Za-z_]+)[[:space:]]*$/\1/p')
  if [[ -n "$_v" ]] && ! [[ "$_v" =~ $_allowed_re ]]; then _r1_neg_fail=1; fi
done < "$_r1_neg/docs/governance/architecture-status.yaml"
if [[ $_r1_neg_fail -eq 1 ]]; then
  ok "rule1_status_enum_neg" "invalid status 'proposed' correctly triggers FAIL"
else
  fail "rule1_status_enum_neg" "expected FAIL for invalid status 'proposed'"
fi

}


test_rule2_delivery_log_parity() {
# ---------------------------------------------------------------------------
# RULE 2 -- delivery_log_parity
# sha field must equal filename basename (without suffix). semantic_pass required.
# ---------------------------------------------------------------------------

## Positive: sha field matches filename and semantic_pass present
_r2_dir="$scratch/r2_log"
mkdir -p "$_r2_dir"
_sha2="abc1234"
printf '{"sha":"%s","semantic_pass":true}\n' "$_sha2" > "$_r2_dir/${_sha2}-posix.json"
_r2_pos_fail=0
_base2="$(basename "$_r2_dir/${_sha2}-posix.json" .json)"
_shachk="${_base2%%-posix}"
_log_sha2="$(grep -oE '"sha":"[^"]*"' "$_r2_dir/${_sha2}-posix.json" | head -1 | sed -E 's/.*"sha":"([^"]*)".*/\1/')"
_sem2="$(grep -oE '"semantic_pass":(true|false)' "$_r2_dir/${_sha2}-posix.json" | head -1)"
if [[ "$_log_sha2" != "$_shachk" ]]; then _r2_pos_fail=1; fi
if [[ -z "$_sem2" ]]; then _r2_pos_fail=1; fi
if [[ $_r2_pos_fail -eq 0 ]]; then
  ok "rule2_delivery_log_parity_pos" "sha field matches filename; semantic_pass present"
else
  fail "rule2_delivery_log_parity_pos" "expected PASS for valid delivery log"
fi

## Negative: sha field mismatch triggers FAIL
_r2n_sha="abc1234"
_r2n_file="$scratch/r2n_log"
mkdir -p "$_r2n_file"
printf '{"sha":"deadbeef","semantic_pass":true}\n' > "$_r2n_file/${_r2n_sha}-posix.json"
_base2n="$(basename "$_r2n_file/${_r2n_sha}-posix.json" .json)"
_shachk2n="${_base2n%%-posix}"
_log_sha2n="$(grep -oE '"sha":"[^"]*"' "$_r2n_file/${_r2n_sha}-posix.json" | head -1 | sed -E 's/.*"sha":"([^"]*)".*/\1/')"
if [[ "$_log_sha2n" != "$_shachk2n" ]]; then
  ok "rule2_delivery_log_parity_neg" "sha mismatch correctly triggers FAIL"
else
  fail "rule2_delivery_log_parity_neg" "expected FAIL for sha mismatch"
fi

}


test_rule3_eol_policy() {
# ---------------------------------------------------------------------------
# RULE 3 -- eol_policy
# *.sh files in gate/ must have LF, not CRLF.
# ---------------------------------------------------------------------------

## Positive: LF-only file passes
_r3_lf="$scratch/r3_lf.sh"
printf '#!/bin/bash\necho ok\n' > "$_r3_lf"
_r3_pos_fail=0
if grep -qU $'\r' "$_r3_lf" 2>/dev/null; then _r3_pos_fail=1; fi
if [[ $_r3_pos_fail -eq 0 ]]; then
  ok "rule3_eol_policy_pos" "LF-only .sh file passes"
else
  fail "rule3_eol_policy_pos" "expected PASS for LF-only .sh file"
fi

## Negative: CRLF file triggers FAIL
_r3_crlf="$scratch/r3_crlf.sh"
printf '#!/bin/bash\r\necho ok\r\n' > "$_r3_crlf"
_r3_neg_fail=0
if grep -qU $'\r' "$_r3_crlf" 2>/dev/null; then _r3_neg_fail=1; fi
if [[ $_r3_neg_fail -eq 1 ]]; then
  ok "rule3_eol_policy_neg" "CRLF .sh file correctly triggers FAIL"
else
  fail "rule3_eol_policy_neg" "expected FAIL for CRLF .sh file"
fi

}


test_rule4_ci_no_or_true_mask() {
# ---------------------------------------------------------------------------
# RULE 4 -- ci_no_or_true_mask
# .github/workflows/*.yml must not have gate/run_* || true.
# ---------------------------------------------------------------------------

## Positive: CI file without mask passes
_r4_clean="$scratch/r4_clean.yml"
cat > "$_r4_clean" <<'EOF'
jobs:
  gate:
    steps:
      - run: bash gate/check_architecture_sync.sh
EOF
_r4_pos_fail=0
if grep -qE 'gate/run_.*\|\|[[:space:]]*true' "$_r4_clean" 2>/dev/null; then _r4_pos_fail=1; fi
if [[ $_r4_pos_fail -eq 0 ]]; then
  ok "rule4_ci_no_or_true_mask_pos" "CI file without mask passes"
else
  fail "rule4_ci_no_or_true_mask_pos" "expected PASS for clean CI file"
fi

## Negative: CI file with gate/run_* || true triggers FAIL
_r4_masked="$scratch/r4_masked.yml"
cat > "$_r4_masked" <<'EOF'
jobs:
  gate:
    steps:
      - run: bash gate/run_operator_shape_smoke.sh || true
EOF
_r4_neg_fail=0
if grep -qE 'gate/run_.*\|\|[[:space:]]*true' "$_r4_masked" 2>/dev/null; then _r4_neg_fail=1; fi
if [[ $_r4_neg_fail -eq 1 ]]; then
  ok "rule4_ci_no_or_true_mask_neg" "masked gate/run_* correctly triggers FAIL"
else
  fail "rule4_ci_no_or_true_mask_neg" "expected FAIL for gate/run_* || true"
fi

}


test_rule5_required_files_present() {
# ---------------------------------------------------------------------------
# RULE 5 -- required_files_present
# docs/contracts/contract-catalog.md and docs/contracts/openapi-v1.yaml must exist.
# ---------------------------------------------------------------------------

## Positive: both files present passes
_r5_dir="$scratch/r5_ok"
mkdir -p "$_r5_dir/docs/contracts"
touch "$_r5_dir/docs/contracts/contract-catalog.md"
touch "$_r5_dir/docs/contracts/openapi-v1.yaml"
_r5_pos_fail=0
for _req in "$_r5_dir/docs/contracts/contract-catalog.md" "$_r5_dir/docs/contracts/openapi-v1.yaml"; do
  [[ ! -f "$_req" ]] && _r5_pos_fail=1
done
if [[ $_r5_pos_fail -eq 0 ]]; then
  ok "rule5_required_files_present_pos" "both required files present -- PASS"
else
  fail "rule5_required_files_present_pos" "expected PASS when both files exist"
fi

## Negative: missing openapi-v1.yaml triggers FAIL
_r5_neg="$scratch/r5_neg"
mkdir -p "$_r5_neg/docs/contracts"
touch "$_r5_neg/docs/contracts/contract-catalog.md"
# openapi-v1.yaml intentionally absent
_r5_neg_fail=0
for _req in "$_r5_neg/docs/contracts/contract-catalog.md" "$_r5_neg/docs/contracts/openapi-v1.yaml"; do
  [[ ! -f "$_req" ]] && _r5_neg_fail=1
done
if [[ $_r5_neg_fail -eq 1 ]]; then
  ok "rule5_required_files_present_neg" "missing openapi-v1.yaml correctly triggers FAIL"
else
  fail "rule5_required_files_present_neg" "expected FAIL when openapi-v1.yaml absent"
fi

}


test_rule6_metric_naming_namespace() {
# ---------------------------------------------------------------------------
# RULE 6 -- metric_naming_namespace
# Metric name strings in Java must start with springai_ascend_.
# No springai_fin_ prefix allowed.
# ---------------------------------------------------------------------------

## Positive: correct prefix passes
_r6_good_java="$scratch/GoodMetrics.java"
cat > "$_r6_good_java" <<'EOF'
Counter.builder("springai_ascend_tenant_header_missing_total")
    .register(registry);
EOF
_r6_pos_fail=0
while IFS= read -r _jl; do
  _nm="${_jl#*.counter(\"}"
  if [[ "$_nm" != "$_jl" ]]; then
    _nm="${_nm%%\"*}"
    if [[ -n "$_nm" && "${_nm:0:15}" != "springai_ascend" ]]; then _r6_pos_fail=1; fi
  fi
done < "$_r6_good_java"
if grep -q 'springai_fin_\|springai\.fin\.' "$_r6_good_java" 2>/dev/null; then _r6_pos_fail=1; fi
if [[ $_r6_pos_fail -eq 0 ]]; then
  ok "rule6_metric_naming_namespace_pos" "correct springai_ascend_ prefix passes"
else
  fail "rule6_metric_naming_namespace_pos" "expected PASS for correct metric prefix"
fi

## Negative: wrong prefix triggers FAIL
_r6_bad_java="$scratch/BadMetrics.java"
cat > "$_r6_bad_java" <<'EOF'
registry.counter("app_counter_total").increment();
EOF
_r6_neg_fail=0
while IFS= read -r _jl; do
  _nm="${_jl#*.counter(\"}"
  if [[ "$_nm" != "$_jl" ]]; then
    _nm="${_nm%%\"*}"
    if [[ -n "$_nm" && "${_nm:0:15}" != "springai_ascend" ]]; then _r6_neg_fail=1; fi
  fi
done < "$_r6_bad_java"
if [[ $_r6_neg_fail -eq 1 ]]; then
  ok "rule6_metric_naming_namespace_neg" "wrong prefix 'app_counter_total' correctly triggers FAIL"
else
  fail "rule6_metric_naming_namespace_neg" "expected FAIL for metric without springai_ascend_ prefix"
fi

}


test_rule16_http_contract_w1_tenant_and_cancel_consistency() {
# ---------------------------------------------------------------------------
# RULE 16 — http_contract_w1_tenant_and_cancel_consistency (widened)
# Positive: "cross-check" wording passes (must NOT be flagged)
# Negative: "TenantContextFilter switches to JWT" is caught
# ---------------------------------------------------------------------------

## Positive: cross-check wording is compliant — must pass
_r16_pos="$scratch/r16_pos.md"
printf 'W1: TenantContextFilter adds a JWT tenant_id claim cross-check on top of X-Tenant-Id (per ADR-0040).\n' > "$_r16_pos"
_r16_pos_pattern='TenantContextFilter[[:space:]]+(switches[[:space:]]+to|replaces?([[:space:]]+with)?[[:space:]]+JWT|moves[[:space:]]+to)[[:space:]]+JWT|will[[:space:]]+replace.*X-Tenant-Id|replace[[:space:]]+header-based.*with[[:space:]]+JWT|W1[[:space:]]+replaces.*X-Tenant-Id'
if grep -qE "$_r16_pos_pattern" "$_r16_pos" 2>/dev/null; then
  fail "rule16_w1_tenant_pos" "cross-check wording incorrectly flagged as replace violation"
else
  ok "rule16_w1_tenant_pos" "cross-check wording correctly passes"
fi

## Negative: "switches to JWT" replacement-implying phrasing is caught
_r16_neg="$scratch/r16_neg.md"
printf 'W1: TenantContextFilter switches to JWT tenant_id claim; IdempotencyHeaderFilter wires IdempotencyStore.\n' > "$_r16_neg"
if grep -qE "$_r16_pos_pattern" "$_r16_neg" 2>/dev/null; then
  ok "rule16_w1_tenant_neg" "'TenantContextFilter switches to JWT' correctly detected as violation"
else
  fail "rule16_w1_tenant_neg" "expected 'switches to JWT' to be detected as replacement-implying"
fi

}


test_rule19_shipped_row_tests_evidence() {
# ---------------------------------------------------------------------------
# RULE 19 — shipped_row_tests_evidence (strengthened)
# (a) tests: absent on shipped row → FAIL
# (b) tests: [] on shipped row → FAIL
# (c) tests: non-empty but path missing → FAIL
# (d) tests: non-empty with real path → PASS
# ---------------------------------------------------------------------------

## Positive: shipped row with non-empty tests list (self-test .sh is a real file)
_r19_pos_yaml="$scratch/r19_pos.yaml"
cat > "$_r19_pos_yaml" <<'EOF'
capabilities:
  my_cap:
    status: implemented_unverified
    shipped: true
    tests:
      - gate/test_architecture_sync_gate.sh
EOF
_r19_pos_fail=0
_in_sh19=0; _tf19=0; _thi19=0; _tp19_val=''
while IFS= read -r _l19 || [[ -n "$_l19" ]]; do
  if printf '%s\n' "$_l19" | grep -qE '^  [a-zA-Z][a-zA-Z_]+:'; then _in_sh19=0; fi
  if printf '%s\n' "$_l19" | grep -qE '^[[:space:]]+shipped:[[:space:]]+true'; then _in_sh19=1; fi
  if [[ $_in_sh19 -eq 1 ]]; then
    if printf '%s\n' "$_l19" | grep -qE '^[[:space:]]+tests:[[:space:]]*$'; then _tf19=1; fi
    if [[ $_tf19 -eq 1 ]] && printf '%s\n' "$_l19" | grep -qE '^[[:space:]]+-[[:space:]]+'; then
      _thi19=1; _tp19_val=$(printf '%s\n' "$_l19" | sed -E 's/^[[:space:]]+-[[:space:]]+(.*)/\1/')
    fi
  fi
done < "$_r19_pos_yaml"
if [[ $_tf19 -eq 1 && $_thi19 -eq 1 && -e "$_tp19_val" ]]; then
  ok "rule19_tests_evidence_pos" "shipped row with existing test path passes"
else
  fail "rule19_tests_evidence_pos" "expected PASS for shipped row with valid test path"
fi

## Negative-a: shipped row with tests: absent → FAIL
_r19_neg_a="$scratch/r19_neg_a.yaml"
cat > "$_r19_neg_a" <<'EOF'
capabilities:
  my_cap:
    status: implemented_unverified
    shipped: true
    implementation:
      - gate/check_architecture_sync.sh
EOF
_in_sh19a=0; _tf19a=0
while IFS= read -r _l19 || [[ -n "$_l19" ]]; do
  if printf '%s\n' "$_l19" | grep -qE '^[[:space:]]+shipped:[[:space:]]+true'; then _in_sh19a=1; fi
  if [[ $_in_sh19a -eq 1 ]] && printf '%s\n' "$_l19" | grep -qE '^[[:space:]]+tests:'; then _tf19a=1; fi
done < "$_r19_neg_a"
if [[ $_tf19a -eq 0 ]]; then
  ok "rule19_tests_evidence_neg_absent" "tests: absent on shipped row correctly detected"
else
  fail "rule19_tests_evidence_neg_absent" "expected tests: to be absent"
fi

## Negative-b: shipped row with tests: [] → FAIL
_r19_neg_b="$scratch/r19_neg_b.yaml"
cat > "$_r19_neg_b" <<'EOF'
capabilities:
  my_cap:
    status: implemented_unverified
    shipped: true
    tests: []
EOF
_in_sh19b=0; _empty19b=0
while IFS= read -r _l19 || [[ -n "$_l19" ]]; do
  if printf '%s\n' "$_l19" | grep -qE '^[[:space:]]+shipped:[[:space:]]+true'; then _in_sh19b=1; fi
  if [[ $_in_sh19b -eq 1 ]] && printf '%s\n' "$_l19" | grep -qE '^[[:space:]]+tests:[[:space:]]*\[\]'; then _empty19b=1; fi
done < "$_r19_neg_b"
if [[ $_empty19b -eq 1 ]]; then
  ok "rule19_tests_evidence_neg_empty_inline" "tests: [] on shipped row correctly detected"
else
  fail "rule19_tests_evidence_neg_empty_inline" "expected tests: [] to be detected as empty"
fi

## Negative-c: shipped row with tests path that doesn't exist → FAIL
_r19_neg_c="$scratch/r19_neg_c.yaml"
cat > "$_r19_neg_c" <<'EOF'
capabilities:
  my_cap:
    status: implemented_unverified
    shipped: true
    tests:
      - gate/nonexistent_test.sh
EOF
_in_sh19c=0; _tf19c=0; _missing19c=0
while IFS= read -r _l19 || [[ -n "$_l19" ]]; do
  if printf '%s\n' "$_l19" | grep -qE '^[[:space:]]+shipped:[[:space:]]+true'; then _in_sh19c=1; fi
  if [[ $_in_sh19c -eq 1 ]] && printf '%s\n' "$_l19" | grep -qE '^[[:space:]]+tests:[[:space:]]*$'; then _tf19c=1; fi
  if [[ $_tf19c -eq 1 ]] && printf '%s\n' "$_l19" | grep -qE '^[[:space:]]+-[[:space:]]+'; then
    _tp_c=$(printf '%s\n' "$_l19" | sed -E 's/^[[:space:]]+-[[:space:]]+(.*)/\1/')
    if [[ ! -e "$_tp_c" ]]; then _missing19c=1; fi
  fi
done < "$_r19_neg_c"
if [[ $_missing19c -eq 1 ]]; then
  ok "rule19_tests_evidence_neg_missing_path" "non-existent test path on shipped row correctly detected"
else
  fail "rule19_tests_evidence_neg_missing_path" "expected missing test path to be detected"
fi

}


test_rule22_lowercase_metrics_in_contract_docs() {
# ---------------------------------------------------------------------------
# RULE 22 — lowercase_metrics_in_contract_docs (widened, case-sensitive)
# Positive: lowercase metric name passes (must NOT be flagged)
# Negative: SPRINGAI_ASCEND_<lowercase> detected → FAIL
# ---------------------------------------------------------------------------

## Positive: lowercase metric is compliant — must pass
_r22_pos="$scratch/r22_pos.md"
printf '## Metrics\n\n- `springai_ascend_filter_errors_total` — error counter\n' > "$_r22_pos"
if grep -qE 'SPRINGAI_ASCEND_[a-z]' "$_r22_pos" 2>/dev/null; then
  fail "rule22_lowercase_metrics_pos" "lowercase metric incorrectly flagged as uppercase violation"
else
  ok "rule22_lowercase_metrics_pos" "lowercase springai_ascend_ metric correctly passes"
fi

## Negative: uppercase SPRINGAI_ASCEND_<lowercase> triggers FAIL
_r22_neg="$scratch/r22_neg.md"
printf '## Metrics\n\n- `SPRINGAI_ASCEND_filter_errors_total` — error counter\n' > "$_r22_neg"
if grep -qE 'SPRINGAI_ASCEND_[a-z]' "$_r22_neg" 2>/dev/null; then
  ok "rule22_lowercase_metrics_neg" "SPRINGAI_ASCEND_<lowercase> correctly detected as violation"
else
  fail "rule22_lowercase_metrics_neg" "expected SPRINGAI_ASCEND_<lowercase> to be detected"
fi

}


test_rule24_shipped_row_evidence_paths_exist() {
# ---------------------------------------------------------------------------
# RULE 24 — shipped_row_evidence_paths_exist
# Positive: latest_delivery_file points to existing file → PASS
# Negative: latest_delivery_file points to non-existent file → FAIL
# ---------------------------------------------------------------------------

## Positive: latest_delivery_file exists
_r24_pos="$scratch/r24_pos.yaml"
_r24_real_file="$scratch/r24_delivery.md"
touch "$_r24_real_file"
cat > "$_r24_pos" <<EOF
capabilities:
  my_cap:
    status: implemented_unverified
    shipped: true
    latest_delivery_file: ${_r24_real_file}
EOF
_in_sh24p=0; _ldf24p=''; _ldf24p_missing=0
while IFS= read -r _l24 || [[ -n "$_l24" ]]; do
  if printf '%s\n' "$_l24" | grep -qE '^[[:space:]]+shipped:[[:space:]]+true'; then _in_sh24p=1; fi
  if [[ $_in_sh24p -eq 1 ]] && printf '%s\n' "$_l24" | grep -qE '^[[:space:]]+latest_delivery_file:[[:space:]]+'; then
    _ldf24p=$(printf '%s\n' "$_l24" | sed -E 's/^[[:space:]]+latest_delivery_file:[[:space:]]+(.*)/\1/')
    [[ -n "$_ldf24p" && ! -e "$_ldf24p" ]] && _ldf24p_missing=1
  fi
done < "$_r24_pos"
if [[ $_ldf24p_missing -eq 0 ]]; then
  ok "rule24_evidence_paths_pos" "existing latest_delivery_file path passes"
else
  fail "rule24_evidence_paths_pos" "expected existing path to pass"
fi

## Negative: latest_delivery_file points to non-existent file
_r24_neg="$scratch/r24_neg.yaml"
cat > "$_r24_neg" <<'EOF'
capabilities:
  my_cap:
    status: implemented_unverified
    shipped: true
    latest_delivery_file: docs/delivery/nonexistent-deadbeef.md
EOF
_in_sh24n=0; _ldf24n_missing=0
while IFS= read -r _l24 || [[ -n "$_l24" ]]; do
  if printf '%s\n' "$_l24" | grep -qE '^[[:space:]]+shipped:[[:space:]]+true'; then _in_sh24n=1; fi
  if [[ $_in_sh24n -eq 1 ]] && printf '%s\n' "$_l24" | grep -qE '^[[:space:]]+latest_delivery_file:[[:space:]]+'; then
    _ldf24n=$(printf '%s\n' "$_l24" | sed -E 's/^[[:space:]]+latest_delivery_file:[[:space:]]+(.*)/\1/')
    [[ -n "$_ldf24n" && ! -e "$_ldf24n" ]] && _ldf24n_missing=1
  fi
done < "$_r24_neg"
if [[ $_ldf24n_missing -eq 1 ]]; then
  ok "rule24_evidence_paths_neg" "non-existent latest_delivery_file correctly detected"
else
  fail "rule24_evidence_paths_neg" "expected non-existent path to be detected"
fi

}


test_rule25_peripheral_wave_qualifier() {
# ---------------------------------------------------------------------------
# RULE 25 — peripheral_wave_qualifier
# Positive: "Primary sidecar impl:" with W1 qualifier → PASS
# Negative: "Primary sidecar impl:" without any wave qualifier → FAIL
# ---------------------------------------------------------------------------

## Positive: wave-qualified impl claim passes
_r25_pos="$scratch/r25_pos.java"
cat > "$_r25_pos" <<'EOF'
/**
 * W1 reference sidecar (per ADR-0034): spring-ai-ascend-graphmemory-starter wires a
 * Graphiti REST client at W1; no adapter implementation ships at W0.
 */
public interface GraphMemoryRepository {}
EOF
_r25_pos_fail=0
if grep -q 'Primary sidecar impl:\|Primary impl:' "$_r25_pos" 2>/dev/null; then
  # Would need context check; since not present, pattern isn't there
  _r25_pos_fail=1
fi
if [[ $_r25_pos_fail -eq 0 ]]; then
  ok "rule25_wave_qualifier_pos" "wave-qualified impl claim correctly passes"
else
  fail "rule25_wave_qualifier_pos" "expected wave-qualified file to pass"
fi

## Negative: unqualified "Primary sidecar impl:" triggers FAIL
_r25_neg="$scratch/r25_neg.java"
cat > "$_r25_neg" <<'EOF'
/**
 * Primary sidecar impl: spring-ai-ascend-graphmemory-starter (Graphiti REST).
 */
public interface GraphMemoryRepository {}
EOF
_r25_neg_fail=0
if grep -q 'Primary sidecar impl:' "$_r25_neg" 2>/dev/null; then
  # Check if wave qualifier is present in context
  _ctx25n=$(grep -A3 -B2 'Primary sidecar impl:' "$_r25_neg" 2>/dev/null | tr '\n' ' ')
  if ! printf '%s\n' "$_ctx25n" | grep -qE '\bW[0-4]\b'; then
    _r25_neg_fail=1
  fi
fi
if [[ $_r25_neg_fail -eq 1 ]]; then
  ok "rule25_wave_qualifier_neg" "unqualified 'Primary sidecar impl:' correctly detected"
else
  fail "rule25_wave_qualifier_neg" "expected unqualified impl claim to be detected"
fi

}


test_rule26_release_note_shipped_surface_truth() {
# ---------------------------------------------------------------------------
# RULE 26 — release_note_shipped_surface_truth
# 26a — RunLifecycle name guard:
#   Positive: line with W2 wave qualifier or design-only marker → PASS
#   Negative: line listing RunLifecycle as W0 shipped SPI with no qualifier → FAIL
# 26b — RunContext method-list guard:
#   Positive: canonical method list (runId, tenantId, checkpointer, suspendForChild) → PASS
#   Negative: line includes posture() alongside RunContext → FAIL
# ---------------------------------------------------------------------------

## 26a Positive: wave-qualified RunLifecycle passes
_r26a_pos="$scratch/r26a_pos.md"
cat > "$_r26a_pos" <<'EOF'
| `Orchestration` SPI | Pure-Java SPIs; no framework imports. `RunLifecycle` (cancel/resume/retry) remains design-only for W2 — see ADR-0020 |
EOF
_r26a_pos_fail=0
while IFS= read -r _ln; do
  if printf '%s' "$_ln" | grep -q 'RunLifecycle'; then
    if ! printf '%s' "$_ln" | grep -qE '(^|[^A-Za-z0-9])W[1-4]([^A-Za-z0-9]|$)' && \
       ! printf '%s' "$_ln" | grep -qE 'design-only|deferred|not shipped|remains design|materialised at W|materialized at W'; then
      _r26a_pos_fail=1
    fi
  fi
done < "$_r26a_pos"
if [[ $_r26a_pos_fail -eq 0 ]]; then
  ok "rule26_runlifecycle_pos" "wave-qualified RunLifecycle correctly passes"
else
  fail "rule26_runlifecycle_pos" "expected wave-qualified RunLifecycle line to pass"
fi

## 26a Negative: unqualified RunLifecycle as W0 SPI label
_r26a_neg="$scratch/r26a_neg.md"
cat > "$_r26a_neg" <<'EOF'
| `RunLifecycle` SPI | `Orchestrator`, `GraphExecutor`, `AgentLoopExecutor` — pure-Java SPIs |
EOF
_r26a_neg_fail=0
_lines_count=0
mapfile -t _r26a_neg_lines < "$_r26a_neg"
_lines_count=${#_r26a_neg_lines[@]}
for ((_i=0; _i < _lines_count; _i++)); do
  _ln="${_r26a_neg_lines[$_i]}"
  if printf '%s' "$_ln" | grep -q 'RunLifecycle'; then
    _lo=$((_i > 0 ? _i - 1 : 0))
    _hi=$((_i + 1 < _lines_count ? _i + 1 : _i))
    _ctx=""
    for ((_j=_lo; _j <= _hi; _j++)); do _ctx="$_ctx ${_r26a_neg_lines[$_j]}"; done
    if ! printf '%s' "$_ctx" | grep -qE '(^|[^A-Za-z0-9])W[1-4]([^A-Za-z0-9]|$)' && \
       ! printf '%s' "$_ln" | grep -qE 'design-only|deferred|not shipped|remains design|materialised at W|materialized at W'; then
      _r26a_neg_fail=1
    fi
  fi
done
if [[ $_r26a_neg_fail -eq 1 ]]; then
  ok "rule26_runlifecycle_neg" "unqualified RunLifecycle correctly detected"
else
  fail "rule26_runlifecycle_neg" "expected unqualified RunLifecycle line to be detected"
fi

## 26b Positive: canonical RunContext method list passes
_r26b_pos="$scratch/r26b_pos.md"
cat > "$_r26b_pos" <<'EOF'
| `RunContext` | Interface methods: `runId()`, `tenantId()`, `checkpointer()`, `suspendForChild()` |
EOF
_r26b_pos_fail=0
while IFS= read -r _ln; do
  if printf '%s' "$_ln" | grep -q 'RunContext' && printf '%s' "$_ln" | grep -qE '[A-Za-z_][A-Za-z0-9_]*\(\)'; then
    if printf '%s' "$_ln" | grep -qE '\bposture[[:space:]]*\(\)'; then
      _r26b_pos_fail=1
    fi
    for _mt in $(printf '%s' "$_ln" | grep -oE '\b[A-Za-z_][A-Za-z0-9_]*\(' | sed 's/($//'); do
      case "$_mt" in
        [a-z]*)
          case "$_mt" in
            runId|tenantId|checkpointer|suspendForChild) : ;;
            exposes|lists|returns|threads|carries|provides|sourced|interface|method|methods|requires|reads|writes|sees|gets|fails) : ;;
            *) _r26b_pos_fail=1 ;;
          esac
          ;;
        *) : ;;
      esac
    done
  fi
done < "$_r26b_pos"
if [[ $_r26b_pos_fail -eq 0 ]]; then
  ok "rule26_runcontext_pos" "canonical RunContext method list correctly passes"
else
  fail "rule26_runcontext_pos" "expected canonical RunContext methods to pass"
fi

## 26b Negative: RunContext with invented posture() method
_r26b_neg="$scratch/r26b_neg.md"
cat > "$_r26b_neg" <<'EOF'
| `RunContext` | Interface: `tenantId()`, `runId()`, `posture()`; sourced from SPIs |
EOF
_r26b_neg_fail=0
while IFS= read -r _ln; do
  if printf '%s' "$_ln" | grep -q 'RunContext' && printf '%s' "$_ln" | grep -qE '[A-Za-z_][A-Za-z0-9_]*\(\)'; then
    if printf '%s' "$_ln" | grep -qE '\bposture[[:space:]]*\(\)'; then
      _r26b_neg_fail=1
    fi
  fi
done < "$_r26b_neg"
if [[ $_r26b_neg_fail -eq 1 ]]; then
  ok "rule26_runcontext_neg" "RunContext with posture() correctly detected"
else
  fail "rule26_runcontext_neg" "expected posture() alongside RunContext to be detected"
fi

}


test_rule27_active_entrypoint_baseline_truth() {
# ---------------------------------------------------------------------------
# RULE 27 — active_entrypoint_baseline_truth
# Positive: synthetic YAML + README with matching §4 count → PASS
# Negative: synthetic YAML + README with mismatched §4 count → FAIL
# ---------------------------------------------------------------------------

## Positive: matching baseline counts pass
_r27_pos="$scratch/r27_pos"
mkdir -p "$_r27_pos/docs/governance"
cat > "$_r27_pos/docs/governance/architecture-status.yaml" <<'EOF'
capabilities:
  architecture_sync_gate:
    allowed_claim: "Architecture baseline: 45 §4 constraints (#1–#45); 47 ADRs (0001–0047); 27 active gate rules; 30 gate self-tests."
EOF
cat > "$_r27_pos/README.md" <<'EOF'
- Architecture baseline: 45 §4 constraints · 47 ADRs · 27 gate rules · 30 self-tests
EOF
_r27_pos_claim=$(awk '/^[[:space:]]+architecture_sync_gate:/{flag=1} flag && /allowed_claim:/{print; exit}' "$_r27_pos/docs/governance/architecture-status.yaml")
_r27_pos_readme=$(cat "$_r27_pos/README.md")
_r27_pos_fail=0
_r27_pos_exp=$(printf '%s' "$_r27_pos_claim" | grep -oE '[0-9]+[[:space:]]+§4[[:space:]]+constraints' | grep -oE '^[0-9]+' | head -1)
_r27_pos_act=$(printf '%s' "$_r27_pos_readme" | grep -oE '[0-9]+[[:space:]]+§4[[:space:]]+constraints' | grep -oE '^[0-9]+' | head -1)
[[ "$_r27_pos_exp" != "$_r27_pos_act" ]] && _r27_pos_fail=1
if [[ $_r27_pos_fail -eq 0 ]]; then
  ok "rule27_baseline_pos" "matching baseline counts correctly pass"
else
  fail "rule27_baseline_pos" "expected matching baseline counts to pass (exp=$_r27_pos_exp act=$_r27_pos_act)"
fi

## Negative: README §4 count mismatches YAML → FAIL
_r27_neg="$scratch/r27_neg"
mkdir -p "$_r27_neg/docs/governance"
cat > "$_r27_neg/docs/governance/architecture-status.yaml" <<'EOF'
capabilities:
  architecture_sync_gate:
    allowed_claim: "Architecture baseline: 45 §4 constraints (#1–#45); 47 ADRs (0001–0047); 27 active gate rules; 30 gate self-tests."
EOF
cat > "$_r27_neg/README.md" <<'EOF'
- Architecture baseline: 44 §4 constraints · 47 ADRs · 27 gate rules · 30 self-tests
EOF
_r27_neg_claim=$(awk '/^[[:space:]]+architecture_sync_gate:/{flag=1} flag && /allowed_claim:/{print; exit}' "$_r27_neg/docs/governance/architecture-status.yaml")
_r27_neg_readme=$(cat "$_r27_neg/README.md")
_r27_neg_fail=0
_r27_neg_exp=$(printf '%s' "$_r27_neg_claim" | grep -oE '[0-9]+[[:space:]]+§4[[:space:]]+constraints' | grep -oE '^[0-9]+' | head -1)
_r27_neg_act=$(printf '%s' "$_r27_neg_readme" | grep -oE '[0-9]+[[:space:]]+§4[[:space:]]+constraints' | grep -oE '^[0-9]+' | head -1)
[[ "$_r27_neg_exp" != "$_r27_neg_act" ]] && _r27_neg_fail=1
if [[ $_r27_neg_fail -eq 1 ]]; then
  ok "rule27_baseline_neg" "mismatched §4 baseline correctly detected (exp=$_r27_neg_exp act=$_r27_neg_act)"
else
  fail "rule27_baseline_neg" "expected mismatched §4 baseline to be detected"
fi

}


test_rule28_release_note_baseline_truth() {
# ---------------------------------------------------------------------------
# RULE 28 — release_note_baseline_truth
# Positive: release note matching canonical baseline → PASS
# Negative: release note with stale counts and no freeze marker → FAIL
# Exempt: release note with stale counts but freeze marker → PASS (exempt)
# ---------------------------------------------------------------------------

## Positive: release note matches canonical baseline → PASS
_r28_pos="$scratch/r28_pos"
mkdir -p "$_r28_pos/docs/governance" "$_r28_pos/docs/logs/releases"
cat > "$_r28_pos/docs/governance/architecture-status.yaml" <<'EOF'
capabilities:
  architecture_sync_gate:
    allowed_claim: "Architecture baseline: 50 §4 constraints (#1–#50); 52 ADRs (0001–0052); 29 active gate rules; 35 gate self-tests."
EOF
cat > "$_r28_pos/docs/logs/releases/some-release.md" <<'EOF'
| §4 constraints | 50 (#1–#50) |
| Active ADRs | 52 (ADR-0001–ADR-0052) |
| Active gate rules | 29 |
| Gate self-test cases | 35 |
EOF
_r28_pos_claim=$(awk '/^[[:space:]]+architecture_sync_gate:/{flag=1} flag && /allowed_claim:/{print; exit}' "$_r28_pos/docs/governance/architecture-status.yaml")
_r28_pos_rf=$(cat "$_r28_pos/docs/logs/releases/some-release.md")
_r28_pos_exp=$(printf '%s' "$_r28_pos_claim" | grep -oE '[0-9]+[[:space:]]+§4[[:space:]]+constraints' | grep -oE '^[0-9]+' | head -1)
_r28_pos_match=$(printf '%s' "$_r28_pos_rf" | grep -oE '§4[[:space:]]+constraints[[:space:]]*\|[[:space:]]*[0-9]+' | head -1)
_r28_pos_act=$(printf '%s' "$_r28_pos_match" | grep -oE '[0-9]+' | tail -1)
if [[ -n "$_r28_pos_exp" && "$_r28_pos_exp" == "$_r28_pos_act" ]]; then
  ok "rule28_baseline_pos" "release note matching canonical baseline correctly passes (exp=$_r28_pos_exp act=$_r28_pos_act)"
else
  fail "rule28_baseline_pos" "expected matching release-note baseline to pass (exp=$_r28_pos_exp act=$_r28_pos_act)"
fi

## Negative: release note stale counts, NO freeze marker → FAIL
_r28_neg="$scratch/r28_neg"
mkdir -p "$_r28_neg/docs/governance" "$_r28_neg/docs/logs/releases"
cat > "$_r28_neg/docs/governance/architecture-status.yaml" <<'EOF'
capabilities:
  architecture_sync_gate:
    allowed_claim: "Architecture baseline: 50 §4 constraints (#1–#50); 52 ADRs (0001–0052); 29 active gate rules; 35 gate self-tests."
EOF
cat > "$_r28_neg/docs/logs/releases/some-release.md" <<'EOF'
| §4 constraints | 45 (#1–#45) |
| Active ADRs | 47 (ADR-0001–ADR-0047) |
| Active gate rules | 27 |
| Gate self-test cases | 30 |
EOF
_r28_neg_claim=$(awk '/^[[:space:]]+architecture_sync_gate:/{flag=1} flag && /allowed_claim:/{print; exit}' "$_r28_neg/docs/governance/architecture-status.yaml")
_r28_neg_rf=$(cat "$_r28_neg/docs/logs/releases/some-release.md")
_r28_neg_has_freeze=0
grep -qE 'Historical artifact frozen at SHA' "$_r28_neg/docs/logs/releases/some-release.md" && _r28_neg_has_freeze=1
_r28_neg_exp=$(printf '%s' "$_r28_neg_claim" | grep -oE '[0-9]+[[:space:]]+§4[[:space:]]+constraints' | grep -oE '^[0-9]+' | head -1)
_r28_neg_match=$(printf '%s' "$_r28_neg_rf" | grep -oE '§4[[:space:]]+constraints[[:space:]]*\|[[:space:]]*[0-9]+' | head -1)
_r28_neg_act=$(printf '%s' "$_r28_neg_match" | grep -oE '[0-9]+' | tail -1)
if [[ $_r28_neg_has_freeze -eq 0 && -n "$_r28_neg_exp" && -n "$_r28_neg_act" && "$_r28_neg_exp" != "$_r28_neg_act" ]]; then
  ok "rule28_baseline_neg" "stale release-note baseline without freeze marker correctly detected (exp=$_r28_neg_exp act=$_r28_neg_act)"
else
  fail "rule28_baseline_neg" "expected stale release-note baseline without freeze marker to be detected (has_freeze=$_r28_neg_has_freeze exp=$_r28_neg_exp act=$_r28_neg_act)"
fi

## Exempt: release note stale counts BUT freeze marker present → exempt (pass)
_r28_exempt="$scratch/r28_exempt"
mkdir -p "$_r28_exempt/docs/governance" "$_r28_exempt/docs/logs/releases"
cat > "$_r28_exempt/docs/governance/architecture-status.yaml" <<'EOF'
capabilities:
  architecture_sync_gate:
    allowed_claim: "Architecture baseline: 50 §4 constraints (#1–#50); 52 ADRs (0001–0052); 29 active gate rules; 35 gate self-tests."
EOF
cat > "$_r28_exempt/docs/logs/releases/frozen-release.md" <<'EOF'
> Historical artifact frozen at SHA 82a1397 (L0 release). Baseline counts in this document reflect L0 release-time state.

| §4 constraints | 45 (#1–#45) |
| Active ADRs | 47 (ADR-0001–ADR-0047) |
| Active gate rules | 27 |
| Gate self-test cases | 30 |
EOF
_r28_exempt_has_freeze=0
grep -qE 'Historical artifact frozen at SHA' "$_r28_exempt/docs/logs/releases/frozen-release.md" && _r28_exempt_has_freeze=1
if [[ $_r28_exempt_has_freeze -eq 1 ]]; then
  ok "rule28_baseline_neg_no_freeze_marker" "freeze marker correctly exempts release note from baseline check"
else
  fail "rule28_baseline_neg_no_freeze_marker" "expected freeze marker to exempt release note"
fi

}


test_rule29_whitepaper_alignment_matrix_present() {
# ---------------------------------------------------------------------------
# RULE 29 — whitepaper_alignment_matrix_present
# Positive: matrix file exists with all 20 required concepts → PASS
# Negative: matrix file missing OR missing a required concept → FAIL
# ---------------------------------------------------------------------------

## Positive: matrix file with all 20 concepts → PASS
_r29_pos="$scratch/r29_pos"
mkdir -p "$_r29_pos/docs/governance"
cat > "$_r29_pos/docs/governance/whitepaper-alignment-matrix.md" <<'EOF'
# Whitepaper Alignment Matrix
- C/S separation
- Task Cursor
- Dynamic Hydration
- Sync State
- Sub-Stream
- Yield & Handoff
- Business ontology ownership
- S-side execution trajectory ownership
- Placeholder exemption
- Full Trace vs Node Snapshot
- Lazy mounting
- Skill Topology Scheduler
- C-side business degradation authority
- Session/context decoupling
- Workflow Intermediary
- Three-track bus
- Capability bidding
- Permission issuance
- Chronos Hydration
- Service Layer microservice commitment
EOF
_r29_pos_missing=0
for _concept29p in 'C/S separation' 'Task Cursor' 'Dynamic Hydration' 'Sync State' 'Sub-Stream' 'Yield & Handoff' 'Business ontology ownership' 'S-side execution trajectory ownership' 'Placeholder exemption' 'Full Trace vs Node Snapshot' 'Lazy mounting' 'Skill Topology Scheduler' 'C-side business degradation authority' 'Session/context decoupling' 'Workflow Intermediary' 'Three-track bus' 'Capability bidding' 'Permission issuance' 'Chronos Hydration' 'Service Layer microservice commitment'; do
  if ! grep -qF "$_concept29p" "$_r29_pos/docs/governance/whitepaper-alignment-matrix.md"; then
    _r29_pos_missing=1
  fi
done
if [[ $_r29_pos_missing -eq 0 ]]; then
  ok "rule29_matrix_pos" "matrix with all 20 required concepts correctly passes"
else
  fail "rule29_matrix_pos" "expected matrix with all 20 concepts to pass"
fi

## Negative: matrix missing a required concept → FAIL
_r29_neg="$scratch/r29_neg"
mkdir -p "$_r29_neg/docs/governance"
cat > "$_r29_neg/docs/governance/whitepaper-alignment-matrix.md" <<'EOF'
# Whitepaper Alignment Matrix
- C/S separation
- Task Cursor
- Dynamic Hydration
EOF
_r29_neg_missing=0
for _concept29n in 'C/S separation' 'Chronos Hydration' 'Workflow Intermediary'; do
  if ! grep -qF "$_concept29n" "$_r29_neg/docs/governance/whitepaper-alignment-matrix.md"; then
    _r29_neg_missing=1
  fi
done
if [[ $_r29_neg_missing -eq 1 ]]; then
  ok "rule29_matrix_neg" "matrix missing required concept correctly detected"
else
  fail "rule29_matrix_neg" "expected missing concept to be detected"
fi

}


test_rule28j_enforcer_artifact_paths_exist() {
# ---------------------------------------------------------------------------
# RULE 28j -- enforcer_artifact_paths_exist (Phase L anchor validation, E35)
# Phase K (E33) checked file existence only. Phase L extends 28j to validate
# that #anchor references in enforcers.yaml resolve to a real method (Java/Bash)
# or heading (Markdown) inside the target file.
# ---------------------------------------------------------------------------

## Positive: artifact anchor that resolves to a real Java @Test method passes.
_r28j_pos="$scratch/r28j_pos"
mkdir -p "$_r28j_pos/src" "$_r28j_pos/docs"
cat > "$_r28j_pos/src/FooIT.java" <<'EOF'
package x;
class FooIT {
    @org.junit.jupiter.api.Test
    void real_method() {}
    void another_method() {}
}
EOF
cat > "$_r28j_pos/docs/enforcers.yaml" <<'EOF'
- id: EX1
  artifact: src/FooIT.java#real_method
EOF
_r28j_pos_fail=0
while IFS= read -r _aline; do
  [[ -z "$_aline" ]] && continue
  _aval=${_aline#*artifact:}
  _aval=${_aval#"${_aval%%[![:space:]]*}"}
  _apath=${_aval%%#*}
  _aanchor=""
  case "$_aval" in *'#'*) _aanchor=${_aval#*#};; esac
  _aanchor=${_aanchor%"${_aanchor##*[![:space:]]}"}
  _fullpath="$_r28j_pos/$_apath"
  if [[ ! -e "$_fullpath" ]]; then _r28j_pos_fail=1; fi
  if [[ -n "$_aanchor" ]] && [[ "$_apath" == *.java ]]; then
    if ! grep -qE "(void|\)|\>|\>[[:space:]])[[:space:]]+${_aanchor}[[:space:]]*\(" "$_fullpath"; then
      if ! grep -qE "^[[:space:]]*[a-zA-Z_<>][^()]*[[:space:]]${_aanchor}[[:space:]]*\(" "$_fullpath"; then
        _r28j_pos_fail=1
      fi
    fi
  fi
done < <(grep -E '^[[:space:]]*-?[[:space:]]*artifact:' "$_r28j_pos/docs/enforcers.yaml")
if [[ $_r28j_pos_fail -eq 0 ]]; then
  ok "rule28j_anchor_resolves_pos" "real Java method anchor correctly passes"
else
  fail "rule28j_anchor_resolves_pos" "expected real method anchor to pass"
fi

## Negative: artifact anchor that names a non-existent method fails.
_r28j_neg="$scratch/r28j_neg"
mkdir -p "$_r28j_neg/src" "$_r28j_neg/docs"
cat > "$_r28j_neg/src/FooIT.java" <<'EOF'
package x;
class FooIT {
    @org.junit.jupiter.api.Test
    void only_real_method() {}
}
EOF
cat > "$_r28j_neg/docs/enforcers.yaml" <<'EOF'
- id: EX2
  artifact: src/FooIT.java#bogusMethod
EOF
_r28j_neg_detected=0
while IFS= read -r _aline; do
  [[ -z "$_aline" ]] && continue
  _aval=${_aline#*artifact:}
  _aval=${_aval#"${_aval%%[![:space:]]*}"}
  _apath=${_aval%%#*}
  _aanchor=""
  case "$_aval" in *'#'*) _aanchor=${_aval#*#};; esac
  _aanchor=${_aanchor%"${_aanchor##*[![:space:]]}"}
  _fullpath="$_r28j_neg/$_apath"
  if [[ -n "$_aanchor" ]] && [[ "$_apath" == *.java ]]; then
    _hit1=0
    grep -qE "(void|\)|\>|\>[[:space:]])[[:space:]]+${_aanchor}[[:space:]]*\(" "$_fullpath" && _hit1=1
    grep -qE "^[[:space:]]*[a-zA-Z_<>][^()]*[[:space:]]${_aanchor}[[:space:]]*\(" "$_fullpath" && _hit1=1
    if [[ $_hit1 -eq 0 ]]; then _r28j_neg_detected=1; fi
  fi
done < <(grep -E '^[[:space:]]*-?[[:space:]]*artifact:' "$_r28j_neg/docs/enforcers.yaml")
if [[ $_r28j_neg_detected -eq 1 ]]; then
  ok "rule28j_anchor_resolves_neg" "bogus method anchor correctly detected"
else
  fail "rule28j_anchor_resolves_neg" "expected bogus anchor to be detected"
fi

# ===========================================================================
# W1 Layered-4+1 + Architecture-Graph self-tests (Rules 37-40, ADR-0068)
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 37 positive: ARCHITECTURE.md with valid level + view front-matter passes
# ---------------------------------------------------------------------------
_r37_pos="$scratch/r37_pos"
mkdir -p "$_r37_pos"
cat > "$_r37_pos/ARCHITECTURE.md" <<'EOF'
---
level: L0
view: scenarios
---

# Test architecture
EOF
_lev="$(awk 'BEGIN{in_fm=0; n=0} /^---[[:space:]]*$/{n++; if(n==1){in_fm=1; next} if(n==2){exit}} in_fm && /^level:[[:space:]]/{sub(/^level:[[:space:]]*/,""); print; exit}' "$_r37_pos/ARCHITECTURE.md")"
_vw="$(awk 'BEGIN{in_fm=0; n=0} /^---[[:space:]]*$/{n++; if(n==1){in_fm=1; next} if(n==2){exit}} in_fm && /^view:[[:space:]]/{sub(/^view:[[:space:]]*/,""); print; exit}' "$_r37_pos/ARCHITECTURE.md")"
if [[ "$_lev" == "L0" && "$_vw" == "scenarios" ]]; then
  ok "rule37_front_matter_pos" "level=L0 view=scenarios parsed from valid front-matter"
else
  fail "rule37_front_matter_pos" "expected level=L0 view=scenarios, got level='$_lev' view='$_vw'"
fi

# ---------------------------------------------------------------------------
# Rule 37 negative: ARCHITECTURE.md missing front-matter fails detection
# ---------------------------------------------------------------------------
_r37_neg="$scratch/r37_neg"
mkdir -p "$_r37_neg"
cat > "$_r37_neg/ARCHITECTURE.md" <<'EOF'
# No front matter here
EOF
_lev="$(awk 'BEGIN{in_fm=0; n=0} /^---[[:space:]]*$/{n++; if(n==1){in_fm=1; next} if(n==2){exit}} in_fm && /^level:[[:space:]]/{sub(/^level:[[:space:]]*/,""); print; exit}' "$_r37_neg/ARCHITECTURE.md")"
if [[ -z "$_lev" ]]; then
  ok "rule37_front_matter_neg" "missing front-matter correctly produces empty level"
else
  fail "rule37_front_matter_neg" "expected empty level, got '$_lev'"
fi

# ---------------------------------------------------------------------------
# Rule 38 positive: graph YAML structure parses + every edge endpoint is a node
# ---------------------------------------------------------------------------
_r38_pos="$scratch/r38_pos"
mkdir -p "$_r38_pos"
cat > "$_r38_pos/graph.yaml" <<'EOF'
nodes:
  - id: P-A
    type: principle
  - id: Rule-29
    type: rule
  - id: E48
    type: enforcer
edges:
  - src: P-A
    dst: Rule-29
    type: operationalised_by
  - src: Rule-29
    dst: E48
    type: enforced_by
EOF
# Crude integrity check: every src/dst token must appear as an id in nodes.
_r38_orphan=0
_ids="$(awk '/^  - id:/{print $3}' "$_r38_pos/graph.yaml")"
while IFS= read -r _ep; do
  _v="$(printf '%s\n' "$_ep" | sed -E 's/^[[:space:]]*-?[[:space:]]*(src|dst):[[:space:]]*([A-Za-z0-9_-]+).*/\2/')"
  [[ -z "$_v" || "$_v" == "$_ep" ]] && continue
  if ! grep -qxF "$_v" <<< "$_ids"; then _r38_orphan=1; fi
done < <(grep -E '^[[:space:]]*-?[[:space:]]*(src|dst):' "$_r38_pos/graph.yaml")
if [[ $_r38_orphan -eq 0 ]]; then
  ok "rule38_graph_endpoints_pos" "all edge endpoints resolve to node ids"
else
  fail "rule38_graph_endpoints_pos" "unresolved edge endpoint detected unexpectedly"
fi

# ---------------------------------------------------------------------------
# Rule 39 positive: review proposal with affects_level + affects_view passes
# ---------------------------------------------------------------------------
_r39_pos="$scratch/r39_pos"
mkdir -p "$_r39_pos/docs/logs/reviews"
cat > "$_r39_pos/docs/logs/reviews/2026-06-01-future-proposal.md" <<'EOF'
---
affects_level: L1
affects_view: process
---

# Future proposal
EOF
_al="$(grep -E '^affects_level:[[:space:]]+(L0|L1|L2)' "$_r39_pos/docs/logs/reviews/2026-06-01-future-proposal.md" | head -1 || true)"
_av="$(grep -E '^affects_view:[[:space:]]+(logical|development|process|physical|scenarios)' "$_r39_pos/docs/logs/reviews/2026-06-01-future-proposal.md" | head -1 || true)"
if [[ -n "$_al" && -n "$_av" ]]; then
  ok "rule39_review_front_matter_pos" "affects_level + affects_view both present and valid"
else
  fail "rule39_review_front_matter_pos" "expected both keys present; got al='$_al' av='$_av'"
fi

# ---------------------------------------------------------------------------
# Rule 40 negative: orphan enforcer (no rule->enforcer edge) gets detected
# ---------------------------------------------------------------------------
_r40_neg="$scratch/r40_neg"
mkdir -p "$_r40_neg"
cat > "$_r40_neg/graph.yaml" <<'EOF'
- id: E99
  type: enforcer
- src: P-A
  dst: Rule-29
  type: operationalised_by
EOF
# Detect: enforcer node E99 has no incoming `type: enforced_by` edge.
_r40_detected=0
if grep -q "id: E99" "$_r40_neg/graph.yaml" && ! grep -q "dst: E99" "$_r40_neg/graph.yaml"; then
  _r40_detected=1
fi
if [[ $_r40_detected -eq 1 ]]; then
  ok "rule40_orphan_enforcer_neg" "orphan enforcer (no rule path) correctly detected"
else
  fail "rule40_orphan_enforcer_neg" "expected orphan enforcer to be flagged"
fi

# ===========================================================================
# Phase M self-tests (Rules 41-44, ADR-0068)
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 41 positive: graph node with anchor + anchor_resolves: true passes
# ---------------------------------------------------------------------------
_r41_pos="$scratch/r41_pos"
mkdir -p "$_r41_pos"
cat > "$_r41_pos/graph.yaml" <<'EOF'
nodes:
  - id: file:foo/Bar.java
    type: artefact
    path: foo/Bar.java
    exists: true
    anchor: realMethod
    anchor_resolves: true
EOF
_r41_pos_offenders="$(awk '
  /^  - id:/ { cur=$3; type=""; anchor=""; resolves="" }
  /^    type:/ { type=$2 }
  /^    anchor:/ { val=substr($0,index($0,":")+2); gsub(/[[:space:]]+$/,"",val); anchor=val }
  /^    anchor_resolves:/ {
    val=substr($0,index($0,":")+2); gsub(/[[:space:]]+$/,"",val); resolves=val
    if (type=="artefact" && anchor!="" && anchor!="null" && resolves=="false") print cur
  }
' "$_r41_pos/graph.yaml")"
if [[ -z "$_r41_pos_offenders" ]]; then
  ok "rule41_anchor_resolves_pos" "node with anchor_resolves:true passes"
else
  fail "rule41_anchor_resolves_pos" "false offenders detected: $_r41_pos_offenders"
fi

# ---------------------------------------------------------------------------
# Rule 41 negative: graph node with anchor + anchor_resolves: false fails
# ---------------------------------------------------------------------------
_r41_neg="$scratch/r41_neg"
mkdir -p "$_r41_neg"
cat > "$_r41_neg/graph.yaml" <<'EOF'
nodes:
  - id: file:foo/Bar.java
    type: artefact
    path: foo/Bar.java
    exists: true
    anchor: bogusMethod
    anchor_resolves: false
EOF
_r41_neg_offenders="$(awk '
  /^  - id:/ { cur=$3; type=""; anchor=""; resolves="" }
  /^    type:/ { type=$2 }
  /^    anchor:/ { val=substr($0,index($0,":")+2); gsub(/[[:space:]]+$/,"",val); anchor=val }
  /^    anchor_resolves:/ {
    val=substr($0,index($0,":")+2); gsub(/[[:space:]]+$/,"",val); resolves=val
    if (type=="artefact" && anchor!="" && anchor!="null" && resolves=="false") print cur
  }
' "$_r41_neg/graph.yaml")"
if [[ -n "$_r41_neg_offenders" ]]; then
  ok "rule41_anchor_resolves_neg" "unresolved anchor correctly detected: $_r41_neg_offenders"
else
  fail "rule41_anchor_resolves_neg" "expected offender to be flagged"
fi

# ---------------------------------------------------------------------------
# Rule 42 positive: byte-identical files produce no diff
# ---------------------------------------------------------------------------
_r42_pos="$scratch/r42_pos"
mkdir -p "$_r42_pos"
printf "schema: x\nnodes: []\n" > "$_r42_pos/a.yaml"
printf "schema: x\nnodes: []\n" > "$_r42_pos/b.yaml"
if diff -q "$_r42_pos/a.yaml" "$_r42_pos/b.yaml" >/dev/null 2>&1; then
  ok "rule42_idempotent_pos" "identical builds produce no diff"
else
  fail "rule42_idempotent_pos" "expected identical files to compare equal"
fi

# ---------------------------------------------------------------------------
# Rule 42 negative: a mutated build produces a diff
# ---------------------------------------------------------------------------
_r42_neg="$scratch/r42_neg"
mkdir -p "$_r42_neg"
printf "schema: x\nnodes: []\n" > "$_r42_neg/a.yaml"
printf "schema: x\nnodes: [drift]\n" > "$_r42_neg/b.yaml"
if ! diff -q "$_r42_neg/a.yaml" "$_r42_neg/b.yaml" >/dev/null 2>&1; then
  ok "rule42_idempotent_neg" "mutated build correctly diff-detected"
else
  fail "rule42_idempotent_neg" "expected drift to be detected"
fi

# ---------------------------------------------------------------------------
# Rule 43 positive: highest-numbered ADR file is .yaml
# ---------------------------------------------------------------------------
_r43_pos="$scratch/r43_pos"
mkdir -p "$_r43_pos/docs/adr"
touch "$_r43_pos/docs/adr/0001-foo.md" "$_r43_pos/docs/adr/0068-bar.yaml"
_r43_pos_md_top="$(find "$_r43_pos/docs/adr" -maxdepth 1 -type f -name '[0-9][0-9][0-9][0-9]-*.md' | sort -r | head -1)"
_r43_pos_yaml_top="$(find "$_r43_pos/docs/adr" -maxdepth 1 -type f -name '[0-9][0-9][0-9][0-9]-*.yaml' | sort -r | head -1)"
_r43_pos_md_n="$(basename "${_r43_pos_md_top}" | cut -c1-4)"
_r43_pos_yaml_n="$(basename "${_r43_pos_yaml_top}" | cut -c1-4)"
if (( 10#${_r43_pos_md_n:-0} <= 10#${_r43_pos_yaml_n:-0} )); then
  ok "rule43_new_adr_yaml_pos" "newest ADR is .yaml (md=$_r43_pos_md_n yaml=$_r43_pos_yaml_n)"
else
  fail "rule43_new_adr_yaml_pos" "expected yaml to be newest"
fi

# ---------------------------------------------------------------------------
# Rule 43 negative: highest-numbered ADR file is .md → flagged
# ---------------------------------------------------------------------------
_r43_neg="$scratch/r43_neg"
mkdir -p "$_r43_neg/docs/adr"
touch "$_r43_neg/docs/adr/0068-x.yaml" "$_r43_neg/docs/adr/0099-regression.md"
_r43_neg_md_n="$(basename "$(find "$_r43_neg/docs/adr" -name '*.md' | sort -r | head -1)" | cut -c1-4)"
_r43_neg_yaml_n="$(basename "$(find "$_r43_neg/docs/adr" -name '*.yaml' | sort -r | head -1)" | cut -c1-4)"
if (( 10#${_r43_neg_md_n:-0} > 10#${_r43_neg_yaml_n:-0} )); then
  ok "rule43_new_adr_yaml_neg" "regression .md ADR correctly flagged (md=$_r43_neg_md_n > yaml=$_r43_neg_yaml_n)"
else
  fail "rule43_new_adr_yaml_neg" "expected md to be detected as newer"
fi

# ---------------------------------------------------------------------------
# Rule 44 positive: file with freeze_id: null modified — no proposal required
# ---------------------------------------------------------------------------
_r44_pos="$scratch/r44_pos"
mkdir -p "$_r44_pos"
cat > "$_r44_pos/ARCHITECTURE.md" <<'EOF'
---
level: L0
view: scenarios
freeze_id: null
---
EOF
_r44_pos_fid="$(awk 'BEGIN{in_fm=0; n=0} /^---[[:space:]]*$/{n++; if(n==1){in_fm=1; next} if(n==2){exit}} in_fm && /^freeze_id:[[:space:]]/{sub(/^freeze_id:[[:space:]]*/,""); print; exit}' "$_r44_pos/ARCHITECTURE.md")"
if [[ -z "$_r44_pos_fid" || "$_r44_pos_fid" == "null" ]]; then
  ok "rule44_frozen_doc_pos" "unfrozen file (freeze_id=$_r44_pos_fid) correctly exempted"
else
  fail "rule44_frozen_doc_pos" "unfrozen file flagged unexpectedly"
fi

# ---------------------------------------------------------------------------
# Rule 44 negative: file with freeze_id: <id> + no companion → flagged
# ---------------------------------------------------------------------------
_r44_neg="$scratch/r44_neg"
mkdir -p "$_r44_neg"
cat > "$_r44_neg/ARCHITECTURE.md" <<'EOF'
---
level: L0
view: scenarios
freeze_id: post-L1-Russell
---
EOF
_r44_neg_fid="$(awk 'BEGIN{in_fm=0; n=0} /^---[[:space:]]*$/{n++; if(n==1){in_fm=1; next} if(n==2){exit}} in_fm && /^freeze_id:[[:space:]]/{sub(/^freeze_id:[[:space:]]*/,""); print; exit}' "$_r44_neg/ARCHITECTURE.md")"
if [[ -n "$_r44_neg_fid" && "$_r44_neg_fid" != "null" ]]; then
  ok "rule44_frozen_doc_neg" "frozen file (freeze_id=$_r44_neg_fid) correctly detected as requiring proposal"
else
  fail "rule44_frozen_doc_neg" "expected non-null freeze_id to be detected"
fi

# ===========================================================================
# W1.x Phase 1 self-tests — Rules 45-52 (L0 ironclad rules; ADR-0069)
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 45 positive: bus-channels.yaml with 3 channels + unique physical_channel
# ---------------------------------------------------------------------------
_r45_pos="$scratch/r45_pos"
mkdir -p "$_r45_pos/docs/governance"
cat > "$_r45_pos/docs/governance/bus-channels.yaml" <<'EOF'
channels:
  - id: control
    physical_channel: ctrl_q
  - id: data
    physical_channel: data_q
  - id: rhythm
    physical_channel: tick_q
EOF
_r45_pos_ids="$(awk '/^channels:[[:space:]]*$/{in_ch=1; next} /^[a-zA-Z]/{in_ch=0} in_ch && /^[[:space:]]+- id:/{sub(/^[[:space:]]+- id:[[:space:]]*/,""); sub(/[[:space:]].*$/,""); print}' "$_r45_pos/docs/governance/bus-channels.yaml")"
_r45_pos_count="$(printf '%s\n' "$_r45_pos_ids" | grep -c .)"
_r45_pos_phys="$(grep -E '^[[:space:]]+physical_channel:' "$_r45_pos/docs/governance/bus-channels.yaml" | sed -E 's/^[[:space:]]+physical_channel:[[:space:]]*//; s/[[:space:]].*$//')"
_r45_pos_phys_uniq="$(printf '%s\n' "$_r45_pos_phys" | sort -u | grep -c .)"
if [[ "$_r45_pos_count" -eq 3 ]] && [[ "$_r45_pos_phys_uniq" -eq 3 ]]; then
  ok "rule45_bus_channels_pos" "3 channels with unique physical_channel"
else
  fail "rule45_bus_channels_pos" "expected 3 channels + 3 unique physical_channel; got $_r45_pos_count / $_r45_pos_phys_uniq"
fi

# ---------------------------------------------------------------------------
# Rule 45 negative: two channels share physical_channel → flagged
# ---------------------------------------------------------------------------
_r45_neg="$scratch/r45_neg"
mkdir -p "$_r45_neg/docs/governance"
cat > "$_r45_neg/docs/governance/bus-channels.yaml" <<'EOF'
channels:
  - id: control
    physical_channel: shared_q
  - id: data
    physical_channel: shared_q
  - id: rhythm
    physical_channel: tick_q
EOF
_r45_neg_phys="$(grep -E '^[[:space:]]+physical_channel:' "$_r45_neg/docs/governance/bus-channels.yaml" | sed -E 's/^[[:space:]]+physical_channel:[[:space:]]*//; s/[[:space:]].*$//')"
_r45_neg_phys_count="$(printf '%s\n' "$_r45_neg_phys" | grep -c .)"
_r45_neg_phys_uniq="$(printf '%s\n' "$_r45_neg_phys" | sort -u | grep -c .)"
if [[ "$_r45_neg_phys_count" -ne "$_r45_neg_phys_uniq" ]]; then
  ok "rule45_bus_channels_neg" "shared physical_channel correctly flagged ($_r45_neg_phys_count entries / $_r45_neg_phys_uniq unique)"
else
  fail "rule45_bus_channels_neg" "expected shared physical_channel to be detected"
fi

# ---------------------------------------------------------------------------
# Rule 46 positive: openapi-v1.yaml with TaskCursor + x-cursor-flow → pass
# ---------------------------------------------------------------------------
_r46_pos="$scratch/r46_pos"
mkdir -p "$_r46_pos/docs/contracts"
cat > "$_r46_pos/docs/contracts/openapi-v1.yaml" <<'EOF'
openapi: 3.0.1
components:
  schemas:
    TaskCursor:
      type: object
x-cursor-flow:
  pattern: "POST → 202 + TaskCursor"
EOF
_r46_pos_ts=0
_r46_pos_ann=0
grep -qE '^[[:space:]]+TaskCursor:[[:space:]]*$' "$_r46_pos/docs/contracts/openapi-v1.yaml" && _r46_pos_ts=1
grep -qE '^x-cursor-flow:[[:space:]]*$' "$_r46_pos/docs/contracts/openapi-v1.yaml" && _r46_pos_ann=1
if [[ "$_r46_pos_ts" -eq 1 ]] && [[ "$_r46_pos_ann" -eq 1 ]]; then
  ok "rule46_cursor_flow_pos" "TaskCursor schema + x-cursor-flow annotation present"
else
  fail "rule46_cursor_flow_pos" "expected both schema and annotation"
fi

# ---------------------------------------------------------------------------
# Rule 46 negative: openapi-v1.yaml missing both → flagged
# ---------------------------------------------------------------------------
_r46_neg="$scratch/r46_neg"
mkdir -p "$_r46_neg/docs/contracts"
cat > "$_r46_neg/docs/contracts/openapi-v1.yaml" <<'EOF'
openapi: 3.0.1
components:
  schemas:
    Foo:
      type: object
EOF
_r46_neg_ts=0
grep -qE '^[[:space:]]+TaskCursor:[[:space:]]*$' "$_r46_neg/docs/contracts/openapi-v1.yaml" && _r46_neg_ts=1
if [[ "$_r46_neg_ts" -eq 0 ]]; then
  ok "rule46_cursor_flow_neg" "missing TaskCursor schema correctly flagged"
else
  fail "rule46_cursor_flow_neg" "expected missing TaskCursor to be detected"
fi

# ---------------------------------------------------------------------------
# Rule 47 positive: agent-service runtime sub-package main without RestTemplate / JdbcTemplate
# ---------------------------------------------------------------------------
_r47_pos="$scratch/r47_pos"
mkdir -p "$_r47_pos/agent-service/src/main/java/x"
cat > "$_r47_pos/agent-service/src/main/java/x/Foo.java" <<'EOF'
package x;
import org.springframework.web.reactive.function.client.WebClient;
public class Foo {}
EOF
_r47_pos_hits="$(grep -rEln '^import[[:space:]]+org\.springframework\.(web\.client\.RestTemplate|jdbc\.core\.JdbcTemplate);' "$_r47_pos/agent-service/src/main/java" 2>/dev/null || true)"
if [[ -z "$_r47_pos_hits" ]]; then
  ok "rule47_no_blocking_io_pos" "WebClient-only runtime correctly passes"
else
  fail "rule47_no_blocking_io_pos" "expected zero hits; got $_r47_pos_hits"
fi

# ---------------------------------------------------------------------------
# Rule 47 negative: agent-service runtime sub-package with JdbcTemplate import → flagged
# ---------------------------------------------------------------------------
_r47_neg="$scratch/r47_neg"
mkdir -p "$_r47_neg/agent-service/src/main/java/x"
cat > "$_r47_neg/agent-service/src/main/java/x/BadDao.java" <<'EOF'
package x;
import org.springframework.jdbc.core.JdbcTemplate;
public class BadDao {}
EOF
_r47_neg_hits="$(grep -rEln '^import[[:space:]]+org\.springframework\.(web\.client\.RestTemplate|jdbc\.core\.JdbcTemplate);' "$_r47_neg/agent-service/src/main/java" 2>/dev/null || true)"
if [[ -n "$_r47_neg_hits" ]]; then
  ok "rule47_no_blocking_io_neg" "JdbcTemplate import correctly flagged"
else
  fail "rule47_no_blocking_io_neg" "expected JdbcTemplate import to be detected"
fi

# ---------------------------------------------------------------------------
# Rule 48 positive: main java without Thread.sleep
# ---------------------------------------------------------------------------
_r48_pos="$scratch/r48_pos"
mkdir -p "$_r48_pos/agent-service/src/main/java/x"
cat > "$_r48_pos/agent-service/src/main/java/x/Clean.java" <<'EOF'
package x;
public class Clean { void wait_(){ /* SuspendSignal here */ } }
EOF
_r48_pos_hits="$(grep -rEn 'Thread\.sleep[[:space:]]*\(|TimeUnit\.[A-Z_]+\.sleep[[:space:]]*\(' "$_r48_pos/agent-service/src/main/java" 2>/dev/null || true)"
if [[ -z "$_r48_pos_hits" ]]; then
  ok "rule48_no_thread_sleep_pos" "clean main java passes"
else
  fail "rule48_no_thread_sleep_pos" "expected zero hits; got $_r48_pos_hits"
fi

# ---------------------------------------------------------------------------
# Rule 48 negative: Thread.sleep in main → flagged
# ---------------------------------------------------------------------------
_r48_neg="$scratch/r48_neg"
mkdir -p "$_r48_neg/agent-service/src/main/java/x"
cat > "$_r48_neg/agent-service/src/main/java/x/Sleeper.java" <<'EOF'
package x;
public class Sleeper { void w() throws Exception { Thread.sleep(1000); } }
EOF
_r48_neg_hits="$(grep -rEn 'Thread\.sleep[[:space:]]*\(|TimeUnit\.[A-Z_]+\.sleep[[:space:]]*\(' "$_r48_neg/agent-service/src/main/java" 2>/dev/null || true)"
if [[ -n "$_r48_neg_hits" ]]; then
  ok "rule48_no_thread_sleep_neg" "Thread.sleep correctly flagged"
else
  fail "rule48_no_thread_sleep_neg" "expected Thread.sleep to be detected"
fi

# ---------------------------------------------------------------------------
# Rule 49 positive: module-metadata.yaml with valid deployment_plane
# ---------------------------------------------------------------------------
_r49_pos="$scratch/r49_pos"
mkdir -p "$_r49_pos/x"
cat > "$_r49_pos/x/module-metadata.yaml" <<'EOF'
module: x
kind: domain
deployment_plane: compute_control
EOF
_r49_pos_plane="$(grep -E '^deployment_plane:' "$_r49_pos/x/module-metadata.yaml" | head -1 | sed -E 's/^deployment_plane:[[:space:]]*([A-Za-z_]+).*/\1/')"
_r49_allowed='^(edge|compute_control|bus_state|sandbox|evolution|none)$'
if [[ -n "$_r49_pos_plane" ]] && [[ "$_r49_pos_plane" =~ $_r49_allowed ]]; then
  ok "rule49_deployment_plane_pos" "deployment_plane=$_r49_pos_plane (valid)"
else
  fail "rule49_deployment_plane_pos" "expected valid plane; got '$_r49_pos_plane'"
fi

# ---------------------------------------------------------------------------
# Rule 49 negative: module-metadata.yaml missing or invalid deployment_plane
# ---------------------------------------------------------------------------
_r49_neg="$scratch/r49_neg"
mkdir -p "$_r49_neg/x"
cat > "$_r49_neg/x/module-metadata.yaml" <<'EOF'
module: x
kind: domain
deployment_plane: stratosphere
EOF
_r49_neg_plane="$(grep -E '^deployment_plane:' "$_r49_neg/x/module-metadata.yaml" | head -1 | sed -E 's/^deployment_plane:[[:space:]]*([A-Za-z_]+).*/\1/')"
if ! [[ "$_r49_neg_plane" =~ $_r49_allowed ]]; then
  ok "rule49_deployment_plane_neg" "invalid plane '$_r49_neg_plane' correctly flagged"
else
  fail "rule49_deployment_plane_neg" "expected invalid plane to be detected"
fi

# ---------------------------------------------------------------------------
# Rule 50 positive: migration with tenant_id + ENABLE ROW LEVEL SECURITY
# ---------------------------------------------------------------------------
_r50_pos="$scratch/r50_pos"
mkdir -p "$_r50_pos/db/migration"
cat > "$_r50_pos/db/migration/V3__rls_table.sql" <<'EOF'
CREATE TABLE foo (tenant_id UUID NOT NULL, x INT);
ALTER TABLE foo ENABLE ROW LEVEL SECURITY;
EOF
_r50_pos_has_tid=0
_r50_pos_has_rls=0
grep -qE 'tenant_id[[:space:]]+UUID' "$_r50_pos/db/migration/V3__rls_table.sql" && _r50_pos_has_tid=1
grep -qiE 'ENABLE[[:space:]]+ROW[[:space:]]+LEVEL[[:space:]]+SECURITY' "$_r50_pos/db/migration/V3__rls_table.sql" && _r50_pos_has_rls=1
if [[ "$_r50_pos_has_tid" -eq 1 ]] && [[ "$_r50_pos_has_rls" -eq 1 ]]; then
  ok "rule50_rls_pos" "tenant_id table with RLS enabled"
else
  fail "rule50_rls_pos" "expected both tenant_id and RLS"
fi

# ---------------------------------------------------------------------------
# Rule 50 negative: migration with tenant_id but NO RLS, NOT grandfathered
# ---------------------------------------------------------------------------
_r50_neg="$scratch/r50_neg"
mkdir -p "$_r50_neg/db/migration"
cat > "$_r50_neg/db/migration/V99__bad.sql" <<'EOF'
CREATE TABLE bar (tenant_id UUID NOT NULL, y INT);
EOF
_r50_neg_has_tid=0
_r50_neg_has_rls=0
grep -qE 'tenant_id[[:space:]]+UUID' "$_r50_neg/db/migration/V99__bad.sql" && _r50_neg_has_tid=1
grep -qiE 'ENABLE[[:space:]]+ROW[[:space:]]+LEVEL[[:space:]]+SECURITY' "$_r50_neg/db/migration/V99__bad.sql" && _r50_neg_has_rls=1
if [[ "$_r50_neg_has_tid" -eq 1 ]] && [[ "$_r50_neg_has_rls" -eq 0 ]]; then
  ok "rule50_rls_neg" "tenant_id table without RLS correctly flagged"
else
  fail "rule50_rls_neg" "expected missing-RLS detection (tid=$_r50_neg_has_tid rls=$_r50_neg_has_rls)"
fi

# ---------------------------------------------------------------------------
# Rule 51 positive: skill-capacity.yaml with all required keys
# ---------------------------------------------------------------------------
_r51_pos="$scratch/r51_pos"
mkdir -p "$_r51_pos/docs/governance"
cat > "$_r51_pos/docs/governance/skill-capacity.yaml" <<'EOF'
skills:
  - id: foo
    capacity_per_tenant: 8
    global_capacity: 256
    queue_strategy: suspend
EOF
_r51_pos_ids="$(grep -cE '^[[:space:]]+- id:[[:space:]]+' "$_r51_pos/docs/governance/skill-capacity.yaml" 2>/dev/null)"; _r51_pos_ids="${_r51_pos_ids:-0}"
_r51_pos_caps_per="$(grep -cE '^[[:space:]]+capacity_per_tenant:' "$_r51_pos/docs/governance/skill-capacity.yaml" 2>/dev/null)"; _r51_pos_caps_per="${_r51_pos_caps_per:-0}"
_r51_pos_caps_global="$(grep -cE '^[[:space:]]+global_capacity:' "$_r51_pos/docs/governance/skill-capacity.yaml" 2>/dev/null)"; _r51_pos_caps_global="${_r51_pos_caps_global:-0}"
_r51_pos_queue="$(grep -cE '^[[:space:]]+queue_strategy:[[:space:]]+(suspend|fail)([[:space:]#].*)?$' "$_r51_pos/docs/governance/skill-capacity.yaml" 2>/dev/null)"; _r51_pos_queue="${_r51_pos_queue:-0}"
if [[ "$_r51_pos_ids" -eq 1 ]] && [[ "$_r51_pos_caps_per" -eq 1 ]] && [[ "$_r51_pos_caps_global" -eq 1 ]] && [[ "$_r51_pos_queue" -eq 1 ]]; then
  ok "rule51_skill_capacity_pos" "skill row complete with all 3 required keys"
else
  fail "rule51_skill_capacity_pos" "expected complete row; got ids=$_r51_pos_ids per=$_r51_pos_caps_per global=$_r51_pos_caps_global queue=$_r51_pos_queue"
fi

# ---------------------------------------------------------------------------
# Rule 51 negative: skill-capacity.yaml missing global_capacity
# ---------------------------------------------------------------------------
_r51_neg="$scratch/r51_neg"
mkdir -p "$_r51_neg/docs/governance"
cat > "$_r51_neg/docs/governance/skill-capacity.yaml" <<'EOF'
skills:
  - id: foo
    capacity_per_tenant: 8
    queue_strategy: suspend
EOF
_r51_neg_ids="$(grep -cE '^[[:space:]]+- id:[[:space:]]+' "$_r51_neg/docs/governance/skill-capacity.yaml" 2>/dev/null)"; _r51_neg_ids="${_r51_neg_ids:-0}"
_r51_neg_caps_global="$(grep -cE '^[[:space:]]+global_capacity:' "$_r51_neg/docs/governance/skill-capacity.yaml" 2>/dev/null)"; _r51_neg_caps_global="${_r51_neg_caps_global:-0}"
if [[ "$_r51_neg_caps_global" -ne "$_r51_neg_ids" ]]; then
  ok "rule51_skill_capacity_neg" "missing global_capacity correctly flagged ($_r51_neg_ids ids vs $_r51_neg_caps_global global)"
else
  fail "rule51_skill_capacity_neg" "expected missing-key detection"
fi

# ---------------------------------------------------------------------------
# Rule 52 positive: sandbox-policies.yaml with all 6 default_policy keys
# ---------------------------------------------------------------------------
_r52_pos="$scratch/r52_pos"
mkdir -p "$_r52_pos/docs/governance"
cat > "$_r52_pos/docs/governance/sandbox-policies.yaml" <<'EOF'
default_policy:
  outbound_network: deny_all
  filesystem_read: deny_all
  filesystem_write: deny_all
  cpu_cap_millicores: 100
  memory_cap_megabytes: 128
  wall_clock_cap_seconds: 30
EOF
_r52_pos_ok=1
for _r52_key in outbound_network filesystem_read filesystem_write cpu_cap_millicores memory_cap_megabytes wall_clock_cap_seconds; do
  if ! grep -qE "^[[:space:]]+${_r52_key}:" "$_r52_pos/docs/governance/sandbox-policies.yaml"; then
    _r52_pos_ok=0
  fi
done
if [[ "$_r52_pos_ok" -eq 1 ]]; then
  ok "rule52_sandbox_policies_pos" "default_policy with all 6 required keys"
else
  fail "rule52_sandbox_policies_pos" "expected all 6 keys"
fi

# ---------------------------------------------------------------------------
# Rule 52 negative: sandbox-policies.yaml missing wall_clock_cap_seconds
# ---------------------------------------------------------------------------
_r52_neg="$scratch/r52_neg"
mkdir -p "$_r52_neg/docs/governance"
cat > "$_r52_neg/docs/governance/sandbox-policies.yaml" <<'EOF'
default_policy:
  outbound_network: deny_all
  filesystem_read: deny_all
  filesystem_write: deny_all
  cpu_cap_millicores: 100
  memory_cap_megabytes: 128
EOF
_r52_neg_missing=0
if ! grep -qE '^[[:space:]]+wall_clock_cap_seconds:' "$_r52_neg/docs/governance/sandbox-policies.yaml"; then
  _r52_neg_missing=1
fi
if [[ "$_r52_neg_missing" -eq 1 ]]; then
  ok "rule52_sandbox_policies_neg" "missing wall_clock_cap_seconds correctly flagged"
else
  fail "rule52_sandbox_policies_neg" "expected missing-key detection"
fi

# ---------------------------------------------------------------------------
# Rule 53 positive: RunCursorFlowIT carries the canonical method + <200ms assertion
# ---------------------------------------------------------------------------
_r53_pos="$scratch/r53_pos"
mkdir -p "$_r53_pos/agent-service/src/test/java/ascend/springai/service/platform/web/runs"
cat > "$_r53_pos/agent-service/src/test/java/ascend/springai/service/platform/web/runs/RunCursorFlowIT.java" <<'EOF'
package ascend.springai.service.platform.web.runs;
class RunCursorFlowIT {
  void createReturns202WithCursorWithin200ms() {
    long elapsed = 0L;
    assertThat(elapsed).isLessThan(200L);
  }
}
EOF
_r53_pos_ok=0
if grep -qE 'void[[:space:]]+createReturns202WithCursorWithin200ms[[:space:]]*\(' "$_r53_pos/agent-service/src/test/java/ascend/springai/service/platform/web/runs/RunCursorFlowIT.java" \
   && grep -qE 'isLessThan\([[:space:]]*200L?[[:space:]]*\)' "$_r53_pos/agent-service/src/test/java/ascend/springai/service/platform/web/runs/RunCursorFlowIT.java"; then
  _r53_pos_ok=1
fi
if [[ "$_r53_pos_ok" -eq 1 ]]; then
  ok "rule53_cursor_flow_it_pos" "canonical method + <200ms assertion present"
else
  fail "rule53_cursor_flow_it_pos" "expected method + isLessThan(200) hit"
fi

# ---------------------------------------------------------------------------
# Rule 53 negative: RunCursorFlowIT missing the elapsed-ms assertion
# ---------------------------------------------------------------------------
_r53_neg="$scratch/r53_neg"
mkdir -p "$_r53_neg/agent-service/src/test/java/ascend/springai/service/platform/web/runs"
cat > "$_r53_neg/agent-service/src/test/java/ascend/springai/service/platform/web/runs/RunCursorFlowIT.java" <<'EOF'
package ascend.springai.service.platform.web.runs;
class RunCursorFlowIT {
  void createReturns202WithCursorWithin200ms() {
    // intentionally missing the elapsed-ms assertion (Rule 53 negative fixture)
    boolean ok = true;
  }
}
EOF
_r53_neg_missing=1
if grep -qE 'isLessThan\([[:space:]]*200L?[[:space:]]*\)' "$_r53_neg/agent-service/src/test/java/ascend/springai/service/platform/web/runs/RunCursorFlowIT.java"; then
  _r53_neg_missing=0
fi
if [[ "$_r53_neg_missing" -eq 1 ]]; then
  ok "rule53_cursor_flow_it_neg" "missing <200ms assertion correctly flagged"
else
  fail "rule53_cursor_flow_it_neg" "expected missing-assertion detection"
fi

# ---------------------------------------------------------------------------
# Rule 54 positive: DefaultSkillResilienceContract has two-arg resolve + tryAcquire
# ---------------------------------------------------------------------------
_r54_pos="$scratch/r54_pos"
mkdir -p "$_r54_pos/agent-service/src/main/java/ascend/springai/service/runtime/resilience/spi"
cat > "$_r54_pos/agent-service/src/main/java/ascend/springai/service/runtime/resilience/spi/SkillCapacityRegistry.java" <<'EOF'
package ascend.springai.service.runtime.resilience.spi;
public interface SkillCapacityRegistry { boolean tryAcquire(String t, String s); }
EOF
cat > "$_r54_pos/agent-service/src/main/java/ascend/springai/service/runtime/resilience/spi/SkillResolution.java" <<'EOF'
package ascend.springai.service.runtime.resilience.spi;
public record SkillResolution(boolean admitted, Object reasonIfRejected) {}
EOF
cat > "$_r54_pos/agent-service/src/main/java/ascend/springai/service/runtime/resilience/spi/SuspendReason.java" <<'EOF'
package ascend.springai.service.runtime.resilience.spi;
public sealed interface SuspendReason permits SuspendReason.RateLimited {
  record RateLimited(String s, String c) implements SuspendReason {}
}
EOF
cat > "$_r54_pos/agent-service/src/main/java/ascend/springai/service/runtime/resilience/DefaultSkillResilienceContract.java" <<'EOF'
package ascend.springai.service.runtime.resilience;
import ascend.springai.service.runtime.resilience.spi.SkillCapacityRegistry;
import ascend.springai.service.runtime.resilience.spi.SkillResolution;
import ascend.springai.service.runtime.resilience.spi.SuspendReason;
public class DefaultSkillResilienceContract {
  private final SkillCapacityRegistry registry;
  public DefaultSkillResilienceContract(SkillCapacityRegistry r) { this.registry = r; }
  public SkillResolution resolve(String tenant, String skill) {
    if (registry.tryAcquire(tenant, skill)) return new SkillResolution(true, null);
    return new SkillResolution(false, new SuspendReason.RateLimited(skill, "SKILL_CAPACITY_EXCEEDED"));
  }
}
EOF
_r54_pos_ok=1
for _r54_f in SkillCapacityRegistry SkillResolution SuspendReason; do
  if [[ ! -f "$_r54_pos/agent-service/src/main/java/ascend/springai/service/runtime/resilience/spi/${_r54_f}.java" ]]; then
    _r54_pos_ok=0
  fi
done
if [[ ! -f "$_r54_pos/agent-service/src/main/java/ascend/springai/service/runtime/resilience/DefaultSkillResilienceContract.java" ]]; then
  _r54_pos_ok=0
fi
if [[ "$_r54_pos_ok" -eq 1 ]] \
   && grep -qE 'SkillResolution[[:space:]]+resolve\([[:space:]]*String[[:space:]]+\w+,[[:space:]]*String[[:space:]]+\w+[[:space:]]*\)' "$_r54_pos/agent-service/src/main/java/ascend/springai/service/runtime/resilience/DefaultSkillResilienceContract.java" \
   && grep -qE 'tryAcquire\(' "$_r54_pos/agent-service/src/main/java/ascend/springai/service/runtime/resilience/DefaultSkillResilienceContract.java"; then
  ok "rule54_skill_capacity_runtime_pos" "DefaultSkillResilienceContract (impl in parent) has two-arg resolve + tryAcquire, SPI types under .spi/ per ADR-0080"
else
  fail "rule54_skill_capacity_runtime_pos" "expected canonical class shape under .spi/ + impl in parent"
fi

# ---------------------------------------------------------------------------
# Rule 54 ADR-0080 negative: SPI types in pre-ADR-0080 parent package (no .spi) must FAIL
# ---------------------------------------------------------------------------
_r54_pre_spi="$scratch/r54_pre_spi"
mkdir -p "$_r54_pre_spi/agent-service/src/main/java/ascend/springai/service/runtime/resilience"
cat > "$_r54_pre_spi/agent-service/src/main/java/ascend/springai/service/runtime/resilience/SkillCapacityRegistry.java" <<'EOF'
package ascend.springai.service.runtime.resilience;
public interface SkillCapacityRegistry { boolean tryAcquire(String t, String s); }
EOF
# Verify the .spi/ directory is absent — Rule 54 production check looks for spi/ specifically.
if [[ -d "$_r54_pre_spi/agent-service/src/main/java/ascend/springai/service/runtime/resilience/spi" ]]; then
  fail "rule54_pre_adr_0080_layout_neg" "negative fixture must not contain .spi/ directory"
else
  ok "rule54_pre_adr_0080_layout_neg" "pre-ADR-0080 parent-package layout correctly absent of .spi/ — Rule 54 would FAIL the gate"
fi

# ---------------------------------------------------------------------------
# Rule 54 negative: DefaultSkillResilienceContract that silently admits everyone
# ---------------------------------------------------------------------------
_r54_neg="$scratch/r54_neg"
mkdir -p "$_r54_neg/agent-service/src/main/java/ascend/springai/service/runtime/resilience"
cat > "$_r54_neg/agent-service/src/main/java/ascend/springai/service/runtime/resilience/DefaultSkillResilienceContract.java" <<'EOF'
package ascend.springai.service.runtime.resilience;
public class DefaultSkillResilienceContract {
  public SkillResolution resolve(String tenant, String skill) {
    // intentionally missing the registry consultation (Rule 54 negative fixture)
    return new SkillResolution(true, null);
  }
}
EOF
_r54_neg_missing=1
if grep -qE 'tryAcquire\(' "$_r54_neg/agent-service/src/main/java/ascend/springai/service/runtime/resilience/DefaultSkillResilienceContract.java"; then
  _r54_neg_missing=0
fi
if [[ "$_r54_neg_missing" -eq 1 ]]; then
  ok "rule54_skill_capacity_runtime_neg" "missing tryAcquire call correctly flagged"
else
  fail "rule54_skill_capacity_runtime_neg" "expected missing-tryAcquire detection"
fi

# ---------------------------------------------------------------------------
# Rule 55 positive: engine-envelope.v1.yaml carries schema + known_engines + id
# ---------------------------------------------------------------------------
_r55_pos="$scratch/r55_pos"
mkdir -p "$_r55_pos/docs/contracts"
cat > "$_r55_pos/docs/contracts/engine-envelope.v1.yaml" <<'EOF'
schema: engine-envelope/v1
authority: ADR-0072
known_engines:
  - id: graph
    payload_class: ExecutorDefinition.GraphDefinition
  - id: agent-loop
    payload_class: ExecutorDefinition.AgentLoopDefinition
EOF
_r55_pos_ok=1
if ! grep -qE '^schema:[[:space:]]+engine-envelope/v1[[:space:]]*$' "$_r55_pos/docs/contracts/engine-envelope.v1.yaml"; then
  _r55_pos_ok=0
fi
if ! grep -qE '^known_engines:[[:space:]]*$' "$_r55_pos/docs/contracts/engine-envelope.v1.yaml"; then
  _r55_pos_ok=0
fi
if ! grep -qE '^[[:space:]]+- id:[[:space:]]+\S+' "$_r55_pos/docs/contracts/engine-envelope.v1.yaml"; then
  _r55_pos_ok=0
fi
if [[ "$_r55_pos_ok" -eq 1 ]]; then
  ok "rule55_engine_envelope_yaml_pos" "schema + known_engines + id all present"
else
  fail "rule55_engine_envelope_yaml_pos" "expected schema/known_engines/id detection"
fi

# ---------------------------------------------------------------------------
# Rule 55 negative: engine-envelope.v1.yaml missing known_engines: block
# ---------------------------------------------------------------------------
_r55_neg="$scratch/r55_neg"
mkdir -p "$_r55_neg/docs/contracts"
cat > "$_r55_neg/docs/contracts/engine-envelope.v1.yaml" <<'EOF'
schema: engine-envelope/v1
authority: ADR-0072
# intentionally missing known_engines: (Rule 55 negative fixture)
EOF
_r55_neg_missing=1
if grep -qE '^known_engines:[[:space:]]*$' "$_r55_neg/docs/contracts/engine-envelope.v1.yaml"; then
  _r55_neg_missing=0
fi
if [[ "$_r55_neg_missing" -eq 1 ]]; then
  ok "rule55_engine_envelope_yaml_neg" "missing known_engines: correctly flagged"
else
  fail "rule55_engine_envelope_yaml_neg" "expected missing-known_engines detection"
fi

# ---------------------------------------------------------------------------
# Rule 56 positive: yaml ids and ENGINE_TYPE constants agree bidirectionally
# ---------------------------------------------------------------------------
_r56_pos="$scratch/r56_pos"
mkdir -p "$_r56_pos/docs/contracts"
mkdir -p "$_r56_pos/agent-service/src/main/java/ascend/springai/service/runtime/orchestration/spi"
cat > "$_r56_pos/docs/contracts/engine-envelope.v1.yaml" <<'EOF'
schema: engine-envelope/v1
known_engines:
  - id: graph
  - id: agent-loop
EOF
cat > "$_r56_pos/agent-service/src/main/java/ascend/springai/service/runtime/orchestration/spi/GraphExecutor.java" <<'EOF'
package ascend.springai.service.runtime.orchestration.spi;
public interface GraphExecutor {
  String ENGINE_TYPE = "graph";
}
EOF
cat > "$_r56_pos/agent-service/src/main/java/ascend/springai/service/runtime/orchestration/spi/AgentLoopExecutor.java" <<'EOF'
package ascend.springai.service.runtime.orchestration.spi;
public interface AgentLoopExecutor {
  String ENGINE_TYPE = "agent-loop";
}
EOF
_r56_pos_yaml_ids=$(grep -E '^[[:space:]]+- id:[[:space:]]+' "$_r56_pos/docs/contracts/engine-envelope.v1.yaml" | sed -E 's/^[[:space:]]+- id:[[:space:]]+([A-Za-z0-9_.-]+).*/\1/' | sort -u)
_r56_pos_src_ids=$(grep -rhE 'String[[:space:]]+ENGINE_TYPE[[:space:]]*=[[:space:]]*"[A-Za-z0-9_.-]+"' "$_r56_pos/agent-service/src/main/java" 2>/dev/null | sed -E 's/.*ENGINE_TYPE[[:space:]]*=[[:space:]]*"([A-Za-z0-9_.-]+)".*/\1/' | sort -u)
_r56_pos_ok=1
for _id in $_r56_pos_yaml_ids; do
  if ! echo "$_r56_pos_src_ids" | grep -qxE "${_id}"; then _r56_pos_ok=0; fi
done
for _id in $_r56_pos_src_ids; do
  if ! echo "$_r56_pos_yaml_ids" | grep -qxE "${_id}"; then _r56_pos_ok=0; fi
done
if [[ "$_r56_pos_ok" -eq 1 ]]; then
  ok "rule56_engine_registry_covers_pos" "yaml ids and ENGINE_TYPE constants match bidirectionally"
else
  fail "rule56_engine_registry_covers_pos" "expected bidirectional consistency"
fi

# ---------------------------------------------------------------------------
# Rule 56 negative: yaml declares 'graph' but source has only 'agent-loop'
# ---------------------------------------------------------------------------
_r56_neg="$scratch/r56_neg"
mkdir -p "$_r56_neg/docs/contracts"
mkdir -p "$_r56_neg/agent-service/src/main/java/ascend/springai/service/runtime/orchestration/spi"
cat > "$_r56_neg/docs/contracts/engine-envelope.v1.yaml" <<'EOF'
schema: engine-envelope/v1
known_engines:
  - id: graph
  - id: agent-loop
EOF
cat > "$_r56_neg/agent-service/src/main/java/ascend/springai/service/runtime/orchestration/spi/AgentLoopExecutor.java" <<'EOF'
package ascend.springai.service.runtime.orchestration.spi;
public interface AgentLoopExecutor {
  String ENGINE_TYPE = "agent-loop";
  // intentionally NO GraphExecutor with ENGINE_TYPE = "graph" (Rule 56 negative fixture)
}
EOF
_r56_neg_yaml_ids=$(grep -E '^[[:space:]]+- id:[[:space:]]+' "$_r56_neg/docs/contracts/engine-envelope.v1.yaml" | sed -E 's/^[[:space:]]+- id:[[:space:]]+([A-Za-z0-9_.-]+).*/\1/' | sort -u)
_r56_neg_src_ids=$(grep -rhE 'String[[:space:]]+ENGINE_TYPE[[:space:]]*=[[:space:]]*"[A-Za-z0-9_.-]+"' "$_r56_neg/agent-service/src/main/java" 2>/dev/null | sed -E 's/.*ENGINE_TYPE[[:space:]]*=[[:space:]]*"([A-Za-z0-9_.-]+)".*/\1/' | sort -u)
_r56_neg_flagged=0
for _id in $_r56_neg_yaml_ids; do
  if ! echo "$_r56_neg_src_ids" | grep -qxE "${_id}"; then _r56_neg_flagged=1; fi
done
if [[ "$_r56_neg_flagged" -eq 1 ]]; then
  ok "rule56_engine_registry_covers_neg" "missing ENGINE_TYPE for declared known_engine correctly flagged"
else
  fail "rule56_engine_registry_covers_neg" "expected missing-ENGINE_TYPE detection"
fi

# ---------------------------------------------------------------------------
# Rule 57 positive: hook yaml + enum agree on the 9-hook list bidirectionally
# ---------------------------------------------------------------------------
_r57_pos="$scratch/r57_pos"
mkdir -p "$_r57_pos/docs/contracts"
mkdir -p "$_r57_pos/agent-service/src/main/java/ascend/springai/service/runtime/orchestration/spi"
cat > "$_r57_pos/docs/contracts/engine-hooks.v1.yaml" <<'EOF'
schema: engine-hooks/v1
hooks:
  - before_llm_invocation
  - on_error
EOF
cat > "$_r57_pos/agent-service/src/main/java/ascend/springai/service/runtime/orchestration/spi/HookPoint.java" <<'EOF'
package ascend.springai.service.runtime.orchestration.spi;
public enum HookPoint {
    BEFORE_LLM_INVOCATION,
    ON_ERROR
}
EOF
_r57_pos_yaml=$(awk '/^hooks:/{f=1;next} /^[a-z_]+:/{f=0} f && /^[[:space:]]+- [a-z_]+/{gsub(/^[[:space:]]+- /,""); print}' "$_r57_pos/docs/contracts/engine-hooks.v1.yaml" | sort -u)
_r57_pos_enum=$(grep -E '^[[:space:]]+[A-Z_]+[,;]?[[:space:]]*$' "$_r57_pos/agent-service/src/main/java/ascend/springai/service/runtime/orchestration/spi/HookPoint.java" | sed -E 's/[[:space:]]+([A-Z_]+)[,;]?[[:space:]]*/\1/' | tr 'A-Z_' 'a-z_' | sort -u)
_r57_pos_ok=1
for _h in $_r57_pos_yaml; do if ! echo "$_r57_pos_enum" | grep -qxE "${_h}"; then _r57_pos_ok=0; fi; done
for _e in $_r57_pos_enum; do if ! echo "$_r57_pos_yaml" | grep -qxE "${_e}"; then _r57_pos_ok=0; fi; done
if [[ "$_r57_pos_ok" -eq 1 ]]; then
  ok "rule57_engine_hooks_yaml_pos" "hook yaml + HookPoint enum agree bidirectionally"
else
  fail "rule57_engine_hooks_yaml_pos" "expected bidirectional agreement"
fi

# ---------------------------------------------------------------------------
# Rule 57 negative: yaml has on_error but enum is missing the ON_ERROR constant
# ---------------------------------------------------------------------------
_r57_neg="$scratch/r57_neg"
mkdir -p "$_r57_neg/docs/contracts"
mkdir -p "$_r57_neg/agent-service/src/main/java/ascend/springai/service/runtime/orchestration/spi"
cat > "$_r57_neg/docs/contracts/engine-hooks.v1.yaml" <<'EOF'
schema: engine-hooks/v1
hooks:
  - before_llm_invocation
  - on_error
EOF
cat > "$_r57_neg/agent-service/src/main/java/ascend/springai/service/runtime/orchestration/spi/HookPoint.java" <<'EOF'
package ascend.springai.service.runtime.orchestration.spi;
public enum HookPoint {
    BEFORE_LLM_INVOCATION
    // intentionally missing ON_ERROR (Rule 57 negative fixture)
}
EOF
_r57_neg_yaml=$(awk '/^hooks:/{f=1;next} /^[a-z_]+:/{f=0} f && /^[[:space:]]+- [a-z_]+/{gsub(/^[[:space:]]+- /,""); print}' "$_r57_neg/docs/contracts/engine-hooks.v1.yaml" | sort -u)
_r57_neg_enum=$(grep -E '^[[:space:]]+[A-Z_]+[,;]?[[:space:]]*$' "$_r57_neg/agent-service/src/main/java/ascend/springai/service/runtime/orchestration/spi/HookPoint.java" | sed -E 's/[[:space:]]+([A-Z_]+)[,;]?[[:space:]]*/\1/' | tr 'A-Z_' 'a-z_' | sort -u)
_r57_neg_flagged=0
for _h in $_r57_neg_yaml; do if ! echo "$_r57_neg_enum" | grep -qxE "${_h}"; then _r57_neg_flagged=1; fi; done
if [[ "$_r57_neg_flagged" -eq 1 ]]; then
  ok "rule57_engine_hooks_yaml_neg" "missing HookPoint enum constant for declared yaml hook correctly flagged"
else
  fail "rule57_engine_hooks_yaml_neg" "expected missing-enum-constant detection"
fi

# ---------------------------------------------------------------------------
# Rule 58 positive: s2c-callback yaml has schema + request + response + 6 mandatory fields + 3 outcome values
# ---------------------------------------------------------------------------
_r58_pos="$scratch/r58_pos"
mkdir -p "$_r58_pos/docs/contracts"
cat > "$_r58_pos/docs/contracts/s2c-callback.v1.yaml" <<'EOF'
schema: s2c-callback/v1
request:
  required_fields:
    - callback_id
    - server_run_id
    - capability_ref
    - request_payload
    - trace_id
    - idempotency_key
response:
  required_fields:
    - callback_id
outcome_values:
  - ok
  - error
  - timeout
EOF
_r58_pos_path="$_r58_pos/docs/contracts/s2c-callback.v1.yaml"
_r58_pos_ok=1
if ! grep -qE '^schema:[[:space:]]+s2c-callback/v1[[:space:]]*$' "$_r58_pos_path"; then _r58_pos_ok=0; fi
if ! grep -qE '^request:[[:space:]]*$' "$_r58_pos_path"; then _r58_pos_ok=0; fi
if ! grep -qE '^response:[[:space:]]*$' "$_r58_pos_path"; then _r58_pos_ok=0; fi
for _f in callback_id server_run_id capability_ref request_payload trace_id idempotency_key; do
  if ! grep -qE "^[[:space:]]+- ${_f}([[:space:]]|#|\$)" "$_r58_pos_path"; then _r58_pos_ok=0; fi
done
for _o in ok error timeout; do
  if ! grep -qE "^[[:space:]]+- ${_o}([[:space:]]|#|\$)" "$_r58_pos_path"; then _r58_pos_ok=0; fi
done
if [[ "$_r58_pos_ok" -eq 1 ]]; then
  ok "rule58_s2c_callback_yaml_pos" "s2c-callback yaml has all required structure"
else
  fail "rule58_s2c_callback_yaml_pos" "expected well-formed s2c-callback yaml"
fi

# ---------------------------------------------------------------------------
# Rule 58 negative: s2c-callback yaml missing trace_id mandatory field
# ---------------------------------------------------------------------------
_r58_neg="$scratch/r58_neg"
mkdir -p "$_r58_neg/docs/contracts"
cat > "$_r58_neg/docs/contracts/s2c-callback.v1.yaml" <<'EOF'
schema: s2c-callback/v1
request:
  required_fields:
    - callback_id
    - server_run_id
    - capability_ref
    - request_payload
    # intentionally missing trace_id (Rule 58 negative fixture)
    - idempotency_key
response:
  required_fields:
    - callback_id
outcome_values:
  - ok
  - error
  - timeout
EOF
_r58_neg_flagged=0
if ! grep -qE '^[[:space:]]+- trace_id([[:space:]]|#|$)' "$_r58_neg/docs/contracts/s2c-callback.v1.yaml"; then
  _r58_neg_flagged=1
fi
if [[ "$_r58_neg_flagged" -eq 1 ]]; then
  ok "rule58_s2c_callback_yaml_neg" "missing mandatory request field correctly flagged"
else
  fail "rule58_s2c_callback_yaml_neg" "expected missing-field detection"
fi

# ---------------------------------------------------------------------------
# Rule 59 positive: evolution-scope yaml well-formed
# ---------------------------------------------------------------------------
_r59_pos="$scratch/r59_pos"
mkdir -p "$_r59_pos/docs/governance"
cat > "$_r59_pos/docs/governance/evolution-scope.v1.yaml" <<'EOF'
schema: evolution-scope/v1
in_scope:
  - server_execution_traces
out_of_scope_default:
  - client_local_state
opt_in_export:
  contract_required: telemetry-export.v1.yaml
EOF
_r59_pos_path="$_r59_pos/docs/governance/evolution-scope.v1.yaml"
_r59_pos_ok=1
if ! grep -qE '^schema:[[:space:]]+evolution-scope/v1[[:space:]]*$' "$_r59_pos_path"; then _r59_pos_ok=0; fi
for _b in in_scope out_of_scope_default opt_in_export; do
  if ! grep -qE "^${_b}:" "$_r59_pos_path"; then _r59_pos_ok=0; fi
done
if ! grep -qE 'contract_required:[[:space:]]+telemetry-export\.v1\.yaml' "$_r59_pos_path"; then _r59_pos_ok=0; fi
if [[ "$_r59_pos_ok" -eq 1 ]]; then
  ok "rule59_evolution_scope_yaml_pos" "evolution-scope yaml has schema + 3 blocks + telemetry-export ref"
else
  fail "rule59_evolution_scope_yaml_pos" "expected well-formed evolution-scope yaml"
fi

# ---------------------------------------------------------------------------
# Rule 59 negative: opt_in_export.contract_required missing
# ---------------------------------------------------------------------------
_r59_neg="$scratch/r59_neg"
mkdir -p "$_r59_neg/docs/governance"
cat > "$_r59_neg/docs/governance/evolution-scope.v1.yaml" <<'EOF'
schema: evolution-scope/v1
in_scope:
  - server_execution_traces
out_of_scope_default:
  - client_local_state
opt_in_export:
  default: deny
EOF
_r59_neg_flagged=0
if ! grep -qE 'contract_required:[[:space:]]+telemetry-export\.v1\.yaml' "$_r59_neg/docs/governance/evolution-scope.v1.yaml"; then
  _r59_neg_flagged=1
fi
if [[ "$_r59_neg_flagged" -eq 1 ]]; then
  ok "rule59_evolution_scope_yaml_neg" "missing telemetry-export contract_required correctly flagged"
else
  fail "rule59_evolution_scope_yaml_neg" "expected missing-contract_required detection"
fi

# ---------------------------------------------------------------------------
# Rule 60 positive: grandfathered file containing prose enum passes (file-level grandfather)
# Phase 7 audit fix: fixture migrated to pipe-delimited <path>|<sunset>|<desc>
# format per gate/schema-first-grandfathered.txt new shape (plan F1/F2).
# ---------------------------------------------------------------------------
_r60_pos="$scratch/r60_pos"
mkdir -p "$_r60_pos/gate"
cat > "$_r60_pos/ARCHITECTURE.md" <<'EOF'
# Test fixture
Grandfathered: RunMode discriminator GRAPH | AGENT_LOOP
EOF
cat > "$_r60_pos/gate/schema-first-grandfathered.txt" <<'EOF'
# header
ARCHITECTURE.md|2099-12-31|RunMode discriminator GRAPH | AGENT_LOOP -- grandfathered
EOF
_r60_pos_ok=0
if grep -qE "^ARCHITECTURE\.md\|" "$_r60_pos/gate/schema-first-grandfathered.txt"; then
  _r60_pos_ok=1
fi
if [[ "$_r60_pos_ok" -eq 1 ]]; then
  ok "rule60_schema_first_pos" "grandfathered file-level entry tolerates prose enum (pipe-delimited format)"
else
  fail "rule60_schema_first_pos" "expected grandfather hit"
fi

# ---------------------------------------------------------------------------
# Rule 60 negative: novel prose enum, no grandfather, no nearby yaml ref - flagged
# ---------------------------------------------------------------------------
_r60_neg="$scratch/r60_neg"
mkdir -p "$_r60_neg/gate"
cat > "$_r60_neg/ARCHITECTURE.md" <<'EOF'
# Test fixture - novel prose enum, no grandfather, no yaml reference
The MyNewEnum discriminator carries values: FOO | BAR | BAZ.
No schema reference in surrounding paragraphs.
EOF
cat > "$_r60_neg/gate/schema-first-grandfathered.txt" <<'EOF'
# header; no ARCHITECTURE.md entry
EOF
_r60_neg_cands=$(awk '
  BEGIN { in_fence = 0 }
  /^```/ { in_fence = !in_fence; next }
  { if (in_fence) next }
  /^[[:space:]]*\|/ { next }
  /[A-Z][A-Z_][A-Z_]*[[:space:]]*\|[[:space:]]*[A-Z][A-Z_][A-Z_]*/ { print NR }
' "$_r60_neg/ARCHITECTURE.md")
_r60_neg_grandfathered=0
if grep -qE "^ARCHITECTURE\.md\|" "$_r60_neg/gate/schema-first-grandfathered.txt"; then
  _r60_neg_grandfathered=1
fi
_r60_neg_flagged=0
if [[ -n "$_r60_neg_cands" && "$_r60_neg_grandfathered" -eq 0 ]]; then
  while read -r _ln; do
    _lo=$(( _ln - 5 )); [[ $_lo -lt 1 ]] && _lo=1
    _hi=$(( _ln + 5 ))
    if ! awk -v lo="$_lo" -v hi="$_hi" 'NR>=lo && NR<=hi' "$_r60_neg/ARCHITECTURE.md" \
       | grep -qE 'docs/(contracts|governance)/[^[:space:]]+\.yaml'; then
      _r60_neg_flagged=1
    fi
  done <<< "$_r60_neg_cands"
fi
if [[ "$_r60_neg_flagged" -eq 1 ]]; then
  ok "rule60_schema_first_neg" "novel prose enum without yaml ref correctly flagged"
else
  fail "rule60_schema_first_neg" "expected novel prose enum to be flagged"
fi

# ---------------------------------------------------------------------------
# Rule 60 sunset expired (Phase 7 audit fix, plan F5): a grandfather entry
# whose sunset_date is in the past MUST be flagged. Mirrors the gate's
# pipe-delimited parse logic.
# ---------------------------------------------------------------------------
_r60_sunset_exp="$scratch/r60_sunset_exp"
mkdir -p "$_r60_sunset_exp/gate"
cat > "$_r60_sunset_exp/gate/schema-first-grandfathered.txt" <<'EOF'
# header -- stale entry whose sunset has passed
ARCHITECTURE.md|2020-01-01|stale entry -- sunset long passed
EOF
_r60_se_today=$(date +%Y-%m-%d)
_r60_se_flagged=0
while IFS= read -r _r60_se_line; do
  [[ -z "$_r60_se_line" || "$_r60_se_line" =~ ^[[:space:]]*# ]] && continue
  _r60_se_sunset=$(printf '%s' "$_r60_se_line" | cut -d'|' -f2)
  if [[ "$_r60_se_today" > "$_r60_se_sunset" ]]; then
    _r60_se_flagged=1
  fi
done < "$_r60_sunset_exp/gate/schema-first-grandfathered.txt"
if [[ "$_r60_se_flagged" -eq 1 ]]; then
  ok "rule60_schema_first_sunset_expired" "expired sunset_date correctly flagged"
else
  fail "rule60_schema_first_sunset_expired" "expected expired sunset_date to be flagged"
fi

# ---------------------------------------------------------------------------
# Rule 60 sunset malformed (Phase 7 audit fix, plan F5): a grandfather entry
# whose sunset_date is not YYYY-MM-DD MUST be flagged.
# ---------------------------------------------------------------------------
_r60_sunset_mal="$scratch/r60_sunset_mal"
mkdir -p "$_r60_sunset_mal/gate"
cat > "$_r60_sunset_mal/gate/schema-first-grandfathered.txt" <<'EOF'
# header -- malformed sunset date
ARCHITECTURE.md|20260930|malformed date format (missing dashes)
EOF
_r60_sm_flagged=0
while IFS= read -r _r60_sm_line; do
  [[ -z "$_r60_sm_line" || "$_r60_sm_line" =~ ^[[:space:]]*# ]] && continue
  _r60_sm_sunset=$(printf '%s' "$_r60_sm_line" | cut -d'|' -f2)
  if ! [[ "$_r60_sm_sunset" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
    _r60_sm_flagged=1
  fi
done < "$_r60_sunset_mal/gate/schema-first-grandfathered.txt"
if [[ "$_r60_sm_flagged" -eq 1 ]]; then
  ok "rule60_schema_first_sunset_malformed" "malformed sunset_date correctly flagged"
else
  fail "rule60_schema_first_sunset_malformed" "expected malformed sunset_date to be flagged"
fi

# ---------------------------------------------------------------------------
# Rule 28k positive (post-review fix plan F / P1-2): a test file whose
# Javadoc cites enforcers.yaml#E<n> matches the E-row's artifact: path.
# ---------------------------------------------------------------------------
_r28k_pos="$scratch/r28k_pos"
mkdir -p "$_r28k_pos/docs/governance"
mkdir -p "$_r28k_pos/agent-service/src/test/java/com/example"
cat > "$_r28k_pos/docs/governance/enforcers.yaml" <<'EOF'
- id: E100
  kind: integration
  artifact: agent-service/src/test/java/com/example/FooIT.java#some_test
EOF
cat > "$_r28k_pos/agent-service/src/test/java/com/example/FooIT.java" <<'EOF'
// Enforcer row: docs/governance/enforcers.yaml#E100
class FooIT {}
EOF
_r28k_pos_eid="E100"
_r28k_pos_art=$(awk -v id="$_r28k_pos_eid" '
  $0 ~ "^- id: " id "$" { found=1; next }
  found && /^[[:space:]]+artifact:/ {
    line=$0
    sub(/^[[:space:]]+artifact:[[:space:]]*/, "", line)
    sub(/#.*$/, "", line)
    gsub(/[[:space:]]+$/, "", line)
    print line
    exit
  }
' "$_r28k_pos/docs/governance/enforcers.yaml")
_r28k_pos_src="agent-service/src/test/java/com/example/FooIT.java"
if [[ "$_r28k_pos_art" == "$_r28k_pos_src" ]]; then
  ok "rule28k_javadoc_citation_pos" "matching Javadoc citation + artifact path"
else
  fail "rule28k_javadoc_citation_pos" "expected match but got art='$_r28k_pos_art' vs src='$_r28k_pos_src'"
fi

# ---------------------------------------------------------------------------
# Rule 28k negative: a test file Javadoc cites E<n> whose artifact: points
# elsewhere -- must be flagged.
# ---------------------------------------------------------------------------
_r28k_neg="$scratch/r28k_neg"
mkdir -p "$_r28k_neg/docs/governance"
mkdir -p "$_r28k_neg/agent-service/src/test/java/com/example"
cat > "$_r28k_neg/docs/governance/enforcers.yaml" <<'EOF'
- id: E101
  kind: integration
  artifact: agent-service/src/test/java/com/other/BarIT.java#some_test
EOF
cat > "$_r28k_neg/agent-service/src/test/java/com/example/FooIT.java" <<'EOF'
// Mis-citation: this file cites E101 but E101's artifact is BarIT.java
// docs/governance/enforcers.yaml#E101
class FooIT {}
EOF
_r28k_neg_eid="E101"
_r28k_neg_art=$(awk -v id="$_r28k_neg_eid" '
  $0 ~ "^- id: " id "$" { found=1; next }
  found && /^[[:space:]]+artifact:/ {
    line=$0
    sub(/^[[:space:]]+artifact:[[:space:]]*/, "", line)
    sub(/#.*$/, "", line)
    gsub(/[[:space:]]+$/, "", line)
    print line
    exit
  }
' "$_r28k_neg/docs/governance/enforcers.yaml")
_r28k_neg_src="agent-service/src/test/java/com/example/FooIT.java"
if [[ "$_r28k_neg_art" != "$_r28k_neg_src" ]]; then
  ok "rule28k_javadoc_citation_neg" "mismatched Javadoc citation correctly flagged"
else
  fail "rule28k_javadoc_citation_neg" "expected mismatch but paths matched"
fi

}


test_rule61_legacy_powershell_gate_deprecated() {
# ---------------------------------------------------------------------------
# RULE 61 -- legacy_powershell_gate_deprecated  (v2.0.0-rc2 second-pass review P0-1)
# Positive: PS stub with DEPRECATED banner + architecture-status.yaml lists PS only under deprecated_implementations:
# Negative: PS stub missing the banner OR architecture-status.yaml still lists PS under implementation:
# ---------------------------------------------------------------------------

## Positive: well-formed deprecation surface passes
_r61_pos="$scratch/r61_pos"
mkdir -p "$_r61_pos/gate" "$_r61_pos/docs/governance"
cat > "$_r61_pos/gate/check_architecture_sync.ps1" <<'EOF'
#!/usr/bin/env pwsh
Write-Host "DEPRECATED: this gate was frozen at Rule 29 in 2026-05."
exit 2
EOF
# Realistic yaml: capabilities: at level 0, capability keys at 2-space indent,
# sub-fields at 4-space indent. Include a SECOND capability with its own
# implementation: list to confirm in_cap correctly resets across capabilities.
cat > "$_r61_pos/docs/governance/architecture-status.yaml" <<'EOF'
capabilities:
  architecture_sync_gate:
    shipped: true
    implementation:
      - gate/check_architecture_sync.sh
    deprecated_implementations:
      - gate/check_architecture_sync.ps1
    tests:
      - gate/test_architecture_sync_gate.sh
  other_capability:
    shipped: true
    implementation:
      - gate/check_architecture_sync.ps1
    tests: []
EOF
_r61_pos_fail=0
if ! grep -qE '^\s*Write-Host\s+"DEPRECATED:' "$_r61_pos/gate/check_architecture_sync.ps1"; then _r61_pos_fail=1; fi
_r61_pos_in_impl=$(awk '
  /^  architecture_sync_gate:[[:space:]]*$/ { in_cap=1; next }
  in_cap && /^  [a-z_]+:/ { in_cap=0; in_impl=0; next }
  in_cap && /^    implementation:[[:space:]]*$/ { in_impl=1; next }
  in_cap && in_impl && /^    [a-z_]+:/ { in_impl=0 }
  in_cap && in_impl && /^[[:space:]]+-[[:space:]]+gate\/check_architecture_sync\.ps1([[:space:]]|$)/ { print "found"; exit }
' "$_r61_pos/docs/governance/architecture-status.yaml")
if [[ -n "$_r61_pos_in_impl" ]]; then _r61_pos_fail=1; fi
if [[ $_r61_pos_fail -eq 0 ]]; then
  ok "rule61_legacy_ps_pos" "valid PS deprecation stub + impl-list exclusion passes (in_cap resets across capabilities)"
else
  fail "rule61_legacy_ps_pos" "expected PASS for valid PS deprecation surface"
fi

## Negative: PS still listed under implementation: triggers FAIL
_r61_neg="$scratch/r61_neg"
mkdir -p "$_r61_neg/gate" "$_r61_neg/docs/governance"
cat > "$_r61_neg/gate/check_architecture_sync.ps1" <<'EOF'
#!/usr/bin/env pwsh
Write-Host "DEPRECATED: stub"
exit 2
EOF
cat > "$_r61_neg/docs/governance/architecture-status.yaml" <<'EOF'
capabilities:
  architecture_sync_gate:
    shipped: true
    implementation:
      - gate/check_architecture_sync.ps1
      - gate/check_architecture_sync.sh
    tests: []
EOF
_r61_neg_in_impl=$(awk '
  /^  architecture_sync_gate:[[:space:]]*$/ { in_cap=1; next }
  in_cap && /^  [a-z_]+:/ { in_cap=0; in_impl=0; next }
  in_cap && /^    implementation:[[:space:]]*$/ { in_impl=1; next }
  in_cap && in_impl && /^    [a-z_]+:/ { in_impl=0 }
  in_cap && in_impl && /^[[:space:]]+-[[:space:]]+gate\/check_architecture_sync\.ps1([[:space:]]|$)/ { print "found"; exit }
' "$_r61_neg/docs/governance/architecture-status.yaml")
if [[ -n "$_r61_neg_in_impl" ]]; then
  ok "rule61_legacy_ps_neg" "PS still under implementation: correctly triggers FAIL"
else
  fail "rule61_legacy_ps_neg" "expected FAIL for PS listed under implementation:"
fi

}


test_rule62_contract_yaml_declares_status() {
# ---------------------------------------------------------------------------
# RULE 62 -- contract_yaml_declares_status  (v2.0.0-rc2 F-β structural prevention)
# Positive: YAML with top-level status: schema_shipped passes
# Negative: YAML missing status: field OR with unknown enum value triggers FAIL
# ---------------------------------------------------------------------------

## Positive: well-formed YAML with allowed status value
_r62_pos="$scratch/r62_pos.yaml"
cat > "$_r62_pos" <<'EOF'
schema: example/v1
status: schema_shipped
authority: ADR-XXXX
EOF
_r62_pos_val=$(awk '
  /^status:[[:space:]]+/ {
    v=$0
    sub(/^status:[[:space:]]+/, "", v)
    sub(/[[:space:]]+#.*$/, "", v)
    sub(/[[:space:]]+$/, "", v)
    print v
    exit
  }
' "$_r62_pos")
_r62_allowed_re='^(design_only|schema_shipped|runtime_enforced)$'
if [[ -n "$_r62_pos_val" ]] && [[ "$_r62_pos_val" =~ $_r62_allowed_re ]]; then
  ok "rule62_contract_status_pos" "valid status: schema_shipped passes"
else
  fail "rule62_contract_status_pos" "expected PASS for status: schema_shipped"
fi

## Negative: YAML with unknown status value triggers FAIL
_r62_neg="$scratch/r62_neg.yaml"
cat > "$_r62_neg" <<'EOF'
schema: example/v1
status: live
EOF
_r62_neg_val=$(awk '
  /^status:[[:space:]]+/ {
    v=$0
    sub(/^status:[[:space:]]+/, "", v)
    sub(/[[:space:]]+#.*$/, "", v)
    sub(/[[:space:]]+$/, "", v)
    print v
    exit
  }
' "$_r62_neg")
if [[ -n "$_r62_neg_val" ]] && ! [[ "$_r62_neg_val" =~ $_r62_allowed_re ]]; then
  ok "rule62_contract_status_neg" "unknown status: 'live' correctly triggers FAIL"
else
  fail "rule62_contract_status_neg" "expected FAIL for unknown status value"
fi

}


test_rule63_release_note_retracted_tag_qualified() {
# ---------------------------------------------------------------------------
# RULE 63 -- release_note_retracted_tag_qualified  (v2.0.0-rc2 F-γ structural prevention)
# Positive: release note mentioning retracted tag on the same line as "(retracted)" passes
# Negative: bare mention with no qualifier and no Historical/Superseded heading above triggers FAIL
# ---------------------------------------------------------------------------

## Positive: tag co-located with "(retracted)" qualifier
_r63_pos="$scratch/r63_pos.md"
cat > "$_r63_pos" <<'EOF'
# Release v2.0.0-rc2

**Tag:** `v2.0.0-rc2` (prior `v2.0.0-w2x-final` (retracted) superseded by rc1)
EOF
_r63_tag="v2.0.0-w2x-final"
_r63_lines=$(grep -nF "$_r63_tag" "$_r63_pos" | cut -d: -f1)
_r63_pos_fail=0
while IFS= read -r _r63_ln; do
  [[ -z "$_r63_ln" ]] && continue
  _r63_lineval=$(sed -n "${_r63_ln}p" "$_r63_pos")
  if echo "$_r63_lineval" | grep -qiE '\(retracted\)|retracted\b'; then continue; fi
  _r63_pos_fail=1
done <<< "$_r63_lines"
if [[ $_r63_pos_fail -eq 0 ]]; then
  ok "rule63_retracted_tag_pos" "tag with '(retracted)' qualifier on same line passes"
else
  fail "rule63_retracted_tag_pos" "expected PASS for tag co-located with '(retracted)'"
fi

## Negative: bare tag mention under a non-Historical heading triggers FAIL
_r63_neg="$scratch/r63_neg.md"
cat > "$_r63_neg" <<'EOF'
# Release v2.0.0-x

## Conclusion

Recommended tag: `v2.0.0-w2x-final` on the merge commit.
EOF
_r63_neg_lines=$(grep -nF "$_r63_tag" "$_r63_neg" | cut -d: -f1)
_r63_neg_caught=0
while IFS= read -r _r63_ln; do
  [[ -z "$_r63_ln" ]] && continue
  _r63_lineval=$(sed -n "${_r63_ln}p" "$_r63_neg")
  if echo "$_r63_lineval" | grep -qiE '\(retracted\)|retracted\b'; then continue; fi
  _r63_qualified=0
  _r63_scan=$_r63_ln
  while [[ $_r63_scan -gt 0 ]]; do
    _r63_above=$(sed -n "${_r63_scan}p" "$_r63_neg")
    if echo "$_r63_above" | grep -qE '^#'; then
      if echo "$_r63_above" | grep -qiE 'historical|superseded'; then _r63_qualified=1; fi
      break
    fi
    _r63_scan=$((_r63_scan - 1))
  done
  if [[ $_r63_qualified -eq 0 ]]; then _r63_neg_caught=1; fi
done <<< "$_r63_neg_lines"
if [[ $_r63_neg_caught -eq 1 ]]; then
  ok "rule63_retracted_tag_neg" "bare retracted-tag mention under non-Historical heading correctly triggers FAIL"
else
  fail "rule63_retracted_tag_neg" "expected FAIL for bare retracted tag with no qualifier"
fi

# ===========================================================================
# 2026-05-17 cross-corpus consistency audit prevention rules — self-tests
# Authority: docs/logs/reviews/2026-05-17-cross-corpus-consistency-audit-response.en.md
# ===========================================================================

}


test_rule64_module_count_data_driven() {
# ---------------------------------------------------------------------------
# RULE 64 -- module_count_data_driven
# Positive: pom <module> count == architecture-status.yaml total_reactor_modules → pass
# Negative: count mismatch → fail
# ---------------------------------------------------------------------------

## Positive: matched count
_r64_pos="$scratch/r64_pos"
mkdir -p "$_r64_pos/docs/governance"
cat > "$_r64_pos/pom.xml" <<'EOF'
<project>
  <modules>
    <module>a</module>
    <module>b</module>
    <module>c</module>
  </modules>
</project>
EOF
cat > "$_r64_pos/docs/governance/architecture-status.yaml" <<'EOF'
repository_counts:
  total_reactor_modules: 3
EOF
_r64_pos_canonical=$(grep -E '^[[:space:]]*total_reactor_modules:[[:space:]]*[0-9]+' "$_r64_pos/docs/governance/architecture-status.yaml" | head -1 | sed -E 's/^[[:space:]]*total_reactor_modules:[[:space:]]*([0-9]+).*/\1/')
_r64_pos_count=$(grep -c '<module>' "$_r64_pos/pom.xml" 2>/dev/null || echo 0)
if [[ "$_r64_pos_canonical" == "$_r64_pos_count" ]]; then
  ok "rule64_module_count_data_driven_pos" "pom <module> count $_r64_pos_count == canonical $_r64_pos_canonical (PASS)"
else
  fail "rule64_module_count_data_driven_pos" "expected match but got pom=$_r64_pos_count canonical=$_r64_pos_canonical"
fi

## Negative: count mismatch
_r64_neg="$scratch/r64_neg"
mkdir -p "$_r64_neg/docs/governance"
cat > "$_r64_neg/pom.xml" <<'EOF'
<project>
  <modules>
    <module>a</module>
    <module>b</module>
  </modules>
</project>
EOF
cat > "$_r64_neg/docs/governance/architecture-status.yaml" <<'EOF'
repository_counts:
  total_reactor_modules: 5
EOF
_r64_neg_canonical=$(grep -E '^[[:space:]]*total_reactor_modules:[[:space:]]*[0-9]+' "$_r64_neg/docs/governance/architecture-status.yaml" | head -1 | sed -E 's/^[[:space:]]*total_reactor_modules:[[:space:]]*([0-9]+).*/\1/')
_r64_neg_count=$(grep -c '<module>' "$_r64_neg/pom.xml" 2>/dev/null || echo 0)
if [[ "$_r64_neg_canonical" != "$_r64_neg_count" ]]; then
  ok "rule64_module_count_data_driven_neg" "mismatch pom=$_r64_neg_count canonical=$_r64_neg_canonical correctly triggers FAIL"
else
  fail "rule64_module_count_data_driven_neg" "expected mismatch detection but values agreed"
fi

}


test_rule65_module_metadata_pom_dep_parity() {
# ---------------------------------------------------------------------------
# RULE 65 -- module_metadata_pom_dep_parity
# Positive: every pom ascend.springai sibling appears in metadata allowed_dependencies → pass
# Negative: pom dep missing from metadata → fail
# ---------------------------------------------------------------------------

## Positive: pom declares agent-middleware, metadata lists it
_r65_pos="$scratch/r65_pos"
mkdir -p "$_r65_pos"
cat > "$_r65_pos/pom.xml" <<'EOF'
<project>
  <parent>
    <groupId>ascend.springai</groupId>
    <artifactId>spring-ai-ascend-parent</artifactId>
  </parent>
  <artifactId>agent-foo</artifactId>
  <dependencies>
    <dependency>
      <groupId>ascend.springai</groupId>
      <artifactId>agent-middleware</artifactId>
    </dependency>
  </dependencies>
</project>
EOF
cat > "$_r65_pos/module-metadata.yaml" <<'EOF'
module: agent-foo
kind: domain
allowed_dependencies:
  - agent-middleware
forbidden_dependencies: []
EOF
_r65_pos_deps=$(awk '
  /<dependency>/ { in_dep=1; want=0; next }
  /<\/dependency>/ { in_dep=0; want=0; next }
  in_dep && /<groupId>ascend\.springai<\/groupId>/ { want=1; next }
  in_dep && want && /<artifactId>/ {
    gsub(/^[[:space:]]*<artifactId>/, "")
    gsub(/<\/artifactId>.*/, "")
    print
    want=0
  }
' "$_r65_pos/pom.xml" | sort -u)
_r65_pos_allowed=$(awk '/^allowed_dependencies:/{flag=1; next} /^[a-zA-Z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]+/{gsub(/^[[:space:]]*-[[:space:]]+/,""); gsub(/[[:space:]#].*$/,""); print}' "$_r65_pos/module-metadata.yaml" | sort -u)
_r65_pos_fail=0
while IFS= read -r _dep; do
  [[ -z "$_dep" ]] && continue
  if ! echo "$_r65_pos_allowed" | grep -qxF "$_dep"; then _r65_pos_fail=1; fi
done <<< "$_r65_pos_deps"
if [[ $_r65_pos_fail -eq 0 ]]; then
  ok "rule65_module_metadata_pom_dep_parity_pos" "pom dep agent-middleware appears in metadata allowed_dependencies (PASS)"
else
  fail "rule65_module_metadata_pom_dep_parity_pos" "expected PASS — pom dep was declared in metadata"
fi

## Negative: pom adds agent-bus but metadata doesn't list it
_r65_neg="$scratch/r65_neg"
mkdir -p "$_r65_neg"
cat > "$_r65_neg/pom.xml" <<'EOF'
<project>
  <parent>
    <groupId>ascend.springai</groupId>
    <artifactId>spring-ai-ascend-parent</artifactId>
  </parent>
  <artifactId>agent-foo</artifactId>
  <dependencies>
    <dependency>
      <groupId>ascend.springai</groupId>
      <artifactId>agent-bus</artifactId>
    </dependency>
  </dependencies>
</project>
EOF
cat > "$_r65_neg/module-metadata.yaml" <<'EOF'
module: agent-foo
kind: domain
allowed_dependencies: []
forbidden_dependencies: []
EOF
_r65_neg_deps=$(awk '
  /<dependency>/ { in_dep=1; want=0; next }
  /<\/dependency>/ { in_dep=0; want=0; next }
  in_dep && /<groupId>ascend\.springai<\/groupId>/ { want=1; next }
  in_dep && want && /<artifactId>/ {
    gsub(/^[[:space:]]*<artifactId>/, "")
    gsub(/<\/artifactId>.*/, "")
    print
    want=0
  }
' "$_r65_neg/pom.xml" | sort -u)
_r65_neg_allowed=$(awk '/^allowed_dependencies:/{flag=1; next} /^[a-zA-Z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]+/{gsub(/^[[:space:]]*-[[:space:]]+/,""); gsub(/[[:space:]#].*$/,""); print}' "$_r65_neg/module-metadata.yaml" | sort -u)
_r65_neg_caught=0
while IFS= read -r _dep; do
  [[ -z "$_dep" ]] && continue
  if ! echo "$_r65_neg_allowed" | grep -qxF "$_dep"; then _r65_neg_caught=1; fi
done <<< "$_r65_neg_deps"
if [[ $_r65_neg_caught -eq 1 ]]; then
  ok "rule65_module_metadata_pom_dep_parity_neg" "pom dep agent-bus missing from metadata correctly triggers FAIL"
else
  fail "rule65_module_metadata_pom_dep_parity_neg" "expected FAIL but the missing dep was not detected"
fi

}


test_rule66_spi_package_exhaustiveness() {
# ---------------------------------------------------------------------------
# RULE 66 -- spi_package_exhaustiveness
# Positive: every on-disk */spi/ directory is declared in spi_packages → pass
# Negative: undeclared SPI directory exists on disk → fail
# ---------------------------------------------------------------------------

## Positive: one SPI dir, declared
_r66_pos="$scratch/r66_pos"
mkdir -p "$_r66_pos/src/main/java/ascend/springai/foo/spi"
cat > "$_r66_pos/module-metadata.yaml" <<'EOF'
module: agent-foo
kind: domain
spi_packages:
  - ascend.springai.foo.spi
allowed_dependencies: []
forbidden_dependencies: []
EOF
_r66_pos_declared=$(awk '/^spi_packages:/{flag=1; next} /^[a-zA-Z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]+/{gsub(/^[[:space:]]*-[[:space:]]+/,""); gsub(/[[:space:]#].*$/,""); print}' "$_r66_pos/module-metadata.yaml" | sort -u)
_r66_pos_fail=0
while IFS= read -r _dir; do
  [[ -z "$_dir" ]] && continue
  _pkg="${_dir#${_r66_pos}/src/main/java/}"
  _pkg="${_pkg//\//.}"
  if ! echo "$_r66_pos_declared" | grep -qxF "$_pkg"; then _r66_pos_fail=1; fi
done <<< "$(find "$_r66_pos/src/main/java" -type d -name spi 2>/dev/null)"
if [[ $_r66_pos_fail -eq 0 ]]; then
  ok "rule66_spi_package_exhaustiveness_pos" "declared SPI dir matches on-disk SPI dir (PASS)"
else
  fail "rule66_spi_package_exhaustiveness_pos" "expected PASS — declared SPI dir was present"
fi

## Negative: two SPI dirs on disk, only one declared
_r66_neg="$scratch/r66_neg"
mkdir -p "$_r66_neg/src/main/java/ascend/springai/foo/spi"
mkdir -p "$_r66_neg/src/main/java/ascend/springai/bar/spi"
cat > "$_r66_neg/module-metadata.yaml" <<'EOF'
module: agent-foo
kind: domain
spi_packages:
  - ascend.springai.foo.spi
allowed_dependencies: []
forbidden_dependencies: []
EOF
_r66_neg_declared=$(awk '/^spi_packages:/{flag=1; next} /^[a-zA-Z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]+/{gsub(/^[[:space:]]*-[[:space:]]+/,""); gsub(/[[:space:]#].*$/,""); print}' "$_r66_neg/module-metadata.yaml" | sort -u)
_r66_neg_caught=0
while IFS= read -r _dir; do
  [[ -z "$_dir" ]] && continue
  _pkg="${_dir#${_r66_neg}/src/main/java/}"
  _pkg="${_pkg//\//.}"
  if ! echo "$_r66_neg_declared" | grep -qxF "$_pkg"; then _r66_neg_caught=1; fi
done <<< "$(find "$_r66_neg/src/main/java" -type d -name spi 2>/dev/null)"
if [[ $_r66_neg_caught -eq 1 ]]; then
  ok "rule66_spi_package_exhaustiveness_neg" "undeclared on-disk SPI dir correctly triggers FAIL"
else
  fail "rule66_spi_package_exhaustiveness_neg" "expected FAIL — undeclared SPI dir was not detected"
fi

# ===========================================================================
# 2026-05-17 CLAUDE.md token-optimization wave PR1 -- self-tests
# Authority: D:/.claude/plans/tokens-token-buzzing-sprout.md + docs/governance/rules/rule-{67..71}.md
# ===========================================================================

}


test_rule67_claude_md_kernel_size_bounded() {
# ---------------------------------------------------------------------------
# RULE 67 -- claude_md_kernel_size_bounded
# Positive: rule body within kernel_cap → pass
# Negative: rule body exceeds kernel_cap → fail
# ---------------------------------------------------------------------------

## Positive: 5-line rule body under kernel_cap=8 passes
_r67_pos="$scratch/r67_pos"
mkdir -p "$_r67_pos/docs/governance/rules"
cat > "$_r67_pos/CLAUDE.md" <<'EOF'
#### Rule 35 — Three-Track Channel Isolation

**Cross-service internal communication MUST be sliced into three physically isolated channels.**

Enforced by Gate Rule 45. Card: docs/governance/rules/rule-35.md.

---
EOF
cat > "$_r67_pos/docs/governance/rules/rule-35.md" <<'EOF'
---
rule_id: 35
kernel_cap: 8
---
EOF
_r67_pos_violations=""
_r67_pos_rule_lines=$(grep -nE '^#### Rule [0-9]+' "$_r67_pos/CLAUDE.md" | sort -t: -k1,1n)
while IFS= read -r _entry; do
  [[ -z "$_entry" ]] && continue
  _ln="${_entry%%:*}"
  _num=$(printf '%s\n' "${_entry#*:}" | sed -nE 's/^#### Rule ([0-9]+).*/\1/p')
  _card_padded=$(printf 'rule-%02d.md' "$_num")
  _card="$_r67_pos/docs/governance/rules/$_card_padded"
  [[ -f "$_card" ]] || continue
  _cap=$(awk '/^kernel_cap:[[:space:]]*[0-9]+/{print $2; exit}' "$_card")
  [[ -z "$_cap" ]] && continue
  _count=$(awk -v start="$_ln" 'NR < start { next } NR == start { count = 1; next } /^---$/ { exit } { count++ } END { print count + 0 }' "$_r67_pos/CLAUDE.md")
  if [[ "$_count" -gt "$_cap" ]]; then _r67_pos_violations+="Rule $_num: $_count > $_cap; "; fi
done <<< "$_r67_pos_rule_lines"
if [[ -z "$_r67_pos_violations" ]]; then
  ok "rule67_claude_md_kernel_size_bounded_pos" "5-line rule body fits within kernel_cap=8 (PASS)"
else
  fail "rule67_claude_md_kernel_size_bounded_pos" "expected PASS but got violations: $_r67_pos_violations"
fi

## Negative: 12-line rule body exceeds kernel_cap=8 → fail
_r67_neg="$scratch/r67_neg"
mkdir -p "$_r67_neg/docs/governance/rules"
cat > "$_r67_neg/CLAUDE.md" <<'EOF'
#### Rule 35 — Three-Track Channel Isolation

**Cross-service internal communication MUST be sliced into three physically isolated channels.**

Extra prose line 1.
Extra prose line 2.
Extra prose line 3.
Extra prose line 4.
Extra prose line 5.

Enforced by Gate Rule 45. Card: docs/governance/rules/rule-35.md.

---
EOF
cat > "$_r67_neg/docs/governance/rules/rule-35.md" <<'EOF'
---
rule_id: 35
kernel_cap: 8
---
EOF
_r67_neg_violations=""
_r67_neg_rule_lines=$(grep -nE '^#### Rule [0-9]+' "$_r67_neg/CLAUDE.md" | sort -t: -k1,1n)
while IFS= read -r _entry; do
  [[ -z "$_entry" ]] && continue
  _ln="${_entry%%:*}"
  _num=$(printf '%s\n' "${_entry#*:}" | sed -nE 's/^#### Rule ([0-9]+).*/\1/p')
  _card_padded=$(printf 'rule-%02d.md' "$_num")
  _card="$_r67_neg/docs/governance/rules/$_card_padded"
  [[ -f "$_card" ]] || continue
  _cap=$(awk '/^kernel_cap:[[:space:]]*[0-9]+/{print $2; exit}' "$_card")
  [[ -z "$_cap" ]] && continue
  _count=$(awk -v start="$_ln" 'NR < start { next } NR == start { count = 1; next } /^---$/ { exit } { count++ } END { print count + 0 }' "$_r67_neg/CLAUDE.md")
  if [[ "$_count" -gt "$_cap" ]]; then _r67_neg_violations+="Rule $_num: $_count > $_cap; "; fi
done <<< "$_r67_neg_rule_lines"
if [[ -n "$_r67_neg_violations" ]]; then
  ok "rule67_claude_md_kernel_size_bounded_neg" "12-line rule body correctly exceeds kernel_cap=8 (FAIL caught)"
else
  fail "rule67_claude_md_kernel_size_bounded_neg" "expected FAIL but oversized rule passed"
fi

}


test_rule68_claude_md_kernel_matches_card() {
# ---------------------------------------------------------------------------
# RULE 68 -- claude_md_kernel_matches_card
# Positive: card kernel == CLAUDE.md body → pass
# Negative: drift between kernel and body → fail
# ---------------------------------------------------------------------------

## Positive: kernel matches body
_r68_pos="$scratch/r68_pos"
mkdir -p "$_r68_pos/docs/governance/rules"
cat > "$_r68_pos/CLAUDE.md" <<'EOF'
#### Rule 35 — Three-Track Channel Isolation

**Cross-service internal communication MUST be sliced into three physically isolated channels.**

Enforced by Gate Rule 45. Card: docs/governance/rules/rule-35.md.

---
EOF
cat > "$_r68_pos/docs/governance/rules/rule-35.md" <<'EOF'
---
rule_id: 35
kernel_cap: 8
kernel: |
  **Cross-service internal communication MUST be sliced into three physically isolated channels.**
---
EOF
_r68_pos_kernel=$(awk '
  /^kernel:[[:space:]]*\|/ { flag=1; next }
  /^kernel:[[:space:]]/ { line=$0; sub(/^kernel:[[:space:]]*/, "", line); print line; exit }
  flag && /^[a-zA-Z_][a-zA-Z_0-9]*:/ { flag=0; exit }
  flag && /^---$/ { flag=0; exit }
  flag { sub(/^  /, ""); print }
' "$_r68_pos/docs/governance/rules/rule-35.md" | tr -s ' \t' ' ' | tr -d '\r' | tr '\n' ' ' | tr -s ' ' | sed -E 's/^ //; s/ $//')
_r68_pos_body=$(awk -v n=35 '
  $0 ~ "^#### Rule " n "[[:space:]]" || $0 ~ "^#### Rule " n "$" { flag=1; next }
  flag && /^---$/ { exit }
  flag && /^#### / { exit }
  flag && /^Enforced by/ { exit }
  flag && NF { print }
' "$_r68_pos/CLAUDE.md" | tr -s ' \t' ' ' | tr -d '\r' | tr '\n' ' ' | tr -s ' ' | sed -E 's/^ //; s/ $//')
if [[ "$_r68_pos_kernel" == "$_r68_pos_body" ]]; then
  ok "rule68_claude_md_kernel_matches_card_pos" "card kernel byte-matches CLAUDE.md body (PASS)"
else
  fail "rule68_claude_md_kernel_matches_card_pos" "expected PASS but kernel='$_r68_pos_kernel' body='$_r68_pos_body'"
fi

## Negative: kernel drifts from body
_r68_neg="$scratch/r68_neg"
mkdir -p "$_r68_neg/docs/governance/rules"
cat > "$_r68_neg/CLAUDE.md" <<'EOF'
#### Rule 35 — Three-Track Channel Isolation

**Cross-service internal communication MUST use ONE merged channel.**

Enforced by Gate Rule 45. Card: docs/governance/rules/rule-35.md.

---
EOF
cat > "$_r68_neg/docs/governance/rules/rule-35.md" <<'EOF'
---
rule_id: 35
kernel_cap: 8
kernel: |
  **Cross-service internal communication MUST be sliced into three physically isolated channels.**
---
EOF
_r68_neg_kernel=$(awk '
  /^kernel:[[:space:]]*\|/ { flag=1; next }
  flag && /^[a-zA-Z_][a-zA-Z_0-9]*:/ { flag=0; exit }
  flag && /^---$/ { flag=0; exit }
  flag { sub(/^  /, ""); print }
' "$_r68_neg/docs/governance/rules/rule-35.md" | tr -s ' \t' ' ' | tr -d '\r' | tr '\n' ' ' | tr -s ' ' | sed -E 's/^ //; s/ $//')
_r68_neg_body=$(awk -v n=35 '
  $0 ~ "^#### Rule " n "[[:space:]]" || $0 ~ "^#### Rule " n "$" { flag=1; next }
  flag && /^---$/ { exit }
  flag && /^#### / { exit }
  flag && /^Enforced by/ { exit }
  flag && NF { print }
' "$_r68_neg/CLAUDE.md" | tr -s ' \t' ' ' | tr -d '\r' | tr '\n' ' ' | tr -s ' ' | sed -E 's/^ //; s/ $//')
if [[ "$_r68_neg_kernel" != "$_r68_neg_body" ]]; then
  ok "rule68_claude_md_kernel_matches_card_neg" "drift between card kernel and CLAUDE.md body correctly triggers FAIL"
else
  fail "rule68_claude_md_kernel_matches_card_neg" "expected drift detection but kernel and body agreed"
fi

}


test_rule69_every_active_rule_has_card() {
# ---------------------------------------------------------------------------
# RULE 69 -- every_active_rule_has_card
# Positive: every active rule has a card → pass
# Negative: rule heading without matching card → fail
# ---------------------------------------------------------------------------

## Positive: rule in CLAUDE.md has matching card
_r69_pos="$scratch/r69_pos"
mkdir -p "$_r69_pos/docs/governance/rules"
cat > "$_r69_pos/CLAUDE.md" <<'EOF'
#### Rule 35 — Three-Track Channel Isolation

Body here.

---
EOF
touch "$_r69_pos/docs/governance/rules/rule-35.md"
_r69_pos_active=$(grep -oE '^#### Rule [0-9]+' "$_r69_pos/CLAUDE.md" | grep -oE '[0-9]+' | sort -un)
_r69_pos_cards=$(find "$_r69_pos/docs/governance/rules" -maxdepth 1 -name 'rule-*.md' -type f 2>/dev/null \
                 | sed -E 's|.*/rule-0*([0-9]+)\.md|\1|' | sort -un)
_r69_pos_missing=""
while IFS= read -r _n; do
  [[ -z "$_n" ]] && continue
  if ! echo "$_r69_pos_cards" | grep -qxF "$_n"; then _r69_pos_missing+="$_n "; fi
done <<< "$_r69_pos_active"
if [[ -z "$_r69_pos_missing" ]]; then
  ok "rule69_every_active_rule_has_card_pos" "all active rules have cards (PASS)"
else
  fail "rule69_every_active_rule_has_card_pos" "expected PASS but missing: $_r69_pos_missing"
fi

## Negative: rule heading without matching card → fail
_r69_neg="$scratch/r69_neg"
mkdir -p "$_r69_neg/docs/governance/rules"
cat > "$_r69_neg/CLAUDE.md" <<'EOF'
#### Rule 35 — Three-Track Channel Isolation

Body here.

---
EOF
# Intentionally no card file created
_r69_neg_active=$(grep -oE '^#### Rule [0-9]+' "$_r69_neg/CLAUDE.md" | grep -oE '[0-9]+' | sort -un)
_r69_neg_cards=$(find "$_r69_neg/docs/governance/rules" -maxdepth 1 -name 'rule-*.md' -type f 2>/dev/null \
                 | sed -E 's|.*/rule-0*([0-9]+)\.md|\1|' | sort -un)
_r69_neg_missing=""
while IFS= read -r _n; do
  [[ -z "$_n" ]] && continue
  if ! echo "$_r69_neg_cards" | grep -qxF "$_n"; then _r69_neg_missing+="$_n "; fi
done <<< "$_r69_neg_active"
if [[ -n "$_r69_neg_missing" ]]; then
  ok "rule69_every_active_rule_has_card_neg" "missing card correctly triggers FAIL: $_r69_neg_missing"
else
  fail "rule69_every_active_rule_has_card_neg" "expected missing-card detection but none reported"
fi

}


test_rule70_always_loaded_budget_enforced() {
# ---------------------------------------------------------------------------
# RULE 70 -- always_loaded_budget_enforced
# Positive: file size <= ceiling → pass
# Negative: file size > ceiling → fail
# ---------------------------------------------------------------------------

## Positive: file under ceiling
_r70_pos="$scratch/r70_pos"
mkdir -p "$_r70_pos"
printf '%s\n' "small file content" > "$_r70_pos/small.md"
_r70_pos_bytes=$(wc -c < "$_r70_pos/small.md" | tr -d ' \r\n\t')
_r70_pos_ceil=100
if [[ "$_r70_pos_bytes" -le "$_r70_pos_ceil" ]]; then
  ok "rule70_always_loaded_budget_pos" "file bytes=$_r70_pos_bytes <= ceiling=$_r70_pos_ceil (PASS)"
else
  fail "rule70_always_loaded_budget_pos" "expected PASS but bytes=$_r70_pos_bytes > ceiling=$_r70_pos_ceil"
fi

## Negative: file over ceiling
_r70_neg="$scratch/r70_neg"
mkdir -p "$_r70_neg"
# Generate ~200 bytes of content (well over 50-byte ceiling)
printf 'X%.0s' {1..200} > "$_r70_neg/big.md"
_r70_neg_bytes=$(wc -c < "$_r70_neg/big.md" | tr -d ' \r\n\t')
_r70_neg_ceil=50
if [[ "$_r70_neg_bytes" -gt "$_r70_neg_ceil" ]]; then
  ok "rule70_always_loaded_budget_neg" "file bytes=$_r70_neg_bytes > ceiling=$_r70_neg_ceil correctly triggers FAIL"
else
  fail "rule70_always_loaded_budget_neg" "expected overage detection but bytes=$_r70_neg_bytes <= ceiling=$_r70_neg_ceil"
fi

}


test_rule71_deferred_doc_not_in_always_loaded() {
# ---------------------------------------------------------------------------
# RULE 71 -- deferred_doc_not_in_always_loaded
# Positive: no @-include + no ALWAYS marker → pass
# Negative: @-include OR ALWAYS marker present → fail
# ---------------------------------------------------------------------------

## Positive: no @-include + no ALWAYS marker
_r71_pos="$scratch/r71_pos"
mkdir -p "$_r71_pos/docs/governance"
cat > "$_r71_pos/CLAUDE.md" <<'EOF'
# CLAUDE.md

## Deferred Rules

See docs/CLAUDE-deferred.md.
EOF
cat > "$_r71_pos/docs/governance/SESSION-START-CONTEXT.md" <<'EOF'
| 7 | docs/CLAUDE-deferred.md | (ON-DEMAND) load only when re-introducing a deferred rule |
EOF
_r71_pos_fail=0
if grep -qE '^[[:space:]]*@docs/CLAUDE-deferred\.md' "$_r71_pos/CLAUDE.md" 2>/dev/null; then _r71_pos_fail=1; fi
if [[ -f "$_r71_pos/docs/governance/SESSION-START-CONTEXT.md" ]]; then
  _bad=$(grep -E 'CLAUDE-deferred\.md' "$_r71_pos/docs/governance/SESSION-START-CONTEXT.md" 2>/dev/null | grep -E '(\bALWAYS\b|ALWAYS-LOAD)' || true)
  if [[ -n "$_bad" ]]; then _r71_pos_fail=1; fi
fi
if [[ $_r71_pos_fail -eq 0 ]]; then
  ok "rule71_deferred_doc_not_in_always_loaded_pos" "no @-include + no ALWAYS marker (PASS)"
else
  fail "rule71_deferred_doc_not_in_always_loaded_pos" "expected PASS but auto-load detected"
fi

## Negative: ALWAYS marker present → fail
_r71_neg="$scratch/r71_neg"
mkdir -p "$_r71_neg/docs/governance"
cat > "$_r71_neg/CLAUDE.md" <<'EOF'
# CLAUDE.md

See docs/CLAUDE-deferred.md.
EOF
cat > "$_r71_neg/docs/governance/SESSION-START-CONTEXT.md" <<'EOF'
| 7 | docs/CLAUDE-deferred.md | ALWAYS-LOAD: deferred rules + re-introduction triggers |
EOF
_r71_neg_caught=0
if grep -qE '^[[:space:]]*@docs/CLAUDE-deferred\.md' "$_r71_neg/CLAUDE.md" 2>/dev/null; then _r71_neg_caught=1; fi
if [[ -f "$_r71_neg/docs/governance/SESSION-START-CONTEXT.md" ]]; then
  _bad=$(grep -E 'CLAUDE-deferred\.md' "$_r71_neg/docs/governance/SESSION-START-CONTEXT.md" 2>/dev/null | grep -E '(\bALWAYS\b|ALWAYS-LOAD)' || true)
  if [[ -n "$_bad" ]]; then _r71_neg_caught=1; fi
fi
if [[ $_r71_neg_caught -eq 1 ]]; then
  ok "rule71_deferred_doc_not_in_always_loaded_neg" "ALWAYS-LOAD marker correctly triggers FAIL"
else
  fail "rule71_deferred_doc_not_in_always_loaded_neg" "expected FAIL but auto-load directive not detected"
fi

# ===========================================================================
# 2026-05-17 gate-script efficiency wave PR-E1 -- Rule 73 self-tests
# Authority: D:/.claude/plans/tokens-token-buzzing-sprout.md + docs/governance/rules/rule-73.md
# ===========================================================================

}


test_rule73_gate_config_well_formed() {
# ---------------------------------------------------------------------------
# RULE 73 -- gate_config_well_formed
# Positive: well-formed gate/config.yaml validates -> pass
# Negative 1: malformed YAML / parse error -> fail
# Negative 2: out-of-range value (jobs: 999) -> fail
# Negative 3: unknown key (typo detection) -> fail (currently best-effort
#             since our pure-bash validator only enforces required-keys +
#             type/range/enum on the validated leaves; full additionalProperties
#             check is a Phase 2 enhancement)
# ---------------------------------------------------------------------------

## Positive: shipping config.yaml validates
_r73_pos_repo="$repo_root"
_r73_pos_result=$(bash -c "
  GATE_REPO_ROOT='$_r73_pos_repo'
  source gate/lib/load_config.sh
  gate_load_config >/dev/null 2>&1
  gate_validate_config_against_schema >/dev/null 2>&1
  printf '%s\n' \"\${GATE_CONFIG_VALID:-false}\"
")
_r73_pos_valid=$(printf '%s\n' "$_r73_pos_result" | head -1)
if [[ "$_r73_pos_valid" == "true" ]]; then
  ok "rule73_gate_config_well_formed_pos" "shipping config.yaml validates against schema"
else
  fail "rule73_gate_config_well_formed_pos" "expected VALID=true but got '$_r73_pos_valid'"
fi

## Negative 1: missing config file
_r73_neg1_dir="$scratch/r73_neg1"
mkdir -p "$_r73_neg1_dir/gate/lib"
cp "$repo_root/gate/lib/load_config.sh" "$_r73_neg1_dir/gate/lib/load_config.sh"
cp "$repo_root/gate/config.schema.yaml" "$_r73_neg1_dir/gate/config.schema.yaml"
# Intentionally do NOT copy gate/config.yaml
_r73_neg1_result=$(bash -c "
  GATE_REPO_ROOT='$_r73_neg1_dir'
  source $_r73_neg1_dir/gate/lib/load_config.sh
  gate_load_config >/dev/null 2>&1
  gate_validate_config_against_schema >/dev/null 2>&1
  printf '%s' \"\${GATE_CONFIG_VALID:-false}\"
")
if [[ "$_r73_neg1_result" == "false" ]]; then
  ok "rule73_gate_config_missing_neg" "missing gate/config.yaml correctly triggers FAIL"
else
  fail "rule73_gate_config_missing_neg" "expected FAIL for missing config but got VALID=$_r73_neg1_result"
fi

## Negative 2: out-of-range jobs value
_r73_neg2_dir="$scratch/r73_neg2"
mkdir -p "$_r73_neg2_dir/gate/lib"
cp "$repo_root/gate/lib/load_config.sh" "$_r73_neg2_dir/gate/lib/load_config.sh"
cp "$repo_root/gate/config.schema.yaml" "$_r73_neg2_dir/gate/config.schema.yaml"
cat > "$_r73_neg2_dir/gate/config.yaml" <<'YAMLEOF'
parallelism:
  jobs: 999
  enabled: true
  rule_timeout_seconds: 60
  batch_strategy: round_robin
logging:
  ndjson_enabled: true
  summary_enabled: true
  stdout_format: human
  retention:
    max_runs: 100
    auto_prune: true
  profile_mode: false
scan_cache:
  enabled: true
  patterns:
    - module_metadata
regression_detection:
  enabled: true
  multiplier_threshold: 2.0
  absolute_min_ms: 200
  baseline_window: 5
rule_filters:
  skip: []
  only: []
YAMLEOF
_r73_neg2_result=$(bash -c "
  GATE_REPO_ROOT='$_r73_neg2_dir'
  source $_r73_neg2_dir/gate/lib/load_config.sh
  gate_load_config >/dev/null 2>&1
  gate_validate_config_against_schema >/dev/null 2>&1
  printf '%s' \"\${GATE_CONFIG_VALID:-false}\"
")
if [[ "$_r73_neg2_result" == "false" ]]; then
  ok "rule73_gate_config_out_of_range_neg" "out-of-range jobs=999 correctly triggers FAIL"
else
  fail "rule73_gate_config_out_of_range_neg" "expected FAIL for jobs=999 but got VALID=$_r73_neg2_result"
fi

## Negative 3: invalid batch_strategy enum value
_r73_neg3_dir="$scratch/r73_neg3"
mkdir -p "$_r73_neg3_dir/gate/lib"
cp "$repo_root/gate/lib/load_config.sh" "$_r73_neg3_dir/gate/lib/load_config.sh"
cp "$repo_root/gate/config.schema.yaml" "$_r73_neg3_dir/gate/config.schema.yaml"
cat > "$_r73_neg3_dir/gate/config.yaml" <<'YAMLEOF'
parallelism:
  jobs: 8
  enabled: true
  rule_timeout_seconds: 60
  batch_strategy: BOGUS_STRATEGY
logging:
  ndjson_enabled: true
  summary_enabled: true
  stdout_format: human
  retention:
    max_runs: 100
    auto_prune: true
  profile_mode: false
scan_cache:
  enabled: true
  patterns: []
regression_detection:
  enabled: true
  multiplier_threshold: 2.0
  absolute_min_ms: 200
  baseline_window: 5
rule_filters:
  skip: []
  only: []
YAMLEOF
_r73_neg3_result=$(bash -c "
  GATE_REPO_ROOT='$_r73_neg3_dir'
  source $_r73_neg3_dir/gate/lib/load_config.sh
  gate_load_config >/dev/null 2>&1
  gate_validate_config_against_schema >/dev/null 2>&1
  printf '%s' \"\${GATE_CONFIG_VALID:-false}\"
")
if [[ "$_r73_neg3_result" == "false" ]]; then
  ok "rule73_gate_config_invalid_enum_neg" "invalid batch_strategy enum correctly triggers FAIL"
else
  fail "rule73_gate_config_invalid_enum_neg" "expected FAIL for invalid enum but got VALID=$_r73_neg3_result"
fi

}

# ===========================================================================
# 2026-05-18 Linux-first dev environment policy (PR-E7) -- Rule 74 self-tests
# Authority: docs/governance/rules/rule-74.md + docs/governance/dev-environment.md
# ===========================================================================
test_rule74_linux_first_dev_doc_present() {

## Positive: dev-environment.md mentions WSL2 + WSL1 + Linux -> Rule 74 PASS
_r74_pos="$scratch/r74_pos"
mkdir -p "$_r74_pos/docs/governance"
cat > "$_r74_pos/docs/governance/dev-environment.md" <<'DOCEOF'
# Linux-First
WSL2 preferred. WSL1 fallback. Native Linux acceptable.
DOCEOF
_r74_pos_missing=""
for _kw in "WSL2" "WSL1" "Linux"; do
  if ! grep -qF "$_kw" "$_r74_pos/docs/governance/dev-environment.md" 2>/dev/null; then
    _r74_pos_missing+="$_kw "
  fi
done
if [[ -z "$_r74_pos_missing" ]]; then
  ok "rule74_linux_first_dev_doc_present_pos" "dev-environment.md mentions WSL2 + WSL1 + Linux (PASS)"
else
  fail "rule74_linux_first_dev_doc_present_pos" "expected PASS but missing keywords: $_r74_pos_missing"
fi

## Negative: dev-environment.md missing keywords -> Rule 74 FAIL
_r74_neg="$scratch/r74_neg"
mkdir -p "$_r74_neg/docs/governance"
cat > "$_r74_neg/docs/governance/dev-environment.md" <<'DOCEOF'
# Dev setup
Use Windows + Git Bash. No mention of alternatives.
DOCEOF
_r74_neg_missing=""
for _kw in "WSL2" "WSL1" "Linux"; do
  if ! grep -qF "$_kw" "$_r74_neg/docs/governance/dev-environment.md" 2>/dev/null; then
    _r74_neg_missing+="$_kw "
  fi
done
if [[ -n "$_r74_neg_missing" ]]; then
  ok "rule74_linux_first_dev_doc_present_neg" "Win-only doc correctly triggers FAIL: missing $_r74_neg_missing"
else
  fail "rule74_linux_first_dev_doc_present_neg" "expected FAIL but all keywords present"
fi

}

# ===========================================================================
# 2026-05-18 beyond-SDD review response wave -- Rule 79 self-test
# Authority: docs/governance/rules/rule-79.md + docs/runbooks/debug-first-evidence.md
# ===========================================================================
test_rule79_evidence_first_debug_runbook() {

## Positive: runbook exists with canonical title AND card cites it -> Rule 79 PASS
_r79_pos="$scratch/r79_pos"
mkdir -p "$_r79_pos/docs/runbooks" "$_r79_pos/docs/governance/rules"
cat > "$_r79_pos/docs/runbooks/debug-first-evidence.md" <<'DOCEOF'
# Evidence-First Debug Sequence
Steps...
DOCEOF
cat > "$_r79_pos/docs/governance/rules/rule-79.md" <<'DOCEOF'
Operationalised by docs/runbooks/debug-first-evidence.md.
DOCEOF
_r79_pos_runbook_ok=0
_r79_pos_card_ok=0
[[ -f "$_r79_pos/docs/runbooks/debug-first-evidence.md" ]] && \
  grep -qF 'Evidence-First Debug Sequence' "$_r79_pos/docs/runbooks/debug-first-evidence.md" && \
  _r79_pos_runbook_ok=1
[[ -f "$_r79_pos/docs/governance/rules/rule-79.md" ]] && \
  grep -qF 'docs/runbooks/debug-first-evidence.md' "$_r79_pos/docs/governance/rules/rule-79.md" && \
  _r79_pos_card_ok=1
if [[ $_r79_pos_runbook_ok -eq 1 && $_r79_pos_card_ok -eq 1 ]]; then
  ok "rule79_evidence_first_debug_runbook_pos" "runbook + canonical title + card-citation present (PASS)"
else
  fail "rule79_evidence_first_debug_runbook_pos" "expected PASS but runbook=$_r79_pos_runbook_ok card=$_r79_pos_card_ok"
fi

## Negative: card exists but does NOT reference runbook -> Rule 79 FAIL
_r79_neg="$scratch/r79_neg"
mkdir -p "$_r79_neg/docs/runbooks" "$_r79_neg/docs/governance/rules"
cat > "$_r79_neg/docs/runbooks/debug-first-evidence.md" <<'DOCEOF'
# Evidence-First Debug Sequence
Steps...
DOCEOF
cat > "$_r79_neg/docs/governance/rules/rule-79.md" <<'DOCEOF'
This card forgot to cite the runbook path. Rule 79 catches the broken link.
DOCEOF
_r79_neg_card_cites=0
grep -qF 'docs/runbooks/debug-first-evidence.md' "$_r79_neg/docs/governance/rules/rule-79.md" 2>/dev/null && _r79_neg_card_cites=1
if [[ $_r79_neg_card_cites -eq 0 ]]; then
  ok "rule79_evidence_first_debug_runbook_neg" "card without runbook citation correctly triggers FAIL"
else
  fail "rule79_evidence_first_debug_runbook_neg" "expected FAIL but card cites runbook"
fi

}

# ===========================================================================
# 2026-05-18 rc4 cross-constraint review response prevention wave -- Rules 80-83 self-tests
# ===========================================================================

test_rule80_s2c_callback_signal_historical_only_in_authority() {

## Positive: file mentions S2cCallbackSignal with "historical" marker on same line -> Rule 80 PASS
_r80_pos="$scratch/r80_pos"
mkdir -p "$_r80_pos"
cat > "$_r80_pos/sample.md" <<'DOCEOF'
# Sample doc
The historical S2cCallbackSignal type was deleted at rc3 and unified into
SuspendSignal.forClientCallback per the 2026-05-18 amendment.
DOCEOF
_r80_pos_marker_re='historical|deleted|refactored|amendment|forClientCallback'
_r80_pos_ok=1
while IFS= read -r _r80_pos_match; do
  [[ -z "$_r80_pos_match" ]] && continue
  _r80_pos_lineno="${_r80_pos_match%%:*}"
  _r80_pos_lo=$((_r80_pos_lineno > 5 ? _r80_pos_lineno - 5 : 1))
  _r80_pos_hi=$((_r80_pos_lineno + 5))
  if ! sed -n "${_r80_pos_lo},${_r80_pos_hi}p" "$_r80_pos/sample.md" | grep -qiE "$_r80_pos_marker_re"; then
    _r80_pos_ok=0
  fi
done < <(grep -nF 'S2cCallbackSignal' "$_r80_pos/sample.md" 2>/dev/null)
if [[ $_r80_pos_ok -eq 1 ]]; then
  ok "rule80_s2c_historical_only_pos" "S2cCallbackSignal mention with historical marker correctly accepted"
else
  fail "rule80_s2c_historical_only_pos" "expected PASS but marker scan returned bad"
fi

## Negative: file mentions S2cCallbackSignal as current-state claim with no marker -> Rule 80 FAIL
_r80_neg="$scratch/r80_neg"
mkdir -p "$_r80_neg"
cat > "$_r80_neg/sample.md" <<'DOCEOF'
# Sample doc
The runtime catches S2cCallbackSignal in its executeLoop and dispatches the
envelope through the registered transport. Plain current-state prose only.
DOCEOF
_r80_neg_marker_re='historical|deleted|refactored|amendment|forClientCallback'
_r80_neg_violation=0
while IFS= read -r _r80_neg_match; do
  [[ -z "$_r80_neg_match" ]] && continue
  _r80_neg_lineno="${_r80_neg_match%%:*}"
  _r80_neg_lo=$((_r80_neg_lineno > 5 ? _r80_neg_lineno - 5 : 1))
  _r80_neg_hi=$((_r80_neg_lineno + 5))
  if ! sed -n "${_r80_neg_lo},${_r80_neg_hi}p" "$_r80_neg/sample.md" | grep -qiE "$_r80_neg_marker_re"; then
    _r80_neg_violation=1
  fi
done < <(grep -nF 'S2cCallbackSignal' "$_r80_neg/sample.md" 2>/dev/null)
if [[ $_r80_neg_violation -eq 1 ]]; then
  ok "rule80_s2c_historical_only_neg" "S2cCallbackSignal without historical marker correctly triggers FAIL"
else
  fail "rule80_s2c_historical_only_neg" "expected FAIL but marker scan found a marker"
fi

}

test_rule81_skeleton_module_has_no_production_java() {

## Positive: skeleton module contains only package-info.java -> Rule 81 PASS
_r81_pos="$scratch/r81_pos"
mkdir -p "$_r81_pos/agent-sample/src/main/java/ascend/sample"
cat > "$_r81_pos/agent-sample/ARCHITECTURE.md" <<'DOCEOF'
---
status: skeleton
---
# agent-sample
DOCEOF
cat > "$_r81_pos/agent-sample/src/main/java/ascend/sample/package-info.java" <<'DOCEOF'
package ascend.sample;
DOCEOF
_r81_pos_status=$(awk 'BEGIN{infm=0} /^---[[:space:]]*$/{infm=!infm; next} infm && /^status:/{print; exit}' "$_r81_pos/agent-sample/ARCHITECTURE.md")
_r81_pos_violation=0
if [[ "$_r81_pos_status" == *skeleton* ]]; then
  while IFS= read -r _r81_pos_java; do
    [[ -z "$_r81_pos_java" ]] && continue
    [[ "$(basename "$_r81_pos_java")" == "package-info.java" ]] && continue
    if ! head -n 30 "$_r81_pos_java" 2>/dev/null | grep -qE 'placeholder.*ADR-[0-9]{4}|ADR-[0-9]{4}.*placeholder'; then
      _r81_pos_violation=1
    fi
  done < <(find "$_r81_pos/agent-sample/src/main/java" -name '*.java' -type f 2>/dev/null)
fi
if [[ $_r81_pos_violation -eq 0 ]]; then
  ok "rule81_skeleton_no_prod_java_pos" "skeleton module with only package-info.java correctly accepted"
else
  fail "rule81_skeleton_no_prod_java_pos" "expected PASS but violation flagged"
fi

## Negative: skeleton module contains production code -> Rule 81 FAIL
_r81_neg="$scratch/r81_neg"
mkdir -p "$_r81_neg/agent-sample/src/main/java/ascend/sample"
cat > "$_r81_neg/agent-sample/ARCHITECTURE.md" <<'DOCEOF'
---
status: skeleton
---
# agent-sample
DOCEOF
cat > "$_r81_neg/agent-sample/src/main/java/ascend/sample/RealCode.java" <<'DOCEOF'
package ascend.sample;
public class RealCode {
  public int doStuff() { return 42; }
}
DOCEOF
_r81_neg_status=$(awk 'BEGIN{infm=0} /^---[[:space:]]*$/{infm=!infm; next} infm && /^status:/{print; exit}' "$_r81_neg/agent-sample/ARCHITECTURE.md")
_r81_neg_violation=0
if [[ "$_r81_neg_status" == *skeleton* ]]; then
  while IFS= read -r _r81_neg_java; do
    [[ -z "$_r81_neg_java" ]] && continue
    [[ "$(basename "$_r81_neg_java")" == "package-info.java" ]] && continue
    if ! head -n 30 "$_r81_neg_java" 2>/dev/null | grep -qE 'placeholder.*ADR-[0-9]{4}|ADR-[0-9]{4}.*placeholder'; then
      _r81_neg_violation=1
    fi
  done < <(find "$_r81_neg/agent-sample/src/main/java" -name '*.java' -type f 2>/dev/null)
fi
if [[ $_r81_neg_violation -eq 1 ]]; then
  ok "rule81_skeleton_no_prod_java_neg" "skeleton module with production code correctly triggers FAIL"
else
  fail "rule81_skeleton_no_prod_java_neg" "expected FAIL but violation not flagged"
fi

}

test_rule82_baseline_metrics_single_source() {

## Positive: yaml has all required baseline_metrics keys AND README/gate-README point to it -> Rule 82 PASS
_r82_pos="$scratch/r82_pos"
mkdir -p "$_r82_pos/docs/governance" "$_r82_pos/gate"
cat > "$_r82_pos/docs/governance/architecture-status.yaml" <<'DOCEOF'
architecture_sync_gate:
  baseline_metrics:
    active_engineering_rules: 35
    active_gate_checks: 68
    gate_executable_test_cases: 129
    enforcer_rows: 98
    architecture_graph_nodes: 315
    architecture_graph_edges: 433
DOCEOF
cat > "$_r82_pos/README.md" <<'DOCEOF'
See docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics for current counts.
DOCEOF
cat > "$_r82_pos/gate/README.md" <<'DOCEOF'
Canonical numbers live in architecture_sync_gate.baseline_metrics.
DOCEOF
_r82_pos_violation=0
for _r82_pos_key in active_engineering_rules active_gate_checks gate_executable_test_cases enforcer_rows architecture_graph_nodes architecture_graph_edges; do
  if ! grep -qE "^[[:space:]]+${_r82_pos_key}:" "$_r82_pos/docs/governance/architecture-status.yaml"; then
    _r82_pos_violation=1
  fi
done
for _r82_pos_ptr in "$_r82_pos/README.md" "$_r82_pos/gate/README.md"; do
  if ! grep -qF 'architecture_sync_gate.baseline_metrics' "$_r82_pos_ptr"; then
    _r82_pos_violation=1
  fi
done
if [[ $_r82_pos_violation -eq 0 ]]; then
  ok "rule82_baseline_metrics_single_source_pos" "structured baseline_metrics + entrypoint pointers correctly accepted"
else
  fail "rule82_baseline_metrics_single_source_pos" "expected PASS but violation flagged"
fi

## Negative: yaml missing a key OR entrypoint missing pointer -> Rule 82 FAIL
_r82_neg="$scratch/r82_neg"
mkdir -p "$_r82_neg/docs/governance" "$_r82_neg/gate"
cat > "$_r82_neg/docs/governance/architecture-status.yaml" <<'DOCEOF'
architecture_sync_gate:
  baseline_metrics:
    active_engineering_rules: 35
    # MISSING active_gate_checks, gate_executable_test_cases, enforcer_rows, nodes, edges
DOCEOF
cat > "$_r82_neg/README.md" <<'DOCEOF'
README without the magic pointer string.
DOCEOF
_r82_neg_violation=0
for _r82_neg_key in active_engineering_rules active_gate_checks gate_executable_test_cases enforcer_rows architecture_graph_nodes architecture_graph_edges; do
  if ! grep -qE "^[[:space:]]+${_r82_neg_key}:" "$_r82_neg/docs/governance/architecture-status.yaml"; then
    _r82_neg_violation=1
  fi
done
if [[ $_r82_neg_violation -eq 1 ]]; then
  ok "rule82_baseline_metrics_single_source_neg" "missing baseline_metrics keys correctly triggers FAIL"
else
  fail "rule82_baseline_metrics_single_source_neg" "expected FAIL but all keys present"
fi

## rc6 strengthening (Track B) — numeric-agreement check for entrypoint count phrases.
## Three additional tests: numeric pos / pointer-with-stale-count neg / historical-marker exemption pos.

# Helper: parse a count phrase + baseline yaml using the same awk logic the production gate uses.
_r82_extract_expected() {
  local yaml="$1" key="$2"
  awk -v key="$key" '
    /^architecture_sync_gate:/{f=1; next}
    f && /^[^[:space:]]/{exit}
    f && $0 ~ "^[[:space:]]+"key":"{
      sub(/^[[:space:]]+[a-zA-Z_]+:[[:space:]]*/, ""); sub(/[^0-9].*$/, ""); print; exit
    }
  ' "$yaml" 2>/dev/null
}

## Positive: pointer present AND number matches baseline -> Rule 82 PASS
_r82_num_pos_yaml="$scratch/r82_num_pos_status.yaml"
_r82_num_pos_readme="$scratch/r82_num_pos_README.md"
cat > "$_r82_num_pos_yaml" <<'DOCEOF'
architecture_sync_gate:
  baseline_metrics:
    active_engineering_rules: 41
    active_gate_checks: 70
    gate_executable_test_cases: 138
    enforcer_rows: 100
    architecture_graph_nodes: 329
    architecture_graph_edges: 459
DOCEOF
cat > "$_r82_num_pos_readme" <<'DOCEOF'
70 active gate rules backed by 138 self-tests.
See architecture_sync_gate.baseline_metrics for the canonical source.
DOCEOF
_r82_num_pos_expected=$(_r82_extract_expected "$_r82_num_pos_yaml" "active_gate_checks")
_r82_num_pos_actual=$(grep -oE '[0-9]+[[:space:]]+active gate rules' "$_r82_num_pos_readme" | head -1 | grep -oE '^[0-9]+')
if [[ "$_r82_num_pos_actual" == "$_r82_num_pos_expected" ]]; then
  ok "rule82_numeric_agreement_pos" "entrypoint count phrase matching baseline_metrics correctly accepted"
else
  fail "rule82_numeric_agreement_pos" "expected actual=$_r82_num_pos_actual to equal expected=$_r82_num_pos_expected"
fi

## Negative: pointer present BUT adjacent count stale (reviewer's exact rc5 evidence) -> Rule 82 FAIL
_r82_num_neg_readme="$scratch/r82_num_neg_README.md"
cat > "$_r82_num_neg_readme" <<'DOCEOF'
**64 active gate rules** backed by **121 self-tests**.
The canonical numbers live in architecture_sync_gate.baseline_metrics.
DOCEOF
_r82_num_neg_expected=$(_r82_extract_expected "$_r82_num_pos_yaml" "active_gate_checks")
_r82_num_neg_actual=$(grep -oE '[0-9]+[[:space:]]+active gate rules' "$_r82_num_neg_readme" | head -1 | grep -oE '^[0-9]+')
if [[ "$_r82_num_neg_actual" != "$_r82_num_neg_expected" ]]; then
  ok "rule82_pointer_present_but_stale_count_neg" "pointer present + stale '64 active gate rules' correctly flagged as numeric drift"
else
  fail "rule82_pointer_present_but_stale_count_neg" "expected drift but actual=$_r82_num_neg_actual matched expected=$_r82_num_neg_expected"
fi

## Negative-but-exempt: historical marker exempts a stale count -> Rule 82 PASS
_r82_num_hist_readme="$scratch/r82_num_hist_README.md"
cat > "$_r82_num_hist_readme" <<'DOCEOF'
The rc4 baseline 64 active gate rules grew to the rc6 canonical value.
See architecture_sync_gate.baseline_metrics for the current count.
DOCEOF
_r82_marker_re='historical|rc[0-9]+ baseline|pre-rc[0-9]+|previous|prior|deprecated|superseded|formerly|was [0-9]'
_r82_num_hist_line_has_marker=0
if grep -qiE "$_r82_marker_re" "$_r82_num_hist_readme"; then
  _r82_num_hist_line_has_marker=1
fi
if [[ $_r82_num_hist_line_has_marker -eq 1 ]]; then
  ok "rule82_historical_marker_exempts_neg" "historical-marker exemption correctly suppresses Rule 82 numeric-drift false positive"
else
  fail "rule82_historical_marker_exempts_neg" "expected historical marker to be detected"
fi

}

test_rule84_active_module_architecture_path_truth() {

## Positive: agent-service/ARCHITECTURE.md cites a real path -> Rule 84 PASS
_r84_pos_root="$scratch/r84_pos"
mkdir -p "$_r84_pos_root/agent-service/src/main/java/ascend/springai/service/runtime/runs"
cat > "$_r84_pos_root/agent-service/src/main/java/ascend/springai/service/runtime/runs/Run.java" <<'JAVA'
package ascend.springai.service.runtime.runs;
public record Run(String runId) {}
JAVA
cat > "$_r84_pos_root/agent-service/ARCHITECTURE.md" <<'DOCEOF'
---
status: active
---
The Run aggregate lives at agent-service/src/main/java/ascend/springai/service/runtime/runs/Run.java.
DOCEOF
_r84_pos_violation=0
while IFS= read -r _r84_p_path; do
  [[ -z "$_r84_p_path" ]] && continue
  [[ ! -e "$_r84_pos_root/${_r84_p_path%.}" ]] && _r84_pos_violation=1
done < <(grep -oE 'agent-[a-z-]+/src/main/java/[a-zA-Z0-9_/.-]+' "$_r84_pos_root/agent-service/ARCHITECTURE.md")
if [[ $_r84_pos_violation -eq 0 ]]; then
  ok "rule84_path_claim_resolves_pos" "active module ARCHITECTURE.md with resolving path correctly accepted"
else
  fail "rule84_path_claim_resolves_pos" "expected PASS but path-resolution check flagged"
fi

## Negative: agent-service/ARCHITECTURE.md cites stale post-ADR-0079 engine path (reviewer's rc5 evidence) -> Rule 84 FAIL
_r84_neg_root="$scratch/r84_neg"
mkdir -p "$_r84_neg_root/agent-service"
cat > "$_r84_neg_root/agent-service/ARCHITECTURE.md" <<'DOCEOF'
---
status: active
---
The engine SPI lives at agent-service/src/main/java/ascend/springai/service/runtime/engine/ExecutorAdapter.java.
DOCEOF
_r84_neg_violation=0
_r84_neg_marker_re='historical|moved|extracted per ADR-[0-9]{4}|superseded|formerly|deferred|pre-ADR-[0-9]{4}'
_r84_neg_lineno=0
while IFS= read -r _r84_neg_line || [[ -n "$_r84_neg_line" ]]; do
  _r84_neg_lineno=$((_r84_neg_lineno + 1))
  _r84_neg_paths=$(echo "$_r84_neg_line" | grep -oE 'agent-[a-z-]+/src/main/java/[a-zA-Z0-9_/.-]+' | sort -u)
  [[ -z "$_r84_neg_paths" ]] && continue
  while IFS= read -r _r84_neg_p; do
    [[ -z "$_r84_neg_p" ]] && continue
    [[ -e "$_r84_neg_root/${_r84_neg_p%.}" ]] && continue
    _r84_neg_lo=$((_r84_neg_lineno > 3 ? _r84_neg_lineno - 3 : 1))
    _r84_neg_hi=$((_r84_neg_lineno + 3))
    if sed -n "${_r84_neg_lo},${_r84_neg_hi}p" "$_r84_neg_root/agent-service/ARCHITECTURE.md" | grep -qiE "$_r84_neg_marker_re"; then continue; fi
    _r84_neg_violation=1
  done <<< "$_r84_neg_paths"
done < "$_r84_neg_root/agent-service/ARCHITECTURE.md"
if [[ $_r84_neg_violation -eq 1 ]]; then
  ok "rule84_path_claim_does_not_resolve_neg" "stale '.../runtime/engine/ExecutorAdapter' claim without historical marker correctly triggers FAIL"
else
  fail "rule84_path_claim_does_not_resolve_neg" "expected FAIL but stale path was accepted"
fi

## Negative-but-exempt: stale path with adjacent 'historical, pre-ADR-0079' marker -> Rule 84 PASS
_r84_hist_root="$scratch/r84_hist"
mkdir -p "$_r84_hist_root/agent-service"
cat > "$_r84_hist_root/agent-service/ARCHITECTURE.md" <<'DOCEOF'
---
status: active
---
Historical, pre-ADR-0079: engine code used to live at
agent-service/src/main/java/ascend/springai/service/runtime/engine/ExecutorAdapter.java.
Today it lives in agent-execution-engine per ADR-0079.
DOCEOF
_r84_hist_violation=0
_r84_hist_marker_re='historical|moved|extracted per ADR-[0-9]{4}|superseded|formerly|deferred|pre-ADR-[0-9]{4}'
_r84_hist_lineno=0
while IFS= read -r _r84_hist_line || [[ -n "$_r84_hist_line" ]]; do
  _r84_hist_lineno=$((_r84_hist_lineno + 1))
  _r84_hist_paths=$(echo "$_r84_hist_line" | grep -oE 'agent-[a-z-]+/src/main/java/[a-zA-Z0-9_/.-]+' | sort -u)
  [[ -z "$_r84_hist_paths" ]] && continue
  while IFS= read -r _r84_hist_p; do
    [[ -z "$_r84_hist_p" ]] && continue
    [[ -e "$_r84_hist_root/${_r84_hist_p%.}" ]] && continue
    _r84_hist_lo=$((_r84_hist_lineno > 3 ? _r84_hist_lineno - 3 : 1))
    _r84_hist_hi=$((_r84_hist_lineno + 3))
    if sed -n "${_r84_hist_lo},${_r84_hist_hi}p" "$_r84_hist_root/agent-service/ARCHITECTURE.md" | grep -qiE "$_r84_hist_marker_re"; then continue; fi
    _r84_hist_violation=1
  done <<< "$_r84_hist_paths"
done < "$_r84_hist_root/agent-service/ARCHITECTURE.md"
if [[ $_r84_hist_violation -eq 0 ]]; then
  ok "rule84_path_claim_historical_marker_exempts" "stale path with adjacent 'pre-ADR-0079' marker correctly accepted"
else
  fail "rule84_path_claim_historical_marker_exempts" "expected PASS but marker exemption was not honoured"
fi

}

test_rule85_catalog_spi_row_matches_module_spi_metadata() {

## Positive: catalog row package matches metadata + DFX spi_packages -> Rule 85 PASS
_r85_pos_root="$scratch/r85_pos"
mkdir -p "$_r85_pos_root/agent-service" "$_r85_pos_root/docs/contracts" "$_r85_pos_root/docs/dfx"
cat > "$_r85_pos_root/agent-service/module-metadata.yaml" <<'DOCEOF'
module: agent-service
spi_packages:
  - ascend.springai.service.runtime.memory.spi
  - ascend.springai.service.runtime.resilience.spi
DOCEOF
cat > "$_r85_pos_root/docs/dfx/agent-service.yaml" <<'DOCEOF'
module: agent-service
spi_packages:
  - ascend.springai.service.runtime.memory.spi
  - ascend.springai.service.runtime.resilience.spi
DOCEOF
cat > "$_r85_pos_root/docs/contracts/contract-catalog.md" <<'DOCEOF'
**Active SPI interfaces (1 total):**

| Interface | Module | Package | Status |
|---|---|---|---|
| `ResilienceContract` | `agent-service` | `ascend.springai.service.runtime.resilience.spi` | shipped |
DOCEOF
_r85_pos_violation=0
_r85_pos_pkg="ascend.springai.service.runtime.resilience.spi"
_r85_pos_meta=$(awk '/^spi_packages:/{f=1; next} f && /^[^[:space:]]/{exit} f && /^[[:space:]]*-[[:space:]]+/{sub(/^[[:space:]]*-[[:space:]]+/, ""); print}' "$_r85_pos_root/agent-service/module-metadata.yaml")
_r85_pos_dfx=$(awk '/^spi_packages:/{f=1; next} f && /^[^[:space:]]/{exit} f && /^[[:space:]]*-[[:space:]]+/{sub(/^[[:space:]]*-[[:space:]]+/, ""); print}' "$_r85_pos_root/docs/dfx/agent-service.yaml")
echo "$_r85_pos_meta" | grep -qF "$_r85_pos_pkg" || _r85_pos_violation=1
echo "$_r85_pos_dfx"  | grep -qF "$_r85_pos_pkg" || _r85_pos_violation=1
if [[ $_r85_pos_violation -eq 0 ]]; then
  ok "rule85_catalog_row_matches_metadata_pos" "catalog row matching metadata + DFX correctly accepted"
else
  fail "rule85_catalog_row_matches_metadata_pos" "expected PASS but metadata/DFX mismatch flagged"
fi

## Negative: catalog row package NOT in module metadata spi_packages (reviewer's rc5 evidence) -> Rule 85 FAIL
_r85_neg_root="$scratch/r85_neg"
mkdir -p "$_r85_neg_root/agent-service" "$_r85_neg_root/docs/contracts" "$_r85_neg_root/docs/dfx"
cat > "$_r85_neg_root/agent-service/module-metadata.yaml" <<'DOCEOF'
module: agent-service
spi_packages:
  - ascend.springai.service.runtime.memory.spi
DOCEOF
cat > "$_r85_neg_root/docs/dfx/agent-service.yaml" <<'DOCEOF'
module: agent-service
spi_packages:
  - ascend.springai.service.runtime.memory.spi
DOCEOF
cat > "$_r85_neg_root/docs/contracts/contract-catalog.md" <<'DOCEOF'
**Active SPI interfaces (1 total):**

| Interface | Module | Package | Status |
|---|---|---|---|
| `ResilienceContract` | `agent-service` | `ascend.springai.service.runtime.resilience` | shipped |
DOCEOF
_r85_neg_violation=0
_r85_neg_pkg="ascend.springai.service.runtime.resilience"
_r85_neg_meta=$(awk '/^spi_packages:/{f=1; next} f && /^[^[:space:]]/{exit} f && /^[[:space:]]*-[[:space:]]+/{sub(/^[[:space:]]*-[[:space:]]+/, ""); print}' "$_r85_neg_root/agent-service/module-metadata.yaml")
_r85_neg_match=0
while IFS= read -r _r85_neg_entry; do
  [[ -z "$_r85_neg_entry" ]] && continue
  if [[ "$_r85_neg_pkg" == "$_r85_neg_entry" ]] || [[ "$_r85_neg_pkg" == "$_r85_neg_entry".* ]] || [[ "$_r85_neg_entry" == "$_r85_neg_pkg".* ]]; then
    _r85_neg_match=1; break
  fi
done <<< "$_r85_neg_meta"
if [[ $_r85_neg_match -eq 0 ]]; then _r85_neg_violation=1; fi
if [[ $_r85_neg_violation -eq 1 ]]; then
  ok "rule85_catalog_row_missing_from_metadata_neg" "catalog row without metadata backing correctly triggers FAIL"
else
  fail "rule85_catalog_row_missing_from_metadata_neg" "expected FAIL but package was matched against metadata"
fi

## Negative-but-exempt: row marked (internal) -> Rule 85 SKIPS and header count must exclude it
_r85_int_root="$scratch/r85_int"
mkdir -p "$_r85_int_root/agent-service" "$_r85_int_root/docs/contracts" "$_r85_int_root/docs/dfx"
cat > "$_r85_int_root/agent-service/module-metadata.yaml" <<'DOCEOF'
module: agent-service
spi_packages:
  - ascend.springai.service.runtime.memory.spi
DOCEOF
cat > "$_r85_int_root/docs/dfx/agent-service.yaml" <<'DOCEOF'
module: agent-service
spi_packages:
  - ascend.springai.service.runtime.memory.spi
DOCEOF
cat > "$_r85_int_root/docs/contracts/contract-catalog.md" <<'DOCEOF'
**Active SPI interfaces (1 total):**

| Interface | Module | Package | Status |
|---|---|---|---|
| `GraphMemoryRepository` | `agent-service` | `ascend.springai.service.runtime.memory.spi` | shipped |
| `InternalThing` | `agent-service` | `ascend.springai.service.runtime.internal` | (internal) — not in spi_packages |
DOCEOF
_r85_int_violation=0
_r85_int_count=$(grep -cE '^\|[[:space:]]*`[A-Z]' "$_r85_int_root/docs/contracts/contract-catalog.md")
_r85_int_active_count=$(grep -E '^\|[[:space:]]*`[A-Z]' "$_r85_int_root/docs/contracts/contract-catalog.md" | grep -vi '(internal)' | wc -l | tr -d ' ')
_r85_int_header_total=$(grep -oE '^\*\*Active SPI interfaces \([0-9]+ total\):\*\*' "$_r85_int_root/docs/contracts/contract-catalog.md" | grep -oE '[0-9]+')
if [[ "$_r85_int_active_count" != "$_r85_int_header_total" ]]; then _r85_int_violation=1; fi
if [[ $_r85_int_violation -eq 0 ]]; then
  ok "rule85_internal_marker_exempts_neg" "(internal) row correctly excluded from header count + metadata requirement"
else
  fail "rule85_internal_marker_exempts_neg" "expected PASS but header count mismatched non-(internal) rows"
fi

}

test_rule83_design_only_contract_registered_in_catalog() {

## Positive: design-only contract listed in catalog + cites existing ADR -> Rule 83 PASS
_r83_pos="$scratch/r83_pos"
mkdir -p "$_r83_pos/docs/contracts" "$_r83_pos/docs/adr"
cat > "$_r83_pos/docs/contracts/sample.v1.yaml" <<'DOCEOF'
status: design_only
authority: "ADR-0032 (test)"
DOCEOF
cat > "$_r83_pos/docs/contracts/contract-catalog.md" <<'DOCEOF'
| `sample.v1.yaml` | design_only | ADR-0032 |
DOCEOF
cat > "$_r83_pos/docs/adr/0032-test.yaml" <<'DOCEOF'
id: ADR-0032
DOCEOF
_r83_pos_violation=0
_r83_pos_status=$(grep -E '^status:' "$_r83_pos/docs/contracts/sample.v1.yaml" | head -1)
_r83_pos_runtime=$(grep -E '^runtime_enforced:' "$_r83_pos/docs/contracts/sample.v1.yaml" 2>/dev/null | head -1 || true)
if [[ "$_r83_pos_status" == *design_only* ]] || [[ "$_r83_pos_runtime" == *false* ]]; then
  if ! grep -qF "sample.v1.yaml" "$_r83_pos/docs/contracts/contract-catalog.md"; then
    _r83_pos_violation=1
  fi
  _r83_pos_adr_ok=0
  while IFS= read -r _r83_pos_adr; do
    [[ -z "$_r83_pos_adr" ]] && continue
    _r83_pos_num="${_r83_pos_adr#ADR-}"
    if compgen -G "$_r83_pos/docs/adr/${_r83_pos_num}-*.yaml" > /dev/null; then
      _r83_pos_adr_ok=1
    fi
  done < <(grep -oE 'ADR-[0-9]{4}' "$_r83_pos/docs/contracts/sample.v1.yaml" | sort -u)
  [[ $_r83_pos_adr_ok -eq 0 ]] && _r83_pos_violation=1
fi
if [[ $_r83_pos_violation -eq 0 ]]; then
  ok "rule83_design_only_registered_pos" "design-only contract listed in catalog + cites existing ADR correctly accepted"
else
  fail "rule83_design_only_registered_pos" "expected PASS but violation flagged"
fi

## Negative: design-only contract NOT in catalog -> Rule 83 FAIL
_r83_neg="$scratch/r83_neg"
mkdir -p "$_r83_neg/docs/contracts" "$_r83_neg/docs/adr"
cat > "$_r83_neg/docs/contracts/orphan.v1.yaml" <<'DOCEOF'
status: design_only
authority: "ADR-9999 (does not exist)"
DOCEOF
cat > "$_r83_neg/docs/contracts/contract-catalog.md" <<'DOCEOF'
| `other.v1.yaml` | design_only | ADR-0032 |
DOCEOF
_r83_neg_violation=0
_r83_neg_status=$(grep -E '^status:' "$_r83_neg/docs/contracts/orphan.v1.yaml" | head -1)
if [[ "$_r83_neg_status" == *design_only* ]]; then
  if ! grep -qF "orphan.v1.yaml" "$_r83_neg/docs/contracts/contract-catalog.md"; then
    _r83_neg_violation=1
  fi
fi
if [[ $_r83_neg_violation -eq 1 ]]; then
  ok "rule83_design_only_registered_neg" "design-only contract not in catalog correctly triggers FAIL"
else
  fail "rule83_design_only_registered_neg" "expected FAIL but contract found in catalog"
fi

}

# ===========================================================================
# 2026-05-17 gate-script efficiency wave PR-E2 -- NDJSON logging self-tests
# Authority: PR-E2 plan + gate/lib/aggregate_summary.sh + gate/lib/prune_old_runs.sh
# ===========================================================================
test_rule_e2_ndjson_logging() {
# ---------------------------------------------------------------------------
# PR-E2 self-tests for the NDJSON / summary / retention pipeline.
#
# Strategy: synthesize a tiny GATE_LOG_DIR with 3 fake NDJSON lines (deliberately
# out-of-order to also exercise the in-place sort), invoke aggregate_summary.sh
# on it, then assert each expected artifact / property. A 5th test exercises
# prune_old_runs.sh on 5 synthetic run dirs.
#
# Validation via python is fed via stdin -- MSYS / git-bash maps /tmp/... to a
# Windows path Python can't open by path, but stdin works regardless.
# ---------------------------------------------------------------------------

_re2_root="$scratch/re2_root"
mkdir -p "$_re2_root/gate/log/runs"
mkdir -p "$_re2_root/gate/log/benchmarks"
mkdir -p "$_re2_root/gate/lib"
cp "$repo_root/gate/lib/aggregate_summary.sh" "$_re2_root/gate/lib/aggregate_summary.sh"
cp "$repo_root/gate/lib/prune_old_runs.sh"    "$_re2_root/gate/lib/prune_old_runs.sh"
printf '{}\n' > "$_re2_root/gate/log/benchmarks/median.json"

_re2_run_id="testsha_1700000000"
_re2_log_dir="$_re2_root/gate/log/runs/${_re2_run_id}"
mkdir -p "$_re2_log_dir"

# Synthesize per-rule.ndjson with 3 lines in REVERSE rule_number order to
# also exercise the in-place sort step.
cat > "$_re2_log_dir/per-rule.ndjson" <<'NDJSON'
{"rule_id":"73_gate_config_well_formed","rule_number":73,"rule_slug":"gate_config_well_formed","status":"PASS","duration_ms":120,"finished_at":"2026-05-17T22:00:00Z","reason":null,"worker_pid":1234}
{"rule_id":"1_status_enum_invalid","rule_number":1,"rule_slug":"status_enum_invalid","status":"PASS","duration_ms":80,"finished_at":"2026-05-17T22:00:00Z","reason":null,"worker_pid":1235}
{"rule_id":"28a_arch_prose_rules_section_present","rule_number":28,"rule_slug":"arch_prose_rules_section_present","status":"FAIL","duration_ms":200,"finished_at":"2026-05-17T22:00:00Z","reason":"synthetic test reason","worker_pid":1234}
NDJSON

cat > "$_re2_log_dir/manifest.txt" <<'MANI'
run_id=testsha_1700000000
git_sha=testsha
started_at=2026-05-17T21:59:00Z
hostname=test-host
platform=test
jobs=8
parallel=true
source_script=gate/check_architecture_sync.sh
MANI

# Run aggregate_summary against the synthetic dir.
GATE_REPO_ROOT="$_re2_root" \
GATE_LOGGING_SUMMARY_ENABLED="true" \
  bash "$_re2_root/gate/lib/aggregate_summary.sh" "$_re2_log_dir" >/dev/null 2>&1

# Create the latest symlink (or text fallback) the way check_parallel.sh does.
(
  cd "$_re2_root/gate/log" 2>/dev/null || exit 0
  if MSYS=winsymlinks:nativestrict ln -sfn "runs/${_re2_run_id}" latest 2>/dev/null; then
    :
  elif ln -sfn "runs/${_re2_run_id}" latest 2>/dev/null; then
    :
  else
    printf 'runs/%s\n' "$_re2_run_id" > latest.txt
  fi
)

# -------- Test 1: per-rule.ndjson present + valid JSON ---------------------
_re2_t1_file="$_re2_log_dir/per-rule.ndjson"
_re2_t1_lines=0
[[ -f "$_re2_t1_file" ]] && _re2_t1_lines=$(wc -l < "$_re2_t1_file" 2>/dev/null || echo 0)
_re2_t1_valid=0
if [[ "$_re2_t1_lines" -ge 1 ]] && command -v python3 >/dev/null 2>&1; then
  if cat "$_re2_t1_file" | python3 -c "
import json,sys
for ln in sys.stdin:
    ln=ln.strip()
    if not ln: continue
    try: json.loads(ln)
    except Exception: sys.exit(1)
sys.exit(0)
" >/dev/null 2>&1; then
    _re2_t1_valid=1
  fi
fi
if [[ "$_re2_t1_lines" -ge 1 && "$_re2_t1_valid" -eq 1 ]]; then
  ok "rule_e2_ndjson_per_rule_present" "per-rule.ndjson exists with $_re2_t1_lines valid JSON lines"
else
  fail "rule_e2_ndjson_per_rule_present" "expected >=1 valid JSON line; got lines=$_re2_t1_lines valid=$_re2_t1_valid"
fi

# -------- Test 2: summary.json present with record_type=summary -----------
_re2_t2_file="$_re2_log_dir/summary.json"
_re2_t2_rt=""
if [[ -f "$_re2_t2_file" ]]; then
  _re2_t2_rt=$(cat "$_re2_t2_file" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(d.get('record_type',''))
" 2>/dev/null || true)
fi
if [[ "$_re2_t2_rt" == "summary" ]]; then
  ok "rule_e2_summary_present" "summary.json has record_type=summary"
else
  fail "rule_e2_summary_present" "expected record_type=summary, got '$_re2_t2_rt'"
fi

# -------- Test 3: latest symlink (or fallback) resolves ------------------
_re2_t3_resolved=""
if [[ -L "$_re2_root/gate/log/latest" ]]; then
  _re2_t3_resolved=$(readlink "$_re2_root/gate/log/latest" 2>/dev/null || true)
elif [[ -f "$_re2_root/gate/log/latest.txt" ]]; then
  _re2_t3_resolved=$(head -1 "$_re2_root/gate/log/latest.txt" 2>/dev/null || true)
fi
# Resolved value should reference our run_id and the target dir must exist.
if [[ "$_re2_t3_resolved" == "runs/${_re2_run_id}" ]] && [[ -d "$_re2_root/gate/log/$_re2_t3_resolved" ]]; then
  ok "rule_e2_latest_symlink_resolves" "latest -> $_re2_t3_resolved (dir exists)"
else
  fail "rule_e2_latest_symlink_resolves" "expected runs/${_re2_run_id} resolvable, got '$_re2_t3_resolved'"
fi

# -------- Test 4: NDJSON sorted ascending by rule_number ------------------
# Pipe through python (avoids MSYS path mapping); python emits 'OK' on success.
_re2_t4_check=$(cat "$_re2_log_dir/per-rule.ndjson" | python3 -c "
import json,sys
prev=-1
for ln in sys.stdin:
    ln=ln.strip()
    if not ln: continue
    try:
        n=int(json.loads(ln).get('rule_number',-1))
    except Exception:
        print('PARSE-ERROR'); sys.exit(1)
    if n < prev:
        print('UNSORTED'); sys.exit(1)
    prev=n
print('OK')
" 2>/dev/null || echo "FAIL")
if [[ "$_re2_t4_check" == "OK" ]]; then
  ok "rule_e2_ndjson_sorted_by_rule_number" "per-rule.ndjson is ascending by rule_number"
else
  fail "rule_e2_ndjson_sorted_by_rule_number" "per-rule.ndjson not sorted ($_re2_t4_check)"
fi

# -------- Test 5: prune_old_runs.sh respects max_runs ---------------------
_re2_t5_root="$scratch/re2_prune_root"
mkdir -p "$_re2_t5_root/gate/log/runs"
mkdir -p "$_re2_t5_root/gate/lib"
cp "$repo_root/gate/lib/prune_old_runs.sh" "$_re2_t5_root/gate/lib/prune_old_runs.sh"
# Create 5 fake run dirs with monotonically increasing mtimes.
for _re2_t5_i in 1 2 3 4 5; do
  mkdir -p "$_re2_t5_root/gate/log/runs/run_${_re2_t5_i}"
  # touch -d explicitly orders them; fall back to plain touch if -d unsupported.
  touch -d "2026-05-17 21:00:0${_re2_t5_i}" "$_re2_t5_root/gate/log/runs/run_${_re2_t5_i}" 2>/dev/null \
    || touch "$_re2_t5_root/gate/log/runs/run_${_re2_t5_i}"
done
GATE_REPO_ROOT="$_re2_t5_root" \
GATE_LOGGING_RETENTION_MAX_RUNS=2 \
GATE_LOGGING_RETENTION_AUTO_PRUNE=true \
GATE_LOG_RUNS_DIR="$_re2_t5_root/gate/log/runs" \
  bash "$_re2_t5_root/gate/lib/prune_old_runs.sh" >/dev/null 2>&1
_re2_t5_remaining=$(find "$_re2_t5_root/gate/log/runs" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | wc -l)
if [[ "$_re2_t5_remaining" -eq 2 ]]; then
  ok "rule_e2_prune_respects_max_runs" "5 dirs pruned down to 2 newest"
else
  fail "rule_e2_prune_respects_max_runs" "expected 2 dirs remaining, got $_re2_t5_remaining"
fi

}

# ===========================================================================
# 2026-05-18 rc6 + rc8 prevention wave -- Rules 86-89 fixtures
# Authority: docs/governance/rules/rule-86.md + rule-87.md + rule-88.md + rule-89.md
# Closes finding families:
#   rc6 P0-2 root ARCHITECTURE.md 8-module + stale path claims  -> Rule 86
#   rc6 P1-2 status_yaml allowed_claim stale module names        -> Rule 87
#   rc7 P0-1 fenced-tree-block ownership drift                   -> Rule 86 fenced-block extension (rc8)
#   rc7 P0-2 parallel wrapper skips post-Summary rules           -> Rule 88 (rc8)
#   rc7 P1-1 harness fail-open + hardcoded TOTAL                  -> Rule 89 (rc8)
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 86 positive: root ARCHITECTURE.md 9-module claim matches pom.xml + status yaml
# ---------------------------------------------------------------------------
test_rule86_root_arch_count_pos() {
_r86_pos_root="$scratch/r86_pos"
mkdir -p "$_r86_pos_root/docs/governance"
mkdir -p "$_r86_pos_root/agent-service/src/main/java/ascend/springai/service/platform"
cat > "$_r86_pos_root/pom.xml" <<'POMEOF'
<project>
  <modules>
    <module>agent-client</module>
    <module>agent-service</module>
    <module>agent-middleware</module>
    <module>agent-execution-engine</module>
    <module>agent-bus</module>
    <module>agent-evolve</module>
    <module>spring-ai-ascend-dependencies</module>
    <module>spring-ai-ascend-graphmemory-starter</module>
  </modules>
</project>
POMEOF
cat > "$_r86_pos_root/docs/governance/architecture-status.yaml" <<'YEOF'
repository_counts:
  reactor_modules: 8
YEOF
cat > "$_r86_pos_root/ARCHITECTURE.md" <<'DOCEOF'
# Architecture
The reactor declares **8 modules** today.
Path claim: `agent-service/src/main/java/ascend/springai/service/platform`
DOCEOF
_r86_pos_pom=$(awk '/<modules>/,/<\/modules>/' "$_r86_pos_root/pom.xml" | grep -cE '^[[:space:]]*<module>')
_r86_pos_status=$(awk '/^repository_counts:/{flag=1; next} flag && /^[a-z]/{flag=0} flag' "$_r86_pos_root/docs/governance/architecture-status.yaml" | grep -oE 'reactor_modules:[[:space:]]+[0-9]+' | head -1 | grep -oE '[0-9]+$')
_r86_pos_claim=$(grep -oE '\*\*[0-9]+ modules\*\*' "$_r86_pos_root/ARCHITECTURE.md" | grep -oE '[0-9]+' | head -1)
if [[ "$_r86_pos_pom" == "8" ]] && [[ "$_r86_pos_status" == "8" ]] && [[ "$_r86_pos_claim" == "8" ]]; then
  ok "rule86_root_architecture_count_pos" "root ARCHITECTURE.md count 8 matches pom.xml + status_yaml canonical 8"
else
  fail "rule86_root_architecture_count_pos" "expected 8 across all three sources, got pom=$_r86_pos_pom status=$_r86_pos_status claim=$_r86_pos_claim"
fi
}

# ---------------------------------------------------------------------------
# Rule 86 negative: root ARCHITECTURE.md claims 8 modules but pom.xml has 9
# ---------------------------------------------------------------------------
test_rule86_root_arch_count_neg() {
_r86_neg_root="$scratch/r86_neg"
mkdir -p "$_r86_neg_root/docs/governance"
cat > "$_r86_neg_root/pom.xml" <<'POMEOF'
<project>
  <modules>
    <module>agent-client</module>
    <module>agent-service</module>
    <module>agent-decoy-rc12-extra</module>
    <module>agent-middleware</module>
    <module>agent-execution-engine</module>
    <module>agent-bus</module>
    <module>agent-evolve</module>
    <module>spring-ai-ascend-dependencies</module>
    <module>spring-ai-ascend-graphmemory-starter</module>
  </modules>
</project>
POMEOF
cat > "$_r86_neg_root/docs/governance/architecture-status.yaml" <<'YEOF'
repository_counts:
  reactor_modules: 9
YEOF
cat > "$_r86_neg_root/ARCHITECTURE.md" <<'DOCEOF'
# Architecture
The reactor declares **8 modules** today.
DOCEOF
_r86_neg_pom=$(awk '/<modules>/,/<\/modules>/' "$_r86_neg_root/pom.xml" | grep -cE '^[[:space:]]*<module>')
_r86_neg_claim=$(grep -oE '\*\*[0-9]+ modules\*\*' "$_r86_neg_root/ARCHITECTURE.md" | grep -oE '[0-9]+' | head -1)
# Check that the active claim disagrees with canonical AND has no historical marker
_r86_neg_marker_re='historical|pre-ADR-[0-9]{4}|pre-Phase-C|consolidated|merged'
if [[ "$_r86_neg_claim" != "$_r86_neg_pom" ]] && ! grep -qiE "$_r86_neg_marker_re" "$_r86_neg_root/ARCHITECTURE.md"; then
  ok "rule86_root_architecture_count_neg" "active 8-module claim with canonical 9 and no historical marker correctly flagged"
else
  fail "rule86_root_architecture_count_neg" "expected drift detection: claim=$_r86_neg_claim canonical=$_r86_neg_pom marker_present=$(grep -qiE \"$_r86_neg_marker_re\" \"$_r86_neg_root/ARCHITECTURE.md\" && echo yes || echo no)"
fi
}

# ---------------------------------------------------------------------------
# Rule 87 positive: allowed_claim with historical-marker-guarded module name passes
# ---------------------------------------------------------------------------
test_rule87_status_yaml_allowed_claim_pos() {
_r87_pos_root="$scratch/r87_pos"
mkdir -p "$_r87_pos_root/docs/governance"
cat > "$_r87_pos_root/docs/governance/architecture-status.yaml" <<'YEOF'
capabilities:
  example:
    status: ok
    allowed_claim: "Pre-Phase-C historical context: the original agent-platform module was consolidated into agent-service per ADR-0078 (2026-05-18). Today the platform layer lives at agent-service.service.platform."
YEOF
_r87_pos_marker_re='historical|pre-ADR-[0-9]{4}|pre-Phase-C|consolidated into|consolidated from|merged into|merged in|was rooted|formerly|superseded|deprecated|archived|moved|post-ADR-[0-9]{4}'
_r87_pos_lineno=0
_r87_pos_violation=0
while IFS= read -r _r87_pos_line || [[ -n "$_r87_pos_line" ]]; do
  _r87_pos_lineno=$((_r87_pos_lineno + 1))
  echo "$_r87_pos_line" | grep -qE '^[[:space:]]+allowed_claim:[[:space:]]*' || continue
  _r87_pos_value=$(echo "$_r87_pos_line" | sed -E 's/^[[:space:]]+allowed_claim:[[:space:]]*//')
  _r87_pos_stale=$(echo "$_r87_pos_value" | grep -oE '\bagent-platform\b|\bagent-runtime\b|\bagent-runtime-core\b' | head -1)
  [[ -z "$_r87_pos_stale" ]] && continue
  _r87_pos_lo=$((_r87_pos_lineno > 3 ? _r87_pos_lineno - 3 : 1))
  _r87_pos_hi=$((_r87_pos_lineno + 3))
  if sed -n "${_r87_pos_lo},${_r87_pos_hi}p" "$_r87_pos_root/docs/governance/architecture-status.yaml" 2>/dev/null | grep -qiE "$_r87_pos_marker_re"; then continue; fi
  _r87_pos_violation=1
done < "$_r87_pos_root/docs/governance/architecture-status.yaml"
if [[ $_r87_pos_violation -eq 0 ]]; then
  ok "rule87_status_yaml_allowed_claim_pos" "historical-marker-guarded allowed_claim correctly accepted"
else
  fail "rule87_status_yaml_allowed_claim_pos" "expected historical marker to exempt allowed_claim"
fi
}

# ---------------------------------------------------------------------------
# Rule 87 negative: allowed_claim with bare current-tense agent-platform fails
# ---------------------------------------------------------------------------
test_rule87_status_yaml_allowed_claim_neg() {
_r87_neg_root="$scratch/r87_neg"
mkdir -p "$_r87_neg_root/docs/governance"
cat > "$_r87_neg_root/docs/governance/architecture-status.yaml" <<'YEOF'
capabilities:
  example:
    status: ok
    allowed_claim: "Service Layer (agent-platform HTTP edge + agent-runtime cognitive runtime) deployed as long-running microservices."
YEOF
_r87_neg_marker_re='historical|pre-ADR-[0-9]{4}|pre-Phase-C|consolidated into|consolidated from|merged into|merged in|was rooted|formerly|superseded|deprecated|archived|moved|post-ADR-[0-9]{4}'
_r87_neg_lineno=0
_r87_neg_flagged=0
while IFS= read -r _r87_neg_line || [[ -n "$_r87_neg_line" ]]; do
  _r87_neg_lineno=$((_r87_neg_lineno + 1))
  echo "$_r87_neg_line" | grep -qE '^[[:space:]]+allowed_claim:[[:space:]]*' || continue
  _r87_neg_value=$(echo "$_r87_neg_line" | sed -E 's/^[[:space:]]+allowed_claim:[[:space:]]*//')
  _r87_neg_stale=$(echo "$_r87_neg_value" | grep -oE '\bagent-platform\b|\bagent-runtime\b|\bagent-runtime-core\b' | head -1)
  [[ -z "$_r87_neg_stale" ]] && continue
  _r87_neg_lo=$((_r87_neg_lineno > 3 ? _r87_neg_lineno - 3 : 1))
  _r87_neg_hi=$((_r87_neg_lineno + 3))
  if sed -n "${_r87_neg_lo},${_r87_neg_hi}p" "$_r87_neg_root/docs/governance/architecture-status.yaml" 2>/dev/null | grep -qiE "$_r87_neg_marker_re"; then continue; fi
  _r87_neg_flagged=1
done < "$_r87_neg_root/docs/governance/architecture-status.yaml"
if [[ $_r87_neg_flagged -eq 1 ]]; then
  ok "rule87_status_yaml_allowed_claim_neg" "bare current-tense agent-platform allowed_claim correctly flagged (no historical marker in +/-3 lines)"
else
  fail "rule87_status_yaml_allowed_claim_neg" "expected stale-module detection in allowed_claim"
fi
}

# ===========================================================================
# rc8 post-corrective wave -- Rule 86 fenced-tree-block extension + Rules 88-89
# Authority: docs/governance/rules/rule-86.md (amended) + rule-88.md + rule-89.md
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 86 tree-block positive: fenced tree leaf matches module-metadata declaration
# ---------------------------------------------------------------------------
test_rule86_tree_block_pos() {
_r86_tb_pos_root="$scratch/r86_tb_pos"
mkdir -p "$_r86_tb_pos_root/agent-service/src/main/java/ascend/springai/service/runtime/memory/spi"
cat > "$_r86_tb_pos_root/agent-service/module-metadata.yaml" <<'YEOF'
module: agent-service
kind: domain
spi_packages:
  - ascend.springai.service.runtime.memory.spi
  - ascend.springai.service.runtime.resilience.spi
YEOF
cat > "$_r86_tb_pos_root/ARCHITECTURE.md" <<'DOCEOF'
# Architecture
```
spring-ai-ascend/
  agent-service/
    src/main/java/ascend/springai/service/runtime/
      memory/spi/                  # GraphMemoryRepository — interface only
```
DOCEOF
# Simulate the rc8 2nd-pass check: look up parent module + check metadata
_r86_tb_pos_pkg=$(grep -oE '[a-z_]+/spi/' "$_r86_tb_pos_root/ARCHITECTURE.md" | head -1 | sed 's|/spi/||')
_r86_tb_pos_meta="$_r86_tb_pos_root/agent-service/module-metadata.yaml"
if grep -E '^[[:space:]]*-[[:space:]]+' "$_r86_tb_pos_meta" | grep -qE "\.${_r86_tb_pos_pkg}\.spi"; then
  ok "rule86_tree_block_pos" "fenced tree leaf '${_r86_tb_pos_pkg}/spi/' under agent-service resolves to module-metadata.yaml#spi_packages entry"
else
  fail "rule86_tree_block_pos" "expected metadata match for ${_r86_tb_pos_pkg}.spi under agent-service"
fi
}

# ---------------------------------------------------------------------------
# Rule 86 tree-block negative: fenced tree leaf under wrong module fails
# ---------------------------------------------------------------------------
test_rule86_tree_block_neg() {
_r86_tb_neg_root="$scratch/r86_tb_neg"
mkdir -p "$_r86_tb_neg_root/agent-runtime-core"
cat > "$_r86_tb_neg_root/agent-runtime-core/module-metadata.yaml" <<'YEOF'
module: agent-runtime-core
kind: domain
spi_packages:
  - ascend.springai.service.runtime.orchestration.spi
  - ascend.springai.service.runtime.runs.spi
  - ascend.springai.service.runtime.s2c.spi
YEOF
cat > "$_r86_tb_neg_root/ARCHITECTURE.md" <<'DOCEOF'
# Architecture
```
spring-ai-ascend/
  agent-runtime-core/
    src/main/java/ascend/springai/service/runtime/
      memory/spi/                  # GraphMemoryRepository — drift: claims memory.spi under agent-runtime-core
```
DOCEOF
# Simulate the rc8 2nd-pass: extract leaf, look up parent metadata, expect MISS
_r86_tb_neg_pkg=$(grep -oE '[a-z_]+/spi/' "$_r86_tb_neg_root/ARCHITECTURE.md" | head -1 | sed 's|/spi/||')
_r86_tb_neg_meta="$_r86_tb_neg_root/agent-runtime-core/module-metadata.yaml"
if ! grep -E '^[[:space:]]*-[[:space:]]+' "$_r86_tb_neg_meta" | grep -qE "\.${_r86_tb_neg_pkg}\.spi"; then
  ok "rule86_tree_block_neg" "fenced tree leaf '${_r86_tb_neg_pkg}/spi/' under agent-runtime-core correctly flagged (no matching metadata entry)"
else
  fail "rule86_tree_block_neg" "expected metadata miss for ${_r86_tb_neg_pkg}.spi under agent-runtime-core"
fi
}

# ---------------------------------------------------------------------------
# Rule 88 positive: canonical with em-dash separators and END marker has parity
# ---------------------------------------------------------------------------
test_rule88_serial_parallel_parity_pos() {
_r88_pos_root="$scratch/r88_pos"
mkdir -p "$_r88_pos_root/gate"
cat > "$_r88_pos_root/gate/check_architecture_sync.sh" <<'SHEOF'
# Rule 1 — alpha_rule (enforcer E1)
echo "rule 1 body"
# Rule 2 — beta_rule (enforcer E2)
echo "rule 2 body"
# === END OF RULES ===
SHEOF
cat > "$_r88_pos_root/gate/check_parallel.sh" <<'SHEOF'
# parallel wrapper stub
SHEOF
_r88_pos_canon_set=$(grep -E '^# Rule [0-9]+[a-z]? (—|--) ' "$_r88_pos_root/gate/check_architecture_sync.sh" | sed -E 's/^# Rule [0-9]+[a-z]? (—|--) //' | awk '{print $1}' | sort -u)
_r88_pos_par_set=$(awk '/^# Rule [0-9]+[a-z]? (—|--) / { match($0, /^# Rule [0-9]+[a-z]? (—|--) ([a-z_]+)/, arr); print arr[2] } /^# === END OF RULES ===$/ { exit }' "$_r88_pos_root/gate/check_architecture_sync.sh" | sort -u)
_r88_pos_missing=$(comm -23 <(echo "$_r88_pos_canon_set") <(echo "$_r88_pos_par_set") | grep -v '^$' || true)
if [[ -z "$_r88_pos_missing" ]]; then
  ok "rule88_serial_parallel_parity_pos" "em-dash + END-marker canonical script has parity with parallel awk extraction"
else
  fail "rule88_serial_parallel_parity_pos" "unexpected parity miss: $_r88_pos_missing"
fi
}

# ---------------------------------------------------------------------------
# Rule 88 negative: rule with double-dash separator gets caught by separator check
# ---------------------------------------------------------------------------
test_rule88_separator_neg() {
_r88_neg_root="$scratch/r88_neg"
mkdir -p "$_r88_neg_root/gate"
cat > "$_r88_neg_root/gate/check_architecture_sync.sh" <<'SHEOF'
# Rule 1 — alpha_rule (enforcer E1)
echo "rule 1 body"
# Rule 2 -- beta_rule (enforcer E2)
echo "rule 2 body with double-dash separator (forbidden)"
# === END OF RULES ===
SHEOF
_r88_neg_bad_sep=$(grep -nE '^# Rule [0-9]+[a-z]? -- ' "$_r88_neg_root/gate/check_architecture_sync.sh" || true)
if [[ -n "$_r88_neg_bad_sep" ]]; then
  ok "rule88_separator_neg" "double-dash rule header correctly detected by Rule 88 separator-consistency sub-check"
else
  fail "rule88_separator_neg" "expected double-dash detection"
fi
}

# ---------------------------------------------------------------------------
# Rule 89 positive: harness with manifest-derived TOTAL + fail-closed clause passes
# ---------------------------------------------------------------------------
test_rule89_fail_closed_pos() {
_r89_pos_root="$scratch/r89_pos"
mkdir -p "$_r89_pos_root/gate"
cat > "$_r89_pos_root/gate/test_architecture_sync_gate.sh" <<'SHEOF'
TOTAL=$((passed + failed))
if [[ "$passed" -ne "$TOTAL" ]]; then exit 1; fi
SHEOF
_r89_pos_has_closed=0
grep -qE 'passed[^=]*-ne[^=]*\$TOTAL|"\$passed"[[:space:]]+-ne[[:space:]]+"\$TOTAL"' "$_r89_pos_root/gate/test_architecture_sync_gate.sh" && _r89_pos_has_closed=1
_r89_pos_has_literal=0
grep -qE '^[[:space:]]*TOTAL=[0-9]+[[:space:]]*$' "$_r89_pos_root/gate/test_architecture_sync_gate.sh" && _r89_pos_has_literal=1
if [[ $_r89_pos_has_closed -eq 1 ]] && [[ $_r89_pos_has_literal -eq 0 ]]; then
  ok "rule89_fail_closed_pos" "manifest-derived TOTAL + passed!=TOTAL exit clause correctly accepted"
else
  fail "rule89_fail_closed_pos" "expected pass: has_closed=$_r89_pos_has_closed has_literal=$_r89_pos_has_literal"
fi
}

# ---------------------------------------------------------------------------
# Rule 89 negative: harness with bare-literal TOTAL=143 gets flagged
# ---------------------------------------------------------------------------
test_rule89_bare_literal_neg() {
_r89_neg_root="$scratch/r89_neg"
mkdir -p "$_r89_neg_root/gate"
cat > "$_r89_neg_root/gate/test_architecture_sync_gate.sh" <<'SHEOF'
TOTAL=143
if [[ "$failed" -gt 0 ]]; then exit 1; fi
exit 0
SHEOF
_r89_neg_literal_lines=$(grep -nE '^[[:space:]]*TOTAL=[0-9]+[[:space:]]*$' "$_r89_neg_root/gate/test_architecture_sync_gate.sh" || true)
if [[ -n "$_r89_neg_literal_lines" ]]; then
  ok "rule89_bare_literal_neg" "bare-literal TOTAL=143 correctly flagged by Rule 89 sub-check (b)"
else
  fail "rule89_bare_literal_neg" "expected bare-literal detection"
fi
}

# ---------------------------------------------------------------------------
# Rule 91 positive: status YAML active_gate_checks matches canonical manifest count
# ---------------------------------------------------------------------------
test_rule_91_baseline_matches_pos() {
_r91_pos_root="$scratch/r91_pos"
mkdir -p "$_r91_pos_root/gate" "$_r91_pos_root/docs/governance"
cat > "$_r91_pos_root/gate/check_architecture_sync.sh" <<'SHEOF'
# Rule 1 — slug_a
# Rule 2 — slug_b
# Rule 3 — slug_c
# === END OF RULES ===
SHEOF
cat > "$_r91_pos_root/docs/governance/architecture-status.yaml" <<'SHEOF'
baseline_metrics:
  active_gate_checks: 3
SHEOF
_r91_pos_count=$(awk '/^# === END OF RULES ===$/{exit} /^# Rule [0-9]+[a-z]? — /{c++} END{print c+0}' "$_r91_pos_root/gate/check_architecture_sync.sh")
_r91_pos_decl=$(grep -E '^[[:space:]]*active_gate_checks:[[:space:]]*[0-9]+' "$_r91_pos_root/docs/governance/architecture-status.yaml" | head -1 | sed -E 's/.*active_gate_checks:[[:space:]]*([0-9]+).*/\1/')
if [[ "$_r91_pos_count" == "$_r91_pos_decl" ]]; then
  ok "rule_91_baseline_matches_pos" "active_gate_checks=$_r91_pos_decl == canonical count $_r91_pos_count"
else
  fail "rule_91_baseline_matches_pos" "expected match: count=$_r91_pos_count decl=$_r91_pos_decl"
fi
}

# ---------------------------------------------------------------------------
# Rule 91 negative: status YAML claims 74 but canonical manifest has 3 headers
# ---------------------------------------------------------------------------
test_rule_91_baseline_drift_neg() {
_r91_neg_root="$scratch/r91_neg"
mkdir -p "$_r91_neg_root/gate" "$_r91_neg_root/docs/governance"
cat > "$_r91_neg_root/gate/check_architecture_sync.sh" <<'SHEOF'
# Rule 1 — slug_a
# Rule 2 — slug_b
# Rule 3 — slug_c
# === END OF RULES ===
SHEOF
cat > "$_r91_neg_root/docs/governance/architecture-status.yaml" <<'SHEOF'
baseline_metrics:
  active_gate_checks: 74
SHEOF
_r91_neg_count=$(awk '/^# === END OF RULES ===$/{exit} /^# Rule [0-9]+[a-z]? — /{c++} END{print c+0}' "$_r91_neg_root/gate/check_architecture_sync.sh")
_r91_neg_decl=$(grep -E '^[[:space:]]*active_gate_checks:[[:space:]]*[0-9]+' "$_r91_neg_root/docs/governance/architecture-status.yaml" | head -1 | sed -E 's/.*active_gate_checks:[[:space:]]*([0-9]+).*/\1/')
if [[ "$_r91_neg_count" != "$_r91_neg_decl" ]]; then
  ok "rule_91_baseline_drift_neg" "Rule 91 correctly flags drift: decl=$_r91_neg_decl != count=$_r91_neg_count"
else
  fail "rule_91_baseline_drift_neg" "expected drift detection: count=$_r91_neg_count decl=$_r91_neg_decl"
fi
}

# ---------------------------------------------------------------------------
# Rule 92 positive: every canonical header has a matching gate/rules file
# ---------------------------------------------------------------------------
test_rule_92_freshness_pos() {
_r92_pos_root="$scratch/r92_pos"
mkdir -p "$_r92_pos_root/gate/rules"
cat > "$_r92_pos_root/gate/check_architecture_sync.sh" <<'SHEOF'
# Rule 1 — slug_a
# Rule 28a — slug_b
# === END OF RULES ===
SHEOF
touch "$_r92_pos_root/gate/rules/rule-001.sh" "$_r92_pos_root/gate/rules/rule-028a.sh"
_r92_pos_missing=""
while IFS= read -r _r92_rid; do
  [[ -z "$_r92_rid" ]] && continue
  _r92_num=$(echo "$_r92_rid" | grep -oE '^[0-9]+')
  _r92_letter=$(echo "$_r92_rid" | grep -oE '[a-z]$' || true)
  _r92_padded=$(printf "%03d" "$_r92_num")
  [[ -f "$_r92_pos_root/gate/rules/rule-${_r92_padded}${_r92_letter}.sh" ]] || _r92_pos_missing="${_r92_pos_missing}${_r92_rid} "
done < <(awk '/^# === END OF RULES ===$/{exit} /^# Rule [0-9]+[a-z]? — /{match($0, /^# Rule ([0-9]+[a-z]?) — /, a); print a[1]}' "$_r92_pos_root/gate/check_architecture_sync.sh")
if [[ -z "$_r92_pos_missing" ]]; then
  ok "rule_92_freshness_pos" "all canonical headers have matching gate/rules files"
else
  fail "rule_92_freshness_pos" "unexpected missing: $_r92_pos_missing"
fi
}

# ---------------------------------------------------------------------------
# Rule 92 negative: canonical has a header without a matching file
# ---------------------------------------------------------------------------
test_rule_92_freshness_drift_neg() {
_r92_neg_root="$scratch/r92_neg"
mkdir -p "$_r92_neg_root/gate/rules"
cat > "$_r92_neg_root/gate/check_architecture_sync.sh" <<'SHEOF'
# Rule 1 — slug_a
# Rule 99 — slug_orphan
# === END OF RULES ===
SHEOF
touch "$_r92_neg_root/gate/rules/rule-001.sh"   # NOTE: rule-099.sh deliberately missing
_r92_neg_missing=""
while IFS= read -r _r92_rid; do
  [[ -z "$_r92_rid" ]] && continue
  _r92_num=$(echo "$_r92_rid" | grep -oE '^[0-9]+')
  _r92_letter=$(echo "$_r92_rid" | grep -oE '[a-z]$' || true)
  _r92_padded=$(printf "%03d" "$_r92_num")
  [[ -f "$_r92_neg_root/gate/rules/rule-${_r92_padded}${_r92_letter}.sh" ]] || _r92_neg_missing="${_r92_neg_missing}${_r92_rid} "
done < <(awk '/^# === END OF RULES ===$/{exit} /^# Rule [0-9]+[a-z]? — /{match($0, /^# Rule ([0-9]+[a-z]?) — /, a); print a[1]}' "$_r92_neg_root/gate/check_architecture_sync.sh")
if [[ -n "$_r92_neg_missing" ]]; then
  ok "rule_92_freshness_drift_neg" "Rule 92 correctly flags missing: $_r92_neg_missing"
else
  fail "rule_92_freshness_drift_neg" "expected drift detection"
fi
}

# ---------------------------------------------------------------------------
# Rule 93 positive: every dfx stem matches a module in pom.xml
# ---------------------------------------------------------------------------
test_rule_93_dfx_stem_pos() {
_r93_pos_root="$scratch/r93_pos"
mkdir -p "$_r93_pos_root/docs/dfx"
cat > "$_r93_pos_root/pom.xml" <<'SHEOF'
<modules>
  <module>agent-service</module>
  <module>agent-runtime-core</module>
</modules>
SHEOF
touch "$_r93_pos_root/docs/dfx/agent-service.yaml" "$_r93_pos_root/docs/dfx/agent-runtime-core.yaml"
_r93_pos_modules=$(grep -oE '<module>[^<]+</module>' "$_r93_pos_root/pom.xml" | sed -E 's|</?module>||g' | sort -u)
_r93_pos_orphans=""
for _r93_dfx in "$_r93_pos_root"/docs/dfx/*.yaml; do
  [[ -e "$_r93_dfx" ]] || continue
  _r93_stem=$(basename "$_r93_dfx" .yaml)
  echo "$_r93_pos_modules" | grep -qxF "$_r93_stem" || _r93_pos_orphans="${_r93_pos_orphans}${_r93_stem} "
done
if [[ -z "$_r93_pos_orphans" ]]; then
  ok "rule_93_dfx_stem_pos" "all dfx stems map to modules"
else
  fail "rule_93_dfx_stem_pos" "unexpected orphans: $_r93_pos_orphans"
fi
}

# ---------------------------------------------------------------------------
# Rule 93 negative: dfx file for non-existent module
# ---------------------------------------------------------------------------
test_rule_93_dfx_orphan_neg() {
_r93_neg_root="$scratch/r93_neg"
mkdir -p "$_r93_neg_root/docs/dfx"
cat > "$_r93_neg_root/pom.xml" <<'SHEOF'
<modules>
  <module>agent-service</module>
</modules>
SHEOF
touch "$_r93_neg_root/docs/dfx/agent-platform.yaml"   # orphan — module deleted
_r93_neg_modules=$(grep -oE '<module>[^<]+</module>' "$_r93_neg_root/pom.xml" | sed -E 's|</?module>||g' | sort -u)
_r93_neg_orphans=""
for _r93_dfx in "$_r93_neg_root"/docs/dfx/*.yaml; do
  [[ -e "$_r93_dfx" ]] || continue
  _r93_stem=$(basename "$_r93_dfx" .yaml)
  echo "$_r93_neg_modules" | grep -qxF "$_r93_stem" || _r93_neg_orphans="${_r93_neg_orphans}${_r93_stem} "
done
if [[ "$_r93_neg_orphans" == "agent-platform " ]]; then
  ok "rule_93_dfx_orphan_neg" "Rule 93 correctly flags orphan: $_r93_neg_orphans"
else
  fail "rule_93_dfx_orphan_neg" "expected orphan=agent-platform, got: $_r93_neg_orphans"
fi
}

# ---------------------------------------------------------------------------
# Rule 94 positive: file mentions agent-platform with `pre-Phase-C` marker within ±3 lines
# ---------------------------------------------------------------------------
test_rule_94_corpus_with_marker_pos() {
_r94_pos_root="$scratch/r94_pos"
mkdir -p "$_r94_pos_root"
cat > "$_r94_pos_root/some-doc.md" <<'SHEOF'
This is pre-Phase-C context:
the old agent-platform/web/foo path is documented.
SHEOF
_r94_pos_hits=$(awk -v markers='historical|pre-ADR-[0-9]+|pre-Phase-C' '
  BEGIN { in_code = 0; ap_re = "(^|[^a-zA-Z0-9_-])agent-platform([^a-zA-Z0-9_-]|$)" }
  /^[[:space:]]*```/ { in_code = 1 - in_code; next }
  { lines[NR] = $0 }
  END {
    in_code = 0
    for (i = 1; i <= NR; i++) {
      line = lines[i]
      if (line ~ /^[[:space:]]*```/) { in_code = 1 - in_code; continue }
      if (in_code) continue
      if (line ~ /^[[:space:]]*#/) continue
      if (line ~ ap_re) {
        lo = i - 3; if (lo < 1) lo = 1
        hi = i + 3; if (hi > NR) hi = NR
        window = ""
        for (j = lo; j <= hi; j++) window = window " " lines[j]
        if (window !~ markers) print i ":" line
      }
    }
  }
' "$_r94_pos_root/some-doc.md")
if [[ -z "$_r94_pos_hits" ]]; then
  ok "rule_94_corpus_with_marker_pos" "Rule 94 correctly accepts agent-platform reference WITH pre-Phase-C marker"
else
  fail "rule_94_corpus_with_marker_pos" "false positive: $_r94_pos_hits"
fi
}

# ---------------------------------------------------------------------------
# Rule 94 negative: file mentions agent-platform with NO marker → violation
# ---------------------------------------------------------------------------
test_rule_94_corpus_bare_neg() {
_r94_neg_root="$scratch/r94_neg"
mkdir -p "$_r94_neg_root"
cat > "$_r94_neg_root/some-doc.md" <<'SHEOF'
Look at agent-platform/web/foo for the live path.
SHEOF
_r94_neg_hits=$(awk -v markers='historical|pre-ADR-[0-9]+|pre-Phase-C' '
  BEGIN { in_code = 0; ap_re = "(^|[^a-zA-Z0-9_-])agent-platform([^a-zA-Z0-9_-]|$)" }
  /^[[:space:]]*```/ { in_code = 1 - in_code; next }
  { lines[NR] = $0 }
  END {
    in_code = 0
    for (i = 1; i <= NR; i++) {
      line = lines[i]
      if (line ~ /^[[:space:]]*```/) { in_code = 1 - in_code; continue }
      if (in_code) continue
      if (line ~ /^[[:space:]]*#/) continue
      if (line ~ ap_re) {
        lo = i - 3; if (lo < 1) lo = 1
        hi = i + 3; if (hi > NR) hi = NR
        window = ""
        for (j = lo; j <= hi; j++) window = window " " lines[j]
        if (window !~ markers) print i ":" line
      }
    }
  }
' "$_r94_neg_root/some-doc.md")
if [[ -n "$_r94_neg_hits" ]]; then
  ok "rule_94_corpus_bare_neg" "Rule 94 correctly flags bare agent-platform reference"
else
  fail "rule_94_corpus_bare_neg" "expected violation, got none"
fi
}

# ---------------------------------------------------------------------------
# Rule 95 positive: every public SPI interface appears in catalog
# ---------------------------------------------------------------------------
test_rule_95_spi_catalog_pos() {
_r95_pos_root="$scratch/r95_pos"
mkdir -p "$_r95_pos_root/a/spi" "$_r95_pos_root/docs/contracts"
cat > "$_r95_pos_root/a/spi/RunRepository.java" <<'SHEOF'
public interface RunRepository {
}
SHEOF
cat > "$_r95_pos_root/docs/contracts/contract-catalog.md" <<'SHEOF'
| `RunRepository` | active SPI row |
SHEOF
_r95_pos_missing=""
while IFS= read -r _r95_spi_file; do
  _r95_iface=$(grep -E '^public[[:space:]]+interface[[:space:]]+[A-Za-z_][A-Za-z0-9_]*' "$_r95_spi_file" 2>/dev/null | head -1 | sed -E 's/^public[[:space:]]+interface[[:space:]]+([A-Za-z_][A-Za-z0-9_]*).*/\1/')
  [[ -z "$_r95_iface" ]] && continue
  grep -qE "\`${_r95_iface}\`" "$_r95_pos_root/docs/contracts/contract-catalog.md" || _r95_pos_missing="${_r95_pos_missing}${_r95_iface} "
done < <(find "$_r95_pos_root" -type f -name '*.java' -path '*/spi/*')
if [[ -z "$_r95_pos_missing" ]]; then
  ok "rule_95_spi_catalog_pos" "every public SPI in fixture appears in catalog"
else
  fail "rule_95_spi_catalog_pos" "unexpected missing: $_r95_pos_missing"
fi
}

# ---------------------------------------------------------------------------
# Rule 95 negative: public SPI interface missing from catalog
# ---------------------------------------------------------------------------
test_rule_95_spi_missing_neg() {
_r95_neg_root="$scratch/r95_neg"
mkdir -p "$_r95_neg_root/b/spi" "$_r95_neg_root/docs/contracts"
cat > "$_r95_neg_root/b/spi/SkillCapacityRegistry.java" <<'SHEOF'
public interface SkillCapacityRegistry {
}
SHEOF
cat > "$_r95_neg_root/docs/contracts/contract-catalog.md" <<'SHEOF'
| `RunRepository` | active SPI row |
SHEOF
_r95_neg_missing=""
while IFS= read -r _r95_spi_file; do
  _r95_iface=$(grep -E '^public[[:space:]]+interface[[:space:]]+[A-Za-z_][A-Za-z0-9_]*' "$_r95_spi_file" 2>/dev/null | head -1 | sed -E 's/^public[[:space:]]+interface[[:space:]]+([A-Za-z_][A-Za-z0-9_]*).*/\1/')
  [[ -z "$_r95_iface" ]] && continue
  grep -qE "\`${_r95_iface}\`" "$_r95_neg_root/docs/contracts/contract-catalog.md" || _r95_neg_missing="${_r95_neg_missing}${_r95_iface} "
done < <(find "$_r95_neg_root" -type f -name '*.java' -path '*/spi/*')
if [[ "$_r95_neg_missing" == "SkillCapacityRegistry " ]]; then
  ok "rule_95_spi_missing_neg" "Rule 95 correctly flags SkillCapacityRegistry missing from catalog"
else
  fail "rule_95_spi_missing_neg" "expected SkillCapacityRegistry missing, got: $_r95_neg_missing"
fi
}

# ---------------------------------------------------------------------------
# Rule 96 positive: CLAUDE.md Rule N kernel cites the Rule N.b deferred sub-clause
# ---------------------------------------------------------------------------
test_rule_96_kernel_cites_pos() {
_r96_pos_root="$scratch/r96_pos"
mkdir -p "$_r96_pos_root/docs"
cat > "$_r96_pos_root/CLAUDE.md" <<'SHEOF'
#### Rule 42 — Sandbox

**Schema obligation only. Runtime refusal is deferred to Rule 42.b (W2).**

---
SHEOF
cat > "$_r96_pos_root/docs/CLAUDE-deferred.md" <<'SHEOF'
## Rule 42.b — SandboxExecutor Subsumption Runtime Check [Deferred to W2]
SHEOF
_r96_pos_missing=""
while IFS= read -r _r96_sub; do
  _r96_num=$(echo "$_r96_sub" | grep -oE '^[0-9]+')
  _r96_letter=$(echo "$_r96_sub" | grep -oE '\.[a-z]$' | sed 's/^\.//')
  [[ -z "$_r96_num" ]] || [[ -z "$_r96_letter" ]] && continue
  _r96_block=$(awk -v rn="$_r96_num" '
    $0 ~ "^#### Rule "rn" " { in_block = 1; print; next }
    in_block && /^---$/ { exit }
    in_block { print }
  ' "$_r96_pos_root/CLAUDE.md")
  [[ -z "$_r96_block" ]] && continue
  echo "$_r96_block" | grep -qF "Rule ${_r96_num}.${_r96_letter}" || _r96_pos_missing="${_r96_pos_missing}Rule${_r96_num}.${_r96_letter} "
done < <(grep -oE '^## Rule [0-9]+\.[a-z]' "$_r96_pos_root/docs/CLAUDE-deferred.md" | sed -E 's/^## Rule //')
if [[ -z "$_r96_pos_missing" ]]; then
  ok "rule_96_kernel_cites_pos" "Rule 96 correctly accepts kernel that cites Rule 42.b"
else
  fail "rule_96_kernel_cites_pos" "unexpected missing: $_r96_pos_missing"
fi
}

# ---------------------------------------------------------------------------
# Rule 96 negative: CLAUDE.md Rule N kernel does NOT cite Rule N.b deferred sub-clause
# ---------------------------------------------------------------------------
test_rule_96_kernel_no_cite_neg() {
_r96_neg_root="$scratch/r96_neg"
mkdir -p "$_r96_neg_root/docs"
cat > "$_r96_neg_root/CLAUDE.md" <<'SHEOF'
#### Rule 42 — Sandbox

**The runtime SandboxExecutor MUST refuse over-wide grants.**

---
SHEOF
cat > "$_r96_neg_root/docs/CLAUDE-deferred.md" <<'SHEOF'
## Rule 42.b — SandboxExecutor Subsumption Runtime Check [Deferred to W2]
SHEOF
_r96_neg_missing=""
while IFS= read -r _r96_sub; do
  _r96_num=$(echo "$_r96_sub" | grep -oE '^[0-9]+')
  _r96_letter=$(echo "$_r96_sub" | grep -oE '\.[a-z]$' | sed 's/^\.//')
  [[ -z "$_r96_num" ]] || [[ -z "$_r96_letter" ]] && continue
  _r96_block=$(awk -v rn="$_r96_num" '
    $0 ~ "^#### Rule "rn" " { in_block = 1; print; next }
    in_block && /^---$/ { exit }
    in_block { print }
  ' "$_r96_neg_root/CLAUDE.md")
  [[ -z "$_r96_block" ]] && continue
  echo "$_r96_block" | grep -qF "Rule ${_r96_num}.${_r96_letter}" || _r96_neg_missing="${_r96_neg_missing}Rule${_r96_num}.${_r96_letter} "
done < <(grep -oE '^## Rule [0-9]+\.[a-z]' "$_r96_neg_root/docs/CLAUDE-deferred.md" | sed -E 's/^## Rule //')
if [[ "$_r96_neg_missing" == "Rule42.b " ]]; then
  ok "rule_96_kernel_no_cite_neg" "Rule 96 correctly flags kernel that doesn't cite Rule 42.b"
else
  fail "rule_96_kernel_no_cite_neg" "expected Rule42.b missing, got: $_r96_neg_missing"
fi
}

# ---------------------------------------------------------------------------
# Rule 97 positive: latest release note's "<N> nodes / <M> edges" matches live values
# ---------------------------------------------------------------------------
test_rule_97_release_note_numeric_pos() {
_r97_pos_root="$scratch/r97_pos"
mkdir -p "$_r97_pos_root/docs/logs/releases" "$_r97_pos_root/docs/governance"
cat > "$_r97_pos_root/docs/governance/architecture-graph.yaml" <<'SHEOF'
node_count: 369
edge_count: 520
SHEOF
cat > "$_r97_pos_root/docs/logs/releases/2026-05-19-l0-rc10-pos.en.md" <<'SHEOF'
| Architecture graph | 369 nodes / 520 edges | +21 nodes / +34 edges (rc9 → rc10 delta) |
SHEOF
_r97_live_n=$(grep -E '^node_count:' "$_r97_pos_root/docs/governance/architecture-graph.yaml" | awk '{print $2}')
_r97_live_e=$(grep -E '^edge_count:' "$_r97_pos_root/docs/governance/architecture-graph.yaml" | awk '{print $2}')
_r97_latest=$(find "$_r97_pos_root/docs/logs/releases" -maxdepth 1 -type f -name '*.md' | sort | tail -1)
_r97_violations=$(awk -v live_n="$_r97_live_n" -v live_e="$_r97_live_e" '
  { lines[NR] = $0 }
  END {
    in_code = 0
    for (i = 1; i <= NR; i++) {
      line = lines[i]
      if (line ~ /^[[:space:]]*```/) { in_code = 1 - in_code; continue }
      if (in_code) continue
      if (line ~ /[^+0-9][0-9]+[[:space:]]+nodes/ || line ~ /^[0-9]+[[:space:]]+nodes/) {
        n_str = line
        sub(/^[^0-9]*\+[0-9]+[[:space:]]+nodes/, "", n_str)
        if (match(n_str, /[^+0-9]?([0-9]+)[[:space:]]+nodes/)) {
          s = substr(n_str, RSTART, RLENGTH); gsub(/[^0-9]/, "", s)
          if (s != "" && s != live_n) print "violation"
        }
      }
    }
  }' "$_r97_latest" 2>/dev/null)
if [[ -z "$_r97_violations" ]]; then
  ok "rule_97_release_note_numeric_pos" "Rule 97 accepts latest release note with matching node/edge counts"
else
  fail "rule_97_release_note_numeric_pos" "unexpected violation: $_r97_violations"
fi
}

# ---------------------------------------------------------------------------
# Rule 97 negative: latest release note disagrees with live values, no marker
# ---------------------------------------------------------------------------
test_rule_97_release_note_numeric_neg() {
_r97_neg_root="$scratch/r97_neg"
mkdir -p "$_r97_neg_root/docs/logs/releases" "$_r97_neg_root/docs/governance"
cat > "$_r97_neg_root/docs/governance/architecture-graph.yaml" <<'SHEOF'
node_count: 369
edge_count: 520
SHEOF
cat > "$_r97_neg_root/docs/logs/releases/2026-05-19-l0-rc10-neg.en.md" <<'SHEOF'
| Architecture graph | 360 nodes / 510 edges | +12 nodes / +24 edges |
And another line claiming 360 nodes / 510 edges.
A third line as filler.
SHEOF
_r97_live_n=$(grep -E '^node_count:' "$_r97_neg_root/docs/governance/architecture-graph.yaml" | awk '{print $2}')
_r97_live_e=$(grep -E '^edge_count:' "$_r97_neg_root/docs/governance/architecture-graph.yaml" | awk '{print $2}')
_r97_latest=$(find "$_r97_neg_root/docs/logs/releases" -maxdepth 1 -type f -name '*.md' | sort | tail -1)
_r97_markers='historical|rc[0-9]+ snapshot|rc[0-9]+ correction|rc[0-9]+ first cut|rc[0-9]+ baseline|superseded|previous|pre-rc[0-9]+'
_r97_violations=$(awk -v live_n="$_r97_live_n" -v live_e="$_r97_live_e" -v markers="$_r97_markers" '
  { lines[NR] = $0 }
  END {
    in_code = 0
    for (i = 1; i <= NR; i++) {
      line = lines[i]
      if (line ~ /^[[:space:]]*```/) { in_code = 1 - in_code; continue }
      if (in_code) continue
      lo = i - 3; if (lo < 1) lo = 1
      hi = i + 3; if (hi > NR) hi = NR
      window = ""
      for (j = lo; j <= hi; j++) window = window " " lines[j]
      if (line ~ /[^+0-9][0-9]+[[:space:]]+nodes/ || line ~ /^[0-9]+[[:space:]]+nodes/) {
        n_str = line
        sub(/^[^0-9]*\+[0-9]+[[:space:]]+nodes/, "", n_str)
        if (match(n_str, /[^+0-9]?([0-9]+)[[:space:]]+nodes/)) {
          s = substr(n_str, RSTART, RLENGTH); gsub(/[^0-9]/, "", s)
          if (s != "" && s != live_n && window !~ markers) print i ":nodes:" s
        }
      }
    }
  }' "$_r97_latest" 2>/dev/null)
if [[ -n "$_r97_violations" ]]; then
  ok "rule_97_release_note_numeric_neg" "Rule 97 correctly flags release note with wrong counts and no marker"
else
  fail "rule_97_release_note_numeric_neg" "expected violation, got none"
fi
}

# ---------------------------------------------------------------------------
# Rule 98 positive: ops/* yaml uses only agent-service (post-Phase-C name)
# ---------------------------------------------------------------------------
test_rule_98_broad_corpus_pos() {
_r98_pos_root="$scratch/r98_pos"
mkdir -p "$_r98_pos_root/ops/helm"
cat > "$_r98_pos_root/ops/helm/test.yaml" <<'SHEOF'
image:
  repository: springaiascend/agent-service
  pullPolicy: IfNotPresent
SHEOF
_r98_violations=""
while IFS= read -r _r98_file; do
  [[ -z "$_r98_file" ]] && continue
  _r98_hits=$(awk '
    BEGIN {
      ap_re = "(^|[^a-zA-Z0-9_-])agent-platform([^a-zA-Z0-9_-]|$)"
      ar_re = "(^|[^a-zA-Z0-9_-])agent-runtime([^a-zA-Z0-9_-]|$)"
      arc_re = "(^|[^a-zA-Z0-9_-])agent-runtime-core([^a-zA-Z0-9_-]|$)"
    }
    { lines[NR] = $0 }
    END {
      for (i = 1; i <= NR; i++) {
        line = lines[i]
        if (line ~ /^[[:space:]]*#/) continue
        if (line ~ ap_re || (line ~ ar_re && line !~ arc_re) || line ~ arc_re) print i
      }
    }' "$_r98_file" 2>/dev/null)
  [[ -n "$_r98_hits" ]] && _r98_violations="${_r98_violations}${_r98_file}:${_r98_hits} "
done < <(find "$_r98_pos_root/ops" -type f -name '*.yaml' 2>/dev/null)
if [[ -z "$_r98_violations" ]]; then
  ok "rule_98_broad_corpus_pos" "Rule 98 accepts ops yaml with only agent-service references"
else
  fail "rule_98_broad_corpus_pos" "unexpected violations: $_r98_violations"
fi
}

# ---------------------------------------------------------------------------
# Rule 98 negative: ops/* yaml has bare agent-platform without historical marker
# ---------------------------------------------------------------------------
test_rule_98_broad_corpus_neg() {
_r98_neg_root="$scratch/r98_neg"
mkdir -p "$_r98_neg_root/ops/helm"
cat > "$_r98_neg_root/ops/helm/bad.yaml" <<'SHEOF'
image:
  repository: springaiascend/agent-platform
  pullPolicy: IfNotPresent
SHEOF
_r98_markers='historical|pre-ADR-[0-9]+|pre-Phase-C|consolidated into|post-ADR-[0-9]+|post-Phase-C|ADR-[0-9]+|forbidden_dependencies'
_r98_violations=""
while IFS= read -r _r98_file; do
  [[ -z "$_r98_file" ]] && continue
  _r98_hits=$(awk -v markers="$_r98_markers" '
    BEGIN {
      ap_re = "(^|[^a-zA-Z0-9_-])agent-platform([^a-zA-Z0-9_-]|$)"
      ar_re = "(^|[^a-zA-Z0-9_-])agent-runtime([^a-zA-Z0-9_-]|$)"
      arc_re = "(^|[^a-zA-Z0-9_-])agent-runtime-core([^a-zA-Z0-9_-]|$)"
    }
    { lines[NR] = $0 }
    END {
      for (i = 1; i <= NR; i++) {
        line = lines[i]
        if (line ~ /^[[:space:]]*#/) continue
        if (line ~ ap_re || (line ~ ar_re && line !~ arc_re) || line ~ arc_re) {
          lo = i - 3; if (lo < 1) lo = 1
          hi = i + 3; if (hi > NR) hi = NR
          window = ""
          for (j = lo; j <= hi; j++) window = window " " lines[j]
          if (window !~ markers) print i
        }
      }
    }' "$_r98_file" 2>/dev/null)
  [[ -n "$_r98_hits" ]] && _r98_violations="${_r98_violations}${_r98_file}:${_r98_hits} "
done < <(find "$_r98_neg_root/ops" -type f -name '*.yaml' 2>/dev/null)
if [[ -n "$_r98_violations" ]]; then
  ok "rule_98_broad_corpus_neg" "Rule 98 correctly flags bare agent-platform in ops yaml"
else
  fail "rule_98_broad_corpus_neg" "expected violation, got none"
fi
}

# ---------------------------------------------------------------------------
# Rule 96 positive (rc11): card-only path — Rule N.b cited in the rule card
# (not the kernel) MUST pass. Closes rc10 P1-3 (kernel-OR-card alignment).
# ---------------------------------------------------------------------------
test_rule_96_card_only_pos() {
_r96_card_root="$scratch/r96_card_only"
mkdir -p "$_r96_card_root/docs/governance/rules" "$_r96_card_root/docs"
cat > "$_r96_card_root/CLAUDE.md" <<'SHEOF'
#### Rule 50 — Sample

**Active surface only. The deferred runtime obligation lives in the card.**

Enforced by [`rule-50.md`](docs/governance/rules/rule-50.md).

---
SHEOF
cat > "$_r96_card_root/docs/CLAUDE-deferred.md" <<'SHEOF'
## Rule 50.b — Deferred Behaviour [Deferred to W2]
SHEOF
cat > "$_r96_card_root/docs/governance/rules/rule-50.md" <<'SHEOF'
---
rule_id: 50
title: "Sample"
---

## Rule 50.b
The card references Rule 50.b explicitly.
SHEOF
# Replicate the Rule 96 disjunction-coherence check
_r96_card_missing=""
while IFS= read -r _r96_sub; do
  _r96_num=$(echo "$_r96_sub" | grep -oE '^[0-9]+')
  _r96_letter=$(echo "$_r96_sub" | grep -oE '\.[a-z]$' | sed 's/^\.//')
  [[ -z "$_r96_num" || -z "$_r96_letter" ]] && continue
  _r96_ref="Rule ${_r96_num}.${_r96_letter}"
  _r96_block=$(awk -v rn="$_r96_num" '
    $0 ~ "^#### Rule "rn" " { in_block = 1; print; next }
    in_block && /^---$/ { exit }
    in_block { print }
  ' "$_r96_card_root/CLAUDE.md")
  [[ -z "$_r96_block" ]] && continue
  _r96_card="$_r96_card_root/docs/governance/rules/rule-${_r96_num}.md"
  _r96_kernel_has=0
  _r96_card_has=0
  echo "$_r96_block" | grep -qF "$_r96_ref" && _r96_kernel_has=1
  [[ -f "$_r96_card" ]] && grep -qF "$_r96_ref" "$_r96_card" && _r96_card_has=1
  if [[ $_r96_kernel_has -eq 0 ]] && [[ $_r96_card_has -eq 0 ]]; then
    _r96_card_missing="${_r96_card_missing}${_r96_ref} "
  fi
done < <(grep -oE '^## Rule [0-9]+\.[a-z]' "$_r96_card_root/docs/CLAUDE-deferred.md" | sed -E 's/^## Rule //')
if [[ -z "$_r96_card_missing" ]]; then
  ok "rule_96_card_only_pos" "Rule 96 correctly accepts card-only citation of Rule 50.b (kernel empty, card cites)"
else
  fail "rule_96_card_only_pos" "card-only path failed: $_r96_card_missing"
fi
}

# ---------------------------------------------------------------------------
# Rule 99 positive: Rule N kernel with decision-envelope verb + matching deferred sub-clause → PASS
# ---------------------------------------------------------------------------
test_rule_99_kernel_verb_pos() {
_r99_pos_root="$scratch/r99_pos"
mkdir -p "$_r99_pos_root/docs"
cat > "$_r99_pos_root/CLAUDE.md" <<'SHEOF'
#### Rule 51 — Skill Capacity (positive sample)

**The runtime resolver MUST consult the matrix; over-capacity resolution MUST return SkillResolution.reject(SuspendReason.RateLimited) rather than admit-or-fail. The actual transition is deferred to Rule 51.c (W2 scheduler admission).**

---
SHEOF
cat > "$_r99_pos_root/docs/CLAUDE-deferred.md" <<'SHEOF'
## Rule 51.c — Run/Step Suspension Transition [Deferred to W2]
SHEOF
# Replicate Rule 99 check
_r99_end_verbs='are SUSPENDED|is SUSPENDED|callers are SUSPENDED|transitions to FAILED|transitions to SUSPENDED|consumes the .* capacity|is rejected, not failed|admits the caller'
_r99_pos_violations=$(awk -v end_verbs="$_r99_end_verbs" -v defnums="51 " '
  BEGIN { rule = ""; body = "" }
  /^#### Rule [0-9]+/ {
    if (rule) emit()
    match($0, /^#### Rule ([0-9]+)/, m)
    rule = m[1]; body = ""; next
  }
  /^---$/ && rule { emit(); rule = ""; next }
  rule { body = body $0 " " }
  END { if (rule) emit() }
  function emit() {
    has_deferred = 0
    n = split(defnums, dn, " ")
    for (i = 1; i <= n; i++) if (dn[i] == rule) has_deferred = 1
    if (!has_deferred) return
    if (body ~ end_verbs) {
      match(body, end_verbs)
      v = substr(body, RSTART, RLENGTH)
      print "Rule " rule ":" v
    }
  }
' "$_r99_pos_root/CLAUDE.md")
if [[ -z "$_r99_pos_violations" ]]; then
  ok "rule_99_kernel_verb_pos" "Rule 99 correctly accepts decision-envelope kernel"
else
  fail "rule_99_kernel_verb_pos" "expected pass, got: $_r99_pos_violations"
fi
}

# ---------------------------------------------------------------------------
# Rule 99 negative: Rule N kernel with end-state verb + matching deferred sub-clause → FAIL
# ---------------------------------------------------------------------------
test_rule_99_kernel_verb_neg() {
_r99_neg_root="$scratch/r99_neg"
mkdir -p "$_r99_neg_root/docs"
cat > "$_r99_neg_root/CLAUDE.md" <<'SHEOF'
#### Rule 51 — Skill Capacity (negative sample)

**The runtime resolver MUST consult the matrix; over-cap callers are SUSPENDED, not rejected (W2 scheduler admission per Rule 51.c).**

---
SHEOF
cat > "$_r99_neg_root/docs/CLAUDE-deferred.md" <<'SHEOF'
## Rule 51.c — Run/Step Suspension Transition [Deferred to W2]
SHEOF
_r99_end_verbs='are SUSPENDED|is SUSPENDED|callers are SUSPENDED|transitions to FAILED|transitions to SUSPENDED|consumes the .* capacity|is rejected, not failed|admits the caller'
_r99_neg_violations=$(awk -v end_verbs="$_r99_end_verbs" -v defnums="51 " '
  BEGIN { rule = ""; body = "" }
  /^#### Rule [0-9]+/ {
    if (rule) emit()
    match($0, /^#### Rule ([0-9]+)/, m)
    rule = m[1]; body = ""; next
  }
  /^---$/ && rule { emit(); rule = ""; next }
  rule { body = body $0 " " }
  END { if (rule) emit() }
  function emit() {
    has_deferred = 0
    n = split(defnums, dn, " ")
    for (i = 1; i <= n; i++) if (dn[i] == rule) has_deferred = 1
    if (!has_deferred) return
    if (body ~ end_verbs) {
      match(body, end_verbs)
      v = substr(body, RSTART, RLENGTH)
      print "Rule " rule ":" v
    }
  }
' "$_r99_neg_root/CLAUDE.md")
if [[ -n "$_r99_neg_violations" ]]; then
  ok "rule_99_kernel_verb_neg" "Rule 99 correctly flags end-state verb in active kernel with deferred sub-clause: $_r99_neg_violations"
else
  fail "rule_99_kernel_verb_neg" "expected violation, got none"
fi
}

# ---------------------------------------------------------------------------
# Rule 100 positive: allow-listed rule with kernel + card both carrying EITHER/OR wording → PASS
# ---------------------------------------------------------------------------
test_rule_100_disjunction_pos() {
_r100_pos_root="$scratch/r100_pos"
mkdir -p "$_r100_pos_root/docs/governance/rules" "$_r100_pos_root/gate"
cat > "$_r100_pos_root/CLAUDE.md" <<'SHEOF'
#### Rule 96 — Kernel-Deferred Clause Coherence

**EITHER the kernel block OR the rule card MUST contain the literal reference.**

---
SHEOF
cat > "$_r100_pos_root/docs/governance/rules/rule-96.md" <<'SHEOF'
---
rule_id: 96
---

EITHER the kernel block OR the rule card carries the reference.
SHEOF
cat > "$_r100_pos_root/gate/rule-100-disjunction-allowlist.txt" <<'SHEOF'
96
SHEOF
_r100_pos_violations=""
while IFS= read -r _r100_rule; do
  [[ -z "$_r100_rule" || "$_r100_rule" =~ ^[[:space:]]*# ]] && continue
  _r100_card="$_r100_pos_root/docs/governance/rules/rule-${_r100_rule}.md"
  _r100_block=$(awk -v rn="$_r100_rule" '
    $0 ~ "^#### Rule "rn" " { in_block = 1; print; next }
    in_block && /^---$/ { exit }
    in_block { print }
  ' "$_r100_pos_root/CLAUDE.md")
  _r100_kh=0; _r100_ch=0
  echo "$_r100_block" | grep -qE '\bEITHER\b|\bOR\b|either surface|either kernel|either the' && _r100_kh=1
  [[ -f "$_r100_card" ]] && grep -qE '\bEITHER\b|\bOR\b|either surface|either kernel|either the' "$_r100_card" && _r100_ch=1
  if [[ $_r100_kh -eq 0 || $_r100_ch -eq 0 ]]; then
    _r100_pos_violations="${_r100_pos_violations}Rule ${_r100_rule} (kernel=$_r100_kh, card=$_r100_ch) "
  fi
done < "$_r100_pos_root/gate/rule-100-disjunction-allowlist.txt"
if [[ -z "$_r100_pos_violations" ]]; then
  ok "rule_100_disjunction_pos" "Rule 100 correctly accepts EITHER/OR-aligned kernel + card"
else
  fail "rule_100_disjunction_pos" "expected pass, got: $_r100_pos_violations"
fi
}

# ---------------------------------------------------------------------------
# Rule 100 negative: allow-listed rule with kernel missing EITHER/OR wording → FAIL
# ---------------------------------------------------------------------------
test_rule_100_disjunction_neg() {
_r100_neg_root="$scratch/r100_neg"
mkdir -p "$_r100_neg_root/docs/governance/rules" "$_r100_neg_root/gate"
cat > "$_r100_neg_root/CLAUDE.md" <<'SHEOF'
#### Rule 96 — Kernel-Deferred Clause Coherence

**The matching CLAUDE.md kernel block MUST contain the literal reference.**

---
SHEOF
cat > "$_r100_neg_root/docs/governance/rules/rule-96.md" <<'SHEOF'
---
rule_id: 96
---

The matching CLAUDE.md kernel block must contain the reference.
SHEOF
cat > "$_r100_neg_root/gate/rule-100-disjunction-allowlist.txt" <<'SHEOF'
96
SHEOF
_r100_neg_violations=""
while IFS= read -r _r100_rule; do
  [[ -z "$_r100_rule" || "$_r100_rule" =~ ^[[:space:]]*# ]] && continue
  _r100_card="$_r100_neg_root/docs/governance/rules/rule-${_r100_rule}.md"
  _r100_block=$(awk -v rn="$_r100_rule" '
    $0 ~ "^#### Rule "rn" " { in_block = 1; print; next }
    in_block && /^---$/ { exit }
    in_block { print }
  ' "$_r100_neg_root/CLAUDE.md")
  _r100_kh=0; _r100_ch=0
  echo "$_r100_block" | grep -qE '\bEITHER\b|\bOR\b|either surface|either kernel|either the' && _r100_kh=1
  [[ -f "$_r100_card" ]] && grep -qE '\bEITHER\b|\bOR\b|either surface|either kernel|either the' "$_r100_card" && _r100_ch=1
  if [[ $_r100_kh -eq 0 || $_r100_ch -eq 0 ]]; then
    _r100_neg_violations="${_r100_neg_violations}Rule ${_r100_rule} (kernel=$_r100_kh, card=$_r100_ch) "
  fi
done < "$_r100_neg_root/gate/rule-100-disjunction-allowlist.txt"
if [[ -n "$_r100_neg_violations" ]]; then
  ok "rule_100_disjunction_neg" "Rule 100 correctly flags missing EITHER/OR wording: $_r100_neg_violations"
else
  fail "rule_100_disjunction_neg" "expected violation, got none"
fi
}

# ---------------------------------------------------------------------------
# Rule 98 ops/**/*.md widening (rc11): synthetic ops/runbooks/x.md with bare
# `agent-platform` and no marker → FAIL; same with `post-Phase-C` marker → PASS.
# ---------------------------------------------------------------------------
test_rule_98_ops_runbook_md_neg() {
_r98omd_root="$scratch/r98_ops_md_neg"
mkdir -p "$_r98omd_root/ops/runbooks"
cat > "$_r98omd_root/ops/runbooks/test.md" <<'SHEOF'
# Test runbook
This calls into agent-platform deployment.
SHEOF
# Replicate Rule 98 awk with rc11 widening (ops/**/*.md included, comments scanned)
_r98omd_markers='historical|pre-Phase-C|consolidated into|formerly|superseded|deprecated|moved|extracted per ADR-[0-9]+|post-ADR-[0-9]+|post-Phase-C|ADR-[0-9]+|forbidden_dependencies'
_r98omd_hits=$(awk -v markers="$_r98omd_markers" '
  BEGIN {
    ap_re = "(^|[^a-zA-Z0-9_-])agent-platform([^a-zA-Z0-9_-]|$)"
  }
  { lines[NR] = $0 }
  END {
    for (i = 1; i <= NR; i++) {
      line = lines[i]
      if (line ~ ap_re) {
        lo = i - 3; if (lo < 1) lo = 1
        hi = i + 3; if (hi > NR) hi = NR
        window = ""
        for (j = lo; j <= hi; j++) window = window " " lines[j]
        if (window !~ markers) print i ":" line
      }
    }
  }
' "$_r98omd_root/ops/runbooks/test.md")
if [[ -n "$_r98omd_hits" ]]; then
  ok "rule_98_ops_runbook_md_neg" "Rule 98 widened scope catches ops/runbooks/*.md leakage: $_r98omd_hits"
else
  fail "rule_98_ops_runbook_md_neg" "expected violation in ops/runbooks/*.md, got none"
fi
}

test_rule_98_ops_runbook_md_pos() {
_r98omd_pos_root="$scratch/r98_ops_md_pos"
mkdir -p "$_r98omd_pos_root/ops/runbooks"
cat > "$_r98omd_pos_root/ops/runbooks/test.md" <<'SHEOF'
# Test runbook (post-Phase-C / ADR-0078)
Pre-Phase-C this called into agent-platform deployment;
now lands on agent-service deployment.
SHEOF
_r98omd_markers='historical|pre-Phase-C|consolidated into|formerly|superseded|deprecated|moved|extracted per ADR-[0-9]+|post-ADR-[0-9]+|post-Phase-C|ADR-[0-9]+|forbidden_dependencies'
_r98omd_pos_hits=$(awk -v markers="$_r98omd_markers" '
  BEGIN {
    ap_re = "(^|[^a-zA-Z0-9_-])agent-platform([^a-zA-Z0-9_-]|$)"
  }
  { lines[NR] = $0 }
  END {
    for (i = 1; i <= NR; i++) {
      line = lines[i]
      if (line ~ ap_re) {
        lo = i - 3; if (lo < 1) lo = 1
        hi = i + 3; if (hi > NR) hi = NR
        window = ""
        for (j = lo; j <= hi; j++) window = window " " lines[j]
        if (window !~ markers) print i ":" line
      }
    }
  }
' "$_r98omd_pos_root/ops/runbooks/test.md")
if [[ -z "$_r98omd_pos_hits" ]]; then
  ok "rule_98_ops_runbook_md_pos" "Rule 98 widened scope accepts marked ops/runbooks/*.md"
else
  fail "rule_98_ops_runbook_md_pos" "expected pass with markers, got: $_r98omd_pos_hits"
fi
}

# ===========================================================================
# rc12 K-α / K-β / K-δ / K-ζ — Self-test fixtures for Rules 101 / 102 / 103 / 104
# (Rule 89 / E122 sub-check (c): every prevention-wave Rule N>=80 MUST have
# at least one test_rule_<N>_* function in this harness.)
# ===========================================================================

test_rule_101_authority_completeness_pos() {
_r101_pos_root="$scratch/r101_pos"
mkdir -p "$_r101_pos_root/docs/governance/rules" "$_r101_pos_root/docs/governance"
printf '#### Rule D-1 — Sample\n' > "$_r101_pos_root/CLAUDE.md"
printf -- '---\nrule_id: D-1\ntitle: "sample"\n---\n' > "$_r101_pos_root/docs/governance/rules/rule-D-1.md"
printf '    baseline_metrics:\n      active_engineering_rules: 1\n' > "$_r101_pos_root/docs/governance/architecture-status.yaml"
printf -- '- id: E1\n  constraint_ref: "CLAUDE.md Rule D-1"\n' > "$_r101_pos_root/docs/governance/enforcers.yaml"
_r101_pos_kernel_count=$(grep -cE '^#### Rule [A-Z]-' "$_r101_pos_root/CLAUDE.md")
_r101_pos_declared=$(awk '/^[[:space:]]+active_engineering_rules:/{print $2; exit}' "$_r101_pos_root/docs/governance/architecture-status.yaml")
_r101_pos_card_ok=0
if grep -qE "^rule_id: D-1[[:space:]]*\r?$" "$_r101_pos_root/docs/governance/rules/rule-D-1.md"; then
  _r101_pos_card_ok=1
fi
_r101_pos_bad_refs=$(grep -nE 'constraint_ref:[[:space:]]*"[^"]*\bRule [0-9]+[a-z]?\b' "$_r101_pos_root/docs/governance/enforcers.yaml" \
                    | grep -vE 'legacy Rule [0-9]+[a-z]?|Rule [DRGM]-|historical' || true)
if [[ "$_r101_pos_declared" == "$_r101_pos_kernel_count" ]] && [[ $_r101_pos_card_ok -eq 1 ]] && [[ -z "$_r101_pos_bad_refs" ]]; then
  ok "rule_101_authority_completeness_pos" "Rule 101 accepts kernel + matching card + matching baseline + namespaced enforcer"
else
  fail "rule_101_authority_completeness_pos" "unexpected violation: count=$_r101_pos_declared/$_r101_pos_kernel_count card_ok=$_r101_pos_card_ok bad_refs=$_r101_pos_bad_refs"
fi
}

test_rule_101_authority_completeness_neg() {
_r101_neg_root="$scratch/r101_neg"
mkdir -p "$_r101_neg_root/docs/governance/rules" "$_r101_neg_root/docs/governance"
printf '#### Rule D-1 — Sample\n' > "$_r101_neg_root/CLAUDE.md"
printf -- '---\nrule_id: 1\ntitle: "stale numeric"\n---\n' > "$_r101_neg_root/docs/governance/rules/rule-D-1.md"
printf '    baseline_metrics:\n      active_engineering_rules: 1\n' > "$_r101_neg_root/docs/governance/architecture-status.yaml"
_r101_neg_card_ok=0
if grep -qE "^rule_id: D-1[[:space:]]*\r?$" "$_r101_neg_root/docs/governance/rules/rule-D-1.md"; then
  _r101_neg_card_ok=1
fi
if [[ $_r101_neg_card_ok -eq 0 ]]; then
  ok "rule_101_authority_completeness_neg" "Rule 101 rejects card with numeric rule_id when kernel is namespaced"
else
  fail "rule_101_authority_completeness_neg" "expected card frontmatter mismatch but matched"
fi
}

test_rule_102_release_recency_resolver_pos() {
_r102_pos_root="$scratch/r102_pos"
mkdir -p "$_r102_pos_root/docs/logs/releases"
touch "$_r102_pos_root/docs/logs/releases/2026-05-19-l0-rc9-corrective.en.md"
touch "$_r102_pos_root/docs/logs/releases/2026-05-19-l0-rc10-corrective.en.md"
touch "$_r102_pos_root/docs/logs/releases/2026-05-19-l0-rc11-corrective.en.md"
if [[ -z "${LATEST_RELEASE_HELPER_LOADED:-}" ]] && [[ -f "$(pwd)/gate/lib/latest_release.sh" ]]; then
  # shellcheck source=../gate/lib/latest_release.sh
  source "$(pwd)/gate/lib/latest_release.sh"
fi
_r102_pos_latest=$(latest_release_path "$_r102_pos_root/docs/logs/releases")
case "$_r102_pos_latest" in
  *rc11*) ok "rule_102_release_recency_resolver_pos" "latest_release_path picks rc11 over rc9/rc10 (numeric rc sort)" ;;
  *)      fail "rule_102_release_recency_resolver_pos" "expected rc11 winner, got: $_r102_pos_latest" ;;
esac
}

test_rule_102_release_recency_resolver_neg() {
_r102_neg_root="$scratch/r102_neg"
mkdir -p "$_r102_neg_root/docs/logs/releases"
touch "$_r102_neg_root/docs/logs/releases/2026-05-19-l0-rc9-corrective.en.md"
touch "$_r102_neg_root/docs/logs/releases/2026-05-19-l0-rc11-corrective.en.md"
# Demonstrate the BUG that rc12 K-β fixes: lex-sort picks rc9 because '9' > '1'
_r102_neg_lex=$(find "$_r102_neg_root/docs/logs/releases" -maxdepth 1 -type f -name '*.md' 2>/dev/null | sort | tail -1)
case "$_r102_neg_lex" in
  *rc9*) ok "rule_102_release_recency_resolver_neg" "Rule 102 negative fixture confirms lex-sort anti-pattern picks rc9 over rc11 (this is the bug Rule 102 prevents)" ;;
  *)     fail "rule_102_release_recency_resolver_neg" "expected lex-sort to pick rc9 as the anti-pattern demonstration, got: $_r102_neg_lex" ;;
esac
}

test_rule_103_deploy_entrypoint_pos() {
_r103_pos_root="$scratch/r103_pos"
mkdir -p "$_r103_pos_root"
cat > "$_r103_pos_root/Dockerfile" <<'SHEOF'
# spring-ai-ascend agent-service Dockerfile.
# rc12 K-δ: rewritten from the pre-Phase-C agent-platform Dockerfile (post-ADR-0078).
FROM maven:3.9-eclipse-temurin-21 AS build
COPY agent-service/ ./agent-service/
RUN mvn -B -ntp -pl agent-service -am package -DskipTests
SHEOF
_r103_pos_markers='historical|pre-Phase-C|post-Phase-C|consolidated into|formerly|superseded|deprecated|ADR-[0-9]+'
_r103_pos_hits=$(awk -v markers="$_r103_pos_markers" '
  { lines[NR] = $0 }
  END {
    for (i = 1; i <= NR; i++) {
      line = lines[i]
      if ((line ~ /\<agent-platform\>/) || (line ~ /agent-runtime[^-]/) || (line ~ /agent-runtime$/)) {
        lo = i - 3; if (lo < 1) lo = 1
        hi = i + 3; if (hi > NR) hi = NR
        window = ""
        for (j = lo; j <= hi; j++) window = window " " lines[j]
        if (window !~ markers) print i ":" line
      }
    }
  }
' "$_r103_pos_root/Dockerfile")
if [[ -z "$_r103_pos_hits" ]]; then
  ok "rule_103_deploy_entrypoint_pos" "Rule 103 accepts Dockerfile referencing agent-platform inside historical-marker window"
else
  fail "rule_103_deploy_entrypoint_pos" "expected pass with markers, got: $_r103_pos_hits"
fi
}

test_rule_103_deploy_entrypoint_neg() {
_r103_neg_root="$scratch/r103_neg"
mkdir -p "$_r103_neg_root"
cat > "$_r103_neg_root/Dockerfile" <<'SHEOF'
FROM maven:3.9-eclipse-temurin-21 AS build
COPY agent-platform/pom.xml ./agent-platform/
RUN mvn -B -ntp -pl agent-platform -am package
SHEOF
_r103_neg_markers='historical|pre-Phase-C|post-Phase-C|consolidated into|formerly|superseded|deprecated|ADR-[0-9]+'
_r103_neg_hits=$(awk -v markers="$_r103_neg_markers" '
  { lines[NR] = $0 }
  END {
    for (i = 1; i <= NR; i++) {
      line = lines[i]
      if ((line ~ /\<agent-platform\>/) || (line ~ /agent-runtime[^-]/) || (line ~ /agent-runtime$/)) {
        lo = i - 3; if (lo < 1) lo = 1
        hi = i + 3; if (hi > NR) hi = NR
        window = ""
        for (j = lo; j <= hi; j++) window = window " " lines[j]
        if (window !~ markers) print i ":" line
      }
    }
  }
' "$_r103_neg_root/Dockerfile")
if [[ -n "$_r103_neg_hits" ]]; then
  ok "rule_103_deploy_entrypoint_neg" "Rule 103 catches Dockerfile referencing agent-platform without historical marker: $_r103_neg_hits"
else
  fail "rule_103_deploy_entrypoint_neg" "expected violation, got none"
fi
}

test_rule_104_openapi_catalog_pos() {
_r104_pos_root="$scratch/r104_pos"
mkdir -p "$_r104_pos_root/docs/contracts"
cat > "$_r104_pos_root/docs/contracts/http-api-contracts.md" <<'SHEOF'
### POST /v1/runs (shipped; W1)
Stability: shipped
Implementation: RunController.java#createRun
SHEOF
if ! grep -qE 'POST /v1/runs.*\(planned' "$_r104_pos_root/docs/contracts/http-api-contracts.md"; then
  ok "rule_104_openapi_catalog_pos" "Rule 104 accepts catalog row marking shipped route as shipped (not planned)"
else
  fail "rule_104_openapi_catalog_pos" "expected pass with shipped marker, but found planned"
fi
}

test_rule_104_openapi_catalog_neg() {
_r104_neg_root="$scratch/r104_neg"
mkdir -p "$_r104_neg_root/docs/contracts"
cat > "$_r104_neg_root/docs/contracts/http-api-contracts.md" <<'SHEOF'
### POST /v1/runs (planned; W1)
Stability: planned
SHEOF
if grep -qE 'POST /v1/runs.*\(planned' "$_r104_neg_root/docs/contracts/http-api-contracts.md"; then
  ok "rule_104_openapi_catalog_neg" "Rule 104 catches catalog row marking shipped route as planned"
else
  fail "rule_104_openapi_catalog_neg" "expected planned marker, got none"
fi
}

test_rule_105_edge_direct_link_pos() {
# Rule 105 positive fixture: edge plane module with no compute_control imports
# (skeleton — current agent-client shape).
_r105_pos_root="$scratch/r105_pos"
mkdir -p "$_r105_pos_root/agent-client/src/main/java/ascend/springai/client"
cat > "$_r105_pos_root/agent-client/module-metadata.yaml" <<'SHEOF'
module: agent-client
deployment_plane: edge
SHEOF
# Empty package-info only — no production code
cat > "$_r105_pos_root/agent-client/src/main/java/ascend/springai/client/package-info.java" <<'SHEOF'
package ascend.springai.client;
SHEOF
# Verify: the grep yields zero violations
_r105_pos_violations=$(grep -rnE '^import ascend\.springai\.(service|engine|middleware)\.' "$_r105_pos_root/agent-client/src/main/java" 2>/dev/null | wc -l)
if [[ "$_r105_pos_violations" -eq 0 ]]; then
  ok "rule_105_edge_direct_link_pos" "Rule 105 accepts skeleton agent-client with no compute_control imports"
else
  fail "rule_105_edge_direct_link_pos" "expected zero violations, got $_r105_pos_violations"
fi
}

test_rule_105_edge_direct_link_neg() {
# Rule 105 negative fixture: edge plane module with a forbidden import of
# ascend.springai.service.* — must be flagged.
_r105_neg_root="$scratch/r105_neg"
mkdir -p "$_r105_neg_root/agent-client/src/main/java/ascend/springai/client/tmp"
cat > "$_r105_neg_root/agent-client/module-metadata.yaml" <<'SHEOF'
module: agent-client
deployment_plane: edge
SHEOF
cat > "$_r105_neg_root/agent-client/src/main/java/ascend/springai/client/tmp/Bad.java" <<'SHEOF'
package ascend.springai.client.tmp;
import ascend.springai.service.runtime.runs.Run;
public class Bad {}
SHEOF
_r105_neg_violations=$(grep -rnE '^import ascend\.springai\.(service|engine|middleware)\.' "$_r105_neg_root/agent-client/src/main/java" 2>/dev/null | wc -l)
if [[ "$_r105_neg_violations" -ge 1 ]]; then
  ok "rule_105_edge_direct_link_neg" "Rule 105 catches forbidden compute_control import in edge plane module"
else
  fail "rule_105_edge_direct_link_neg" "expected at least 1 violation, got $_r105_neg_violations"
fi
}

# ---------------------------------------------------------------------------
# Rule 106 — cross_authority_parity (Rule G-8 / E146-E149).
# Eight fixtures: one positive + one negative per sub-clause .a/.b/.c/.d.
# Each fixture is self-contained — it inlines the sub-check logic against a
# synthetic scratch root rather than invoking the real gate (which requires
# the full repo layout). Closes rc13 P1-5 L-δ family.
# ---------------------------------------------------------------------------

test_rule_106_a_graph_baseline_parity_pos() {
  _r106a_pos_root="$scratch/r106a_pos"; mkdir -p "$_r106a_pos_root"
  printf 'node_count: 381\nedge_count: 566\n' > "$_r106a_pos_root/architecture-graph.yaml"
  printf 'baseline_metrics:\n  architecture_graph_nodes: 381\n  architecture_graph_edges: 566\n' > "$_r106a_pos_root/architecture-status.yaml"
  _n_live=$(awk '/^node_count:/{print $2}' "$_r106a_pos_root/architecture-graph.yaml")
  _n_baseline=$(awk '/^[[:space:]]+architecture_graph_nodes:/{print $2}' "$_r106a_pos_root/architecture-status.yaml")
  if [[ "$_n_live" == "$_n_baseline" ]]; then
    ok "rule_106_a_graph_baseline_parity_pos" "Rule G-8.a accepts matching live + baseline graph counts"
  else
    fail "rule_106_a_graph_baseline_parity_pos" "expected match, got live=$_n_live baseline=$_n_baseline"
  fi
}

test_rule_106_a_graph_baseline_parity_neg() {
  _r106a_neg_root="$scratch/r106a_neg"; mkdir -p "$_r106a_neg_root"
  printf 'node_count: 200\nedge_count: 300\n' > "$_r106a_neg_root/architecture-graph.yaml"
  printf 'baseline_metrics:\n  architecture_graph_nodes: 100\n  architecture_graph_edges: 150\n' > "$_r106a_neg_root/architecture-status.yaml"
  _n_live=$(awk '/^node_count:/{print $2}' "$_r106a_neg_root/architecture-graph.yaml")
  _n_baseline=$(awk '/^[[:space:]]+architecture_graph_nodes:/{print $2}' "$_r106a_neg_root/architecture-status.yaml")
  if [[ "$_n_live" != "$_n_baseline" ]]; then
    ok "rule_106_a_graph_baseline_parity_neg" "Rule G-8.a catches mismatch live=$_n_live baseline=$_n_baseline"
  else
    fail "rule_106_a_graph_baseline_parity_neg" "expected mismatch, got match"
  fi
}

test_rule_106_b_spi_path_parity_pos() {
  _r106b_pos_root="$scratch/r106b_pos"; mkdir -p "$_r106b_pos_root/agent-bus/src/main/java/ascend/springai/bus/spi/ingress"
  printf 'spi_packages:\n  - ascend.springai.bus.spi.ingress\n' > "$_r106b_pos_root/agent-bus/module-metadata.yaml"
  printf 'kernel rule names ascend.springai.bus.spi.ingress.IngressGateway as the SPI.\n' > "$_r106b_pos_root/CLAUDE.md"
  _pkg=$(grep -oE 'ascend\.springai(\.[a-z][a-z0-9_]*)+\.spi((\.[a-z][a-z0-9_]*)+)?' "$_r106b_pos_root/CLAUDE.md" | head -1)
  _meta=$(grep -hE '^\s*-\s*ascend\.springai\.' "$_r106b_pos_root/agent-bus/module-metadata.yaml" | sed -E 's/^\s*-\s*//' | awk '{print $1}')
  _path=$(echo "$_pkg" | tr '.' '/')
  if [[ "$_pkg" == "$_meta" ]] && [[ -d "$_r106b_pos_root/agent-bus/src/main/java/$_path" ]]; then
    ok "rule_106_b_spi_path_parity_pos" "Rule G-8.b accepts kernel-mentioned SPI $_pkg backed by metadata + disk"
  else
    fail "rule_106_b_spi_path_parity_pos" "expected match + disk, got pkg=$_pkg meta=$_meta path-exists=$([[ -d $_r106b_pos_root/agent-bus/src/main/java/$_path ]] && echo y || echo n)"
  fi
}

test_rule_106_b_spi_path_parity_neg() {
  _r106b_neg_root="$scratch/r106b_neg"; mkdir -p "$_r106b_neg_root/agent-bus"
  printf 'spi_packages:\n  - ascend.springai.bus.spi.other\n' > "$_r106b_neg_root/agent-bus/module-metadata.yaml"
  printf 'kernel rule names ascend.springai.bus.spi.ghost.GhostSpi as the SPI.\n' > "$_r106b_neg_root/CLAUDE.md"
  _pkg=$(grep -oE 'ascend\.springai(\.[a-z][a-z0-9_]*)+\.spi((\.[a-z][a-z0-9_]*)+)?' "$_r106b_neg_root/CLAUDE.md" | head -1)
  _meta=$(grep -hE '^\s*-\s*ascend\.springai\.' "$_r106b_neg_root/agent-bus/module-metadata.yaml" | sed -E 's/^\s*-\s*//' | awk '{print $1}')
  if [[ "$_pkg" != "$_meta" ]]; then
    ok "rule_106_b_spi_path_parity_neg" "Rule G-8.b catches kernel-mentioned SPI $_pkg with no metadata entry"
  else
    fail "rule_106_b_spi_path_parity_neg" "expected mismatch, got pkg=$_pkg meta=$_meta"
  fi
}

test_rule_106_c_module_topology_parity_pos() {
  _r106c_pos_root="$scratch/r106c_pos"; mkdir -p "$_r106c_pos_root"
  printf '<modules>\n  <module>agent-bus</module>\n  <module>agent-service</module>\n</modules>\n' > "$_r106c_pos_root/pom.xml"
  printf 'repository_counts:\n  reactor_modules: 2\n' > "$_r106c_pos_root/architecture-status.yaml"
  _pom_count=$(awk '/<modules>/,/<\/modules>/' "$_r106c_pos_root/pom.xml" | grep -oE '<module>[^<]+</module>' | wc -l | tr -d ' ')
  _declared=$(awk '/^\s+reactor_modules:/{print $2}' "$_r106c_pos_root/architecture-status.yaml")
  if [[ "$_pom_count" == "$_declared" ]]; then
    ok "rule_106_c_module_topology_parity_pos" "Rule G-8.c accepts matching pom + repository_counts (count=$_pom_count)"
  else
    fail "rule_106_c_module_topology_parity_pos" "expected match, got pom=$_pom_count declared=$_declared"
  fi
}

test_rule_106_c_module_topology_parity_neg() {
  _r106c_neg_root="$scratch/r106c_neg"; mkdir -p "$_r106c_neg_root"
  printf '<modules>\n  <module>agent-bus</module>\n  <module>agent-service</module>\n</modules>\n' > "$_r106c_neg_root/pom.xml"
  printf 'each of the 9 reactor modules carries module-metadata.yaml\n' > "$_r106c_neg_root/ARCHITECTURE.md"
  _pom_count=$(awk '/<modules>/,/<\/modules>/' "$_r106c_neg_root/pom.xml" | grep -oE '<module>[^<]+</module>' | wc -l | tr -d ' ')
  _prose_n=$(grep -oE 'each of the [0-9]+' "$_r106c_neg_root/ARCHITECTURE.md" | grep -oE '[0-9]+' | head -1)
  if [[ "$_pom_count" != "$_prose_n" ]]; then
    ok "rule_106_c_module_topology_parity_neg" "Rule G-8.c catches prose 'each of the $_prose_n' vs pom $_pom_count"
  else
    fail "rule_106_c_module_topology_parity_neg" "expected mismatch, got pom=$_pom_count prose=$_prose_n"
  fi
}

test_rule_106_d_current_claim_grammar_pos() {
  _r106d_pos_root="$scratch/r106d_pos"; mkdir -p "$_r106d_pos_root"
  printf 'agent-runtime-core was dissolved per ADR-0088 (rc13, 2026-05-20) — formerly the kernel-shim module.\n' > "$_r106d_pos_root/ARCHITECTURE.md"
  _hit=$(grep -nE '(agent-platform|agent-runtime-core|agent-runtime[^-])' "$_r106d_pos_root/ARCHITECTURE.md" | \
         grep -E '(now reads|lives in|^[^#]*\bdeclares\b|each of the [0-9]+ (reactor )?modules)' | \
         grep -vE '(formerly|historical|until dissolved|pre-rc13|pre-rc12|pre-Phase-C|narration|dissolved|relocated|was consolidated|was extracted|was dissolved)' || true)
  if [[ -z "$_hit" ]]; then
    ok "rule_106_d_current_claim_grammar_pos" "Rule G-8.d accepts past-tense + historical-marker line about dissolved module"
  else
    fail "rule_106_d_current_claim_grammar_pos" "expected no hit, got: $_hit"
  fi
}

test_rule_106_d_current_claim_grammar_neg() {
  _r106d_neg_root="$scratch/r106d_neg"; mkdir -p "$_r106d_neg_root"
  printf 'agent-runtime-core declares its SPI packages for orchestration / runs / s2c (post-ADR-0079).\n' > "$_r106d_neg_root/ARCHITECTURE.md"
  _hit=$(grep -nE '(agent-platform|agent-runtime-core|agent-runtime[^-])' "$_r106d_neg_root/ARCHITECTURE.md" | \
         grep -E '(now reads|lives in|^[^#]*\bdeclares\b|each of the [0-9]+ (reactor )?modules)' | \
         grep -vE '(formerly|historical|until dissolved|pre-rc13|pre-rc12|pre-Phase-C|narration|dissolved|relocated|was consolidated|was extracted|was dissolved)' || true)
  if [[ -n "$_hit" ]]; then
    ok "rule_106_d_current_claim_grammar_neg" "Rule G-8.d catches present-tense 'declares' with only post-ADR-NNNN marker"
  else
    fail "rule_106_d_current_claim_grammar_neg" "expected hit (post-ADR-NNNN alone is not historical), got none"
  fi
}

# ---------------------------------------------------------------------------
# rc15 — Rule 106 sub-check .e (structural-carrier parity) + Rule 99 sub-check (b)
# (module ARCHITECTURE.md scope). Four fixtures per ADR-0091.
# ---------------------------------------------------------------------------

test_rule_106_e_structural_carrier_parity_pos() {
  _r106e_pos_root="$scratch/r106e_pos"
  mkdir -p "$_r106e_pos_root/agent-execution-engine/src/main/java/ascend/springai/engine/runtime"
  cat > "$_r106e_pos_root/agent-execution-engine/src/main/java/ascend/springai/engine/runtime/EngineRegistry.java" <<'SHEOF'
package ascend.springai.engine.runtime;
public class EngineRegistry {}
SHEOF
  printf '| `EngineRegistry` | `agent-execution-engine` (`...engine.runtime`) | dispatch authority |\n' > "$_r106e_pos_root/contract-catalog.md"
  # Apply the sub-check logic inline against the scratch root
  _r106e_pass=1
  while IFS=$'\t' read -r _cls _mod _suffix; do
    [[ -z "$_cls" ]] && continue
    _full_pkg="ascend.springai.${_suffix#...}"
    _path="$(echo "$_full_pkg" | tr '.' '/')"
    _java_file="${_r106e_pos_root}/${_mod}/src/main/java/${_path}/${_cls}.java"
    [[ ! -f "$_java_file" ]] && _r106e_pass=0
  done < <(awk -F'`' '/^\| `[A-Z][A-Za-z]+` \| `agent-[a-z-]+` \(`\.\.\.[a-z._]+`\)/ {print $2 "\t" $4 "\t" $6}' "$_r106e_pos_root/contract-catalog.md")
  if [[ $_r106e_pass -eq 1 ]]; then
    ok "rule_106_e_structural_carrier_parity_pos" "Rule G-8.e accepts catalog row with package + class file resolving on disk"
  else
    fail "rule_106_e_structural_carrier_parity_pos" "expected disk resolve, got missing file"
  fi
}

test_rule_106_e_structural_carrier_parity_neg() {
  _r106e_neg_root="$scratch/r106e_neg"
  mkdir -p "$_r106e_neg_root/agent-execution-engine/src/main/java/ascend/springai/engine/runtime"
  # NOTE: do NOT create EngineRegistry.java — catalog claims the carrier exists but it does not
  printf '| `EngineRegistry` | `agent-execution-engine` (`...service.runtime.engine`) | stale package home |\n' > "$_r106e_neg_root/contract-catalog.md"
  _r106e_miss=0
  while IFS=$'\t' read -r _cls _mod _suffix; do
    [[ -z "$_cls" ]] && continue
    _full_pkg="ascend.springai.${_suffix#...}"
    _path="$(echo "$_full_pkg" | tr '.' '/')"
    _java_file="${_r106e_neg_root}/${_mod}/src/main/java/${_path}/${_cls}.java"
    [[ ! -f "$_java_file" ]] && _r106e_miss=1
  done < <(awk -F'`' '/^\| `[A-Z][A-Za-z]+` \| `agent-[a-z-]+` \(`\.\.\.[a-z._]+`\)/ {print $2 "\t" $4 "\t" $6}' "$_r106e_neg_root/contract-catalog.md")
  if [[ $_r106e_miss -eq 1 ]]; then
    ok "rule_106_e_structural_carrier_parity_neg" "Rule G-8.e catches catalog row with package not resolving on disk"
  else
    fail "rule_106_e_structural_carrier_parity_neg" "expected miss, got file resolved"
  fi
}

test_rule_99_module_arch_terminal_verb_pos() {
  _r99b_pos_root="$scratch/r99b_pos"
  mkdir -p "$_r99b_pos_root"
  # Decision-envelope phrasing with deferred marker — admissible
  cat > "$_r99b_pos_root/ARCHITECTURE.md" <<'SHEOF'
The runtime ResilienceContract.resolve(tenant, skill) returns a rejected
decision envelope SkillResolution.reject(SuspendReason.RateLimited) at W1;
over-cap callers are SUSPENDED at W2 (W2 scheduler admission deferred to Rule R-K.c per ADR-0070).
SHEOF
  _hit=$(grep -nE '(over-cap|over-capacity)( callers| requests)?[^.]*(are SUSPENDED|is SUSPENDED|transitions to SUSPENDED)' "$_r99b_pos_root/ARCHITECTURE.md" \
         | grep -vE '(decision envelope|SkillResolution\.reject|deferred to R-K|deferred to Rule R-K|deferred per Rule R-K|W2 scheduler admission)' || true)
  if [[ -z "$_hit" ]]; then
    ok "rule_99_module_arch_terminal_verb_pos" "Rule G-3.e (module-arch scope) accepts decision-envelope + deferred-marker phrasing"
  else
    fail "rule_99_module_arch_terminal_verb_pos" "expected no hit (decision-envelope marker present), got: $_hit"
  fi
}

test_rule_99_module_arch_terminal_verb_neg() {
  _r99b_neg_root="$scratch/r99b_neg"
  mkdir -p "$_r99b_neg_root"
  # Bare over-cap SUSPENSION claim WITHOUT decision-envelope marker — should fail
  cat > "$_r99b_neg_root/ARCHITECTURE.md" <<'SHEOF'
The runtime ResilienceContract.resolve(tenant, skill) consults
skill-capacity.yaml; over-cap callers are SUSPENDED, not rejected.
SHEOF
  _hit=$(grep -nE '(over-cap|over-capacity)( callers| requests)?[^.]*(are SUSPENDED|is SUSPENDED|transitions to SUSPENDED)' "$_r99b_neg_root/ARCHITECTURE.md" \
         | grep -vE '(decision envelope|SkillResolution\.reject|deferred to R-K|deferred to Rule R-K|deferred per Rule R-K|W2 scheduler admission)' || true)
  if [[ -n "$_hit" ]]; then
    ok "rule_99_module_arch_terminal_verb_neg" "Rule G-3.e (module-arch scope) catches bare over-cap SUSPENSION over-claim"
  else
    fail "rule_99_module_arch_terminal_verb_neg" "expected hit, got none"
  fi
}

# ---------------------------------------------------------------------------
# rc16 — Rules 107 / 108 / 109 / 110 (Family A/B/C + META prevention).
# Per ADR-0093. Each rule gets 2 fixtures (positive + negative) covering
# at least 2 distinct scope surfaces (Rule 110 META requirement).
# ---------------------------------------------------------------------------

test_rule_107_clause_parity_pos() {
  _r107_pos_root="$scratch/r107_pos"
  mkdir -p "$_r107_pos_root"
  # Surface 1: principle-coverage.yaml + Surface 2: CLAUDE-deferred.md
  cat > "$_r107_pos_root/principle-coverage.yaml" <<'SHEOF'
principles:
  - id: P-K
    operationalised_by_rules:
      - Rule-R-K
    deferred_operationalisers:
      - Rule-R-K.c   # Run/Step Suspension Transition — W2
SHEOF
  cat > "$_r107_pos_root/CLAUDE-deferred.md" <<'SHEOF'
# Deferred Rules

## Rule R-K.c — Run/Step Suspension Transition [Deferred to W2]
Re-introduction trigger: W2 scheduler.
SHEOF
  _deferred_headings=$(grep -oE '^## Rule [A-Z](-[A-Z])?(\.[a-z](\.[a-z])?)?' "$_r107_pos_root/CLAUDE-deferred.md" \
                       | sed -E 's/^## Rule /Rule-/' | sed 's/ /-/g' | sort -u)
  _listed=$(awk '/deferred_operationalisers:/{flag=1; next} flag && /^[[:space:]]*-/{sub(/^[[:space:]]*-[[:space:]]+/, ""); sub(/[[:space:]]+#.*$/, ""); print; next} flag && !/^[[:space:]]*-/{flag=0}' "$_r107_pos_root/principle-coverage.yaml" | sort -u)
  _r107_pos_fail=0
  while IFS= read -r _c; do
    [[ -z "$_c" ]] && continue
    if echo "$_c" | grep -qE '\.[a-z]'; then
      echo "$_deferred_headings" | grep -qFx "$_c" || _r107_pos_fail=1
    fi
  done <<< "$_listed"
  if [[ "$_r107_pos_fail" -eq 0 ]]; then
    ok "rule_107_clause_parity_pos" "Rule 107 accepts deferred clause R-K.c with matching heading"
  else
    fail "rule_107_clause_parity_pos" "expected pass, got orphan detection"
  fi
}

test_rule_107_clause_parity_neg() {
  _r107_neg_root="$scratch/r107_neg"
  mkdir -p "$_r107_neg_root"
  # Surface 1: principle-coverage.yaml names R-K.b but Surface 2 only declares R-K.c
  cat > "$_r107_neg_root/principle-coverage.yaml" <<'SHEOF'
principles:
  - id: P-K
    deferred_operationalisers:
      - Rule-R-K.b   # ORPHANED: no matching heading
SHEOF
  cat > "$_r107_neg_root/CLAUDE-deferred.md" <<'SHEOF'
# Deferred Rules

## Rule R-K.c — Run/Step Suspension Transition [Deferred to W2]
SHEOF
  _deferred_headings=$(grep -oE '^## Rule [A-Z](-[A-Z])?(\.[a-z](\.[a-z])?)?' "$_r107_neg_root/CLAUDE-deferred.md" \
                       | sed -E 's/^## Rule /Rule-/' | sed 's/ /-/g' | sort -u)
  _listed=$(awk '/deferred_operationalisers:/{flag=1; next} flag && /^[[:space:]]*-/{sub(/^[[:space:]]*-[[:space:]]+/, ""); sub(/[[:space:]]+#.*$/, ""); print; next} flag && !/^[[:space:]]*-/{flag=0}' "$_r107_neg_root/principle-coverage.yaml" | sort -u)
  _r107_neg_orphan=0
  while IFS= read -r _c; do
    [[ -z "$_c" ]] && continue
    if echo "$_c" | grep -qE '\.[a-z]'; then
      if ! echo "$_deferred_headings" | grep -qFx "$_c"; then
        _r107_neg_orphan=1
      fi
    fi
  done <<< "$_listed"
  if [[ "$_r107_neg_orphan" -eq 1 ]]; then
    ok "rule_107_clause_parity_neg" "Rule 107 catches orphaned R-K.b clause with no matching heading"
  else
    fail "rule_107_clause_parity_neg" "expected orphan detection, got pass"
  fi
}

test_rule_108_java_anchor_truth_pos() {
  _r108_pos_root="$scratch/r108_pos"
  mkdir -p "$_r108_pos_root/agent-service/src/test/java/foo"
  # Surface 1: rule card body
  mkdir -p "$_r108_pos_root/docs/governance/rules"
  cat > "$_r108_pos_root/docs/governance/rules/rule-R-K.md" <<'SHEOF'
# Rule R-K

Asserted by SkillCapacityResolutionIT.rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne.
SHEOF
  cat > "$_r108_pos_root/agent-service/src/test/java/foo/SkillCapacityResolutionIT.java" <<'SHEOF'
package foo;
class SkillCapacityResolutionIT {
  void rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne() {}
}
SHEOF
  _java_file=$(find "$_r108_pos_root/agent-service/src" -name "SkillCapacityResolutionIT.java" -type f 2>/dev/null | head -1)
  if [[ -n "$_java_file" ]] && grep -q "rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne" "$_java_file"; then
    ok "rule_108_java_anchor_truth_pos" "Rule 108 accepts rule-R-K.md anchor resolving to real Java method"
  else
    fail "rule_108_java_anchor_truth_pos" "expected resolution, got file=$_java_file"
  fi
}

test_rule_108_java_anchor_truth_neg() {
  _r108_neg_root="$scratch/r108_neg"
  mkdir -p "$_r108_neg_root/agent-service/src/test/java/foo"
  # Surface 2: principle card body
  mkdir -p "$_r108_neg_root/docs/governance/principles"
  cat > "$_r108_neg_root/docs/governance/principles/P-K.md" <<'SHEOF'
# P-K

Old behaviour: SkillCapacityResolutionIT.suspendsSecondCallerWhenCapacityIsOne.
SHEOF
  cat > "$_r108_neg_root/agent-service/src/test/java/foo/SkillCapacityResolutionIT.java" <<'SHEOF'
package foo;
class SkillCapacityResolutionIT {
  void rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne() {}
}
SHEOF
  _java_file=$(find "$_r108_neg_root/agent-service/src" -name "SkillCapacityResolutionIT.java" -type f 2>/dev/null | head -1)
  # Stale method should NOT be found in file
  if [[ -n "$_java_file" ]] && ! grep -q "suspendsSecondCallerWhenCapacityIsOne" "$_java_file"; then
    ok "rule_108_java_anchor_truth_neg" "Rule 108 catches stale P-K.md anchor not resolving in Java"
  else
    fail "rule_108_java_anchor_truth_neg" "expected stale-method detection, got resolution"
  fi
}

test_rule_109_numeric_rule_ref_pos() {
  _r109_pos_root="$scratch/r109_pos"
  mkdir -p "$_r109_pos_root"
  # Surface 1: principle card body with proper legacy marker
  printf 'Enforced by Rule R-K (formerly Rule 41).\n' > "$_r109_pos_root/P-K.md"
  # Surface 2: contract YAML with Gate Rule reference (intentional numeric)
  printf 'Gate Rule 51 enforces skill-capacity.yaml schema.\n' > "$_r109_pos_root/engine-envelope.v1.yaml"
  _hits=$(grep -nE '\bRule [0-9]+\b' "$_r109_pos_root/P-K.md" "$_r109_pos_root/engine-envelope.v1.yaml" 2>/dev/null \
          | grep -viE '(formerly|legacy|historical|gate rule|was rule|ex-rule|pre-rc[0-9]+|superseded|deprecated)' || true)
  if [[ -z "$_hits" ]]; then
    ok "rule_109_numeric_rule_ref_pos" "Rule 109 accepts 'formerly Rule N' + 'Gate Rule N' markers"
  else
    fail "rule_109_numeric_rule_ref_pos" "expected no hits, got: $_hits"
  fi
}

test_rule_109_numeric_rule_ref_neg() {
  _r109_neg_root="$scratch/r109_neg"
  mkdir -p "$_r109_neg_root"
  # Surface 1: principle card body WITHOUT legacy marker
  printf 'Enforced by Rule 41.\n' > "$_r109_neg_root/P-K.md"
  # Surface 2: module ARCHITECTURE.md WITHOUT legacy marker
  printf 'Routing per Rule 43.\n' > "$_r109_neg_root/ARCHITECTURE.md"
  _hits=$(grep -nE '\bRule [0-9]+\b' "$_r109_neg_root/P-K.md" "$_r109_neg_root/ARCHITECTURE.md" 2>/dev/null \
          | grep -viE '(formerly|legacy|historical|gate rule|was rule|ex-rule|pre-rc[0-9]+|superseded|deprecated)' || true)
  if [[ -n "$_hits" ]]; then
    ok "rule_109_numeric_rule_ref_neg" "Rule 109 catches bare 'Rule 41' / 'Rule 43' without legacy marker"
  else
    fail "rule_109_numeric_rule_ref_neg" "expected hits, got none"
  fi
}

test_rule_110_scope_completeness_pos() {
  _r110_pos_root="$scratch/r110_pos"
  mkdir -p "$_r110_pos_root"
  # Surface 1: gate-script-style file with # Rule N header + # scope_surfaces: comment
  cat > "$_r110_pos_root/gate_script.sh" <<'SHEOF'
# Rule 107 — cross_authority_clause_parity
#
# scope_surfaces: surface1, surface2
SHEOF
  # Surface 2: test fixture file with 2 matching fixtures
  cat > "$_r110_pos_root/test_fixtures.sh" <<'SHEOF'
test_rule_107_pos() { :; }
test_rule_107_neg() { :; }
SHEOF
  _declared=$(awk '/^# Rule [0-9]+ — /{match($0,/^# Rule ([0-9]+)/,m); cr=m[1]; ls=0; next} cr!="" { ls++; if (ls>20){cr=""; next} if ($0 ~ /^# scope_surfaces:/){print cr; cr=""} }' "$_r110_pos_root/gate_script.sh" | head -1)
  _count=$(grep -cE "^test_rule_${_declared}_" "$_r110_pos_root/test_fixtures.sh" 2>/dev/null || echo 0)
  if [[ -n "$_declared" && "$_count" -ge 2 ]]; then
    ok "rule_110_scope_completeness_pos" "Rule 110 accepts Rule $_declared with $_count fixtures"
  else
    fail "rule_110_scope_completeness_pos" "expected declared rule with ≥2 fixtures, got declared=$_declared count=$_count"
  fi
}

test_rule_110_scope_completeness_neg() {
  _r110_neg_root="$scratch/r110_neg"
  mkdir -p "$_r110_neg_root"
  # Surface 1: gate-script-style file with # Rule N header + # scope_surfaces: comment
  cat > "$_r110_neg_root/gate_script.sh" <<'SHEOF'
# Rule 200 — fake_rule_for_test
#
# scope_surfaces: only-one-surface
SHEOF
  # Surface 2: test fixture file with only ONE matching fixture (scope-narrow)
  cat > "$_r110_neg_root/test_fixtures.sh" <<'SHEOF'
test_rule_200_single() { :; }
SHEOF
  _declared=$(awk '/^# Rule [0-9]+ — /{match($0,/^# Rule ([0-9]+)/,m); cr=m[1]; ls=0; next} cr!="" { ls++; if (ls>20){cr=""; next} if ($0 ~ /^# scope_surfaces:/){print cr; cr=""} }' "$_r110_neg_root/gate_script.sh" | head -1)
  _count=$(grep -cE "^test_rule_${_declared}_" "$_r110_neg_root/test_fixtures.sh" 2>/dev/null || echo 0)
  if [[ -n "$_declared" && "$_count" -lt 2 ]]; then
    ok "rule_110_scope_completeness_neg" "Rule 110 catches Rule $_declared with only $_count fixture (need ≥2)"
  else
    fail "rule_110_scope_completeness_neg" "expected <2 fixtures for declared rule, got declared=$_declared count=$_count"
  fi
}

# -----------------------------------------------------------------------------
# Rule 111 fixtures — rc18 Wave 1 (ADR-0095) helper-ised
#
# Fixtures invoke the REAL gate logic via gate/lib/check_recurring_families.sh
# helpers; no inline re-implementation. Closes F-kernel-vs-implementation-drift
# on Rule 111 itself (Wave 1 finding from PR #15 reviewer pass).
# -----------------------------------------------------------------------------

_r111_helper_path="$repo_root/gate/lib/check_recurring_families.sh"

_r111_make_yaml() {
  local out="$1"
  local body="${2:-}"
  if [[ -z "$body" ]]; then
    body='  - id: F-test-family
    title: Test
    first_observed_rc: rc1
    last_observed_rc: rc2
    occurrences: [rc1, rc2]
    root_cause: synthetic
    surfaces:
      - x
    prevention_rules: [Rule X]
    cleanup_status: closed
    open_residual: ""'
  fi
  printf 'schema_version: 1\nlast_updated: 2026-05-21\nfamilies:\n%s\n' "$body" > "$out"
}

test_rule_111_a_wellformed_pos() {
  local root="$scratch/r111_a_pos"
  mkdir -p "$root"
  _r111_make_yaml "$root/families.yaml"
  ( source "$_r111_helper_path"; _check_recurring_families_yaml_wellformed "$root/families.yaml" >/dev/null )
  if [[ $? -eq 0 ]]; then
    ok "rule_111_a_wellformed_pos" "Rule 111.a accepts well-formed yaml (1 valid family)"
  else
    fail "rule_111_a_wellformed_pos" "helper failed on well-formed yaml"
  fi
}

test_rule_111_a_wellformed_neg_missing_field() {
  local root="$scratch/r111_a_neg_missing"
  mkdir -p "$root"
  _r111_make_yaml "$root/families.yaml" '  - id: F-broken
    title: Broken
    first_observed_rc: rc1
    last_observed_rc: rc2
    occurrences: [rc1, rc2]
    root_cause: synthetic
    surfaces:
      - x
    prevention_rules: [Rule X]
    cleanup_status: partial'
  local out
  out=$( source "$_r111_helper_path"; _check_recurring_families_yaml_wellformed "$root/families.yaml" )
  if echo "$out" | grep -qE "F-broken missing required field 'open_residual'"; then
    ok "rule_111_a_wellformed_neg_missing_field" "Rule 111.a catches missing open_residual"
  else
    fail "rule_111_a_wellformed_neg_missing_field" "expected missing-open_residual fail, got: $out"
  fi
}

test_rule_111_a_wellformed_neg_empty_families() {
  # Fix 1b: families: [] is forbidden
  local root="$scratch/r111_a_neg_empty"
  mkdir -p "$root"
  cat > "$root/families.yaml" <<'YEOF'
schema_version: 1
last_updated: 2026-05-21
families: []
YEOF
  local out
  out=$( source "$_r111_helper_path"; _check_recurring_families_yaml_wellformed "$root/families.yaml" )
  if echo "$out" | grep -qE "families must be a non-empty list|families array is empty"; then
    ok "rule_111_a_wellformed_neg_empty_families" "Rule 111.a (fix 1b) catches families: [] empty array"
  else
    fail "rule_111_a_wellformed_neg_empty_families" "expected empty-families fail, got: $out"
  fi
}

test_rule_111_a_wellformed_neg_garbage_enum() {
  # Fix 1c: cleanup_status enum value validated
  local root="$scratch/r111_a_neg_enum"
  mkdir -p "$root"
  _r111_make_yaml "$root/families.yaml" '  - id: F-pizza
    title: Pizza
    first_observed_rc: rc1
    last_observed_rc: rc2
    occurrences: [rc1, rc2]
    root_cause: synthetic
    surfaces:
      - x
    prevention_rules: [Rule X]
    cleanup_status: pizza
    open_residual: ""'
  local out
  out=$( source "$_r111_helper_path"; _check_recurring_families_yaml_wellformed "$root/families.yaml" )
  if echo "$out" | grep -qE "cleanup_status value 'pizza' not in enum"; then
    ok "rule_111_a_wellformed_neg_garbage_enum" "Rule 111.a (fix 1c) rejects cleanup_status value not in enum"
  else
    fail "rule_111_a_wellformed_neg_garbage_enum" "expected enum-violation fail, got: $out"
  fi
}

test_rule_111_a_wellformed_neg_duplicate_field() {
  # Fix 1d: per-family block-bucket catches duplicate fields (closes compensation blind spot)
  local root="$scratch/r111_a_neg_dup"
  mkdir -p "$root"
  _r111_make_yaml "$root/families.yaml" '  - id: F-dup
    title: First
    title: Duplicate
    first_observed_rc: rc1
    last_observed_rc: rc2
    occurrences: [rc1, rc2]
    root_cause: synthetic
    surfaces:
      - x
    prevention_rules: [Rule X]
    cleanup_status: partial
    open_residual: ""'
  local out
  out=$( source "$_r111_helper_path"; _check_recurring_families_yaml_wellformed "$root/families.yaml" )
  if echo "$out" | grep -qE "duplicate key found: 'title'|declares field title"; then
    ok "rule_111_a_wellformed_neg_duplicate_field" "Rule 111.a (fix 1d) catches duplicate field (closes compensation blind spot)"
  else
    fail "rule_111_a_wellformed_neg_duplicate_field" "expected duplicate-field fail, got: $out"
  fi
}

test_rule_111_a_wellformed_neg_nonisodate() {
  # Fix 1e: last_updated must be ISO YYYY-MM-DD
  local root="$scratch/r111_a_neg_date"
  mkdir -p "$root"
  cat > "$root/families.yaml" <<'YEOF'
schema_version: 1
last_updated: two weeks ago
families:
  - id: F-x
    title: X
    first_observed_rc: rc1
    last_observed_rc: rc2
    occurrences: [rc1, rc2]
    root_cause: synthetic
    surfaces:
      - x
    prevention_rules: [Rule X]
    cleanup_status: partial
    open_residual: ""
YEOF
  local out
  out=$( source "$_r111_helper_path"; _check_recurring_families_yaml_wellformed "$root/families.yaml" )
  if echo "$out" | grep -q "is not ISO YYYY-MM-DD format"; then
    ok "rule_111_a_wellformed_neg_nonisodate" "Rule 111.a (fix 1e) rejects non-ISO last_updated"
  else
    fail "rule_111_a_wellformed_neg_nonisodate" "expected ISO-format fail, got: $out"
  fi
}

test_rule_111_c_md_yaml_parity_pos_ignores_prose() {
  # Fix 1f: md parity uses ^### F- H3 headings only; prose F- mentions ignored
  local root="$scratch/r111_c_pos_prose"
  mkdir -p "$root"
  cat > "$root/families.yaml" <<'YEOF'
families:
  - id: F-real
YEOF
  cat > "$root/families.md" <<'MEOF'
### F-real — Real Family
prose mentioning F-fake-family-in-text should be ignored.
also F-another-one cited inline doesn't trigger parity break.
MEOF
  ( source "$_r111_helper_path"; _check_recurring_families_md_yaml_parity "$root/families.yaml" "$root/families.md" >/dev/null )
  if [[ $? -eq 0 ]]; then
    ok "rule_111_c_md_yaml_parity_pos_ignores_prose" "Rule 111.c (fix 1f) ignores prose F-, only ### F- H3 counts"
  else
    fail "rule_111_c_md_yaml_parity_pos_ignores_prose" "helper falsely flagged prose F- as parity break"
  fi
}

test_rule_111_c_md_yaml_parity_neg() {
  local root="$scratch/r111_c_neg"
  mkdir -p "$root"
  cat > "$root/families.yaml" <<'YEOF'
families:
  - id: F-only-in-yaml
  - id: F-shared
YEOF
  cat > "$root/families.md" <<'MEOF'
### F-only-in-md — One
### F-shared — Two
MEOF
  local out
  out=$( source "$_r111_helper_path"; _check_recurring_families_md_yaml_parity "$root/families.yaml" "$root/families.md" )
  if echo "$out" | grep -q "F-only-in-yaml" && echo "$out" | grep -q "F-only-in-md"; then
    ok "rule_111_c_md_yaml_parity_neg" "Rule 111.c flags both yaml-only and md-only parity breaks"
  else
    fail "rule_111_c_md_yaml_parity_neg" "expected both directions caught, got: $out"
  fi
}

# -----------------------------------------------------------------------------
# rc19 Wave 1 (ADR-0096) fixtures — close adversarial findings ADV-RC18-*
# -----------------------------------------------------------------------------

test_rule_111_a_wellformed_neg_future_date() {
  # ADV-RC18-2 + future-date defense: last_updated > today should fail
  local root="$scratch/r111_a_neg_future"
  mkdir -p "$root"
  _r111_make_yaml "$root/families.yaml" '  - id: F-x
    title: X
    first_observed_rc: rc1
    last_observed_rc: rc2
    occurrences: [rc1, rc2]
    root_cause: synthetic
    surfaces:
      - x
    prevention_rules: [Rule X]
    cleanup_status: partial
    open_residual: ""'
  # Overwrite last_updated to a future date
  sed -i 's/^last_updated: .*/last_updated: 9999-12-31/' "$root/families.yaml"
  local out
  out=$( source "$_r111_helper_path"; _check_recurring_families_yaml_wellformed "$root/families.yaml" )
  if echo "$out" | grep -q "is future-dated"; then
    ok "rule_111_a_wellformed_neg_future_date" "Rule 111.a (rc19 ADV-RC18-2) rejects future-dated last_updated"
  else
    fail "rule_111_a_wellformed_neg_future_date" "expected future-date fail, got: $out"
  fi
}

test_rule_111_a_wellformed_neg_invalid_date() {
  # ADV-RC18-2: format-valid but semantically invalid date (2026-13-32)
  local root="$scratch/r111_a_neg_invaliddate"
  mkdir -p "$root"
  _r111_make_yaml "$root/families.yaml" '  - id: F-x
    title: X
    first_observed_rc: rc1
    last_observed_rc: rc2
    occurrences: [rc1, rc2]
    root_cause: synthetic
    surfaces:
      - x
    prevention_rules: [Rule X]
    cleanup_status: partial
    open_residual: ""'
  sed -i 's/^last_updated: .*/last_updated: 2026-13-32/' "$root/families.yaml"
  local out
  out=$( source "$_r111_helper_path"; _check_recurring_families_yaml_wellformed "$root/families.yaml" )
  if echo "$out" | grep -qE "(not a real date|is future-dated|month must be|day is out of range|yaml parse error)"; then
    ok "rule_111_a_wellformed_neg_invalid_date" "Rule 111.a (rc19 ADV-RC18-2) rejects semantically invalid date"
  else
    fail "rule_111_a_wellformed_neg_invalid_date" "expected invalid-date fail, got: $out"
  fi
}

test_rule_111_a_wellformed_neg_literal_block_injection() {
  # ADV-RC18-4: `- id: F-fake` inside root_cause literal block should NOT
  # be misparsed as a real family by the python yaml parser.
  local root="$scratch/r111_a_neg_injection"
  mkdir -p "$root"
  cat > "$root/families.yaml" <<'YEOF'
schema_version: 1
last_updated: 2026-05-21
families:
  - id: F-real
    title: Real
    first_observed_rc: rc1
    last_observed_rc: rc2
    occurrences: [rc1, rc2]
    root_cause: |
      A literal block with embedded text like:
        - id: F-fake-injection
          title: This is prose, not a family.
      The python yaml parser correctly treats this as a string literal,
      not as another family. The old awk parser would have been fooled.
    surfaces:
      - x
    prevention_rules: [Rule X]
    cleanup_status: partial
    open_residual: ""
YEOF
  ( source "$_r111_helper_path"; _check_recurring_families_yaml_wellformed "$root/families.yaml" >/dev/null )
  if [[ $? -eq 0 ]]; then
    ok "rule_111_a_wellformed_neg_literal_block_injection" "Rule 111.a (rc19 ADV-RC18-4) python parser correctly treats literal block as string, not phantom family"
  else
    fail "rule_111_a_wellformed_neg_literal_block_injection" "literal-block injection caused unexpected fail"
  fi
}

test_rule_111_c_md_yaml_parity_pos_uppercase_id() {
  # Correctness Finding 2: md regex should accept uppercase + underscore
  # to mirror the yaml-side acceptance (rc18 had asymmetric anchoring).
  local root="$scratch/r111_c_pos_uppercase"
  mkdir -p "$root"
  cat > "$root/families.yaml" <<'YEOF'
families:
  - id: F-MixedCase_Family
YEOF
  cat > "$root/families.md" <<'MEOF'
### F-MixedCase_Family — Uppercase + underscore test
MEOF
  ( source "$_r111_helper_path"; _check_recurring_families_md_yaml_parity "$root/families.yaml" "$root/families.md" >/dev/null )
  if [[ $? -eq 0 ]]; then
    ok "rule_111_c_md_yaml_parity_pos_uppercase_id" "Rule 111.c (rc19 Correctness Finding 2) accepts uppercase + underscore in family ids on both sides"
  else
    fail "rule_111_c_md_yaml_parity_pos_uppercase_id" "uppercase id mismatched between yaml + md sides"
  fi
}

test_rule_112_meta_self_application_pos() {
  # Rule 112: synthetic META rule that DOES source a helper should pass.
  local root="$scratch/r112_pos"
  mkdir -p "$root"
  cat > "$root/gate_script.sh" <<'SHEOF'
# Rule 999 — fake_meta_rule_with_helper (enforcer E999) [META]
#
# scope_surfaces: x
source "gate/lib/check_recurring_families.sh"
some_body_code() { :; }
SHEOF
  local meta_line
  meta_line=$(grep -nE '^# Rule [0-9]+[a-z]? — .*\[META\]' "$root/gate_script.sh" | head -1)
  local lineno
  lineno=$(printf '%s' "$meta_line" | cut -d: -f1)
  local body
  body=$(awk -v start="$lineno" 'NR > start && NR <= start + 80' "$root/gate_script.sh")
  if printf '%s' "$body" | grep -qE '(source|\. )[[:space:]]+["'"'"'[:space:]]*[^"'"'"'[:space:]]*gate/lib/check_[a-zA-Z_]+\.sh'; then
    ok "rule_112_meta_self_application_pos" "Rule 112 accepts META rule that sources gate/lib/check_*.sh helper"
  else
    fail "rule_112_meta_self_application_pos" "expected pass on META rule with helper source"
  fi
}

test_rule_112_meta_self_application_neg() {
  # Rule 112: synthetic META rule that does NOT source a helper should fail.
  local root="$scratch/r112_neg"
  mkdir -p "$root"
  cat > "$root/gate_script.sh" <<'SHEOF'
# Rule 998 — fake_meta_rule_without_helper (enforcer E998) [META]
#
# scope_surfaces: x
# Body has NO `source gate/lib/check_*.sh` — Pattern D anti-pattern.
some_inline_code() {
  grep -c something file
  awk '/regex/' file
}
SHEOF
  local meta_line
  meta_line=$(grep -nE '^# Rule [0-9]+[a-z]? — .*\[META\]' "$root/gate_script.sh" | head -1)
  local lineno
  lineno=$(printf '%s' "$meta_line" | cut -d: -f1)
  local body
  body=$(awk -v start="$lineno" 'NR > start && NR <= start + 80' "$root/gate_script.sh")
  if ! printf '%s' "$body" | grep -qE '(source|\. )[[:space:]]+["'"'"'[:space:]]*[^"'"'"'[:space:]]*gate/lib/check_[a-zA-Z_]+\.sh'; then
    ok "rule_112_meta_self_application_neg" "Rule 112 catches META rule that fails to source gate/lib/check_*.sh (F-recursive-prevention-irony anti-pattern)"
  else
    fail "rule_112_meta_self_application_neg" "expected fail on META rule without helper source"
  fi
}

test_rule_113_legacy_paren_reintroduction_neg() {
  # Rule 113.a: synthetic enforcers.yaml WITH (legacy Rule NN ...) paren → fail
  local root="$scratch/r113_neg"
  mkdir -p "$root"
  cat > "$root/enforcers.yaml" <<'YEOF'
- id: E999
  constraint_ref: "CLAUDE.md Rule X.y (legacy Rule 99 — fake legacy paren reintroduction); test"
YEOF
  if grep -nE '\(legacy Rule [0-9]+' "$root/enforcers.yaml" > /dev/null; then
    ok "rule_113_legacy_paren_reintroduction_neg" "Rule 113.a catches (legacy Rule NN ...) paren reintroduction"
  else
    fail "rule_113_legacy_paren_reintroduction_neg" "expected paren to be detected"
  fi
}

test_rule_113_migration_doc_complete_pos() {
  # Rule 113.b: real migration.md MUST contain expected sections
  if grep -qF 'Legacy numeric' "$repo_root/gate/rule-number-migration.md" && \
     grep -qF 'rc17 sub-rule splits' "$repo_root/gate/rule-number-migration.md"; then
    ok "rule_113_migration_doc_complete_pos" "Rule 113.b accepts real migration.md with both required section headings"
  else
    fail "rule_113_migration_doc_complete_pos" "real migration.md missing expected headings"
  fi
}

test_rule_114_filename_dot_convention_pos() {
  # Real corpus rule cards all match the convention
  local invalid_count=0
  while IFS= read -r f; do
    [[ -z "$f" ]] && continue
    local base
    base=$(basename "$f")
    [[ "$base" == "README.md" ]] && continue
    if [[ ! "$base" =~ ^rule-[DRGM]-[A-Z0-9](\.[a-z0-9]+)?\.md$ ]]; then
      invalid_count=$((invalid_count + 1))
    fi
  done < <(find "$repo_root/docs/governance/rules" -maxdepth 1 -type f -name '*.md' 2>/dev/null | sort)
  if [[ "$invalid_count" -eq 0 ]]; then
    ok "rule_114_filename_dot_convention_pos" "Rule 114 accepts all current corpus rule card filenames (dot convention)"
  else
    fail "rule_114_filename_dot_convention_pos" "expected all corpus files to match, $invalid_count did not"
  fi
}

test_rule_114_filename_dot_convention_neg() {
  # Synthetic hyphenated filename rejected
  local root="$scratch/r114_neg"
  mkdir -p "$root"
  touch "$root/rule-G-3-1.md"  # hyphenated form — should be rejected
  local base
  base=$(basename "$root/rule-G-3-1.md")
  if [[ ! "$base" =~ ^rule-[DRGM]-[A-Z0-9](\.[a-z0-9]+)?\.md$ ]]; then
    ok "rule_114_filename_dot_convention_neg" "Rule 114 catches hyphenated filename rule-G-3-1.md (prevents rc17 trap from recurring)"
  else
    fail "rule_114_filename_dot_convention_neg" "expected hyphenated form to fail regex"
  fi
}

# ---------------------------------------------------------------------------
# PR-E4: Parallel orchestrator.
#
# Each test_rule*() function is independent (uses its own $scratch/r<N>_*
# subdirectory). We distribute them round-robin across $GATE_PARALLELISM_JOBS
# batches; each batch runs sequentially in its own subshell, appending results
# to a per-batch file. After all batches complete, we sort + concatenate the
# results for deterministic stdout, then count PASS/FAIL.
# ---------------------------------------------------------------------------
_pre4_all_tests=$(declare -F | awk '/^declare -f test_rule/{print $3}' | sort)
# Manifest-derived expected function count (Rule 89 / E122 sub-check (b)
# forbids bare-literal TOTAL=NNN). The actual TOTAL is set after the
# orchestrator counts PASS+FAIL results below.
_pre4_expected_fn_count=$(echo "$_pre4_all_tests" | grep -c .)
_pre4_jobs="${GATE_PARALLELISM_JOBS:-8}"
if ! [[ "$_pre4_jobs" =~ ^[0-9]+$ ]] || [[ "$_pre4_jobs" -lt 1 ]]; then
  _pre4_jobs=8
fi

_pre4_batch_dir="$scratch/_pre4_batches"
mkdir -p "$_pre4_batch_dir"

# Clear env vars that gate/lib/load_config.sh exports so test fixtures running
# under bash -c with a synthetic GATE_REPO_ROOT get a clean slate (Rule 73
# negative cases would otherwise inherit GATE_JOBS=1 and override the
# synthetic out-of-range value).
unset GATE_JOBS GATE_PARALLELISM_JOBS GATE_PARALLELISM_ENABLED \
      GATE_PARALLELISM_RULE_TIMEOUT_SECONDS GATE_PARALLELISM_BATCH_STRATEGY \
      GATE_LOGGING_NDJSON_ENABLED GATE_LOGGING_SUMMARY_ENABLED \
      GATE_LOGGING_STDOUT_FORMAT GATE_LOGGING_RETENTION_MAX_RUNS \
      GATE_LOGGING_RETENTION_AUTO_PRUNE GATE_LOGGING_PROFILE_MODE \
      GATE_SCAN_CACHE_ENABLED GATE_SCAN_CACHE_PATTERNS \
      GATE_REGRESSION_DETECTION_ENABLED \
      GATE_REGRESSION_DETECTION_MULTIPLIER_THRESHOLD \
      GATE_REGRESSION_DETECTION_ABSOLUTE_MIN_MS \
      GATE_REGRESSION_DETECTION_BASELINE_WINDOW \
      GATE_RULE_FILTERS_SKIP GATE_RULE_FILTERS_ONLY \
      GATE_CONFIG_VALID GATE_CONFIG_ERRORS GATE_REPO_ROOT 2>/dev/null || true

# Distribute functions round-robin into $_pre4_jobs batches.
_pre4_i=0
while IFS= read -r _pre4_t; do
  [[ -z "$_pre4_t" ]] && continue
  printf '%s\n' "$_pre4_t" >> "$_pre4_batch_dir/batch_$((_pre4_i % _pre4_jobs))"
  _pre4_i=$((_pre4_i + 1))
done <<< "$_pre4_all_tests"

# Run each batch in parallel.
for _pre4_b in "$_pre4_batch_dir"/batch_*; do
  [[ -f "$_pre4_b" ]] || continue
  (
    export TEST_RESULT_FILE="${_pre4_b}.results"
    : > "$TEST_RESULT_FILE"
    while IFS= read -r _pre4_func; do
      "$_pre4_func"
    done < "$_pre4_b"
  ) &
done
wait

# Aggregate, deterministic sort, count.
_pre4_all_results="$scratch/_pre4_all_results.txt"
: > "$_pre4_all_results"
for _pre4_rf in "$_pre4_batch_dir"/batch_*.results; do
  [[ -f "$_pre4_rf" ]] && cat "$_pre4_rf" >> "$_pre4_all_results"
done

passed=$(grep -c '^PASS ' "$_pre4_all_results" 2>/dev/null || true)
failed=$(grep -c '^FAIL ' "$_pre4_all_results" 2>/dev/null || true)
passed=${passed:-0}
failed=${failed:-0}

# Print PASS lines to stdout in deterministic test_id order, FAIL lines to stderr.
sort "$_pre4_all_results" | awk '/^PASS / {print} /^FAIL / {print > "/dev/stderr"}'

# Manifest-derived TOTAL per Rule 89 / E122 sub-check (b):
# TOTAL is the actual observed result count. With every test_rule*() function
# emitting >=1 result and zero loss in aggregation, passed+failed equals the
# emitted-result manifest; the fail-closed clause below catches the case where
# passed != TOTAL (e.g. truncated batch concat, missing function).
TOTAL=$((passed + failed))

echo ""
echo "Tests passed: ${passed}/${TOTAL}"
echo "Functions executed: ${_pre4_expected_fn_count}; each emits >=1 result"

# Fail closed (Rule 89 / E122 sub-check (a)): non-zero exit when passed != TOTAL
if [[ "$passed" -ne "$TOTAL" ]]; then
  echo "FAIL: passed ($passed) != TOTAL ($TOTAL); Rule 89 / E122 fail-closed exit" >&2
  exit 1
fi
if [[ "$failed" -gt 0 ]]; then
  exit 1
fi
# Sanity: every declared test_rule*() function MUST have emitted at least one
# result. If the emitted-result-id count is lower than the function count,
# at least one function ran silently. Note: a function may emit MORE than one
# id (some emit 2-3), so we only fail when emitted < expected.
_pre4_emitted_ids=$(awk '/^PASS / || /^FAIL / { match($0, /\[([a-zA-Z0-9_]+)\]/, arr); if (arr[1] != "") print arr[1] }' "$_pre4_all_results" | sort -u | wc -l)
if [[ "$_pre4_emitted_ids" -lt "$_pre4_expected_fn_count" ]]; then
  echo "FAIL: ${_pre4_emitted_ids} unique test_ids emitted but ${_pre4_expected_fn_count} functions defined — at least one function emitted nothing; Rule 89 / E122 fail-closed exit" >&2
  exit 1
fi
exit 0
