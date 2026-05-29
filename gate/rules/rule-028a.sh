#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 28a — tenant_column_present. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 28a — tenant_column_present (Rule 28 sub-check, ADR-0059, enforcer E15)
# Every CREATE TABLE under any */src/main/resources/db/migration/*.sql that
# isn't a control/system table must declare a tenant_id column.
# Exemptions: health_check (singleton system row).
# ---------------------------------------------------------------------------
_r28a_fail=0
_python_bin=$(command -v python3 || command -v python || echo "")
while IFS= read -r _mig; do
  [[ -z "$_mig" ]] && continue
  if [[ -z "$_python_bin" ]]; then
    # No Python available — fall back to a crude shell heuristic: every
    # CREATE TABLE block must contain 'tenant_id' somewhere before its
    # terminating ';'. We use awk for the statement-level split.
    if awk '
      BEGIN { RS=";"; FS=""; IGNORECASE=1 }
      /CREATE[[:space:]]+TABLE/ {
        if ($0 ~ /health_check/) next
        if ($0 !~ /tenant_id/) { print "FAIL: " FILENAME; exit 1 }
      }
    ' "$_mig"; then :; else _r28a_fail=1; fi
    continue
  fi
  "$_python_bin" - "$_mig" <<'PY' || _r28a_fail=1
import re, sys
path = sys.argv[1]
text = open(path, encoding='utf-8').read()
# tokenize by semicolons; for each CREATE TABLE, inspect the body
for stmt in text.split(';'):
    m = re.search(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-zA-Z_][a-zA-Z0-9_]*)', stmt, re.IGNORECASE)
    if not m: continue
    name = m.group(1)
    if name in ('health_check',):
        continue
    if not re.search(r'\btenant_id\b', stmt, re.IGNORECASE):
        print(f"FAIL: {path}: table '{name}' lacks tenant_id column")
        sys.exit(1)
sys.exit(0)
PY
  if [[ $? -ne 0 ]]; then
    fail_rule "tenant_column_present" "$_mig declares a tenant-scoped table without a tenant_id column. Per Rule 28a / enforcer E15."
    _r28a_fail=1
  fi
done < <(printf '%s\n' "${_SCAN_MIGRATION_SQL:-$(find . -path '*/src/main/resources/db/migration/*.sql' -not -path './target/*' 2>/dev/null | sort || true)}" | grep -E '\.sql$' || true)
if [[ $_r28a_fail -eq 0 ]]; then pass_rule "tenant_column_present"; fi

# ---------------------------------------------------------------------------
