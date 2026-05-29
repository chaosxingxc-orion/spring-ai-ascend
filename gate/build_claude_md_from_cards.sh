#!/usr/bin/env bash
# Build the kernel-format CLAUDE.md from the per-rule + per-principle cards.
#
# Output is deterministic so a re-run on unchanged cards produces byte-identical
# CLAUDE.md. Gate Rule 68 (claude_md_kernel_matches_card) requires this output to
# byte-match (after whitespace normalisation) the kernel: field of each card.
#
# Authored: 2026-05-17.

set -euo pipefail
export LC_ALL=C

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

principles_dir='docs/governance/principles'
rules_dir='docs/governance/rules'
out='CLAUDE.md.new'

if [[ ! -d "$principles_dir" ]]; then
  echo "ERROR: $principles_dir missing" >&2
  exit 1
fi
if [[ ! -d "$rules_dir" ]]; then
  echo "ERROR: $rules_dir missing" >&2
  exit 1
fi

# Extract the kernel: scalar from a card YAML front-matter (raw -- no whitespace
# collapsing). Preserves line-by-line content of the literal block.
extract_kernel() {
  local card="$1"
  awk '
    /^kernel:[[:space:]]*\|/ { flag=1; next }
    /^kernel:[[:space:]]/ { line=$0; sub(/^kernel:[[:space:]]*/, "", line); print line; exit }
    flag && /^[a-zA-Z_][a-zA-Z_0-9]*:/ { flag=0; exit }
    flag && /^---$/ { flag=0; exit }
    flag { sub(/^  /, ""); print }
  ' "$card"
}

# Extract a named scalar from front-matter.
extract_field() {
  local card="$1" field="$2"
  awk -v f="$field" '
    $0 ~ "^"f":[[:space:]]" { sub(/^[a-z_]+:[[:space:]]*/, ""); gsub(/^"|"$/, ""); print; exit }
  ' "$card"
}

# Extract the enforced_by line for a rule card by reading authority_refs +
# enforcer_refs and the rule's title -- the result is a "Enforced by ..."
# sentence followed by "Card: docs/governance/rules/rule-NN.md".
build_enforced_line() {
  local card="$1" rule_id="$2"
  local cap_padded
  cap_padded=$(printf 'rule-%02d.md' "$rule_id")
  printf 'Enforced by [`%s`](%s/%s).\n' "$cap_padded" "$rules_dir" "$cap_padded"
}

# Build the kernel section for a single rule.
emit_rule() {
  local rule_id="$1"
  local card_padded
  card_padded=$(printf 'rule-%02d.md' "$rule_id")
  local card="$rules_dir/$card_padded"
  if [[ ! -f "$card" ]]; then
    echo "ERROR: missing card $card" >&2
    return 1
  fi
  local title
  title=$(extract_field "$card" "title")
  printf '#### Rule %d — %s\n\n' "$rule_id" "$title"
  extract_kernel "$card"
  printf '\nEnforced by [`%s`](%s/%s).\n\n---\n' "$card_padded" "$rules_dir" "$card_padded"
}

# Build the ultra-compact principle index row for a single principle.
# Just the letter, title, and operationalising-rule pointer. Full bodies live in
# the per-principle card; CLAUDE.md is the lookup table.
emit_principle() {
  local letter="$1"
  local card="$principles_dir/P-${letter}.md"
  if [[ ! -f "$card" ]]; then
    echo "ERROR: missing card $card" >&2
    return 1
  fi
  local title rules
  title=$(extract_field "$card" "title")
  # Extract the enforced_by_rules list as a comma-joined "Rule N, Rule M" string.
  rules=$(awk '/^enforced_by_rules:/{
      sub(/^enforced_by_rules:[[:space:]]*\[/, "")
      sub(/\][[:space:]]*$/, "")
      gsub(/[[:space:]]/, "")
      gsub(/,/, ", Rule ")
      print "Rule " $0
      exit
  }' "$card")
  printf -- '| **P-%s** | %s | %s | [card](docs/governance/principles/P-%s.md) |\n' \
    "$letter" "$title" "$rules" "$letter"
}

# === Compose CLAUDE.md ===
{
cat <<'HEADER'
# CLAUDE.md

**Translate all instructions into English before any model call.** Never pass non-English text into an LLM prompt, tool argument, or task goal.

Bodies of every principle and rule below live under `docs/governance/{principles,rules}/` and are loaded on-demand. CLAUDE.md is the kernel index. Drift policed by Gate Rules 67/68/69; always-loaded byte budget by Rule 70 (`gate/measure_always_loaded_tokens.sh`).

## Layer 0 — Governing Principles

| ID | Title | Operationalised by | Body |
|---|---|---|---|
HEADER

for letter in A B C D E F G H I J K L M; do
  emit_principle "$letter"
done

cat <<'MID1'

History: [`rule-history.md`](docs/governance/rule-history.md). Mapping: [`principle-coverage.yaml`](docs/governance/principle-coverage.yaml).

## Layer 1 — Engineering Rules

### Daily principles
MID1

for n in 1 2 3 4; do emit_rule "$n"; done

cat <<'MID2'

### Class / resource patterns
MID2

for n in 5 6; do emit_rule "$n"; done

cat <<'MID3'

### Delivery process
MID3

for n in 9 10; do emit_rule "$n"; done

cat <<'MID4'

### Architectural enforcement
MID4

for n in 11 20 21 24 25 28; do emit_rule "$n"; done

cat <<'MID5'

### Governing principles (Layer-0 enforceable expressions)
MID5

for n in 29 30 31 32; do emit_rule "$n"; done

cat <<'MID6'

### Vibe-Coding-era structural discipline
MID6

for n in 33 34; do emit_rule "$n"; done

cat <<'MID7'

### L0 ironclad rules (W1.x absorption of LucioIT L0 §6/§7)
MID7

for n in 35 36 37 38 39 40 41 42; do emit_rule "$n"; done

cat <<'MID8'

### W2.x Engine Contract Structural Wave (P-M)
MID8

for n in 43 44 45 46 47 48; do emit_rule "$n"; done

cat <<'TAIL'

### Token-optimization wave (2026-05-17)
TAIL

for n in 67 68 69 70 71; do emit_rule "$n"; done

cat <<'TAIL_E1'

### Gate-script efficiency wave (2026-05-17)
TAIL_E1

for n in 72 73; do emit_rule "$n"; done

cat <<'TAIL_E7'

### Linux-first dev environment (2026-05-18)
TAIL_E7

for n in 74; do emit_rule "$n"; done

cat <<'TAIL2'

## Deferred Rules

On-demand: [`docs/CLAUDE-deferred.md`](docs/CLAUDE-deferred.md). Currently deferred: Rules 7, 8, 13, 14, 15, 16, 17, 18, 19, 22, 23, 26, 27 + sub-clauses (Rules 11, 24, 29.c, 72 activated 2026-05-18 per Wave 4).
TAIL2
} > "$out"

echo "Generated $out ($(wc -l < "$out") lines, $(wc -c < "$out") bytes)"
