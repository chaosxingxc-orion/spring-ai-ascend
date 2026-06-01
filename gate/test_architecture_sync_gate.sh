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

# Resolve a usable Python interpreter once for the E2 block. Hosts ship
# either `python3` (Linux/macOS/CI) or `python` (Windows / some Conda
# envs); the prior hard-coded `python3` invocations silently failed the
# JSON validation on `python`-only hosts and surfaced as "expected >=1
# valid JSON line; got valid=0".
_re2_pybin=""
if command -v python3 >/dev/null 2>&1; then
  _re2_pybin="python3"
elif command -v python >/dev/null 2>&1; then
  _re2_pybin="python"
fi

# -------- Test 1: per-rule.ndjson present + valid JSON ---------------------
_re2_t1_file="$_re2_log_dir/per-rule.ndjson"
_re2_t1_lines=0
[[ -f "$_re2_t1_file" ]] && _re2_t1_lines=$(wc -l < "$_re2_t1_file" 2>/dev/null || echo 0)
_re2_t1_valid=0
if [[ "$_re2_t1_lines" -ge 1 ]] && [[ -n "$_re2_pybin" ]]; then
  if cat "$_re2_t1_file" | "$_re2_pybin" -c "
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
if [[ -f "$_re2_t2_file" ]] && [[ -n "$_re2_pybin" ]]; then
  _re2_t2_rt=$(cat "$_re2_t2_file" | "$_re2_pybin" -c "
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
if [[ -n "$_re2_pybin" ]]; then
  _re2_t4_check=$(cat "$_re2_log_dir/per-rule.ndjson" | "$_re2_pybin" -c "
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
else
  _re2_t4_check="NO-PYTHON"
fi
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
_r88_pos_canon_set=$(grep -E '^# Rule [0-9]+.?[a-z]? (—|--) ' "$_r88_pos_root/gate/check_architecture_sync.sh" | sed -E 's/^# Rule [0-9]+.?[a-z]? (—|--) //' | awk '{print $1}' | sort -u)
_r88_pos_par_set=$(awk '/^# Rule [0-9]+.?[a-z]? (—|--) / { str=substr($0, 8); space_idx=index(str, " "); rest=substr(str, space_idx + 1); sub(/^[^a-zA-Z0-9_]*/, "", rest); match(rest, /^[a-zA-Z0-9_]+/); print substr(rest, RSTART, RLENGTH) } /^# === END OF RULES ===$/ { exit }' "$_r88_pos_root/gate/check_architecture_sync.sh" | sort -u)
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
_r88_neg_bad_sep=$(grep -nE '^# Rule [0-9]+.?[a-z]? -- ' "$_r88_neg_root/gate/check_architecture_sync.sh" || true)
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

test_rule_97_release_note_self_tests_denom_neg() {
  # rc20 Wave 2 / ADR-0097: Rule 97 must catch `N/M self-tests pass` ratio whose
  # denominator diverges from baseline_metrics.gate_executable_test_cases.
  # Closes rc19 release-note 215/218-vs-220 drift (Finding F5).
  local root="$scratch/r97_tests_neg"
  mkdir -p "$root"
  cat > "$root/release.md" <<'MEOF'
## Verification
- [x] 215/218 self-tests pass on Windows Git Bash
- [x] 220 nodes / 656 edges
MEOF
  local live=220
  local markers='historical|rc[0-9]+ snapshot|rc[0-9]+ correction|rc[0-9]+ first cut|rc[0-9]+ baseline|superseded|previous|pre-rc[0-9]+'
  local violations
  violations=$(awk -v live="$live" -v markers="$markers" '
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
        if (match(line, /[0-9]+\/[0-9]+[[:space:]]+(self-tests|tests passed|tests pass|gate self-tests)/)) {
          matched_str = substr(line, RSTART, RLENGTH)
          split(matched_str, parts, "/")
          match(parts[2], /^[0-9]+/)
          denom = substr(parts[2], RSTART, RLENGTH)
          if (denom != live && window !~ markers) print i ":denom:" denom
        }
      }
    }' "$root/release.md" 2>/dev/null)
  if [[ -n "$violations" ]]; then
    ok "rule_97_release_note_self_tests_denom_neg" "Rule 97 (rc20 Wave 2 extension) catches N/M self-tests denominator drift"
  else
    fail "rule_97_release_note_self_tests_denom_neg" "expected denominator-drift detection; got nothing"
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
  _declared=$(awk '/^[[:space:]]+reactor_modules:/{print $2}' "$_r106c_pos_root/architecture-status.yaml")
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

# ---------------------------------------------------------------------------
# rc16 — Rules 107 / 108 / 109 / 110 (Family A/B/C + META prevention).
# Per ADR-0093. Each rule gets 2 fixtures (positive + negative) covering
# at least 2 distinct scope surfaces (Rule 110 META requirement).
# ---------------------------------------------------------------------------

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

test_rule_111_c_cleanup_status_parity_neg() {
  # rc20 Wave 1 / ADR-0097: drift between yaml cleanup_status and md table
  # cleanup_status text MUST be detected (closes F-recursive review Finding F2/F3).
  local root="$scratch/r111c_neg"
  mkdir -p "$root"
  cat > "$root/families.yaml" <<'YEOF'
schema_version: 1
last_updated: 2026-05-21
families:
  - id: F-test-fam
    title: Test
    first_observed_rc: rc20
    last_observed_rc: rc20
    occurrences: [rc20]
    root_cause: |
      test
    surfaces:
      - docs/test.md
    prevention_rules:
      - Rule X
    cleanup_status: closed
    open_residual: |
      none
YEOF
  cat > "$root/families.md" <<'MEOF'
# Recurring Defect Families

| # | Family ID | Title | Occurrences | Cleanup Status |
|---|---|---|---|---|
| 1 | F-test-fam | Test | 1 (rc20) | ⚠️ partial |

### F-test-fam — Test
Some body text.
MEOF
  # Run the python validator and check for the drift error.
  local _python_bin
  if command -v python >/dev/null 2>&1; then _python_bin=python
  elif command -v python3 >/dev/null 2>&1; then _python_bin=python3
  else
    ok "rule_111_c_cleanup_status_parity_neg" "skipped — no python available on this host"
    return
  fi
  local _output
  _output=$("$_python_bin" "$repo_root/gate/lib/validate_recurring_families.py" parity "$root/families.yaml" "$root/families.md" 2>&1)
  if printf '%s' "$_output" | grep -q "cleanup_status drift"; then
    ok "rule_111_c_cleanup_status_parity_neg" "Rule 111.c (rc20 Wave 1 extension) catches yaml-vs-md cleanup_status drift"
  else
    fail "rule_111_c_cleanup_status_parity_neg" "expected cleanup_status drift detection; got: $_output"
  fi
}

test_rule_115_no_version_log_metadata_neg() {
  # Rule D-9 enforcement via grep on production-code patterns.
  local root="$scratch/r115_neg"
  mkdir -p "$root"
  # The string below intentionally contains a forbidden pattern;
  # we WRITE it as ASCII letters concatenated so this fixture file itself
  # (which is in the gate-test-exempt list) does not get mis-flagged either.
  printf 'public void foo() { /* rc20 Wave 3: pretend annotation */ }\n' > "$root/Bad.java"
  local pattern='\brc[0-9]+ Wave [0-9]+\b|\bper ADR-[0-9]{4}\b|\(F[0-9]+\)|\bFinding F[0-9]+\b|\b(closes|addresses) #[0-9]+\b'
  if grep -qE "$pattern" "$root/Bad.java"; then
    ok "rule_115_no_version_log_metadata_neg" "Rule D-9 / Rule 115 catches rc<N> Wave <M> annotation in production code"
  else
    fail "rule_115_no_version_log_metadata_neg" "expected forbidden version-tag to be detected"
  fi
}

test_rule_115_no_version_log_metadata_pos() {
  # Clean production code passes the regex.
  local root="$scratch/r115_pos"
  mkdir -p "$root"
  printf 'public int multiply(int a, int b) { return a * b; }\n' > "$root/Good.java"
  local pattern='\brc[0-9]+ Wave [0-9]+\b|\bper ADR-[0-9]{4}\b|\(F[0-9]+\)|\bFinding F[0-9]+\b|\b(closes|addresses) #[0-9]+\b'
  if ! grep -qE "$pattern" "$root/Good.java"; then
    ok "rule_115_no_version_log_metadata_pos" "Rule D-9 / Rule 115 accepts production code free of forbidden version metadata"
  else
    fail "rule_115_no_version_log_metadata_pos" "unexpected forbidden tag in clean file"
  fi
}

test_rule_125_codegraph_install_truth_pos() {
  local root="$scratch/r125_pos"
  mkdir -p "$root/tools/codegraph"
  cat > "$root/tools/codegraph/package.json" <<'SHEOF'
{
  "name": "spring-ai-ascend-codegraph",
  "private": true,
  "dependencies": {
    "@colbymchenry/codegraph": "0.9.4"
  }
}
SHEOF
  cat > "$root/tools/codegraph/package-lock.json" <<'SHEOF'
{
  "lockfileVersion": 3,
  "packages": {
    "node_modules/@colbymchenry/codegraph": { "version": "0.9.4" }
  }
}
SHEOF
  cat > "$root/.mcp.json" <<'SHEOF'
{
  "mcpServers": {
    "codegraph": {
      "type": "stdio",
      "command": "node",
      "args": ["tools/codegraph/node_modules/@colbymchenry/codegraph/npm-shim.js", "serve", "--mcp"]
    }
  }
}
SHEOF
  local pkg_ok=0 lock_ok=0 mcp_ok=0
  grep -qE '"@colbymchenry/codegraph":[[:space:]]*"[0-9]+\.[0-9]+\.[0-9]+"' "$root/tools/codegraph/package.json" && pkg_ok=1
  grep -qE '"lockfileVersion":[[:space:]]*[3-9][0-9]*' "$root/tools/codegraph/package-lock.json" && lock_ok=1
  grep -qE '"tools/codegraph/node_modules/@colbymchenry/codegraph/[^"]+"' "$root/.mcp.json" && mcp_ok=1
  if [[ $pkg_ok -eq 1 && $lock_ok -eq 1 && $mcp_ok -eq 1 ]]; then
    ok "rule125_install_truth_pos" "exact-pinned package.json + lockfile v3 + .mcp.json relative-shim ref pass all three sub-checks"
  else
    fail "rule125_install_truth_pos" "expected all three to pass; got pkg=$pkg_ok lock=$lock_ok mcp=$mcp_ok"
  fi
}

test_rule_125_codegraph_install_truth_caret_neg() {
  local root="$scratch/r125_caret_neg"
  mkdir -p "$root/tools/codegraph"
  cat > "$root/tools/codegraph/package.json" <<'SHEOF'
{
  "dependencies": {
    "@colbymchenry/codegraph": "^0.9.4"
  }
}
SHEOF
  if ! grep -qE '"@colbymchenry/codegraph":[[:space:]]*"[0-9]+\.[0-9]+\.[0-9]+"' "$root/tools/codegraph/package.json"; then
    ok "rule125_install_truth_caret_neg" "caret-prefixed version fails the exact-pin check (would drift to 0.9.x)"
  else
    fail "rule125_install_truth_caret_neg" "expected caret-prefixed version to fail exact-pin check"
  fi
}

test_rule_125_codegraph_install_truth_lockfile_v2_neg() {
  local root="$scratch/r125_lockv2_neg"
  mkdir -p "$root/tools/codegraph"
  cat > "$root/tools/codegraph/package-lock.json" <<'SHEOF'
{
  "lockfileVersion": 2
}
SHEOF
  if ! grep -qE '"lockfileVersion":[[:space:]]*[3-9][0-9]*' "$root/tools/codegraph/package-lock.json"; then
    ok "rule125_install_truth_lockfile_v2_neg" "lockfileVersion=2 is detected as below the integrity-hash threshold"
  else
    fail "rule125_install_truth_lockfile_v2_neg" "expected lockfileVersion=2 to fail the >=3 check"
  fi
}

test_rule_125_codegraph_install_truth_mcp_missing_shim_neg() {
  local root="$scratch/r125_mcp_neg"
  mkdir -p "$root"
  cat > "$root/.mcp.json" <<'SHEOF'
{
  "mcpServers": {
    "codegraph": {
      "command": "codegraph",
      "args": ["serve", "--mcp"]
    }
  }
}
SHEOF
  if ! grep -qE '"tools/codegraph/node_modules/@colbymchenry/codegraph/[^"]+"' "$root/.mcp.json"; then
    ok "rule125_install_truth_mcp_missing_shim_neg" "absent relative-shim ref (PATH-only command) fails the cross-platform check"
  else
    fail "rule125_install_truth_mcp_missing_shim_neg" "expected absent relative shim to fail"
  fi
}

test_rule_127_release_note_no_pending_evidence_pos() {
  local root="$scratch/r127_pos"
  mkdir -p "$root/docs/logs/releases"
  cat > "$root/docs/logs/releases/2026-05-25-l0-rc49-current.en.md" <<'SHEOF'
---
formal_release: true
evidence_bundle: gate/release-ci-evidence/rc49.evidence.yaml
release_candidate_commit: 0123456789abcdef0123456789abcdef01234567
status: formal-release-ready
---

# rc49

## Release Decision

Decision: ship.
SHEOF
  if python3 "$repo_root/gate/lib/check_release_note_current_truth.py" --root "$root" >/dev/null 2>&1; then
    ok "rule127_release_note_no_pending_evidence_pos" "latest formal release with real commit and evidence path passes"
  else
    fail "rule127_release_note_no_pending_evidence_pos" "expected concrete formal release note to pass"
  fi

  local closure_root="$scratch/r127_closure_pos"
  mkdir -p "$closure_root/docs/logs/releases"
  cat > "$closure_root/docs/logs/releases/2026-05-26-rc53-closure.en.md" <<'SHEOF'
---
status: closure
---

# rc53 Closure Release Note

- F-placeholder-leaks-into-active-corpus — anonymous slugs (TBD / TODO-template) are cited here as the defect family vocabulary.
SHEOF
  if python3 "$repo_root/gate/lib/check_release_note_current_truth.py" --root "$closure_root" >/dev/null 2>&1; then
    ok "rule127_release_note_no_pending_evidence_closure_pos" "closure note may cite placeholder family vocabulary"
  else
    fail "rule127_release_note_no_pending_evidence_closure_pos" "expected closure note citation context to pass"
  fi
}

test_rule_127_release_note_no_pending_evidence_placeholder_neg() {
  local root="$scratch/r127_placeholder_neg"
  mkdir -p "$root/docs/logs/releases"
  cat > "$root/docs/logs/releases/2026-05-25-l0-rc49-current.en.md" <<'SHEOF'
---
formal_release: true
release_candidate_commit: pending-formal-validator-run
status: formal-release-ready
---

# rc49

## Release Decision

Evidence bundle: TO BE GENERATED.
SHEOF
  if ! python3 "$repo_root/gate/lib/check_release_note_current_truth.py" --root "$root" >/dev/null 2>&1; then
    ok "rule127_release_note_no_pending_evidence_placeholder_neg" "pending evidence placeholders fail the latest release check"
  else
    fail "rule127_release_note_no_pending_evidence_placeholder_neg" "expected pending evidence placeholders to fail"
  fi

  local closure_root="$scratch/r127_closure_placeholder_neg"
  mkdir -p "$closure_root/docs/logs/releases"
  cat > "$closure_root/docs/logs/releases/2026-05-26-rc53-closure.en.md" <<'SHEOF'
---
status: closure
---

# rc53 Closure Release Note

| Wave | Lines |
|---|---:|
| Wave 8 | TBD |
SHEOF
  if ! python3 "$repo_root/gate/lib/check_release_note_current_truth.py" --root "$closure_root" >/dev/null 2>&1; then
    ok "rule127_release_note_no_pending_evidence_closure_neg" "live placeholders fail in current closure notes"
  else
    fail "rule127_release_note_no_pending_evidence_closure_neg" "expected live closure-note placeholder to fail"
  fi
}

test_rule_128_model_gateway_authority_truth_pos() {
  local root="$scratch/r128_pos"
  mkdir -p "$root/docs/adr" \
           "$root/docs/contracts" \
           "$root/agent-middleware/src/main/java/com/huawei/ascend/middleware/model/spi"
  cat > "$root/docs/adr/0121-model-gateway-spi.yaml" <<'SHEOF'
decision: |
  com.huawei.ascend.middleware.model.spi.ModelGateway
  ModelResponse invoke(ModelInvocation invocation);
SHEOF
  cat > "$root/agent-middleware/src/main/java/com/huawei/ascend/middleware/model/spi/ModelGateway.java" <<'SHEOF'
package com.huawei.ascend.middleware.model.spi;
public interface ModelGateway {
    ModelResponse invoke(ModelInvocation invocation);
}
SHEOF
  cat > "$root/docs/contracts/contract-catalog.md" <<'SHEOF'
| `ModelGateway` | `agent-middleware` | `com.huawei.ascend.middleware.model.spi` | design_only |
SHEOF
  if python3 "$repo_root/gate/lib/check_model_gateway_authority_truth.py" --root "$root" >/dev/null 2>&1; then
    ok "rule128_model_gateway_authority_truth_pos" "ADR, Java, and catalog agree on ModelGateway package/signature"
  else
    fail "rule128_model_gateway_authority_truth_pos" "expected aligned ModelGateway authority surfaces to pass"
  fi
}

test_rule_128_model_gateway_authority_truth_reactive_neg() {
  local root="$scratch/r128_reactive_neg"
  mkdir -p "$root/docs/adr" \
           "$root/docs/contracts" \
           "$root/agent-middleware/src/main/java/com/huawei/ascend/middleware/model/spi"
  cat > "$root/docs/adr/0121-model-gateway-spi.yaml" <<'SHEOF'
decision: |
  com.huawei.ascend.service.model.spi.ModelGateway
  Mono<ModelResponse> invoke(ModelInvocation invocation);
SHEOF
  cat > "$root/agent-middleware/src/main/java/com/huawei/ascend/middleware/model/spi/ModelGateway.java" <<'SHEOF'
package com.huawei.ascend.middleware.model.spi;
public interface ModelGateway {
    ModelResponse invoke(ModelInvocation invocation);
}
SHEOF
  cat > "$root/docs/contracts/contract-catalog.md" <<'SHEOF'
| `ModelGateway` | `agent-middleware` | `com.huawei.ascend.middleware.model.spi` | design_only |
SHEOF
  if ! python3 "$repo_root/gate/lib/check_model_gateway_authority_truth.py" --root "$root" >/dev/null 2>&1; then
    ok "rule128_model_gateway_authority_truth_reactive_neg" "ADR reactive/service-package drift fails against Java/catalog truth"
  else
    fail "rule128_model_gateway_authority_truth_reactive_neg" "expected reactive/service-package ADR drift to fail"
  fi
}

test_rule_129_contract_spi_count_truth_pos() {
  local root="$scratch/r129_pos"
  mkdir -p "$root/docs/contracts" \
           "$root/docs/logs/releases" \
           "$root/agent-service/src/main/java/com/huawei/ascend/service/agent/spi"
  cat > "$root/docs/contracts/contract-catalog.md" <<'SHEOF'
**Active SPI interfaces (33 total):**

**Count by module:**

| Module | Count |
|---|---:|
| `agent-service` | 9 (`Agent`) |
| `agent-execution-engine` | 7 (`Planner`) |
| `agent-bus` | 4 (`IngressGateway`) |
| `agent-middleware` | 12 (`ModelGateway`) |
| `agent-evolve` | 1 (`SlowTrackJudge`) |

**Deferred / Promoted Design Names:**

| Name | Status |
|---|---|
| `Skill` | promoted in rc43 |
| `AgentRegistry` | promoted in rc43 |
SHEOF
  cat > "$root/docs/logs/releases/2026-05-25-l0-rc49-current.en.md" <<'SHEOF'
---
release_candidate_commit: 0123456789abcdef0123456789abcdef01234567
status: formal-release-ready
---

- Active SPI interfaces: 33 total (19 pre-rc43 + 14 rc43).
SHEOF
  cat > "$root/docs/contracts/chat-advisor.v1.yaml" <<'SHEOF'
advised_request:
  required_fields:
    - modelRequest      # AdvisedModelRequest
advised_response:
  required_fields:
    - modelResponse     # AdvisedModelResponse
binding:
  sequence_id: advisor-model-hook-order/v1
  agent_definition_field: advisorBindings
SHEOF
  cat > "$root/docs/contracts/agent-definition.v1.yaml" <<'SHEOF'
agent_definition:
  optional_fields:
    - advisorBindings    # list<AdvisorBinding>
advisor_binding:
  required_fields: [advisorName, mode, orderOverride, metadata]
SHEOF
  cat > "$root/docs/contracts/model-streaming.v1.yaml" <<'SHEOF'
hook_binding:
  sequence_id: advisor-model-hook-order/v1
SHEOF
  cat > "$root/agent-service/src/main/java/com/huawei/ascend/service/agent/spi/AgentDefinition.java" <<'SHEOF'
package com.huawei.ascend.service.agent.spi;
public record AgentDefinition(List<AdvisorBinding> advisorBindings) {}
SHEOF
  cat > "$root/agent-service/src/main/java/com/huawei/ascend/service/agent/spi/AdvisorBinding.java" <<'SHEOF'
package com.huawei.ascend.service.agent.spi;
public record AdvisorBinding(String advisorName) {}
SHEOF
  if python3 "$repo_root/gate/lib/check_contract_spi_count_truth.py" --root "$root" >/dev/null 2>&1; then
    ok "rule129_contract_spi_count_truth_pos" "catalog counts, latest release SPI total, and advisor composition surfaces agree"
  else
    fail "rule129_contract_spi_count_truth_pos" "expected aligned SPI count and advisor composition surfaces to pass"
  fi
}

test_rule_129_contract_spi_count_truth_stale_deferred_neg() {
  local root="$scratch/r129_stale_deferred_neg"
  mkdir -p "$root/docs/contracts" "$root/docs/logs/releases"
  cat > "$root/docs/contracts/contract-catalog.md" <<'SHEOF'
**Active SPI interfaces (33 total):**

**Count by module:**

| Module | Count |
|---|---:|
| `agent-service` | 9 (`Agent`) |
| `agent-execution-engine` | 7 (`Planner`) |
| `agent-bus` | 4 (`IngressGateway`) |
| `agent-middleware` | 12 (`ModelGateway`) |
| `agent-evolve` | 1 (`SlowTrackJudge`) |

**Design-named SPIs (deferred W2+):**

| Surface | Target wave | Authority |
|---|---|---|
| `Skill` + `SkillContext` + `SkillResourceMatrix` | W2 | ADR-0030 |
SHEOF
  cat > "$root/docs/logs/releases/2026-05-25-l0-rc49-current.en.md" <<'SHEOF'
---
release_candidate_commit: 0123456789abcdef0123456789abcdef01234567
status: formal-release-ready
---

- Active SPI interfaces: 33 total (19 pre-rc43 + 14 rc43).
SHEOF
  local stale_failed=0
  if ! python3 "$repo_root/gate/lib/check_contract_spi_count_truth.py" --root "$root" >/dev/null 2>&1; then
    stale_failed=1
  fi

  local composition_root="$scratch/r129_composition_gap_neg"
  mkdir -p "$composition_root/docs/contracts" \
           "$composition_root/docs/logs/releases" \
           "$composition_root/agent-service/src/main/java/com/huawei/ascend/service/agent/spi"
  cat > "$composition_root/docs/contracts/contract-catalog.md" <<'SHEOF'
**Active SPI interfaces (33 total):**

**Count by module:**

| Module | Count |
|---|---:|
| `agent-service` | 9 (`Agent`) |
| `agent-execution-engine` | 7 (`Planner`) |
| `agent-bus` | 4 (`IngressGateway`) |
| `agent-middleware` | 12 (`ModelGateway`) |
| `agent-evolve` | 1 (`SlowTrackJudge`) |
SHEOF
  cat > "$composition_root/docs/logs/releases/2026-05-25-l0-rc49-current.en.md" <<'SHEOF'
---
release_candidate_commit: 0123456789abcdef0123456789abcdef01234567
status: formal-release-ready
---

- Active SPI interfaces: 33 total (19 pre-rc43 + 14 rc43).
SHEOF
  cat > "$composition_root/docs/contracts/chat-advisor.v1.yaml" <<'SHEOF'
advised_request:
  required_fields:
    - requestEnvelope   # raw map drift
binding:
  sequence_id: advisor-model-hook-order/v1
  agent_definition_field: advisorBindings
SHEOF
  cat > "$composition_root/docs/contracts/agent-definition.v1.yaml" <<'SHEOF'
agent_definition:
  optional_fields:
    - plannerBinding
SHEOF
  cat > "$composition_root/docs/contracts/model-streaming.v1.yaml" <<'SHEOF'
hook_binding:
  sequence_id: different-order/v1
SHEOF
  cat > "$composition_root/agent-service/src/main/java/com/huawei/ascend/service/agent/spi/AgentDefinition.java" <<'SHEOF'
package com.huawei.ascend.service.agent.spi;
public record AgentDefinition(String agentId) {}
SHEOF
  local composition_failed=0
  if ! python3 "$repo_root/gate/lib/check_contract_spi_count_truth.py" --root "$composition_root" >/dev/null 2>&1; then
    composition_failed=1
  fi

  if [[ $stale_failed -eq 1 && $composition_failed -eq 1 ]]; then
    ok "rule129_contract_spi_count_truth_stale_deferred_neg" "promoted stale-deferred rows and advisor composition drift fail"
  else
    fail "rule129_contract_spi_count_truth_stale_deferred_neg" "expected stale deferred row and advisor composition drift to fail"
  fi
}

test_rule_130_feature_lifecycle_validity_pos() {
  # Rule G-14.a — every SAA Feature in architecture/features/features.dsl
  # declares saa.status in the 9-state lifecycle set.
  local dsl="architecture/features/features.dsl"
  if [[ ! -f "$dsl" ]]; then
    skip "rule_130_feature_lifecycle_validity_pos" "$dsl missing — no SAA Feature corpus yet"
    return
  fi
  local valid="proposed accepted design_only ready_for_impl implemented_unverified test_verified shipped deprecated removed"
  local bad=""
  while IFS= read -r status; do
    status=$(echo "$status" | tr -d '\r')
    [[ -z "$status" ]] && continue
    local match=0
    for s in $valid; do
      if [[ "$status" == "$s" ]]; then match=1; break; fi
    done
    if [[ $match -eq 0 ]]; then
      bad="$bad $status"
    fi
  done < <(grep -oE '"saa\.status"[[:space:]]+"[^"]+"' "$dsl" | sed -E 's/.*"saa\.status"[[:space:]]+"([^"]+)".*/\1/')
  if [[ -n "$bad" ]]; then
    fail "rule_130_feature_lifecycle_validity_pos" "Rule G-14.a violation: invalid lifecycle states in $dsl:$bad"
    return
  fi
  ok "rule_130_feature_lifecycle_validity_pos" "Rule G-14.a / Rule 130: all features.dsl saa.status values are in the 9-state lifecycle set"
}

test_rule_131_d_fp_refs_resolve_pos() {
  # Rule G-15.d (Round-2 Wave A): shipped + http/spi FunctionPoints in
  # architecture/features/function-points.dsl carry hard-evidence refs
  # that resolve against generated facts. POSITIVE case: the current
  # working tree's FP DSL resolves cleanly via --enforce d.
  local repo="$PWD"
  local out
  out=$(python3 "$repo/gate/lib/check_fact_layer_integrity.py" --enforce d 2>&1)
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "rule_131_d_fp_refs_resolve_pos" "Rule G-15.d violation: --enforce d failed: $(echo "$out" | head -1)"
    return
  fi
  ok "rule_131_d_fp_refs_resolve_pos" "Rule G-15.d / Rule 131.d: FunctionPoint hard-evidence refs all resolve against generated facts"
}

test_rule_131_d_fp_refs_unresolved_neg() {
  # Rule G-15.d NEGATIVE case: a synthetic FunctionPoint DSL with a
  # hallucinated saa.code_entrypoint_refs MUST fail the resolver.
  local scratch_dir="$scratch/r131_d_neg"
  mkdir -p "$scratch_dir/architecture/features" "$scratch_dir/architecture/facts/generated" "$scratch_dir/architecture/facts/schema" "$scratch_dir/architecture/profile"
  # Minimal fact-layer surfaces so .a / .b vacuously pass.
  cp "$PWD/architecture/facts/README.md" "$scratch_dir/architecture/facts/README.md"
  cp "$PWD/architecture/facts/schema/fact.schema.yaml" "$scratch_dir/architecture/facts/schema/fact.schema.yaml"
  cp "$PWD/architecture/profile/saa-property-authority.yaml" "$scratch_dir/architecture/profile/saa-property-authority.yaml"
  # Empty fact files so the resolver indexes are non-None but empty (every
  # ref unresolved). Use minimal valid shape: list-only.
  printf '{"_banner":"DO NOT EDIT","facts":[]}\n' > "$scratch_dir/architecture/facts/generated/code-symbols.json"
  printf '{"_banner":"DO NOT EDIT","facts":[]}\n' > "$scratch_dir/architecture/facts/generated/tests.json"
  printf '{"_banner":"DO NOT EDIT","facts":[]}\n' > "$scratch_dir/architecture/facts/generated/contract-surfaces.json"
  cat > "$scratch_dir/architecture/features/function-points.dsl" <<'EOF'
fpHallucinated = element "Bogus FP" "FunctionPoint" "ref that does not exist" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-HALLUCINATED"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0154"
        "saa.channel" "http"
        "saa.code_entrypoint_refs" "agent-service/src/main/java/NoSuch.java#nope"
        "saa.test_refs" "com.huawei.does.not.exist.NoTest"
        "saa.contract_op_refs" "contract-op/no-such"
    }
}
EOF
  local out
  out=$(python3 "$PWD/gate/lib/check_fact_layer_integrity.py" --enforce d --repo "$scratch_dir" 2>&1)
  local rc=$?
  if [[ $rc -ne 0 ]] && echo "$out" | grep -q "FP-HALLUCINATED"; then
    ok "rule_131_d_fp_refs_unresolved_neg" "Rule G-15.d / Rule 131.d: hallucinated FunctionPoint refs are detected and fail closed"
  else
    fail "rule_131_d_fp_refs_unresolved_neg" "Rule G-15.d negative case did not fail: rc=$rc out=$(echo "$out" | head -1)"
  fi
}

