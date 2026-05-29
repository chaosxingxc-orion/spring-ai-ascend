#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 20 — module_metadata_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 20 — module_metadata_truth
# ADR-0043: module README.md files must not reference Java class names that
# do not exist in the repository.
# ---------------------------------------------------------------------------
_r20_fail=0
_ghost_classes20=('GraphitiRestGraphMemoryRepository' 'CogneeGraphMemoryRepository')
while IFS= read -r _rm20; do
  [[ -z "$_rm20" ]] && continue
  for _gc20 in "${_ghost_classes20[@]}"; do
    if grep -qF "$_gc20" "$_rm20" 2>/dev/null; then
      if ! find . -name "${_gc20}.java" -not -path './target/*' -not -path './.git/*' | grep -q .; then
        fail_rule "module_metadata_truth" "$_rm20 references class '$_gc20' but no .java file exists. Per ADR-0043 Gate Rule 20 module READMEs must not reference non-existent Java classes."
        _r20_fail=1
      fi
    fi
  done
done < <(find . -name 'README.md' ! -path './docs/*' ! -path './third_party/*' ! -path './target/*' 2>/dev/null | sort || true)
if [[ $_r20_fail -eq 0 ]]; then pass_rule "module_metadata_truth"; fi

# ---------------------------------------------------------------------------
