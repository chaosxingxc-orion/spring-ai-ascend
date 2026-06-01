# The AI Knowledge System

This tree is the project's **AI knowledge system** — the searchable corpus that helps humans and AI
agents understand the repository. It is deliberately **outside the engineering main-path** and is
**not governed**: no ADR governs it, no blocking gate enforces it. It is maintained only by
**advisory integrity scripts** that keep it from drifting into self-contradiction.

> **The principle.** Governance constrains the engineering main-path (product code, architecture-of-record,
> runtime contracts, and the small set of current governed invariants). Knowledge is everything an agent
> *could* want to know. Conflating the two — making every piece of knowledge a binding precondition or a
> gate — is the defect this system exists to undo. Knowledge is **available unless needed**; governance
> **applies only when justified**.

## What is "knowledge" here — two homes, one search

Knowledge is everything governance does **not** enforce. It lives in two places, both reached by
`knowledge/_tools/search.sh`:

| Where | Holds |
|---|---|
| `knowledge/` (this tree) | Knowledge authored directly: lessons, synthesis, cross-cutting notes, and the advisory tooling in `knowledge/_tools/`. |
| **knowledge in place** (not moved) | The existing record, already excluded from governance by the keystone: the **ADR decision record** (`docs/adr/`), the **history / wave logs** (`docs/logs/`), and the **architecture delivery / design narrative** (`docs/architecture/`). |

ADRs are deliberately **not** physically relocated: they are simultaneously the decision record *and* the
generated fact-layer's source (`architecture/facts/generated/adrs.json`). The keystone already makes them
knowledge — governance corpus scans exclude `docs/adr/` — so they are knowledge **in place**, and the
search reaches them where they live rather than severing them from the fact layer.

## How to use it

### Search
```
knowledge/_tools/search.sh "<query>"        # ripgrep across the knowledge tree, ranked by path
knowledge/_tools/search.sh --titles "<q>"   # match document titles/headings only
```
Load the *smallest* slice that answers your task. You do **not** need to read the whole tree to start
work — that broad-reading tax is exactly what this system removes.

### Add or update knowledge
1. Drop a markdown or YAML file in the matching subtree (or edit an existing one).
2. Give it a clear `# Title` (H1) and, for decision/lesson docs, a short front-matter block
   (`id:`, `topic:`, `status:`, `supersedes:` where relevant).
3. Run the integrity check (below). Fix anything it flags.
4. Commit. **No ADR, no gate, no review-proposal is required** to maintain knowledge.

### Check integrity (advisory)
```
knowledge/_tools/check_integrity.py          # parse + links + unique-IDs + contradiction heuristics
```
This is **advisory** — it returns non-zero only to help you catch corruption (unparseable files,
broken intra-knowledge links, duplicate IDs, contradictory status). It is **never** wired into the
blocking architecture gate. **Red line:** if this script is ever made a blocking/coverage gate, the
knowledge↔governance conflation has returned — do not do it.

## The bridge to governance (promotion)

Knowledge is the default home for everything learned. A piece of knowledge becomes **governance** only
when it is a *current governed invariant* — something whose violation causes real product, security,
compatibility, tenant-isolation, or release risk. Promotion is deliberate and rare:

1. State the invariant and the concrete failure it prevents.
2. Name an owner and a review date.
3. Express it as a rule/contract in the main-path, with a machine-checkable enforcer **only if** it
   meets the gate-admission bar.

Most knowledge is never promoted. That is correct.

## What does NOT belong here

- Runtime contracts, the architecture-of-record, current governed invariants → those are governed
  main-path (`docs/contracts/`, `architecture/`, `docs/governance/`).
- New blocking gates or rules → governance, not knowledge.
- Anything that must be *obeyed* to do work safely → governance.
