# Knowledge ↔ Governance Rebalancing — Program Charter

Date: 2026-06-01
Status: active
Owner (direction + approval): repository owner (small team, 2–5 + AI)
Branch: `governance/knowledge-governance-separation` (based on current `origin/main`)
Parent analysis: Codex proposal `docs/reviews/2026-05-31-ai-governance-knowledge-system-rebalancing-proposal.en.md`
Approved plan: `D:/.claude/plans/ai-ai-ai-tokens-adr-adr-adr-imperative-lark.md`

## Organizing principle

**Governance constrains the engineering main-path only. The AI knowledge system sits outside it.**
The bug being fixed: Rule R-C ("Code-as-Contract") + meta-enforcer E28 pulled the knowledge corpus
*into* the governed main-path ("every normative sentence — including every ADR — maps to an enforcer").
The cure: declare the knowledge system explicitly out of governance scope, maintained by lightweight
integrity scripts (not ADRs, not blocking gates), searchable, documented.

## Prime directive (binds every wave)

1. **Subtractive** — the program removes/relocates/merges; it does not grow governance.
2. **Frozen** — no new blocking rule, blocking gate, baseline counter, or mandatory-reading requirement
   is added during the program, except a security/release emergency approved by the owner.
3. **Reuse, not mint** — reuse existing structures (the 5 ADR authority states + one `governed_invariant`
   bit; existing fact layer; existing search infra). Do not invent parallel taxonomies.
4. **Red line** — the knowledge-system integrity scripts stay *advisory* (parse / links / unique-IDs /
   contradiction-detect). If they ever become coverage/authority gates, the bug is back.

## Safety invariants

- All work lands on this branch. **Nothing merges to `main` without explicit owner approval.**
- Each wave is an independently reviewable, revertable commit.
- The abandoned in-flight branch `governance/adr-corpus-systematic-cleanup` is left unmerged and
  recoverable; its untracked normalization artifacts were discarded (the conflation-deepening direction).

## Wave tracks (see approved plan for the full DAG)

- **INV** — re-grounded inventory against current `main` (read-only, parallel).
- **K** — Knowledge system: `knowledge/` directory + advisory integrity scripts + search + usage guide;
  relocate knowledge ADRs/docs.
- **G** — Governance: keystone (narrow R-C/E28 to main-path, exclude `knowledge/`) → retire/demote ~50%
  → promote the governed-invariant minority.
- **R** — Systematic refresh of `CLAUDE.md` + `README` and their full link graph.
- **P** — Admission/retirement policy + governance budget.

## Note on current base

Current `main` is mid-reorganization (architecture tree moved under `docs/architecture/`, an
`architecture2/` merge duplicate present, no `normalized/` ADR layer). All counts and paths are
re-grounded by the INV phase before any editing wave proceeds.
