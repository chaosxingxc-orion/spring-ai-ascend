#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 66 — spi_package_exhaustiveness. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 66 — spi_package_exhaustiveness (enforcer E96, G3 prevention)
#
# For each <module>/module-metadata.yaml, every src/main/java/.../spi
# directory MUST appear in spi_packages. Catches drift where a developer
# adds a new SPI package (e.g. runtime.s2c.spi) but forgets to declare it
# in the metadata.
# ---------------------------------------------------------------------------
_r66_fail=0
while IFS= read -r _r66_meta; do
  [[ -z "$_r66_meta" ]] && continue
  _r66_mod_dir="$(dirname "$_r66_meta")"
  _r66_src="${_r66_mod_dir}/src/main/java"
  [[ -d "$_r66_src" ]] || continue
  _r66_declared=$(awk '/^spi_packages:/{flag=1; next} /^[a-zA-Z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]+/{gsub(/^[[:space:]]*-[[:space:]]+/,""); gsub(/[[:space:]#].*$/,""); print}' "$_r66_meta" | sort -u)
  while IFS= read -r _r66_dir; do
    [[ -z "$_r66_dir" ]] && continue
    _r66_pkg="${_r66_dir#${_r66_src}/}"
    _r66_pkg="${_r66_pkg//\//.}"
    if ! echo "$_r66_declared" | grep -qxF "$_r66_pkg"; then
      fail_rule "spi_package_exhaustiveness" "$_r66_dir exists on disk but package '$_r66_pkg' is not declared in $_r66_meta spi_packages (G3 prevention)"
      _r66_fail=1
    fi
  done <<< "$(find "$_r66_src" -type d -name spi 2>/dev/null)"
done <<< "${_SCAN_MODULE_METADATA:-$(find . -maxdepth 3 -name module-metadata.yaml -not -path './target/*' -not -path './.claude/*' 2>/dev/null)}"
if [[ $_r66_fail -eq 0 ]]; then pass_rule "spi_package_exhaustiveness"; fi

# ===========================================================================
# CLAUDE.md token-optimization wave -- PR1 (2026-05-17)
# Authority: docs/governance/rules/rule-{67..71}.md
#            + D:\.claude\plans\tokens-token-buzzing-sprout.md
# Goal: shrink always-loaded governance set from ~99K -> ~10.6K tokens.
# Rules 67-71 with enforcer rows E97-E101 and 10 self-tests (2 per rule).
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 67 -- claude_md_kernel_size_bounded (enforcer E97)
#
# For each "#### Rule NN" heading in CLAUDE.md, count the lines between the
# heading and the next "---" separator (inclusive of the heading). Look up
# kernel_cap from docs/governance/rules/rule-NN.md front-matter; fail if
# exceeded. If the card does not exist yet, this rule is SKIPPED for that
# rule (the missing card is caught by Rule 69 instead).
#
# Cap discipline (per CLAUDE.md token-optimization wave):
#   daily principles (Rules 1-6, 9, 10): kernel_cap: 12
#   architectural + ironclad (Rules 20-48): kernel_cap: 6
# ---------------------------------------------------------------------------
_r67_fail=0
_r67_claude='CLAUDE.md'
_r67_cards_dir='docs/governance/rules'
if [[ ! -f "$_r67_claude" ]]; then
  fail_rule "claude_md_kernel_size_bounded" "$_r67_claude missing"
  _r67_fail=1
elif [[ ! -d "$_r67_cards_dir" ]]; then
  # No cards yet -- rule is vacuously true during initial PR1 landing.
  pass_rule "claude_md_kernel_size_bounded"
