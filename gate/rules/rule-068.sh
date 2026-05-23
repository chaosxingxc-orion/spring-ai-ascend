#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 68 — claude_md_kernel_matches_card. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 68 — claude_md_kernel_matches_card (enforcer E98)
#
# For every docs/governance/rules/rule-NN.md card, extract the kernel: scalar
# from the YAML front-matter, normalise whitespace, and assert the same text
# appears verbatim in the body of "#### Rule NN" in CLAUDE.md. Fails on drift.
# If no cards exist (initial PR1 landing), the rule is vacuously true.
# ---------------------------------------------------------------------------
_r68_fail=0
_r68_claude='CLAUDE.md'
_r68_cards_dir='docs/governance/rules'
_r68_deferred_doc='docs/CLAUDE-deferred.md'
if [[ ! -f "$_r68_claude" ]]; then
  fail_rule "claude_md_kernel_matches_card" "$_r68_claude missing"
  _r68_fail=1
elif [[ ! -d "$_r68_cards_dir" ]]; then
  pass_rule "claude_md_kernel_matches_card"
else
  # Perf fix (2026-05-23): replace per-card 22-fork awk/sed/tr pipeline
  # (~50 cards × ~22 forks = ~1100 forks per gate run, ~17s on WSL/mnt/d)
  # with a single python pass that reads all cards + CLAUDE.md once.
  _r68_drift="$("${GATE_PYTHON_BIN:-python3}" - "$_r68_cards_dir" "$_r68_claude" "$_r68_deferred_doc" <<'PYEOF'
import os, re, sys, pathlib
cards_dir, claude_md, deferred_doc = sys.argv[1:4]

def norm(s: str) -> str:
    """Collapse all whitespace runs to single spaces; strip outer."""
    return re.sub(r"\s+", " ", s).strip()

# Parse CLAUDE.md once. For each "#### Rule <id> ..." heading, capture body lines
# until blank-line+`Enforced` OR `---` OR next "####" heading.
claude_text = pathlib.Path(claude_md).read_text(encoding="utf-8", errors="replace").splitlines()
bodies: dict[str, str] = {}
i, n = 0, len(claude_text)
while i < n:
    m = re.match(r"^#### Rule (\S+?)(?:\s|$)", claude_text[i])
    if m:
        rid = m.group(1)
        buf = []
        i += 1
        while i < n:
            line = claude_text[i]
            if line.startswith("---") or line.startswith("#### ") or line.startswith("Enforced by"):
                break
            if line.strip():
                buf.append(line)
            i += 1
        bodies[rid] = norm(" ".join(buf))
        continue
    i += 1

deferred_text = ""
if os.path.isfile(deferred_doc):
    deferred_text = pathlib.Path(deferred_doc).read_text(encoding="utf-8", errors="replace")

drift = []
for card in sorted(pathlib.Path(cards_dir).glob("rule-*.md")):
    base = card.stem  # rule-XX
    rid = base[5:]    # strip "rule-"
    if not rid:
        continue
    # Normalise integer ids by stripping leading zeros for heading match.
    rid_match = re.sub(r"^0+(?=\d)", "", rid) if rid.isdigit() else rid

    # Extract kernel: scalar (literal block `|` or inline). Stop at next
    # top-level key or `---`.
    txt = card.read_text(encoding="utf-8", errors="replace").splitlines()
    kernel_lines: list[str] = []
    in_block = False
    for line in txt:
        if not in_block:
            mk = re.match(r"^kernel:\s*\|", line)
            if mk:
                in_block = True
                continue
            mi = re.match(r"^kernel:\s+(.+)$", line)
            if mi:
                kernel_lines.append(mi.group(1))
                break
        else:
            if re.match(r"^[A-Za-z_][A-Za-z_0-9]*:", line) or line.rstrip() == "---":
                break
            kernel_lines.append(line.lstrip())
    kernel = norm(" ".join(kernel_lines))
    if not kernel:
        continue

    body = bodies.get(rid_match, "")
    if not body:
        # Deferred-only sub-clause cards (e.g. R-A.c) live in CLAUDE-deferred.md.
        # Check for `Rule <id>` reference there before flagging drift.
        if deferred_text and re.search(rf"(^|[^A-Za-z0-9])Rule\s+{re.escape(rid_match)}([^A-Za-z0-9]|$)", deferred_text):
            continue
        drift.append(f"Rule {rid_match}: card exists but no body in CLAUDE.md")
    elif kernel != body:
        drift.append(f"Rule {rid_match} drift")
sys.stdout.write("; ".join(drift))
PYEOF
)"
  if [[ -n "$_r68_drift" ]]; then
    fail_rule "claude_md_kernel_matches_card" "$_r68_drift"
    _r68_fail=1
  fi
  if [[ $_r68_fail -eq 0 ]]; then
    pass_rule "claude_md_kernel_matches_card"
  fi
fi

# ---------------------------------------------------------------------------
