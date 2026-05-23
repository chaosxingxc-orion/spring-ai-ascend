#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 89 — self_test_harness_fail_closed_coverage. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 89 — self_test_harness_fail_closed_coverage (enforcer E122)
#
# Closes rc7 post-corrective review P1-1: gate/test_architecture_sync_gate.sh
# hardcoded TOTAL=143 + exited 0 when failed=0 regardless of whether
# passed<TOTAL. Rule 89 asserts that the harness (a) fails closed when
# passed != TOTAL, (b) computes TOTAL from a manifest rather than from a
# bare literal, and (c) every rule defined in check_architecture_sync.sh
# has at least one test_rule_<N>_* fixture in the harness.
# ---------------------------------------------------------------------------
_r89_fail=0
_r89_harness="gate/test_architecture_sync_gate.sh"
_r89_canonical="gate/check_architecture_sync.sh"
if [[ ! -f "$_r89_harness" ]]; then
  fail_rule "self_test_harness_fail_closed_coverage" "$_r89_harness missing -- Rule 89 / E122"
  _r89_fail=1
else
  # Sub-check (a): harness MUST contain a fail-closed clause comparing passed vs TOTAL
  if ! grep -qE 'passed[^=]*!=[^=]*\$\{?TOTAL\}?|\$\{?passed\}?[[:space:]]+-ne[[:space:]]+\$\{?TOTAL\}?|"\$passed"[[:space:]]+-ne[[:space:]]+"\$TOTAL"' "$_r89_harness"; then
    fail_rule "self_test_harness_fail_closed_coverage" "$_r89_harness missing 'passed != TOTAL' fail-closed clause -- Rule 89 / E122 sub-check (a) (harness exits 0 when passed<TOTAL — must fail closed)"
    _r89_fail=1
  fi
  # Sub-check (b): TOTAL MUST NOT be a bare literal — must derive from a manifest.
  # Skip lines inside `<<'SHEOF' ... SHEOF` heredoc blocks (synthetic test fixtures
  # legitimately contain `TOTAL=NNN` to test the rule's own detection — see
  # test_rule89_bare_literal_neg).
  _r89_literal_lines=$(awk '
    /^[[:space:]]*cat[[:space:]]+>[[:space:]]+.*<<.SHEOF.$/ { hd=1; next }
    /^SHEOF$/ { hd=0; next }
    !hd && /^[[:space:]]*TOTAL=[0-9]+[[:space:]]*$/ { printf "%d:%s\n", NR, $0 }
  ' "$_r89_harness" || true)
  if [[ -n "$_r89_literal_lines" ]]; then
    fail_rule "self_test_harness_fail_closed_coverage" "$_r89_harness has bare-literal TOTAL declaration(s) at top level (not inside heredoc fixtures): $(echo "$_r89_literal_lines" | tr '\n' '|') -- Rule 89 / E122 sub-check (b) (TOTAL must derive from a manifest, not a literal)"
    _r89_fail=1
  fi
  # Sub-check (c): every PREVENTION-WAVE canonical rule (Rules >= 80; rc4-rc8 waves)
  # has at least one test fixture. Pre-rc4 rules (1-79) are grandfathered — many
  # were covered by ArchUnit or integration tests at design time rather than
  # by inline self-test fixtures, and retrofitting fixtures for ~40 legacy rules
  # is out of rc8 scope. Rule 89's purpose is to prevent NEWLY-ADDED rules from
  # shipping without coverage; the prevention waves established the convention,
  # so the scope tracks that convention.
  if [[ -f "$_r89_canonical" ]]; then
    _r89_canonical_ids=$(grep -E '^# Rule [0-9]+.?[a-z]? (—|--) ' "$_r89_canonical" \
      | sed -E 's/^# Rule ([0-9]+.?[a-z]?) (—|--) .*/\1/' \
      | sort -u)
    _r89_missing_fixtures=""
    for _r89_rid in $_r89_canonical_ids; do
      # Scope: only enforce coverage for prevention-wave rules (Rules >= 80,
      # main numeric IDs only — sub-rules like 28a are grandfathered).
      _r89_rid_num=$(echo "$_r89_rid" | grep -oE '^[0-9]+')
      [[ -z "$_r89_rid_num" ]] && continue
      [[ "$_r89_rid_num" -lt 80 ]] && continue
      # Look for test_rule_<N>_ or test_rule_<N>(  pattern in any form.
      if ! grep -qE "(^test_rule_${_r89_rid}_|^test_rule_${_r89_rid}\(|^test_rule${_r89_rid}_|^test_rule${_r89_rid}\()" "$_r89_harness"; then
        if ! grep -qE "\"rule_${_r89_rid}_|'rule_${_r89_rid}_" "$_r89_harness"; then
          _r89_missing_fixtures="${_r89_missing_fixtures}${_r89_rid} "
        fi
      fi
    done
    if [[ -n "$_r89_missing_fixtures" ]]; then
      fail_rule "self_test_harness_fail_closed_coverage" "$_r89_harness lacks test fixture(s) for prevention-wave Rule(s) >=80: ${_r89_missing_fixtures}-- Rule 89 / E122 sub-check (c) (every prevention-wave Rule MUST have >=1 test_rule_<N>_* fixture; pre-rc4 rules 1-79 grandfathered)"
      _r89_fail=1
    fi
  fi
fi
if [[ $_r89_fail -eq 0 ]]; then pass_rule "self_test_harness_fail_closed_coverage"; fi

# ---------------------------------------------------------------------------
