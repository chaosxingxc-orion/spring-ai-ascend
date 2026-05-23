#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 23 — active_doc_internal_links_resolve. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 23 — active_doc_internal_links_resolve
# ADR-0043: markdown links ](relative-path) in active normative docs must
# resolve to files that exist on disk. Excludes http://, https://, anchors.
# ---------------------------------------------------------------------------
_r23_fail=0
# Perf fix (2026-05-23): the original loop forked `grep | sed` per file +
# `cd | realpath` per link (~hundreds × ~10 = thousands of forks). On
# WSL/mnt/d this ran ~65s per gate. Replaced with a single python pass
# that reads each file once, extracts links via re, and resolves with
# os.path.normpath + os.path.exists. ADR-0043, same semantics.
_r23_violations=$("${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, sys
from pathlib import Path
LINK_RE = re.compile(r'\]\(([^)]+)\)')
EXCLUDE_DIRS = ('./docs/archive/', './docs/logs/', './docs/adr/',
                './docs/delivery/', './docs/v6-rationale/', './docs/plans/',
                './third_party/', './target/', './.git/')

def is_excluded(p: str) -> bool:
    return any(p.startswith(d) for d in EXCLUDE_DIRS)

violations = []
for root, dirs, files in os.walk('.', topdown=True):
    # Prune excluded dirs in-place.
    dirs[:] = [d for d in dirs if not is_excluded(os.path.join(root, d) + '/')]
    for fn in files:
        if not fn.endswith('.md'):
            continue
        fpath = os.path.join(root, fn)
        if is_excluded(fpath):
            continue
        try:
            text = Path(fpath).read_text(encoding='utf-8', errors='replace')
        except OSError:
            continue
        fdir = os.path.dirname(fpath)
        for link in LINK_RE.findall(text):
            # Skip external + anchor-only.
            if link.startswith(('http://', 'https://', 'mailto:', '#')):
                continue
            # Strip anchor fragment.
            path_only = link.split('#', 1)[0]
            if not path_only:
                continue
            resolved = os.path.normpath(os.path.join(fdir, path_only))
            if not os.path.exists(resolved):
                violations.append((fpath, link, resolved))

for fpath, link, resolved in violations:
    print(f"{fpath}\t{link}\t{resolved}")
PYEOF
)
if [[ -n "$_r23_violations" ]]; then
  while IFS=$'\t' read -r _r23_file _r23_link _r23_resolved; do
    [[ -z "$_r23_file" ]] && continue
    fail_rule "active_doc_internal_links_resolve" "$_r23_file has broken link to '$_r23_link' (resolved: '$_r23_resolved'). Per ADR-0043 Gate Rule 23 all internal links in active docs must resolve."
    _r23_fail=1
  done <<< "$_r23_violations"
fi
if [[ $_r23_fail -eq 0 ]]; then pass_rule "active_doc_internal_links_resolve"; fi

# ---------------------------------------------------------------------------
