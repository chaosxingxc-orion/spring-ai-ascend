#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 116 — parallel_linux_scripts_mandate. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 116 — parallel_linux_scripts_mandate (enforcer E164)
#
# Operationalises Rule G-10. Every gate script under gate/*.sh (top-level,
# excluding the parallel orchestrator and canonical source) MUST either be
# listed in gate/serial-only-paths.txt (one-shot / helper / diagnostic /
# generator exemption list) OR carry a parallel-execution mechanism
# (xargs -P, GNU parallel, or background jobs with explicit wait).
#
# Vacuously passes if gate/serial-only-paths.txt is absent (initial-deployment
# fallback). Companion list: gate/serial-only-paths.txt.
# ---------------------------------------------------------------------------
_r116_fail=0
_r116_exempt_file='gate/serial-only-paths.txt'
if [[ ! -f "$_r116_exempt_file" ]]; then
  pass_rule "parallel_linux_scripts_mandate"
else
  _r116_exempt=$(grep -vE '^[[:space:]]*#|^[[:space:]]*$' "$_r116_exempt_file" 2>/dev/null | sort -u)
  _r116_drift=""
  for _r116_sh in gate/*.sh; do
    [[ -f "$_r116_sh" ]] || continue
    [[ "$_r116_sh" == "gate/check_parallel.sh" || "$_r116_sh" == "gate/check_architecture_sync.sh" ]] && continue
    if printf '%s\n' "$_r116_exempt" | grep -Fxq "$_r116_sh"; then
      continue
    fi
    # Tighter regex per rc21 PR review: drop trailing-`wait` alternative
    # (matched any line ending in word `wait`, e.g. `# we wait` — false-pass).
    # Accepted parallel mechanisms:
    #   1. `xargs -P<N>` (any -P flag, with or without space before N)
    #   2. `parallel` command at start of line
    #   3. `wait` builtin at start of line (after `&`-backgrounded jobs)
    #   4. `&` line-end (background job indicator, must be paired with wait)
    if grep -qE 'xargs[[:space:]]+([^|]*[[:space:]])?-P[0-9[:space:]]|^[[:space:]]*parallel([[:space:]]|$)|^[[:space:]]*wait([[:space:]]|$|;)|&[[:space:]]*$' "$_r116_sh" 2>/dev/null; then
      continue
    fi
    _r116_drift+="$_r116_sh; "
    _r116_fail=1
  done
  if [[ $_r116_fail -eq 0 ]]; then
    pass_rule "parallel_linux_scripts_mandate"
  else
    fail_rule "parallel_linux_scripts_mandate" "Gate scripts lacking parallel-execution mechanism (xargs -P / parallel / wait) AND not exempted in gate/serial-only-paths.txt: ${_r116_drift}-- Rule G-10 / E164"
  fi
fi

# ---------------------------------------------------------------------------
