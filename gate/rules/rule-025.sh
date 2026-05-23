#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 25 — peripheral_wave_qualifier. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 25 — peripheral_wave_qualifier
# ADR-0045: SPI Javadoc must not use "Primary sidecar impl:" or "Primary impl:"
# without a wave qualifier (W0-W4) in context. Active markdown docs must not use
# "Sidecar adapter —" without a wave qualifier or ADR reference. Closes PERIPHERAL-DRIFT.
# ---------------------------------------------------------------------------
_r25_fail=0
# Perf fix (2026-05-23): both 25a (java sources, ±2-line context) and 25b
# (active markdown, in-line context) consolidated into a single python pass.
# Original ran ~hundreds of files × ~3-5 forks per match = ~17s; the rewrite
# finishes in ~1s. Same regex patterns and same context windows.
_r25_violations="$("${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re
from pathlib import Path

W_RE = re.compile(r'(?:^|[^A-Za-z0-9])W[0-4](?:[^A-Za-z0-9]|$)')
MARKER_25B = re.compile(r'ADR-')
violations: list[str] = []

# 25a: agent-service main java files, "Primary impl:" / "Primary sidecar impl:" need W0-W4 in ±2 lines.
prim_re = re.compile(r'Primary sidecar impl:|Primary impl:')
java_root = 'agent-service/src/main/java'
if os.path.isdir(java_root):
    for dirpath, _, files in os.walk(java_root):
        for fn in files:
            if not fn.endswith('.java'): continue
            p = os.path.join(dirpath, fn)
            try: lines = Path(p).read_text(encoding='utf-8', errors='replace').splitlines()
            except OSError: continue
            n = len(lines)
            for i, ln in enumerate(lines):
                if not prim_re.search(ln): continue
                lo = max(0, i - 2); hi = min(n, i + 3)
                ctx = ' '.join(lines[lo:hi])
                if not W_RE.search(ctx):
                    violations.append(f"25a\t{p}\t{i+1}")

# 25b: active md files, "Sidecar adapter —" on a line lacking W0-W4 AND ADR-.
EXCLUDE_DIRS = ('./docs/archive/', './docs/logs/reviews/', './docs/adr/',
                './docs/delivery/', './docs/v6-rationale/', './docs/plans/',
                './third_party/', './target/', './.git/')
def excluded(p: str) -> bool:
    return any(p.startswith(d) for d in EXCLUDE_DIRS)
sidecar_re = re.compile(r'Sidecar adapter —')
for root, dirs, files in os.walk('.', topdown=True):
    dirs[:] = [d for d in dirs if not excluded(os.path.join(root, d) + '/')]
    for fn in files:
        if not fn.endswith('.md'): continue
        p = os.path.join(root, fn)
        if excluded(p): continue
        try: lines = Path(p).read_text(encoding='utf-8', errors='replace').splitlines()
        except OSError: continue
        for i, ln in enumerate(lines):
            if not sidecar_re.search(ln): continue
            if W_RE.search(ln) or MARKER_25B.search(ln): continue
            violations.append(f"25b\t{p}\t{i+1}")

for v in violations: print(v)
PYEOF
)"
if [[ -n "$_r25_violations" ]]; then
  while IFS=$'\t' read -r _r25_kind _r25_path _r25_line; do
    [[ -z "$_r25_kind" ]] && continue
    case "$_r25_kind" in
      25a) fail_rule "peripheral_wave_qualifier" "$_r25_path:$_r25_line contains 'Primary.*impl:' without wave qualifier (W0-W4) in context. Per ADR-0045 Gate Rule 25 future-wave impl claims must carry wave qualifiers." ;;
      25b) fail_rule "peripheral_wave_qualifier" "$_r25_path:$_r25_line contains 'Sidecar adapter —' without wave qualifier or ADR reference. Per ADR-0045 Gate Rule 25." ;;
    esac
    _r25_fail=1
  done <<< "$_r25_violations"
fi
if [[ $_r25_fail -eq 0 ]]; then pass_rule "peripheral_wave_qualifier"; fi

# ---------------------------------------------------------------------------