else
  _r67_violations=""
  # Extract every Rule NN heading line number from CLAUDE.md.
  _r67_rule_lines=$(grep -nE '^#### Rule [0-9]+' "$_r67_claude" | sort -t: -k1,1n)
  while IFS= read -r _r67_entry; do
    [[ -z "$_r67_entry" ]] && continue
    _r67_ln="${_r67_entry%%:*}"
    _r67_rest="${_r67_entry#*:}"
    _r67_num=$(printf '%s\n' "$_r67_rest" | sed -nE 's/^#### Rule ([0-9]+).*/\1/p')
    [[ -z "$_r67_num" ]] && continue
    _r67_card_padded=$(printf 'rule-%02d.md' "$_r67_num")
    _r67_card="${_r67_cards_dir}/${_r67_card_padded}"
    if [[ ! -f "$_r67_card" ]]; then
      # No card -- skip (Rule 69 will catch it).
      continue
    fi
    _r67_cap=$(awk '/^kernel_cap:[[:space:]]*[0-9]+/{print $2; exit}' "$_r67_card")
    [[ -z "$_r67_cap" ]] && continue
    # Count lines from heading until next '---' separator (exclusive of separator).
    _r67_count=$(awk -v start="$_r67_ln" '
      NR < start { next }
      NR == start { count = 1; next }
      /^---$/ { exit }
      { count++ }
      END { print count + 0 }
    ' "$_r67_claude")
    if [[ "$_r67_count" -gt "$_r67_cap" ]]; then
      _r67_violations+="Rule $_r67_num: $_r67_count lines > cap $_r67_cap; "
      _r67_fail=1
    fi
  done <<< "$_r67_rule_lines"
  if [[ $_r67_fail -eq 0 ]]; then
    pass_rule "claude_md_kernel_size_bounded"
  else
    fail_rule "claude_md_kernel_size_bounded" "$_r67_violations"
  fi
fi

# ---------------------------------------------------------------------------
# Rule 68 -- claude_md_kernel_matches_card (enforcer E98)
#
# For every docs/governance/rules/rule-NN.md card, extract the kernel: scalar
# from the YAML front-matter, normalise whitespace, and assert the same text
# appears verbatim in the body of "#### Rule NN" in CLAUDE.md. Fails on drift.
# If no cards exist (initial PR1 landing), the rule is vacuously true.
# ---------------------------------------------------------------------------
_r68_fail=0
_r68_claude='CLAUDE.md'
_r68_cards_dir='docs/governance/rules'
if [[ ! -f "$_r68_claude" ]]; then
  fail_rule "claude_md_kernel_matches_card" "$_r68_claude missing"
  _r68_fail=1
elif [[ ! -d "$_r68_cards_dir" ]]; then
  pass_rule "claude_md_kernel_matches_card"
else
  _r68_drift=""
  while IFS= read -r _r68_card; do
    [[ -z "$_r68_card" ]] && continue
    _r68_base=$(basename "$_r68_card" .md)
    _r68_num=$(printf '%s\n' "$_r68_base" | sed -nE 's/^rule-0*([0-9]+)$/\1/p')
    [[ -z "$_r68_num" ]] && continue
    # Extract the kernel: scalar from card front-matter (supports both '|' literal
    # block style and inline scalar). Stop at the next top-level key or '---'.
    _r68_kernel=$(awk '
      /^kernel:[[:space:]]*\|/ { flag=1; next }
      /^kernel:[[:space:]]/ { line=$0; sub(/^kernel:[[:space:]]*/, "", line); print line; exit }
      flag && /^[a-zA-Z_][a-zA-Z_0-9]*:/ { flag=0; exit }
      flag && /^---$/ { flag=0; exit }
      flag { sub(/^  /, ""); print }
    ' "$_r68_card" | tr -s ' \t' ' ' | tr -d '\r' | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//' | tr '\n' ' ' | tr -s ' ' | sed -E 's/^ //; s/ $//')
    [[ -z "$_r68_kernel" ]] && continue
    # Extract the body of "#### Rule NN" from CLAUDE.md: lines until the first
    # blank-line + "Enforced" or until "---" or until the next heading.
    _r68_body=$(awk -v n="$_r68_num" '
      $0 ~ "^#### Rule " n "[[:space:]]" || $0 ~ "^#### Rule " n "$" { flag=1; next }
      flag && /^---$/ { exit }
      flag && /^#### / { exit }
      flag && /^Enforced by/ { exit }
      flag && NF { print }
    ' "$_r68_claude" | tr -s ' \t' ' ' | tr -d '\r' | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//' | tr '\n' ' ' | tr -s ' ' | sed -E 's/^ //; s/ $//')
    if [[ -z "$_r68_body" ]]; then
      _r68_drift+="Rule $_r68_num: card exists but no body in CLAUDE.md; "
      _r68_fail=1
    elif [[ "$_r68_kernel" != "$_r68_body" ]]; then
      _r68_drift+="Rule $_r68_num drift; "
      _r68_fail=1
    fi
  done < <(find "$_r68_cards_dir" -maxdepth 1 -name 'rule-*.md' -type f 2>/dev/null | sort)
  if [[ $_r68_fail -eq 0 ]]; then
    pass_rule "claude_md_kernel_matches_card"
  else
    fail_rule "claude_md_kernel_matches_card" "$_r68_drift"
  fi
fi

# ---------------------------------------------------------------------------
# Rule 69 -- every_active_rule_has_card (enforcer E99)
#
# Every "#### Rule NN" heading in CLAUDE.md MUST have a sibling
# docs/governance/rules/rule-NN.md (zero-padded). Every card MUST either
# (a) appear as a heading in CLAUDE.md, or
# (b) appear as a "Rule NN" reference in docs/CLAUDE-deferred.md.
# Orphan cards that satisfy neither are a fail.
#
# Initial PR1 mode (loose): if docs/governance/rules/ does not exist yet,
# the rule is vacuously true so the budget-gate and other rules can land first.
# ---------------------------------------------------------------------------
_r69_fail=0
_r69_claude='CLAUDE.md'
_r69_deferred='docs/CLAUDE-deferred.md'
_r69_cards_dir='docs/governance/rules'
if [[ ! -d "$_r69_cards_dir" ]]; then
  pass_rule "every_active_rule_has_card"
else
  # Extract active rule numbers from CLAUDE.md.
  _r69_active=$(grep -oE '^#### Rule [0-9]+' "$_r69_claude" 2>/dev/null | grep -oE '[0-9]+' | sort -un)
  # Extract card numbers from filenames.
  _r69_cards=$(find "$_r69_cards_dir" -maxdepth 1 -name 'rule-*.md' -type f 2>/dev/null \
                 | sed -E 's|.*/rule-0*([0-9]+)[a-z]?\.md|\1|' | sort -un)
  # Missing cards: active rule with no card.
  _r69_missing=""
  while IFS= read -r _n; do
    [[ -z "$_n" ]] && continue
    if ! echo "$_r69_cards" | grep -qxF "$_n"; then
      _r69_missing+="$_n "
    fi
  done <<< "$_r69_active"
  if [[ -n "$_r69_missing" ]]; then
    fail_rule "every_active_rule_has_card" "active rules with no card: $_r69_missing"
    _r69_fail=1
  fi
  # Orphan cards: card exists but rule is neither active nor deferred.
  _r69_orphans=""
  while IFS= read -r _n; do
    [[ -z "$_n" ]] && continue
    if echo "$_r69_active" | grep -qxF "$_n"; then
      continue
    fi
    # Check deferred file mentions "Rule NN" (allow optional sub-clause suffix like 29.c).
    if [[ -f "$_r69_deferred" ]] && grep -qE "Rule[[:space:]]+${_n}([.][a-z])?\b" "$_r69_deferred"; then
      continue
    fi
    _r69_orphans+="$_n "
  done <<< "$_r69_cards"
  if [[ -n "$_r69_orphans" ]]; then
    fail_rule "every_active_rule_has_card" "orphan cards (no active or deferred reference): $_r69_orphans"
    _r69_fail=1
  fi
  if [[ $_r69_fail -eq 0 ]]; then
    pass_rule "every_active_rule_has_card"
  fi
fi

# ---------------------------------------------------------------------------
# Rule 70 -- always_loaded_budget_enforced (enforcer E100)
#
# Invokes gate/measure_always_loaded_tokens.sh which walks every file listed
# in gate/always-loaded-budget.txt and fails if any file exceeds its ceiling.
# This is the primary defence against CLAUDE.md regressing back to its
# pre-shrink size after PR1 lands.
# ---------------------------------------------------------------------------
_r70_fail=0
_r70_script='gate/measure_always_loaded_tokens.sh'
if [[ ! -f "$_r70_script" ]]; then
  fail_rule "always_loaded_budget_enforced" "$_r70_script missing"
  _r70_fail=1
else
  _r70_out=$(bash "$_r70_script" 2>&1)
  _r70_rc=$?
  if [[ $_r70_rc -ne 0 ]]; then
    # Extract just the OVER / MISSING lines for the error message.
    _r70_violations=$(printf '%s\n' "$_r70_out" | grep -E '(OVER|MISSING)' | tr '\n' ';' | sed 's/;$//')
    fail_rule "always_loaded_budget_enforced" "${_r70_violations:-budget script exited $_r70_rc}"
    _r70_fail=1
  fi
fi
if [[ $_r70_fail -eq 0 ]]; then pass_rule "always_loaded_budget_enforced"; fi

# ---------------------------------------------------------------------------
# Rule 71 -- deferred_doc_not_in_always_loaded (enforcer E101)
#
# Once docs/CLAUDE-deferred.md is demoted from the always-loaded set, the
# demote must stay durable. Fails if:
#   (a) CLAUDE.md contains a literal '@docs/CLAUDE-deferred.md' include directive
#       (the Claude Code auto-load syntax), OR
#   (b) docs/governance/SESSION-START-CONTEXT.md table row for CLAUDE-deferred.md
#       contains an ALWAYS-LOAD / ALWAYS marker.
# Plain prose pointers ("see docs/CLAUDE-deferred.md") are fine.
# ---------------------------------------------------------------------------
_r71_fail=0
_r71_claude='CLAUDE.md'
_r71_sscontext='docs/governance/SESSION-START-CONTEXT.md'
if [[ -f "$_r71_claude" ]] && grep -qE '^[[:space:]]*@docs/CLAUDE-deferred\.md' "$_r71_claude" 2>/dev/null; then
  fail_rule "deferred_doc_not_in_always_loaded" "$_r71_claude contains @docs/CLAUDE-deferred.md auto-load -- must be on-demand"
  _r71_fail=1
fi
if [[ -f "$_r71_sscontext" ]]; then
  # Look at lines mentioning CLAUDE-deferred.md and reject ones marked ALWAYS / ALWAYS-LOAD.
  _r71_bad=$(grep -E 'CLAUDE-deferred\.md' "$_r71_sscontext" 2>/dev/null | grep -E '(\bALWAYS\b|ALWAYS-LOAD)' || true)
  if [[ -n "$_r71_bad" ]]; then
    fail_rule "deferred_doc_not_in_always_loaded" "$_r71_sscontext marks CLAUDE-deferred.md as ALWAYS-LOAD"
    _r71_fail=1
  fi
fi
if [[ $_r71_fail -eq 0 ]]; then pass_rule "deferred_doc_not_in_always_loaded"; fi

# ===========================================================================
# Gate-script efficiency wave PR-E1 (2026-05-17)
# Authority: D:/.claude/plans/tokens-token-buzzing-sprout.md + docs/governance/rules/rule-73.md
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 73 -- gate_config_well_formed (enforcer E103)
#
# Sources gate/lib/load_config.sh and runs the validator. Fails if:
#   - gate/config.yaml or gate/config.schema.yaml missing
#   - YAML parser detected malformed input (__ERROR__ sentinel)
#   - Required top-level key missing
#   - Type / range / enum violation on any validated leaf
#
# The validator implementation lives in gate/lib/load_config.sh
# (gate_validate_config_against_schema). This rule is the gate-side wrapper.
# ---------------------------------------------------------------------------
_r73_fail=0
_r73_loader='gate/lib/load_config.sh'
_r73_config='gate/config.yaml'
_r73_schema='gate/config.schema.yaml'
if [[ ! -f "$_r73_loader" ]]; then
  fail_rule "gate_config_well_formed" "$_r73_loader missing -- cannot validate gate/config.yaml"
  _r73_fail=1
elif [[ ! -f "$_r73_config" ]]; then
  fail_rule "gate_config_well_formed" "$_r73_config missing"
  _r73_fail=1
elif [[ ! -f "$_r73_schema" ]]; then
  fail_rule "gate_config_well_formed" "$_r73_schema missing"
  _r73_fail=1
else
  # Run validation in a subshell so we don't pollute the main shell with
  # the loader's exported GATE_* variables. Capture VALID + ERRORS via stdout.
  _r73_result=$(bash -c '
    source '"'$_r73_loader'"'
    gate_load_config >/dev/null 2>&1
    gate_validate_config_against_schema >/dev/null 2>&1
    printf "%s\n" "${GATE_CONFIG_VALID:-false}"
    printf "%s" "${GATE_CONFIG_ERRORS:-}"
  ')
  _r73_valid=$(printf '%s\n' "$_r73_result" | head -1)
  _r73_errors=$(printf '%s\n' "$_r73_result" | tail -n +2)
  if [[ "$_r73_valid" == "true" ]]; then
    pass_rule "gate_config_well_formed"
  else
    fail_rule "gate_config_well_formed" "$(printf '%s' "$_r73_errors" | tr '\n' ';')"
    _r73_fail=1
  fi
fi

# ===========================================================================
# Linux-first dev environment policy (PR-E7, 2026-05-18)
# Authority: docs/governance/rules/rule-74.md + docs/governance/dev-environment.md
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 74 -- linux_first_dev_doc_present (enforcer E104)
#
# docs/governance/dev-environment.md MUST exist and MUST mention all three
# of: WSL2 (preferred), WSL1 (fallback), and Linux (native). The doc is the
# canonical guide an engineer reads when first joining the project; its
# absence (or absence of the Linux-first recommendation) signals the policy
# has been silently weakened.
# ---------------------------------------------------------------------------
_r74_fail=0
_r74_doc='docs/governance/dev-environment.md'
if [[ ! -f "$_r74_doc" ]]; then
  fail_rule "linux_first_dev_doc_present" "$_r74_doc missing -- Rule 74 requires the canonical Linux-first setup guide on disk"
  _r74_fail=1
else
  _r74_missing=""
  for _r74_kw in "WSL2" "WSL1" "Linux"; do
    if ! grep -qF "$_r74_kw" "$_r74_doc" 2>/dev/null; then
      _r74_missing+="${_r74_kw} "
    fi
  done
  if [[ -n "$_r74_missing" ]]; then
    fail_rule "linux_first_dev_doc_present" "$_r74_doc missing required Linux-first keywords: ${_r74_missing}-- Rule 74 requires the doc to recommend WSL2, WSL1, and native Linux"
    _r74_fail=1
  fi
fi
if [[ $_r74_fail -eq 0 ]]; then pass_rule "linux_first_dev_doc_present"; fi

# ===========================================================================
# Wave 4 — small rule activations (2026-05-18)
# Authority: D:/.claude/plans/spicy-mixing-galaxy.md Wave 4.
# ===========================================================================

# ---------------------------------------------------------------------------
