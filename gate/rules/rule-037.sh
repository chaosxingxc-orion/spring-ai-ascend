#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 37 — architecture_artefact_front_matter. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 37 — architecture_artefact_front_matter (enforcer E55, ADR-0068)
#
# Every L0/L1/L2 architecture artefact MUST declare a level: + view:
# front-matter (YAML at top of file for .md; top-level key for .yaml).
# Targets: ARCHITECTURE.md, architecture/docs/L1/agent-*.md architecture/docs/L1/agent-service/ARCHITECTURE.md, architecture/docs/L2/**/*.md (excluding
# README.md while empty), docs/adr/*.yaml.
# ---------------------------------------------------------------------------
_r37_fail=0
_valid_levels='^(L0|L1|L2)$'
_valid_views='^(logical|development|process|physical|scenarios)$'

_check_front_matter_md() {
  local _f="$1"
  local _level _view
  _level="$(awk 'BEGIN{in_fm=0; n=0} /^---[[:space:]]*$/{n++; if(n==1){in_fm=1; next} if(n==2){exit}} in_fm && /^level:[[:space:]]/{sub(/^level:[[:space:]]*/,""); sub(/[[:space:]]*$/,""); print; exit}' "$_f" 2>/dev/null)"
  _view="$(awk 'BEGIN{in_fm=0; n=0} /^---[[:space:]]*$/{n++; if(n==1){in_fm=1; next} if(n==2){exit}} in_fm && /^view:[[:space:]]/{sub(/^view:[[:space:]]*/,""); sub(/[[:space:]]*$/,""); print; exit}' "$_f" 2>/dev/null)"
  if [[ -z "$_level" ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f missing 'level:' YAML front-matter (CLAUDE.md Rule 33 / ADR-0068)"; _r37_fail=1; return
  fi
  if [[ ! "$_level" =~ $_valid_levels ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f level: '$_level' is not one of L0|L1|L2"; _r37_fail=1
  fi
  if [[ -z "$_view" ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f missing 'view:' YAML front-matter (CLAUDE.md Rule 33 / ADR-0068)"; _r37_fail=1; return
  fi
  if [[ ! "$_view" =~ $_valid_views ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f view: '$_view' is not one of logical|development|process|physical|scenarios"; _r37_fail=1
  fi
}

_check_front_matter_yaml() {
  local _f="$1"
  local _level _view
  _level="$(grep -E '^level:[[:space:]]' "$_f" 2>/dev/null | head -1 | sed -E 's/^level:[[:space:]]*([A-Za-z0-9_]+).*/\1/')"
  _view="$(grep -E '^view:[[:space:]]' "$_f" 2>/dev/null | head -1 | sed -E 's/^view:[[:space:]]*([A-Za-z0-9_]+).*/\1/')"
  if [[ -z "$_level" ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f missing top-level 'level:' (CLAUDE.md Rule 33 / ADR-0068)"; _r37_fail=1; return
  fi
  if [[ ! "$_level" =~ $_valid_levels ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f level: '$_level' is not one of L0|L1|L2"; _r37_fail=1
  fi
  if [[ -z "$_view" ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f missing top-level 'view:' (CLAUDE.md Rule 33 / ADR-0068)"; _r37_fail=1; return
  fi
  if [[ ! "$_view" =~ $_valid_views ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f view: '$_view' is not one of logical|development|process|physical|scenarios"; _r37_fail=1
  fi
}

# Perf fix (2026-05-23): the original ran 2-4 forks per file (~100 files →
# ~400 forks, ~14s on WSL/mnt/d). Replaced with a single python pass that
# walks all target paths, parses front-matter, and validates level/view
# against the same {L0|L1|L2} / {logical|development|process|physical|scenarios}
# enums. Same fail messages so the upstream surface (release-note baseline /
# Rule 28 references) is unchanged.
_r37_violations="$("${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, glob
from pathlib import Path

valid_levels = {'L0', 'L1', 'L2'}
valid_views = {'logical', 'development', 'process', 'physical', 'scenarios'}

def fail(kind: str, path: str, detail: str):
    print(f"{kind}\t{path}\t{detail}")

def check_md(path: str):
    try: lines = Path(path).read_text(encoding='utf-8', errors='replace').splitlines()
    except OSError: return
    in_fm = False; fm_count = 0; level = ''; view = ''
    for ln in lines:
        if re.match(r'^---\s*$', ln):
            fm_count += 1
            if fm_count == 1: in_fm = True; continue
            if fm_count == 2: break
            continue
        if not in_fm: continue
        m = re.match(r'^level:\s*(.+?)\s*$', ln)
        if m and not level: level = m.group(1)
        m = re.match(r'^view:\s*(.+?)\s*$', ln)
        if m and not view: view = m.group(1)
    if not level: fail('MD_LEVEL_MISSING', path, '')
    elif level not in valid_levels: fail('MD_LEVEL_BAD', path, level)
    if not view: fail('MD_VIEW_MISSING', path, '')
    elif view not in valid_views: fail('MD_VIEW_BAD', path, view)

def check_yaml(path: str):
    try: text = Path(path).read_text(encoding='utf-8', errors='replace')
    except OSError: return
    level = ''; view = ''
    for ln in text.splitlines():
        m = re.match(r'^level:\s*([A-Za-z0-9_]+)', ln)
        if m and not level: level = m.group(1)
        m = re.match(r'^view:\s*([A-Za-z0-9_]+)', ln)
        if m and not view: view = m.group(1)
    if not level: fail('YAML_LEVEL_MISSING', path, '')
    elif level not in valid_levels: fail('YAML_LEVEL_BAD', path, level)
    if not view: fail('YAML_VIEW_MISSING', path, '')
    elif view not in valid_views: fail('YAML_VIEW_BAD', path, view)

targets_md = []
if os.path.isfile('ARCHITECTURE.md'): targets_md.append('ARCHITECTURE.md')
for d in sorted(os.listdir('.')):
    p = os.path.join(d, 'ARCHITECTURE.md')
    if os.path.isfile(p) and p != 'ARCHITECTURE.md':
        targets_md.append(p.replace('\\', '/'))
targets_md.extend(sorted(glob.glob('architecture/docs/L2/**/*.md', recursive=True)))
for p in targets_md: check_md(p)

for p in sorted(glob.glob('docs/adr/*.yaml')):
    check_yaml(p)
PYEOF
)"
if [[ -n "$_r37_violations" ]]; then
  while IFS=$'\t' read -r _r37_kind _r37_path _r37_val; do
    [[ -z "$_r37_kind" ]] && continue
    case "$_r37_kind" in
      MD_LEVEL_MISSING)   fail_rule "architecture_artefact_front_matter" "$_r37_path missing 'level:' YAML front-matter (CLAUDE.md Rule 33 / ADR-0068)" ;;
      MD_LEVEL_BAD)       fail_rule "architecture_artefact_front_matter" "$_r37_path level: '$_r37_val' is not one of L0|L1|L2" ;;
      MD_VIEW_MISSING)    fail_rule "architecture_artefact_front_matter" "$_r37_path missing 'view:' YAML front-matter (CLAUDE.md Rule 33 / ADR-0068)" ;;
      MD_VIEW_BAD)        fail_rule "architecture_artefact_front_matter" "$_r37_path view: '$_r37_val' is not one of logical|development|process|physical|scenarios" ;;
      YAML_LEVEL_MISSING) fail_rule "architecture_artefact_front_matter" "$_r37_path missing top-level 'level:' (CLAUDE.md Rule 33 / ADR-0068)" ;;
      YAML_LEVEL_BAD)     fail_rule "architecture_artefact_front_matter" "$_r37_path level: '$_r37_val' is not one of L0|L1|L2" ;;
      YAML_VIEW_MISSING)  fail_rule "architecture_artefact_front_matter" "$_r37_path missing top-level 'view:' (CLAUDE.md Rule 33 / ADR-0068)" ;;
      YAML_VIEW_BAD)      fail_rule "architecture_artefact_front_matter" "$_r37_path view: '$_r37_val' is not one of logical|development|process|physical|scenarios" ;;
    esac
    _r37_fail=1
  done <<< "$_r37_violations"
fi
if [[ $_r37_fail -eq 0 ]]; then pass_rule "architecture_artefact_front_matter"; fi

# ---------------------------------------------------------------------------
