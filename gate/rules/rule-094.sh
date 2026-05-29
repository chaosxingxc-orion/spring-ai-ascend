#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 94 — active_corpus_deleted_module_name_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 94 — active_corpus_deleted_module_name_truth (enforcer E129)
#
# Closes rc8 post-corrective review P1-3: Rule 87 only guards
# architecture-status.yaml allowed_claim text; current-tense pre-Phase-C
# module names still appeared in ARCHITECTURE.md §4 constraints, rule cards,
# and test Javadocs. Rule 94 widens the path-truth check to those surfaces.
#
# Scope: active `.md`, `.yaml`, and `*.java` files NOT under docs/archive/,
# docs/logs/reviews/, docs/logs/releases/2026-05-1[0-7]-*.md (historical), and lines
# inside fenced code blocks OR yaml comments. Pattern: word-boundary
# `agent-platform` OR `agent-runtime` (negative-filtered against
# `agent-runtime-core`). Exemption: a historical marker on the same line OR
# within ±3 lines.
# ---------------------------------------------------------------------------
_r94_fail=0
_r94_marker_vocab="gate/active-corpus-name-exemption-markers.txt"
_r94_path_vocab="gate/active-corpus-name-exemption-paths.txt"
if [[ ! -f "$_r94_marker_vocab" ]]; then
  fail_rule "active_corpus_deleted_module_name_truth" "$_r94_marker_vocab missing -- Rule 94 / E129 (Wave 2 vocabulary externalisation)"
  _r94_fail=1
fi
if [[ ! -f "$_r94_path_vocab" ]]; then
  fail_rule "active_corpus_deleted_module_name_truth" "$_r94_path_vocab missing -- Rule 94 / E129 (Wave 2 vocabulary externalisation)"
  _r94_fail=1
fi
# Perf fix (2026-05-23): the original loop forked `awk` once per file in
# the active corpus (~thousands of files post-exempt). On WSL/mnt/d that
# was ~19s per gate run. Replaced with a single python pass that prunes
# excluded dirs via os.walk, applies the same exempt-prefix + test-resource
# filter, and checks the same three deleted-module patterns with ±3-line
# marker exemption. Same semantics, same `gate/active-corpus-name-*` vocab.
_r94_violations="$(
  GATE_R94_MARKER_VOCAB="$_r94_marker_vocab" \
  GATE_R94_PATH_VOCAB="$_r94_path_vocab" \
  "${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, sys
from pathlib import Path

marker_vocab = os.environ['GATE_R94_MARKER_VOCAB']
path_vocab   = os.environ['GATE_R94_PATH_VOCAB']

def load_vocab(p):
    out = []
    if not os.path.isfile(p): return out
    for line in Path(p).read_text(encoding='utf-8', errors='replace').splitlines():
        s = line.strip()
        if not s or s.startswith('#'): continue
        out.append(s)
    return out

markers = load_vocab(marker_vocab)
marker_re = re.compile('|'.join(markers)) if markers else None
exempt_prefixes = tuple(load_vocab(path_vocab))

# Word-boundary surrogate matching the awk version exactly.
ap_re  = re.compile(r'(?:^|[^a-zA-Z0-9_-])agent-platform(?:[^a-zA-Z0-9_-]|$)')
ar_re  = re.compile(r'(?:^|[^a-zA-Z0-9_-])agent-runtime(?:[^a-zA-Z0-9_-]|$)')
arc_re = re.compile(r'(?:^|[^a-zA-Z0-9_-])agent-runtime-core(?:[^a-zA-Z0-9_-]|$)')
fence_re = re.compile(r'^\s*```')
yaml_comment_re = re.compile(r'^\s*#')

# Build file list via os.walk with topdown pruning (faster than find on /mnt/d).
EXTS = ('.md', '.yaml', '.yml', '.java')
PRUNE = {'target', '.git', 'node_modules'}
files: list[str] = []
for root, dirs, fnames in os.walk('.', topdown=True):
    dirs[:] = [d for d in dirs if d not in PRUNE]
    for fn in fnames:
        if not fn.endswith(EXTS): continue
        rel = os.path.join(root, fn)
        if rel.startswith('./'): rel = rel[2:]
        files.append(rel)
files.sort()

violations: list[str] = []
for f in files:
    if '/src/test/resources/' in f: continue
    if any(f.startswith(p) for p in exempt_prefixes): continue
    try:
        text = Path(f).read_text(encoding='utf-8', errors='replace')
    except OSError:
        continue
    lines = text.splitlines()
    n = len(lines)
    in_code = False
    # Two-pass to track fence state up-front (matches awk semantics: state
    # established by first walk, then validated in second walk).
    fence_state = [False] * n
    s = False
    for i, ln in enumerate(lines):
        if fence_re.match(ln):
            s = not s
            fence_state[i] = s
            continue
        fence_state[i] = s
    for i, ln in enumerate(lines):
        if fence_re.match(ln): continue
        if fence_state[i]: continue
        if yaml_comment_re.match(ln): continue
        hit = ap_re.search(ln) or (ar_re.search(ln) and not arc_re.search(ln)) or arc_re.search(ln)
        if not hit: continue
        lo = max(0, i - 3); hi = min(n, i + 4)
        if marker_re:
            window = ' '.join(lines[lo:hi])
            if marker_re.search(window): continue
        violations.append(f"{f}:{i+1}:{ln}")

for v in violations:
    print(v)
PYEOF
)"
if [[ -n "$_r94_violations" ]]; then
  _r94_first=$(printf '%s\n' "$_r94_violations" | head -5 | tr '\n' '|')
  fail_rule "active_corpus_deleted_module_name_truth" "active corpus contains current-tense pre-Phase-C module name(s) without historical marker (first 5): ${_r94_first}-- Rule 94 / E129 (markers loaded from gate/active-corpus-name-exemption-markers.txt; exempt paths from gate/active-corpus-name-exemption-paths.txt)"
  _r94_fail=1
fi
if [[ $_r94_fail -eq 0 ]]; then pass_rule "active_corpus_deleted_module_name_truth"; fi

# ---------------------------------------------------------------------------
