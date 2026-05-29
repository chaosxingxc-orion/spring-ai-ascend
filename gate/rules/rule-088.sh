#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 88 — serial_parallel_gate_slug_parity. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 88 — serial_parallel_gate_slug_parity (enforcer E121)
#
# Closes rc7 post-corrective review P0-2: check_parallel.sh silently skipped
# Rules 86-87 because (a) its awk exit pattern was `^# Summary$` (a comment
# header that happened to live between Rule 85 and Rule 86), and (b) its
# header regex required em-dash `—` while Rules 86-87 originally used
# double-dash `--`. Both defects compound: even fixing one would leave the
# other. Rule 88 asserts at gate time that the set of rule headers the
# canonical script defines equals the set the parallel wrapper would extract.
# ---------------------------------------------------------------------------
_r88_fail=0
_r88_canonical="gate/check_architecture_sync.sh"
_r88_parallel="gate/check_parallel.sh"
if [[ ! -f "$_r88_canonical" ]] || [[ ! -f "$_r88_parallel" ]]; then
  fail_rule "serial_parallel_gate_slug_parity" "canonical or parallel script missing -- Rule 88 / E121"
  _r88_fail=1
else
  _r88_canonical_set=$(grep -E '^# Rule [0-9]+.?[a-z]? (—|--) ' "$_r88_canonical" \
    | sed -E 's/^# Rule [0-9]+.?[a-z]? (—|--) //' \
    | awk '{print $1}' \
    | sort -u)
  _r88_parallel_set=$(awk '
    /^# Rule [0-9]+.?[a-z]? (—|--) / {
      str = substr($0, 8)
      space_idx = index(str, " ")
      rest = substr(str, space_idx + 1)
      sub(/^[^a-zA-Z0-9_]*/, "", rest)
      match(rest, /^[a-zA-Z0-9_]+/)
      print substr(rest, RSTART, RLENGTH)
    }
    /^# === END OF RULES ===$/ { exit }
  ' "$_r88_canonical" | sort -u)
  _r88_missing=$(comm -23 <(echo "$_r88_canonical_set") <(echo "$_r88_parallel_set") | grep -v '^$' || true)
  _r88_extra=$(comm -13 <(echo "$_r88_canonical_set") <(echo "$_r88_parallel_set") | grep -v '^$' || true)
  if [[ -n "$_r88_missing" ]]; then
    fail_rule "serial_parallel_gate_slug_parity" "parallel wrapper would skip rule(s): $(echo "$_r88_missing" | tr '\n' ' ')-- Rule 88 / E121 (serial-canonical defines rules the parallel awk extraction misses)"
    _r88_fail=1
  fi
  if [[ -n "$_r88_extra" ]]; then
    fail_rule "serial_parallel_gate_slug_parity" "parallel awk would extract rule(s) not defined as canonical pass_rule blocks: $(echo "$_r88_extra" | tr '\n' ' ')-- Rule 88 / E121"
    _r88_fail=1
  fi
  # Sub-check: canonical separator consistency — every rule header MUST use em-dash `—`.
  _r88_bad_sep=$(grep -nE '^# Rule [0-9]+.?[a-z]? -- ' "$_r88_canonical" | head -3 || true)
  if [[ -n "$_r88_bad_sep" ]]; then
    fail_rule "serial_parallel_gate_slug_parity" "rule header(s) use double-dash separator instead of em-dash: $(echo "$_r88_bad_sep" | tr '\n' '|') -- Rule 88 / E121 (separator consistency)"
    _r88_fail=1
  fi
  # Sub-check: parallel wrapper MUST declare END marker awk-extraction terminator
  if ! grep -qE '^# === END OF RULES ===$' "$_r88_canonical"; then
    fail_rule "serial_parallel_gate_slug_parity" "$_r88_canonical missing '# === END OF RULES ===' terminator marker that check_parallel.sh awk uses to bound rule extraction -- Rule 88 / E121"
    _r88_fail=1
  fi
fi
if [[ $_r88_fail -eq 0 ]]; then pass_rule "serial_parallel_gate_slug_parity"; fi

