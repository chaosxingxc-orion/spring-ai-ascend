#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 94 — active_corpus_deleted_module_name_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 94 — active_corpus_deleted_module_name_truth (enforcer E129)
#
# Closes rc8 post-corrective review P1-3: Rule 87 only guards
# architecture-status.yaml allowed_claim text; current-tense pre-Phase-C
# module names still appeared in ARCHITECTURE.md §4 constraints, rule cards,
# and test Javadocs. Rule 94 widens the path-truth check to those surfaces.
#
# Scope: active `.md`, `.yaml`, and `*.java` files NOT under docs/archive/,
# docs/reviews/, docs/releases/2026-05-1[0-7]-*.md (historical), and lines
# inside fenced code blocks OR yaml comments. Pattern: word-boundary
# `agent-platform` OR `agent-runtime` (negative-filtered against
# `agent-runtime-core`). Exemption: a historical marker on the same line OR
# within ±3 lines.
# ---------------------------------------------------------------------------
_r94_fail=0
_r94_markers='historical|pre-ADR-[0-9]+|pre-Phase-C|consolidated into|merged into|was rooted|formerly|superseded|deprecated|archived|moved|extracted per ADR-[0-9]+|post-ADR-[0-9]+'
_r94_violations=""
while IFS= read -r _r94_file; do
  [[ -z "$_r94_file" ]] && continue
  case "$_r94_file" in
    docs/archive/*|docs/reviews/*) continue ;;
    docs/releases/2026-05-1[0-7]-*) continue ;;
    docs/releases/2026-05-1[0-7]/*) continue ;;
  esac
  # Within-file: lines containing word-boundary agent-platform or agent-runtime
  # (excluding agent-runtime-core), outside fenced code blocks, outside yaml
  # comment lines, no marker within ±3 lines.
  # GNU awk doesn't honor `\b` word-boundary; use POSIX bracket-class boundaries.
  _r94_hits=$(awk -v markers="$_r94_markers" '
    BEGIN {
      in_code = 0
      # Word-boundary surrogate: (^|[^a-zA-Z0-9_-]) ... ([^a-zA-Z0-9_-]|$)
      ap_re = "(^|[^a-zA-Z0-9_-])agent-platform([^a-zA-Z0-9_-]|$)"
      ar_re = "(^|[^a-zA-Z0-9_-])agent-runtime([^a-zA-Z0-9_-]|$)"
      arc_re = "(^|[^a-zA-Z0-9_-])agent-runtime-core([^a-zA-Z0-9_-]|$)"
    }
    /^[[:space:]]*```/ { in_code = 1 - in_code; next }
    { lines[NR] = $0 }
    END {
      in_code = 0
      for (i = 1; i <= NR; i++) {
        line = lines[i]
        if (line ~ /^[[:space:]]*```/) { in_code = 1 - in_code; continue }
        if (in_code) continue
        if (line ~ /^[[:space:]]*#/) continue
        if (line ~ ap_re || (line ~ ar_re && line !~ arc_re)) {
          lo = i - 3; if (lo < 1) lo = 1
          hi = i + 3; if (hi > NR) hi = NR
          window = ""
          for (j = lo; j <= hi; j++) window = window " " lines[j]
          if (window !~ markers) print i ":" line
        }
      }
    }
  ' "$_r94_file" 2>/dev/null || true)
  if [[ -n "$_r94_hits" ]]; then
    while IFS= read -r _r94_hit; do
      _r94_violations="${_r94_violations}${_r94_file}:${_r94_hit}\n"
    done <<< "$_r94_hits"
  fi
done < <(find . -type f \( -name '*.md' -o -name '*.yaml' -o -name '*.java' \) -not -path './target/*' -not -path './.git/*' -not -path './*/target/*' | sed 's|^\./||')
if [[ -n "$_r94_violations" ]]; then
  _r94_first=$(printf '%b' "$_r94_violations" | head -5 | tr '\n' '|')
  fail_rule "active_corpus_deleted_module_name_truth" "active corpus contains current-tense pre-Phase-C module name(s) without historical marker (first 5): ${_r94_first}-- Rule 94 / E129 (rc8 post-corrective P1-3 closure; widens Rule 87 from status-yaml allowed_claim to root constraints + rule cards + test Javadocs)"
  _r94_fail=1
fi
if [[ $_r94_fail -eq 0 ]]; then pass_rule "active_corpus_deleted_module_name_truth"; fi

# ---------------------------------------------------------------------------
