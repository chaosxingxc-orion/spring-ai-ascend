#!/usr/bin/env bash
# Update architecture-status.yaml allowed_claim numeric baselines + append
# rc21 narrative. Targeted sed-based update; safe for the single-line
# allowed_claim prose.

set -euo pipefail
export LC_ALL=C

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

f="docs/governance/architecture-status.yaml"
[[ -f "$f" ]] || { echo "ERROR: $f missing"; exit 1; }

# Numeric baseline replacements (only on lines containing allowed_claim).
sed -i '
  /allowed_claim:/ {
    s/96 ADRs (0001–0097/97 ADRs (0001–0098/g
    s/127 active gate rules/129 active gate rules/g
    s/38 active engineering rules/40 active engineering rules/g
    s/162 enforcer rows/164 enforcer rows/g
    s/407 graph nodes \/ 667 graph edges/414 graph nodes \/ 686 graph edges/g
    s/9 recurring defect families/10 recurring defect families/g
    s/(rc20 wave — 2026-05-21 L0 ratchet/(rc21 wave — 2026-05-21 L0 ratchet/g
  }
' "$f"

# Append rc21 narrative inside the closing double-quote of allowed_claim line.
# Match the trailing `." (end of allowed_claim) and inject narrative before it.
python3 - <<'PY'
import re, io

path = "docs/governance/architecture-status.yaml"
with open(path, "rb") as h:
    data = h.read().decode("utf-8")

rc21_narrative = (
    "; rc21 ratchets work-time enforcement via 6-phase scenario-loaded "
    "contracts per ADR-0098 (5 phase contracts under docs/governance/contracts/ "
    "+ 5 phase-entry skills under .claude/skills/ + CLAUDE.md Phase Entry "
    "directive table) + 2 new engineering rules (G-10 parallel-Linux-scripts "
    "mandate + G-11 phase-contract rule-allocation coherence) + 2 new gate "
    "rules (Rule 116 + Rule 117) + 2 new enforcers (E164 + E165) + "
    "dist/skills/ minimal distribution layer (manifest.yaml + README.md) "
    "for downstream platform consumers per Principle P-A + new family "
    "F-progressive-loading-weak-enforcement (the root cause this wave "
    "closes: rules ghost during work-time even when CLAUDE.md auto-loads "
    "kernels because the model doesn't refocus on rule paragraphs at "
    "phase entry — fix is phase-scoped contract loading via skills; closed "
    "structural fix, behavioural verification deferred to rc22+ observation)"
)

# Find the last `."` on the allowed_claim line and inject before it.
# Single-line yaml strings end with a `"` followed by newline.
pattern = re.compile(r'(    allowed_claim: ".+?)\."\n', re.DOTALL)
match = pattern.search(data)
if not match:
    raise SystemExit("ERROR: allowed_claim closing pattern not found")
start, end = match.span()
original = data[start:end]
# Inject narrative before the trailing `."\n`.
updated = original[:-3] + rc21_narrative + '."\n'
new_data = data[:start] + updated + data[end:]
with open(path, "wb") as h:
    h.write(new_data.encode("utf-8"))
print(f"OK injected rc21 narrative ({len(rc21_narrative)} chars)")
PY
