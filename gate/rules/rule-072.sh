#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 72 — rule_duration_regression_check. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 72 — rule_duration_regression_check (enforcer E102)
# Vacuously passes until gate/log/benchmarks/median.json has >= 5 entries
# (bootstrap window per ADR-0077). After bootstrap: fail if any rule's
# current duration > 2x baseline median AND > 200ms absolute.
# ---------------------------------------------------------------------------
_r72_fail=0
_r72_median='gate/log/benchmarks/median.json'
_r72_current="${GATE_LOG_DIR:-gate/log/latest}/per-rule.ndjson"
if [[ ! -s "$_r72_median" ]] || ! command -v jq >/dev/null 2>&1; then
  pass_rule "rule_duration_regression_check"
elif [[ ! -f "$_r72_current" ]]; then
  pass_rule "rule_duration_regression_check"
else
  _r72_baseline_count="$(jq 'length' "$_r72_median" 2>/dev/null || echo 0)"
  if [[ "${_r72_baseline_count:-0}" -lt 5 ]]; then
    pass_rule "rule_duration_regression_check"
  else
    _r72_alerts="$(jq -r --slurpfile baseline "$_r72_median" '
      . as $row | $baseline[0][$row.rule_slug] as $median |
      if ($median != null and $row.duration_ms > $median * 2 and $row.duration_ms > 200)
      then "\($row.rule_slug): \($row.duration_ms)ms (median \($median)ms)"
      else empty end
    ' "$_r72_current" 2>/dev/null || true)"
    if [[ -n "$_r72_alerts" ]]; then
      fail_rule "rule_duration_regression_check" "$_r72_alerts"
      _r72_fail=1
    else
      pass_rule "rule_duration_regression_check"
    fi
  fi
fi

# ===========================================================================
# SPI metadata integrity wave (2026-05-18)
# Authority: docs/governance/rules/rule-{75..78}.md
# Rules 75-78 with enforcer rows E108-E111. Prevents the SPI declaration vs
# physical layout drift surfaced by the 2026-05-18 SPI integrity audit
# (T2.B2 extraction left engine.spi empty + orchestration.spi double-claimed
# across two Maven modules + dfx yaml omitting/mis-nesting spi_packages).
# ===========================================================================

