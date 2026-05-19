#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 76 — no_split_spi_packages. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 76 — no_split_spi_packages (enforcer E109)
#
# A given Java spi package MUST be declared by exactly one Maven module's
# module-metadata.yaml#spi_packages. Two modules co-declaring the same
# package is a split-package — Maven and JPMS cannot reason about ownership.
# Catches the 2026-05-18 root cause (orchestration.spi historical double-
# declaration by agent-runtime-core AND agent-execution-engine — both modules
# resolved by rc13 ADR-0088 dissolution).
# ---------------------------------------------------------------------------
_r76_fail=0
_r76_tmp="$(mktemp 2>/dev/null || echo /tmp/r76.$$)"
: > "$_r76_tmp"
while IFS= read -r _r76_meta; do
  [[ -z "$_r76_meta" ]] && continue
  _r76_mod="$(grep -E '^[[:space:]]*module:' "$_r76_meta" 2>/dev/null | head -1 | sed -E 's/^[[:space:]]*module:[[:space:]]*([A-Za-z0-9_-]+).*/\1/')"
  _r76_pkgs=$(awk '/^spi_packages:/{flag=1; next} /^[a-zA-Z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]+/{gsub(/^[[:space:]]*-[[:space:]]+/,""); gsub(/["\047]/,""); gsub(/[[:space:]#].*$/,""); print}' "$_r76_meta")
  while IFS= read -r _r76_pkg; do
    [[ -z "$_r76_pkg" ]] && continue
    printf '%s|%s\n' "$_r76_pkg" "$_r76_mod" >> "$_r76_tmp"
  done <<< "$_r76_pkgs"
done <<< "${_SCAN_MODULE_METADATA:-$(find . -maxdepth 3 -name module-metadata.yaml -not -path './target/*' -not -path './.claude/*' 2>/dev/null)}"
_r76_dupes=$(sort "$_r76_tmp" | awk -F'|' '{ owners[$1]=owners[$1]" "$2; counts[$1]++ } END { for (k in counts) if (counts[k] > 1) print k "|" owners[k] }')
rm -f "$_r76_tmp"
if [[ -n "$_r76_dupes" ]]; then
  while IFS= read -r _r76_d; do
    fail_rule "no_split_spi_packages" "spi package '${_r76_d%%|*}' declared by multiple modules:${_r76_d#*|} (Rule 76 / E109)"
    _r76_fail=1
  done <<< "$_r76_dupes"
fi
if [[ $_r76_fail -eq 0 ]]; then pass_rule "no_split_spi_packages"; fi

# ---------------------------------------------------------------------------