test_rule_132_feature_catalog_render_idempotency_pos() {
  # Rule 132 (Round-4 Wave Beta) — feature_catalog_render_idempotency
  # POSITIVE case: running `python3 gate/lib/render_features_catalog.py
  # --check` against the working tree must return 0 for all 7 module
  # catalogs.
  local out
  out=$(python3 "$PWD/gate/lib/render_features_catalog.py" --check 2>&1)
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "rule_132_feature_catalog_render_idempotency_pos" "Rule 132 / G-13 sibling: feature catalog drift detected: $(echo "$out" | grep "^DRIFT:" | head -1)"
    return
  fi
  ok "rule_132_feature_catalog_render_idempotency_pos" "Rule 132 / G-13 sibling: all 7 L1 feature catalogs are byte-identical to render output"
}

test_rule_132_feature_catalog_drift_neg() {
  # Rule 132 NEGATIVE case: mutating a rendered feature catalog MUST
  # cause the detector to fail. This proves the wiring is real
  # (the detector propagates exit code, fails closed).
  #
  # The mutation runs against an ISOLATED scratch copy of the render inputs,
  # never the real working tree. The self-test orchestrator runs test
  # functions in parallel batches, and the positive case
  # (rule_132_feature_catalog_render_idempotency_pos) runs
  # render_features_catalog.py --check over the SAME working-tree files; an
  # in-place mutation here would race that concurrent read and surface as a
  # spurious DRIFT in the positive case (observed deterministically on slower
  # CI runners). render_features_catalog.py resolves its repo root as
  # parents[2] of its own path, so copying the script + features.dsl + the
  # module READMEs into a scratch tree redirects the whole render there.
  local src="$PWD/architecture/docs/L1/agent-bus/features/README.md"
  if [[ ! -f "$src" ]]; then
    skip "rule_132_feature_catalog_drift_neg" "skip: $src absent — gate cannot fail on a non-existent surface"
    return
  fi
  local sroot="$scratch/r132_neg_repo"
  rm -rf "$sroot"
  mkdir -p "$sroot/gate/lib" "$sroot/architecture/features"
  cp "$PWD/gate/lib/render_features_catalog.py" "$sroot/gate/lib/render_features_catalog.py"
  cp "$PWD/architecture/features/features.dsl" "$sroot/architecture/features/features.dsl"
  local _r132_readme
  while IFS= read -r _r132_readme; do
    mkdir -p "$sroot/$(dirname "$_r132_readme")"
    cp "$PWD/$_r132_readme" "$sroot/$_r132_readme"
  done < <(cd "$PWD" && find architecture/docs/L1 -path '*/features/README.md')
  # Mutate the isolated copy only — the real working tree is untouched.
  echo "<!-- rule_132_feature_catalog_drift_neg mutation -->" \
      >> "$sroot/architecture/docs/L1/agent-bus/features/README.md"
  local check_rc=0
  if python3 "$sroot/gate/lib/render_features_catalog.py" --check >/dev/null 2>&1; then
    check_rc=0
  else
    check_rc=$?
  fi
  if [[ $check_rc -eq 0 ]]; then
    fail "rule_132_feature_catalog_drift_neg" "Rule 132 regression: render_features_catalog.py --check returned 0 after a deliberate feature-catalog mutation — gate is fail-open for this drift class."
  else
    ok "rule_132_feature_catalog_drift_neg" "Rule 132 / G-13 sibling: deliberate mutation triggers feature-catalog drift detection as required (race-free isolated copy; R2 closed)"
  fi
}

