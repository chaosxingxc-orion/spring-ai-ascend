#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 4 — ci_no_or_true_mask. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 4 — ci_no_or_true_mask
# .github/workflows/*.yml files must not contain gate/run_* invocations
# masked with || true.
# ---------------------------------------------------------------------------
_r4_fail=0
while IFS= read -r _wf; do
  [[ -f "$_wf" ]] || continue
  if grep -qE 'gate/run_.*\|\|[[:space:]]*true' "$_wf" 2>/dev/null; then
    fail_rule "ci_no_or_true_mask" "$_wf contains gate/run_* masked with || true"
    _r4_fail=1
    break
  fi
done < <(find .github/workflows -maxdepth 1 -name '*.yml' -type f 2>/dev/null | sort || true)
if [[ $_r4_fail -eq 0 ]]; then pass_rule "ci_no_or_true_mask"; fi

# ---------------------------------------------------------------------------
