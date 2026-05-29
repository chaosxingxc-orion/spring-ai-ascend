#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 63 — release_note_retracted_tag_qualified. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 63 — release_note_retracted_tag_qualified (v2.0.0-rc2 / second-pass review F-γ structural prevention)
#
# Every tag listed in docs/governance/retracted-tags.txt MUST, wherever it is
# mentioned in an active release note under docs/logs/releases/*.md, appear either
#   (a) on the same line as "(retracted)" (case-insensitive), OR
#   (b) under a markdown heading (line starting with '#') containing
#       "Historical" or "Superseded" (case-insensitive).
# Drift would let a retracted tag be re-cited as a recommendation in a fresh
# release-note section, recreating the F-γ stale-evidence defect that the
# second-pass review's P1-2 finding flagged.
# ---------------------------------------------------------------------------
_r63_fail=0
_r63_list="docs/governance/retracted-tags.txt"
if [[ ! -f "$_r63_list" ]]; then
  fail_rule "release_note_retracted_tag_qualified" "$_r63_list missing -- v2.0.0-rc2 second-pass review F-γ prevention expects this list"
  _r63_fail=1
else
  # Perf fix (2026-05-23): replaced quadruple-nested bash loop (~25 docs ×
  # ~few tags × ~few lines × per-line sed/grep = ~1000+ forks, ~14s) with
  # a single python pass. Same logic: (a) `(retracted)` on the same line OR
  # (b) nearest upward `#` heading contains 'historical'/'superseded'.
  _r63_violations="$(
    GATE_R63_LIST="$_r63_list" "${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, glob
from pathlib import Path

list_path = os.environ['GATE_R63_LIST']
tags: list[str] = []
for line in Path(list_path).read_text(encoding='utf-8', errors='replace').splitlines():
    s = line.strip()
    if not s or s.startswith('#'): continue
    # First pipe field, trimmed.
    tag = s.split('|', 1)[0].strip()
    if tag: tags.append(tag)

if not tags: raise SystemExit(0)

retracted_re = re.compile(r'\(retracted\)|retracted\b', re.IGNORECASE)
hist_re = re.compile(r'historical|superseded', re.IGNORECASE)
heading_re = re.compile(r'^#')
docs = sorted(glob.glob('docs/logs/releases/*.md'))

for doc in docs:
    try: lines = Path(doc).read_text(encoding='utf-8', errors='replace').splitlines()
    except OSError: continue
    for i, ln in enumerate(lines):
        for tag in tags:
            if tag not in ln: continue
            # (a) same line carries (retracted).
            if retracted_re.search(ln): continue
            # (b) nearest upward heading qualifies.
            qualified = False
            for j in range(i, -1, -1):
                if heading_re.match(lines[j]):
                    qualified = bool(hist_re.search(lines[j]))
                    break
            if not qualified:
                print(f"{doc}\t{i+1}\t{tag}")
PYEOF
  )"
  if [[ -n "$_r63_violations" ]]; then
    while IFS=$'\t' read -r _r63_doc _r63_ln _r63_tag; do
      [[ -z "$_r63_doc" ]] && continue
      fail_rule "release_note_retracted_tag_qualified" "$_r63_doc:$_r63_ln mentions retracted tag '$_r63_tag' without '(retracted)' qualifier on the line OR a 'Historical'/'Superseded' heading above"
      _r63_fail=1
    done <<< "$_r63_violations"
  fi
fi
if [[ $_r63_fail -eq 0 ]]; then pass_rule "release_note_retracted_tag_qualified"; fi

# ===========================================================================
# Cross-corpus consistency audit prevention rules (2026-05-17)
# Authority: docs/logs/reviews/2026-05-17-cross-corpus-consistency-audit-response.en.md
# Closes structural design flaws G1, G2, G3 surfaced by the audit:
#   G1 — module count was hardcoded in 4 places
#   G2 — no metadata-vs-pom dependency cross-check
#   G3 — no SPI-package exhaustiveness cross-check
# Rules 64-66 with enforcer rows E94-E96 and 6 self-tests (2 per rule).
# ===========================================================================

# ---------------------------------------------------------------------------