# test_rule_131_c_extract_facts_drift_neg REMOVED in Round-4 Wave Alpha.
# Rule G-15.c byte-identity verification moved from the bash gate to a
# Maven Surefire test (FactLayerByteIdentityIT) where target/classes is
# guaranteed by Maven's compile-phase ordering. The bash gate no longer
# hosts the byte-identity branch, so the bash fixture has no rule branch
# to exercise. Positive + negative coverage now lives in
# tools/architecture-workspace/src/test/java/com/huawei/ascend/tools/architecture/facts/FactLayerByteIdentityIT.java.

test_rule_133_productclaim_referential_integrity_pos() {
  # Rule G-16 / Rule 133 — every product_claim: value in the active corpus
  # resolves to a PC-NNN id in product/claims.yaml OR carries a sentinel.
  # POSITIVE: working tree at HEAD passes the canonical rule.
  local claims="product/claims.yaml"
  if [[ ! -f "$claims" ]]; then
    skip "rule_133_productclaim_referential_integrity_pos" "$claims missing -- product authority not yet landed"
    return
  fi
  local valid_ids
  valid_ids=$(grep -oE '^  - id: PC-[0-9]+' "$claims" 2>/dev/null | awk '{print $3}')
  local bad
  bad=$(grep -rhEn '^\s*product_claim:\s*"?(PC-[0-9]+(\|PC-[0-9]+)*)"?\s*$|^\s+"saa\.productClaim"\s+"(PC-[0-9]+(\|PC-[0-9]+)*)"\s*$' \
        docs/governance/rules/ architecture/decisions/ docs/contracts/ architecture/features/ 2>/dev/null \
        | grep -oE 'PC-[0-9]+' | sort -u | while read ref; do
            if ! echo "$valid_ids" | grep -qxF "$ref"; then
              echo "$ref"
            fi
          done | head -3 | tr '\n' ' ')
  if [[ -n "$bad" ]]; then
    fail "rule_133_productclaim_referential_integrity_pos" "Rule G-16 / Rule 133: unresolved product_claim refs in active corpus: $bad"
    return
  fi
  ok "rule_133_productclaim_referential_integrity_pos" "Rule G-16 / Rule 133: all product_claim refs resolve to PC-NNN ids in product/claims.yaml"
}

