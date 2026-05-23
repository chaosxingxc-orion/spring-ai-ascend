#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 15 — no_active_refs_deleted_wave_plan_paths. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 15 — no_active_refs_deleted_wave_plan_paths
# ADR-0041: active .md files (outside archive/reviews/third_party/target/.git)
# must not reference docs/plans/engineering-plan-W0-W4.md or
# docs/plans/roadmap-W0-W4.md. Both plans were archived per ADR-0037.
# ---------------------------------------------------------------------------
_r15_fail=0
_deleted_plan_refs=('docs/plans/engineering-plan-W0-W4.md' 'docs/plans/roadmap-W0-W4.md')
# Perf fix (2026-05-23): replaced per-file × per-pattern grep loop (~hundreds
# × 2 = ~hundreds of forks, ~16s) with a single bulk `grep -lFf` against the
# pre-built file list. Identical "first match wins" semantics.
_r15_files=$(find . -name '*.md' \
  ! -path './docs/archive/*' \
  ! -path './docs/logs/reviews/*' \
  ! -path './docs/adr/*' \
  ! -path './docs/delivery/*' \
  ! -path './docs/v6-rationale/*' \
  ! -path './third_party/*' \
  ! -path './target/*' \
  ! -path './.git/*' \
  -type f 2>/dev/null | sort || true)
if [[ -n "$_r15_files" ]]; then
  _r15_first_hit=$(printf '%s\n' "$_r15_files" \
    | xargs -d '\n' -r grep -lFf <(printf '%s\n' "${_deleted_plan_refs[@]}") 2>/dev/null \
    | head -1 || true)
  if [[ -n "$_r15_first_hit" ]]; then
    # Identify which deleted ref triggered the match (for the error message).
    _r15_ref=""
    for _r15_candidate in "${_deleted_plan_refs[@]}"; do
      if grep -qF "$_r15_candidate" "$_r15_first_hit" 2>/dev/null; then
        _r15_ref="$_r15_candidate"; break
      fi
    done
    fail_rule "no_active_refs_deleted_wave_plan_paths" "$_r15_first_hit references deleted plan path '${_r15_ref:-?}'. Per ADR-0041 Gate Rule 15 active docs must not reference archived plan paths."
    _r15_fail=1
  fi
fi
if [[ $_r15_fail -eq 0 ]]; then pass_rule "no_active_refs_deleted_wave_plan_paths"; fi

# ---------------------------------------------------------------------------
