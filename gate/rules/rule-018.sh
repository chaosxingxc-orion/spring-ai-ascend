#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 18 — deleted_spi_starter_names_outside_catalog. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 18 — deleted_spi_starter_names_outside_catalog
# ADR-0041 extends Rule 13: deleted SPI/starter names must not appear in
# third_party/MANIFEST.md, docs/cross-cutting/oss-bill-of-materials.md, README.md.
# ---------------------------------------------------------------------------
_r18_fail=0
_deleted_names18=(
  'LongTermMemoryRepository' 'ToolProvider' 'LayoutParser' 'DocumentSourceConnector'
  'PolicyEvaluator' 'IdempotencyRepository' 'ArtifactRepository'
  'spring-ai-ascend-memory-starter' 'spring-ai-ascend-skills-starter'
  'spring-ai-ascend-knowledge-starter' 'spring-ai-ascend-governance-starter'
  'spring-ai-ascend-persistence-starter' 'spring-ai-ascend-resilience-starter'
  'spring-ai-ascend-mem0-starter' 'spring-ai-ascend-docling-starter'
  'spring-ai-ascend-langchain4j-profile'
)
# Perf fix (2026-05-23): the original loop forked grep N_files × N_names
# times (~thousands × 16 = ~50k forks). On WSL/mnt/d that was ~225s per
# gate run. Replaced with a single bulk `grep -Ff <(patterns) <files>` call
# (~1s) — same 16 fixed-string patterns, same file set, identical
# pass/fail semantics. ADR-0043 (widened to full ACTIVE_NORMATIVE_DOCS).
_r18_files=$(find . -name '*.md' -o -name '*.yaml' 2>/dev/null \
  | grep -vE '/docs/(archive|logs/reviews|adr|delivery|v6-rationale|plans)/|/third_party/|/target/|/\.git/' \
  | sort || true)
if [[ -n "$_r18_files" ]]; then
  _r18_patterns=$(printf '%s\n' "${_deleted_names18[@]}")
  # -H forces filename prefix; -F = fixed strings; -f - reads patterns from stdin.
  _r18_hits=$(printf '%s\n' "$_r18_files" | xargs -d '\n' -r grep -HnFf <(printf '%s\n' "$_r18_patterns") 2>/dev/null || true)
  if [[ -n "$_r18_hits" ]]; then
    while IFS= read -r _r18_hit; do
      [[ -z "$_r18_hit" ]] && continue
      # Parse `file:line:content` → extract first matching deleted-name token.
      _r18_file="${_r18_hit%%:*}"
      _r18_rest="${_r18_hit#*:}"
      _r18_line="${_r18_rest%%:*}"
      _r18_text="${_r18_rest#*:}"
      _r18_matched=""
      for _r18_name in "${_deleted_names18[@]}"; do
        if [[ "$_r18_text" == *"$_r18_name"* ]]; then _r18_matched="$_r18_name"; break; fi
      done
      fail_rule "deleted_spi_starter_names_outside_catalog" "$_r18_file:$_r18_line references deleted name '${_r18_matched:-?}'. Per ADR-0043 Gate Rule 18 (widened) this is a contract-surface truth violation."
      _r18_fail=1
    done <<< "$_r18_hits"
  fi
fi
if [[ $_r18_fail -eq 0 ]]; then pass_rule "deleted_spi_starter_names_outside_catalog"; fi

# ---------------------------------------------------------------------------