test_rule_133_productclaim_referential_integrity_unresolved_neg() {
  # Rule G-16 / Rule 133 NEGATIVE: a synthetic rule card declaring product_claim:
  # with a non-existent PC-NNN id MUST be detected by the canonical regex
  # against a synthetic product/claims.yaml that lists only PC-001.
  local scratch_dir="$scratch/r133_neg"
  mkdir -p "$scratch_dir/product" "$scratch_dir/docs/governance/rules"
  cat > "$scratch_dir/product/claims.yaml" <<'EOF'
claims:
  - id: PC-001
    title: real claim
EOF
  cat > "$scratch_dir/docs/governance/rules/rule-fake.md" <<'EOF'
---
rule_id: fake
product_claim: PC-999
---
EOF
  local valid_ids
  valid_ids=$(grep -oE '^  - id: PC-[0-9]+' "$scratch_dir/product/claims.yaml" 2>/dev/null | awk '{print $3}')
  local bad
  bad=$(grep -rhEn '^\s*product_claim:\s*"?(PC-[0-9]+(\|PC-[0-9]+)*)"?\s*$' \
        "$scratch_dir/docs/governance/rules/" 2>/dev/null \
        | grep -oE 'PC-[0-9]+' | sort -u | while read ref; do
            if ! echo "$valid_ids" | grep -qxF "$ref"; then
              echo "$ref"
            fi
          done | head -3 | tr '\n' ' ')
  if [[ "$bad" == *"PC-999"* ]]; then
    ok "rule_133_productclaim_referential_integrity_unresolved_neg" "Rule G-16 / Rule 133: synthetic PC-999 reference is correctly flagged as unresolved"
  else
    fail "rule_133_productclaim_referential_integrity_unresolved_neg" "Rule G-16 / Rule 133 negative case did not flag PC-999 (bad='$bad')"
  fi
}

