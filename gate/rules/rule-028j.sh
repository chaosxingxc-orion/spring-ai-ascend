#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 28j — enforcer_artifact_paths_exist. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 28j — enforcer_artifact_paths_exist (Phase K F6 + Phase L P0-2, E33+E35)
# Every `artifact:` path in docs/governance/enforcers.yaml MUST resolve to a
# real file on disk. `#anchor` suffixes (e.g. `RunHttpContractIT.java#cancel...`
# or `check_architecture_sync.sh#rule_10`) MUST also resolve to a real method
# (.java/.sh) or heading (.md) inside that file. Phase L strengthens the
# file-only check (which let E5/E6/E24 ship with anchors pointing at methods
# that did not exist — closes reviewer finding P0-2).
# ---------------------------------------------------------------------------
_r28j_fail=0
# Perf fix (2026-05-23): the original loop forked grep 1-5x per artifact
# row (~200 rows × ~3 avg forks = ~600 forks). On WSL/mnt/d that was ~19s
# per gate run. Replaced with a single python pass that parses enforcers.yaml
# once and caches file content per target — multiple artifact rows pointing
# at the same file (common) now share one read. Same anchor-detection rules.
if [[ -f "$_efile" ]]; then
  _r28j_violations="$(
    GATE_R28J_EFILE="$_efile" "${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, sys
from pathlib import Path

efile = os.environ['GATE_R28J_EFILE']
artifact_re = re.compile(r'^\s*artifact:\s*(.+?)\s*$')
artifacts: list[tuple[str, str]] = []  # (path, anchor)
for line in Path(efile).read_text(encoding='utf-8', errors='replace').splitlines():
    m = artifact_re.match(line)
    if not m: continue
    val = m.group(1)
    if '#' in val:
        path, anchor = val.split('#', 1)
    else:
        path, anchor = val, ''
    path = path.strip()
    if not path: continue
    artifacts.append((path, anchor))

file_cache: dict[str, str] = {}
def read(p: str) -> str:
    if p not in file_cache:
        try: file_cache[p] = Path(p).read_text(encoding='utf-8', errors='replace')
        except OSError: file_cache[p] = ''
    return file_cache[p]

viol: list[str] = []
for path, anchor in artifacts:
    if not os.path.exists(path):
        viol.append(f"PATH\t{path}\t")
        continue
    if not anchor: continue
    text = read(path)
    ok = True
    if path.endswith('.java'):
        # Method declaration: `(void|...)<ws>anchor<ws>*(`
        m1 = re.search(rf'(void|\)|>|>\s)\s+{re.escape(anchor)}\s*\(', text)
        m2 = re.search(rf'(?m)^\s*[a-zA-Z_<>][^()]*\s{re.escape(anchor)}\s*\(', text)
        ok = bool(m1 or m2)
    elif path.endswith(('.sh', '.bash')):
        # Bash function definition or `# Rule N — anchor` or `(pass_rule|fail_rule) "anchor"`.
        m1 = re.search(rf'(?:^|\s){re.escape(anchor)}\s*\(\)', text)
        m2 = re.search(rf'(?m)^\s*function\s+{re.escape(anchor)}\b', text)
        m3 = re.search(rf'(?m)^#\s*Rule\s+[0-9a-z]+\s+(?:—|--)\s+{re.escape(anchor)}\b', text)
        m4 = re.search(rf'\b(?:pass_rule|fail_rule)\s+"{re.escape(anchor)}"', text)
        ok = bool(m1 or m2 or m3 or m4)
    elif path.endswith('.md'):
        ok = bool(re.search(rf'(?m)^#+\s.*{re.escape(anchor)}', text))
    elif path.endswith(('.yaml', '.yml')):
        ok = anchor in text
    else:
        ok = anchor in text
    if not ok:
        viol.append(f"ANCHOR\t{path}\t{anchor}")
for v in viol: print(v)
PYEOF
  )"
  if [[ -n "$_r28j_violations" ]]; then
    while IFS=$'\t' read -r _r28j_kind _r28j_path _r28j_anchor; do
      [[ -z "$_r28j_kind" ]] && continue
      case "$_r28j_kind" in
        PATH)
          fail_rule "enforcer_artifact_paths_exist" "enforcers.yaml declares artifact path '$_r28j_path' which does not exist on disk. Per Rule 28j / enforcer E33."
          ;;
        ANCHOR)
          fail_rule "enforcer_artifact_paths_exist" "enforcers.yaml declares artifact anchor '$_r28j_path#$_r28j_anchor' but no method/heading/rule with that name exists in the target file. Per Rule 28j / enforcer E33 (anchor validation added in Phase L, enforcer E35)."
          ;;
      esac
      _r28j_fail=1
    done <<< "$_r28j_violations"
  fi
fi
if [[ $_r28j_fail -eq 0 ]]; then pass_rule "enforcer_artifact_paths_exist"; fi

# ---------------------------------------------------------------------------
