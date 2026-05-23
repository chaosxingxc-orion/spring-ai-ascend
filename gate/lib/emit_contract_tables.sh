#!/usr/bin/env bash
# Emit Active Rules tables for each phase contract under
# docs/governance/contracts/, per the rc21 / ADR-0098 rule-allocation map.
#
# Output goes to /tmp/contract-tables-<phase>.md (one per phase). The caller
# copies the relevant block into each contract.md file.
#
# Source of truth: the allocation map in this script (block-comment below).
# Per Rule 126 / Wave 4: phase contracts MUST link rule cards by reference,
# not copy kernel text — this avoids triple-byte-match drift across
# CLAUDE.md ↔ rule card ↔ contract table.

set -euo pipefail
export LC_ALL=C

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

# Allocation: id|always_on|design|impl|verify|commit|review
# Codes: P=primary, X=cross-ref, -=not in phase
# G-9 carries dual-P (commit + review); the table emits P for both.
ALLOC=$(cat <<'EOF'
D-1|P|X|X|X|X|X
D-2|P|-|X|-|-|X
D-3|-|-|-|P|X|X
D-4|-|X|P|X|X|-
D-5|-|-|-|X|P|X
D-6|-|P|X|-|-|-
D-7|-|P|X|-|-|-
D-8|-|P|X|-|-|-
D-9|P|X|X|X|X|X
G-1|-|P|-|X|X|-
G-2|-|X|-|-|P|X
G-2.1|-|-|-|-|P|X
G-3|-|X|-|-|P|X
G-3.1|-|X|-|-|P|-
G-4|P|-|-|-|X|-
G-5|-|-|-|P|X|-
G-6|-|-|-|P|X|-
G-7|P|-|X|X|X|-
G-8|-|X|-|-|P|X
G-9|-|-|-|-|P|P
M-1|-|P|X|-|-|-
M-2|-|P|X|-|X|-
R-A|-|P|X|X|-|-
R-B|-|-|-|-|P|-
R-C|-|P|X|X|-|-
R-C.1|-|P|X|-|-|-
R-C.2|-|X|P|X|-|-
R-D|-|P|X|X|-|-
R-E|-|P|X|-|-|-
R-F|-|P|X|X|-|-
R-G|-|X|P|X|-|-
R-H|-|X|P|X|-|-
R-I|-|P|X|-|-|-
R-I.1|-|P|X|-|-|-
R-J|-|P|X|X|-|-
R-K|-|P|X|-|-|-
R-L|-|P|X|-|-|-
R-M|-|P|X|X|-|-
EOF
)

PRINCIPLES="P-A P-B P-C P-D P-E P-F P-G P-H P-I P-J P-K P-L P-M"

card_title() {
  local id="$1" dir="$2"
  local card="$dir/rule-${id}.md"
  [[ "$dir" == *principles* ]] && card="$dir/${id}.md"
  if [[ -f "$card" ]]; then
    awk '/^title:/{sub(/^title:[[:space:]]*/,""); gsub(/^"|"$/,""); print; exit}' "$card"
  else
    echo "(card missing)"
  fi
}

emit_table_for_phase() {
  local phase="$1"
  # Column index: always_on=2 design=3 impl=4 verify=5 commit=6 review=7
  declare -A IDX=( [always_on]=2 [design]=3 [impl]=4 [verify]=5 [commit]=6 [review]=7 )
  local col="${IDX[$phase]}"
  [[ -z "$col" ]] && { echo "ERROR: unknown phase $phase" >&2; return 1; }

  echo "| Rule | Title | Marker | Card |"
  echo "|---|---|---|---|"
  while IFS='|' read -r id co_aon co_des co_imp co_ver co_com co_rev; do
    [[ -z "$id" ]] && continue
    local cells=( "" "$id" "$co_aon" "$co_des" "$co_imp" "$co_ver" "$co_com" "$co_rev" )
    local marker="${cells[$col]}"
    [[ "$marker" == "-" ]] && continue
    local title
    title=$(card_title "$id" "docs/governance/rules")
    echo "| ${id} | ${title} | **${marker}** | [\`rule-${id}.md\`](../rules/rule-${id}.md) |"
  done <<<"$ALLOC"

  # Principles only appear in design phase (all P)
  if [[ "$phase" == "design" ]]; then
    for p in $PRINCIPLES; do
      local title
      title=$(card_title "$p" "docs/governance/principles")
      echo "| ${p} | ${title} | **P** | [\`${p}.md\`](../principles/${p}.md) |"
    done
  fi
}

# Use a per-invocation temp directory so two concurrent emit_contract_tables.sh
# runs (CI matrix, dev parallel shell) cannot clobber each other on the
# previously-hard-coded /tmp/contract-tables-<phase>.md path. The caller
# parses the WROTE lines below to discover where each file landed.
EMIT_TMPDIR="$(mktemp -d -t contract-tables.XXXXXX)"
trap 'rm -rf "$EMIT_TMPDIR"' EXIT
for phase in always_on design impl verify commit review; do
  out="${EMIT_TMPDIR}/contract-tables-${phase}.md"
  {
    echo "## Active rules — ${phase} phase"
    echo ""
    emit_table_for_phase "$phase"
  } > "$out"
  lines=$(wc -l < "$out")
  echo "WROTE $out ($lines lines)"
done