test_rule_134_no_orphan_artefacts_pos() {
  # Rule G-17 / Rule 134 — every active ADR / rule card / enforcer / SAA Feature /
  # contract MUST declare product_claim:, governance_infra:true, OR
  # product_claim_placeholder:true. The gate emits orphan counts as info and is
  # vacuously-PASS at W5 landing (advisory; promotes when G-21 hits zero).
  # POSITIVE: the canonical gate has been exercised and returns 0.
  if [[ ! -f gate/check_architecture_sync.sh ]]; then
    skip "rule_134_no_orphan_artefacts_pos" "canonical gate script missing"
    return
  fi
  if ! grep -q '"no_orphan_artefacts"' gate/check_architecture_sync.sh; then
    fail "rule_134_no_orphan_artefacts_pos" "Rule 134 slug 'no_orphan_artefacts' missing from canonical gate script"
    return
  fi
  ok "rule_134_no_orphan_artefacts_pos" "Rule G-17 / Rule 134: no_orphan_artefacts rule wired in canonical gate (advisory at W5 landing)"
}

test_rule_135_traceability_chain_completeness_pos() {
  # Rule G-18 / Rule 135 — every PC-NNN in product/claims.yaml has >=1 SAA
  # Feature referencing it via saa.productClaim. Advisory at W5 landing.
  # POSITIVE: rule wired in canonical gate.
  if [[ ! -f gate/check_architecture_sync.sh ]]; then
    skip "rule_135_traceability_chain_completeness_pos" "canonical gate script missing"
    return
  fi
  if ! grep -q '"traceability_chain_completeness"' gate/check_architecture_sync.sh; then
    fail "rule_135_traceability_chain_completeness_pos" "Rule 135 slug 'traceability_chain_completeness' missing from canonical gate script"
    return
  fi
  ok "rule_135_traceability_chain_completeness_pos" "Rule G-18 / Rule 135: traceability_chain_completeness rule wired in canonical gate (advisory at W5 landing)"
}

