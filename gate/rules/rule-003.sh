#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 3 — eol_policy. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 3 — eol_policy
# All *.sh files in gate/ must have LF line endings (not CRLF).
# ---------------------------------------------------------------------------
_r3_fail=0
while IFS= read -r _shf; do
  [[ -z "$_shf" ]] && continue
  [[ ! -f "$_shf" ]] && continue
  if grep -qU $'\r' "$_shf" 2>/dev/null; then
    fail_rule "eol_policy" "$_shf contains CRLF; must be LF"
    _r3_fail=1
    break
  fi
done < <(find gate -maxdepth 1 -name '*.sh' -type f 2>/dev/null | sort || true)
if [[ $_r3_fail -eq 0 ]]; then pass_rule "eol_policy"; fi

# ---------------------------------------------------------------------------
