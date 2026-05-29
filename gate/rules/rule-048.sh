#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 48 — no_thread_sleep_in_business_code. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 48 — no_thread_sleep_in_business_code (enforcer E67, Rule 38 / P-H)
#
# No production class under agent-service/src/main/java/** or
# agent-service/src/main/java/** may invoke Thread.sleep(...) or
# TimeUnit.<unit>.sleep(...). Test code is excluded.
# ---------------------------------------------------------------------------
_r48_fail=0
# Post-Phase-C (ADR-0078): both platform and runtime sub-packages are scanned
# under the single agent-service module. Pre-Phase-C this iterated over the
# two separate Maven modules.
for _r48_root in agent-service/src/main/java; do
  [[ ! -d "$_r48_root" ]] && continue
  _r48_hits="$(grep -rEn 'Thread\.sleep[[:space:]]*\(|TimeUnit\.[A-Z_]+\.sleep[[:space:]]*\(' "$_r48_root" 2>/dev/null || true)"
  if [[ -n "$_r48_hits" ]]; then
    while IFS= read -r _line; do
      [[ -z "$_line" ]] && continue
      fail_rule "no_thread_sleep_in_business_code" "$_line — physical sleep is forbidden (Chronos Hydration Rule 38); use SuspendSignal + bus Tick Engine"
      _r48_fail=1
    done <<< "$_r48_hits"
  fi
done
if [[ $_r48_fail -eq 0 ]]; then pass_rule "no_thread_sleep_in_business_code"; fi

# ---------------------------------------------------------------------------