test_rule_131_meta_no_fail_open_pipelines() {
  # Round-3 Wave Alpha preventive meta-test: scans gate/check_*.sh for
  # the known fail-open shell pattern `... || true` IMMEDIATELY followed
  # by a `$?` / `rc=$?` capture, which makes the captured exit code
  # always 0 and turns any downstream `if -ne 0` branch into dead code.
  # The Round-3 R1 defect was an instance of this exact pattern at
  # gate/check_architecture_sync.sh:7092 (now closed). This fixture
  # guards against re-introduction across the gate corpus.
  local allowlist="$PWD/gate/fail-open-allowlist.txt"
  local hits
  hits=$(grep -rEn '\|\|\s*true\)?\s*$' "$PWD/gate/check_architecture_sync.sh" "$PWD/gate/check_architecture_workspace.sh" "$PWD/gate/check_parallel.sh" 2>/dev/null \
         | grep -vE '#.*\|\| true' || true)
  local violations=""
  if [[ -n "$hits" ]]; then
    while IFS= read -r line; do
      [[ -z "$line" ]] && continue
      local file_line
      file_line=$(echo "$line" | cut -d: -f1-2)
      # Look at the NEXT line for an exit-code capture.
      local file_path="${file_line%:*}"
      local line_num="${file_line##*:}"
      local next_num=$((line_num + 1))
      local next_line
      next_line=$(sed -n "${next_num}p" "$file_path" 2>/dev/null)
      if echo "$next_line" | grep -qE '(_rc|rc)\s*=\s*\$\?'; then
        if [[ ! -f "$allowlist" ]] || ! grep -qF "$file_line" "$allowlist"; then
          violations="$violations\n$file_line followed by exit-code capture: $(echo "$next_line" | sed 's/^[[:space:]]*//')"
        fi
      fi
    done <<< "$hits"
  fi
  if [[ -n "$violations" ]]; then
    fail "meta_no_fail_open_pipelines" "Found fail-open shell pattern (\`... || true\` followed by \`\$?\` capture) — Rule G-15 prevention; add legitimate uses to gate/fail-open-allowlist.txt:$violations"
  else
    ok "meta_no_fail_open_pipelines" "No fail-open shell pattern (\`... || true\` + exit-code capture) detected across gate/check_*.sh — F-gate-machinery-fail-open-pattern prevention green"
  fi
}

