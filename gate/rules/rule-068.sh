#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 68 — claude_md_kernel_matches_card. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 68 — claude_md_kernel_matches_card (enforcer E98)
#
# For every docs/governance/rules/rule-NN.md card with status:active, if the
# card's rule_id appears as "#### Rule <id>" in CLAUDE.md, the body paragraph
# MUST byte-match the kernel: scalar (after whitespace normalisation). Cards
# whose rule_id does NOT appear in CLAUDE.md are NOT drift -- CLAUDE.md is the
# team-collaboration kernel only, and rule cards are sole authority for rule
# definitions. Cards with status:deferred/retired/proposed may exist without
# any CLAUDE.md heading.
# If no cards exist, the rule is vacuously true.
# ---------------------------------------------------------------------------
_r68_fail=0
_r68_claude='CLAUDE.md'
_r68_cards_dir='docs/governance/rules'
if [[ ! -f "$_r68_claude" ]]; then
  fail_rule "claude_md_kernel_matches_card" "$_r68_claude missing"
  _r68_fail=1
elif [[ ! -d "$_r68_cards_dir" ]]; then
  pass_rule "claude_md_kernel_matches_card"
else
  _r68_drift="$("${GATE_PYTHON_BIN:-python3}" - "$_r68_cards_dir" "$_r68_claude" <<'PYEOF'
import re, sys, pathlib
cards_dir, claude_md = sys.argv[1:3]

def norm(s: str) -> str:
    return re.sub(r"\s+", " ", s).strip()

def extract_field(text: str, field: str) -> str:
    m = re.search(rf"(?m)^{re.escape(field)}:\s*(.+?)$", text)
    if not m:
        return ""
    val = m.group(1).strip()
    if (val.startswith('"') and val.endswith('"')) or (val.startswith("'") and val.endswith("'")):
        val = val[1:-1]
    return val

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

drift = []
for card in sorted(pathlib.Path(cards_dir).glob("rule-*.md")):
    base = card.stem
    rid = base[5:]
    if not rid:
        continue
    rid_match = re.sub(r"^0+(?=\d)", "", rid) if rid.isdigit() else rid

    card_text = card.read_text(encoding="utf-8", errors="replace")
    status = extract_field(card_text, "status").lower()
    if status and status != "active":
        continue

    txt = card_text.splitlines()
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
        # Active card not in CLAUDE.md -- OK. CLAUDE.md is collaboration-only.
        continue
    if kernel != body:
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
