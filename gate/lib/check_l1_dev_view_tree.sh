#!/usr/bin/env bash
# gate/lib/check_l1_dev_view_tree.sh — Rule G-1.1.a real implementation.
#
# Scans every agent-*/ARCHITECTURE.md for a Development View section
# containing a fenced ```text``` directory tree. Cross-checks each
# documented package path against the filesystem.
#
# Authority: ADR-0099 (rc22) + rc27 corrective (rc22-2 closure).
# Enforcer: E166.
#
# Exit codes:
#   0 — all 6 agent-*/ARCHITECTURE.md files pass.
#   1 — at least one file missing the Development View OR documented
#       a package path that does not exist on disk.
#
# Public functions:
#   check_l1_dev_view_tree           — runs the check, returns 0/1
#   check_l1_dev_view_tree_for_file  — checks a single ARCHITECTURE.md
#
# Output: stdout TSV `<status>\t<file>\t<detail>` per checked file
#         (status: PASS | FAIL | SKIP).

set -uo pipefail
export LC_ALL=C

if [[ -z "${GATE_REPO_ROOT:-}" ]]; then
  GATE_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi

# Extract fenced text blocks from a Markdown file that appear within a
# section heading matching `## *Development View*` or `## 3. *Development*`.
# Returns the raw block content (without fences) on stdout.
_l1_extract_dev_view_block() {
  local _file="$1"
  awk '
    BEGIN { in_dev_section=0; in_fence=0 }
    /^##[[:space:]]/ {
      if ($0 ~ /[Dd]evelopment[[:space:]]+[Vv]iew/) {
        in_dev_section=1; next
      } else {
        in_dev_section=0; next
      }
    }
    in_dev_section && /^```text/ { in_fence=1; next }
    in_dev_section && in_fence && /^```/ { in_fence=0; next }
    in_dev_section && in_fence { print }
  ' "$_file"
}

# rc28+rc30 hybrid: basename whole-line match against module's src/ basename
# set. rc29 attempted full-relative-path tightening but the sed delimiter `|`
# collided with the `(main|test)` alternation (NEW-DEFECT-1), making the
# helper silently no-op every input. rc30 reverts to rc28's basename approach
# (proven to work in CI) while retaining rc29's intent for future hardening.
#
# Trade-off acknowledged: basename-only match accepts a bogus "fake_path/spi/"
# tree-block claim because `spi` exists somewhere under the module's src/.
# Full-path reconstruction (tracking tree-drawing indentation) is the proper
# fix; deferred to a later wave with a fixture-driven self-test (per rc18
# F-recursive-prevention-irony lesson).
_l1_validate_tree_paths() {
  local _module="$1"
  local _block="$2"
  local _failed=0
  local _basename_set
  # rc32: switch to GNU `find -printf '%f\n'`. The previous
  # `find | xargs -I{} basename {}` form was observed to silently drop
  # entries on GitHub Actions Ubuntu runners (CI failed Rule 118 with
  # `missing-dir:orchestration/` despite the directory existing in git
  # and being visible locally on WSL Ubuntu). `-printf '%f\n'` emits
  # basenames directly without per-dir subprocess spawning, eliminating
  # the xargs-stdin race AND running ~50-100x faster on large trees.
  _basename_set=$(find "$GATE_REPO_ROOT/$_module/src" -type d -printf '%f\n' 2>/dev/null | sort -u)
  while IFS= read -r _line; do
    local _path
    _path=$(printf '%s' "$_line" | sed -E 's/^[│├└─[:space:]]*//; s/[[:space:]]+#.*$//')
    [[ "$_path" != */ ]] && continue
    [[ "$_path" == "$_module/" ]] && continue
    local _seg="${_path%/}"
    [[ -z "$_seg" ]] && continue
    # The segment may contain `/`; take the LAST component for basename match.
    # rc30: use `##*/` to avoid sed (and any delimiter collision risk).
    local _basename="${_seg##*/}"
    if echo "$_basename_set" | grep -qFx "$_basename"; then
      continue
    fi
    echo "missing-dir:$_path"
    _failed=1
  done <<< "$_block"
  return $_failed
}

# Public: check one ARCHITECTURE.md file.
check_l1_dev_view_tree_for_file() {
  local _file="$1"
  if [[ ! -f "$_file" ]]; then
    echo "SKIP	$_file	file-missing"
    return 0
  fi
  local _module
  _module=$(dirname "$_file" | sed 's|.*/||')
  local _block
  _block=$(_l1_extract_dev_view_block "$_file")
  if [[ -z "$_block" ]]; then
    echo "FAIL	$_file	no-development-view-fenced-text-block (Rule G-1.1.a)"
    return 1
  fi
  local _bad
  _bad=$(_l1_validate_tree_paths "$_module" "$_block")
  if [[ -n "$_bad" ]]; then
    echo "FAIL	$_file	$(echo "$_bad" | head -3 | tr '\n' ';')"
    return 1
  fi
  echo "PASS	$_file	"
  return 0
}

# Public: check all 6 agent-*/ARCHITECTURE.md files.
check_l1_dev_view_tree() {
  local _root="${GATE_L1_TREE_ROOT:-$GATE_REPO_ROOT}"
  local _fail=0
  for _arch in "$_root"/agent-*/ARCHITECTURE.md; do
    [[ -f "$_arch" ]] || continue
    if ! check_l1_dev_view_tree_for_file "$_arch"; then
      _fail=1
    fi
  done
  return $_fail
}