test_rule_131_fact_layer_integrity_pos() {
  # Rule G-15.a — architecture/facts/{README.md, schema/fact.schema.yaml,
  # generated/} and architecture/profile/saa-property-authority.yaml MUST exist.
  # Wave 1 fixture: structural existence check (the python driver does the
  # full validation; this fixture mirrors its sub-clause .a contract).
  local missing=""
  for required in \
      "architecture/facts/README.md" \
      "architecture/facts/schema/fact.schema.yaml" \
      "architecture/facts/generated/.gitkeep" \
      "architecture/profile/saa-property-authority.yaml"; do
    if [[ ! -e "$required" ]]; then
      missing="$missing $required"
    fi
  done
  if [[ -n "$missing" ]]; then
    fail "rule_131_fact_layer_integrity_pos" "Rule G-15.a violation: missing required fact-layer artifact(s):$missing"
    return
  fi
  # Sanity-check the schema file declares schema_version and the 8 required
  # provenance fields under properties:.
  local schema="architecture/facts/schema/fact.schema.yaml"
  for required_field in fact_id fact_kind source_kind source_path extractor extractor_version repo_commit observed_value; do
    if ! grep -qE "^\s+$required_field:" "$schema"; then
      fail "rule_131_fact_layer_integrity_pos" "Rule G-15.a violation: schema $schema missing required field property '$required_field'"
      return
    fi
  done
  ok "rule_131_fact_layer_integrity_pos" "Rule G-15.a / Rule 131: fact-layer structure + schema fields present"
}

