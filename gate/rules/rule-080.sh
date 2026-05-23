#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 80 — s2c_callback_signal_historical_only_in_authority. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 80 — s2c_callback_signal_historical_only_in_authority (enforcer E113)
#
# In authoritative entrypoints (CLAUDE.md, README.md, root ARCHITECTURE.md,
# agent-*/ARCHITECTURE.md, docs/contracts/*.v1.yaml, docs/adr/*.yaml,
# docs/adr/*.md), the deleted Java type name S2cCallbackSignal MUST appear
# only in paragraphs marked historical / deleted / refactored from /
# amendments / rc3-unification (within +/-5 lines). v2.0.0-rc3 unified S2C
# suspension into the checked SuspendSignal.forClientCallback(...) variant
# (ADR-0074 2026-05-18 amendment); live current-state claims naming
# S2cCallbackSignal are forbidden in authoritative docs.
# ---------------------------------------------------------------------------
_r80_fail=0
_r80_vocab="gate/historical-marker-vocabulary.txt"
if [[ ! -f "$_r80_vocab" ]]; then
  fail_rule "s2c_callback_signal_historical_only_in_authority" "$_r80_vocab missing -- Rule 80 / E113 (Wave 2 vocabulary externalisation)"
  _r80_fail=1
fi
_r80_marker_re="$(grep -vE '^[[:space:]]*(#|$)' "$_r80_vocab" 2>/dev/null | tr '\n' '|' | sed 's/|$//')"
# Perf fix (2026-05-23): replaced per-authority-file grep + per-match sed|grep
# (~110 files × ~3 forks = ~14s) with a single python pass. Same scope
# (CLAUDE.md, README.md, ARCHITECTURE.md, contracts, ADRs, agent-*/ARCH), same
# ±5-line marker window, same vocabulary file.
_r80_violations="$(
  GATE_R80_MARKER_RE="$_r80_marker_re" "${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, glob
from pathlib import Path

marker_src = os.environ.get('GATE_R80_MARKER_RE', '')
marker_re = re.compile(marker_src, re.IGNORECASE) if marker_src else None

targets: list[str] = []
for p in ('CLAUDE.md', 'README.md', 'ARCHITECTURE.md'):
    if os.path.isfile(p): targets.append(p)
targets.extend(sorted(glob.glob('docs/contracts/*.v1.yaml')))
targets.extend(sorted(glob.glob('docs/adr/*.yaml')))
targets.extend(sorted(glob.glob('docs/adr/*.md')))
for arch in sorted(glob.glob('agent-*/ARCHITECTURE.md')):
    targets.append(arch)

for path in targets:
    try: lines = Path(path).read_text(encoding='utf-8', errors='replace').splitlines()
    except OSError: continue
    n = len(lines)
    for i, ln in enumerate(lines):
        if 'S2cCallbackSignal' not in ln: continue
        lo = max(0, i - 5); hi = min(n, i + 6)
        window = '\n'.join(lines[lo:hi])
        if marker_re and marker_re.search(window): continue
        print(f"{path}\t{i+1}")
PYEOF
)"
if [[ -n "$_r80_violations" ]]; then
  while IFS=$'\t' read -r _r80_file _r80_lineno; do
    [[ -z "$_r80_file" ]] && continue
    fail_rule "s2c_callback_signal_historical_only_in_authority" "$_r80_file:$_r80_lineno mentions S2cCallbackSignal without a historical/deleted/refactored/amendment marker within +/-5 lines -- Rule 80 / E113"
    _r80_fail=1
  done <<< "$_r80_violations"
fi
if [[ $_r80_fail -eq 0 ]]; then pass_rule "s2c_callback_signal_historical_only_in_authority"; fi