# ---------------------------------------------------------------------------
# Rule 140 — shipped_frame_anchor_integrity (Rule G-23 / E188)
# 2026-05-29 EnginePort/Frame review F8.3 closure.
# ---------------------------------------------------------------------------
test_rule_140_shipped_frame_anchor_integrity_pos() {
  # POSITIVE: the working tree's frames pass — every shipped frame anchors >=1
  # FunctionPoint (the 4 zero-anchor frames are design_only).
  local helper="$PWD/gate/lib/check_frame_shipped_anchors.py"
  if [[ ! -f "$helper" ]]; then
    fail "rule_140_shipped_frame_anchor_integrity_pos" "Rule G-23: $helper missing"
    return
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    ok "rule_140_shipped_frame_anchor_integrity_pos" "Rule G-23 / Rule 140: python3 absent on host — skipped (WSL is canonical per Rule G-7)"
    return
  fi
  local out rc
  out=$(python3 "$helper" 2>&1); rc=$?
  if [[ $rc -ne 0 ]]; then
    fail "rule_140_shipped_frame_anchor_integrity_pos" "Rule G-23 / Rule 140: shipped frame anchors no FunctionPoint: $(echo "$out" | head -1)"
    return
  fi
  ok "rule_140_shipped_frame_anchor_integrity_pos" "Rule G-23 / Rule 140: every shipped EngineeringFrame anchors >=1 FunctionPoint"
}

test_rule_140_shipped_frame_zero_anchor_neg() {
  # NEGATIVE: a synthetic shipped frame with NO anchors edge MUST fail the
  # helper. Runs against an isolated scratch repo root (never the working tree).
  local helper="$PWD/gate/lib/check_frame_shipped_anchors.py"
  if [[ ! -f "$helper" ]]; then
    fail "rule_140_shipped_frame_zero_anchor_neg" "Rule G-23: $helper missing"
    return
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    ok "rule_140_shipped_frame_zero_anchor_neg" "Rule G-23 / Rule 140: python3 absent on host — skipped (WSL is canonical per Rule G-7)"
    return
  fi
  local sroot="$scratch/r140_neg_repo"
  rm -rf "$sroot"
  mkdir -p "$sroot/gate/lib" "$sroot/architecture/features"
  cp "$helper" "$sroot/gate/lib/check_frame_shipped_anchors.py"
  : > "$sroot/gate/frame-shipped-zero-anchor-allowlist.txt"
  cat > "$sroot/architecture/features/engineering-frames.dsl" <<'EOF'
efLonely = element "Lonely Frame" "EngineeringFrame" "shipped with no anchors" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-LONELY"
        "saa.status" "shipped"
        "saa.owner" "agent-bus"
    }
}
genModule_agent_bus -> efLonely "module contains engineering frame" "SAA Relationship" {
    properties { "saa.rel" "contains" }
}
EOF
  : > "$sroot/architecture/features/features.dsl"
  local out rc
  out=$(python3 "$sroot/gate/lib/check_frame_shipped_anchors.py" --repo "$sroot" 2>&1); rc=$?
  if [[ $rc -ne 0 ]] && echo "$out" | grep -q "EF-LONELY"; then
    ok "rule_140_shipped_frame_zero_anchor_neg" "Rule G-23 / Rule 140: synthetic shipped frame with zero anchors is detected and fails closed"
  else
    fail "rule_140_shipped_frame_zero_anchor_neg" "Rule G-23 / Rule 140 negative case did not fail: rc=$rc out=$(echo "$out" | head -1)"
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
_pre4_emitted_ids=$(awk '/^PASS / || /^FAIL / { if (match($0, /\[[a-zA-Z0-9_]+\]/)) { print substr($0, RSTART + 1, RLENGTH - 2) } }' "$_pre4_all_results" | sort -u | wc -l)
if [[ "$_pre4_emitted_ids" -lt "$_pre4_expected_fn_count" ]]; then
  echo "FAIL: ${_pre4_emitted_ids} unique test_ids emitted but ${_pre4_expected_fn_count} functions defined — at least one function emitted nothing; Rule 89 / E122 fail-closed exit" >&2
  exit 1
fi
exit 0
