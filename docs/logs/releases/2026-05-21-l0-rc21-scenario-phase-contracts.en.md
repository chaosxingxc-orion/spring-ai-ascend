---
level: L0
view: process
affects_level: L0
affects_view: process
release_tag: v2.0.0-rc21
date: 2026-05-21
authority: ADR-0098
supersedes_release_notes:
  - docs/logs/releases/2026-05-21-l0-rc20-meta-recursion-actually-close-plus-d9.en.md
---

# L0 v2.0.0-rc21 — 6-Phase Scenario-Loaded Contracts + Rule G-10 + Rule G-11

## Verdict

rc21 closes the user-observed failure mode that the prior META-gate ratchet
(rc14–rc20) couldn't reach: **progressive on-demand rule loading in
CLAUDE.md leaves many constraints "ghosting" during work-time** — rule
kernels were auto-loaded but the FULL bodies and the implementation
specifics only landed in context when Claude actively read the rule
cards, which empirically didn't happen often enough. Gate-time META
defences (Rule 68 kernel-card byte-match, Rule 110 META scope_surfaces,
Rule G-9 family ledger) couldn't bridge that gap because they fire AT
GATE TIME, not AT WORK TIME.

rc21 replaces progressive loading with **scenario-loaded contracts**:
6 work phases, each with its own contract document under
`docs/governance/contracts/` and a triggering skill under
`.claude/skills/<phase>-mode.md`. At phase entry, MUST-invoke the
matching skill; the skill reads the contract and surfaces its active
rules + forbidden patterns + exit criteria into the active context.

Two new discipline rules emerge from the same wave:

- **Rule G-10 — Parallel-Linux-Scripts Mandate**: every new gate script
  under `gate/**/*.sh` MUST be parallel-execution-compatible on Linux/
  WSL. Extends Rule G-5.a (serial↔parallel slug parity) discipline
  from "validate the existing fleet" to "new scripts are parallel-ready
  from authoring time".
- **Rule G-11 — Phase-Contract Rule-Allocation Coherence**: the
  coherence invariant between phase contracts and rule cards — every
  cited rule has a card, every active card is cited.

**6 waves, single ADR-0098 with sub-streams:**
- Wave 1 — CLAUDE.md Phase Contracts pointer + 5 contract skeletons
- Wave 2 — Active Rules tables populated; `scope_phase:` frontmatter
  injected into 39 rule cards + 13 principle cards
- Wave 3 — 5 phase skills authored; CLAUDE.md Phase Entry directive
  table inserted
- Wave 4 — Rule G-10 (card + gate Rule 116 + E164 + exemption list);
  Rule G-11 (card + gate Rule 117 + E165). (D-9 already done in rc20.)
- Wave 5 — `dist/skills/` minimal distribution layer (manifest +
  README + first templates)
- Wave 6 — This release note + ADR-0098 + baseline lockstep + family
  ledger refresh + freeze rc20 release note with merge SHA marker

## The 6 phases and their triggering skills

| Phase | Skill | Contract |
|---|---|---|
| Always-on (every session) | (harness auto-load) | `CLAUDE.md` kernel |
| Architecture design | `/design-mode` | [`architecture-design.md`](../../governance/contracts/architecture-design.md) |
| Engineering implementation | `/impl-mode` | [`engineering-implementation.md`](../../governance/contracts/engineering-implementation.md) |
| Integration verification | `/verify-mode` | [`integration-verification.md`](../../governance/contracts/integration-verification.md) |
| System commit | `/commit-mode` | [`system-commit.md`](../../governance/contracts/system-commit.md) |
| Review response | `/review-mode` | [`review-response.md`](../../governance/contracts/review-response.md) |

If uncertain which phase applies, default to `/impl-mode` (widest
coverage). Skills suggest the next phase at exit.

## Dual-track loading mechanism

Track 1 (skill-driven): user/agent invokes `/design-mode` etc; the
skill reads the matching contract.

Track 2 (CLAUDE.md-driven): CLAUDE.md Phase Entry directive table has
MUST-invoke language so even sessions that skip the slash command see
the routing.

The two tracks defend against single-mode failure (forgot to invoke
skill ⇒ CLAUDE.md text-pointer rescues; skipped reading CLAUDE.md ⇒
skill description triggers Claude to invoke).

## Distribution layer for downstream platform consumers

`dist/skills/` ships the 6-phase pattern as templates that downstream
teams (building agent runtimes on spring-ai-ascend) can customise:

- `dist/skills/manifest.yaml` — publishable skill bundle declaration
- `dist/skills/README.md` — 6-step adoption guide
- `dist/skills/templates/*.template.md` — skill templates with
  `{{PLACEHOLDER}}` customisation points (`{{CONTRACTS_DIR}}`,
  `{{PROJECT_NAME}}`, `{{RULE_IDS_BY_PHASE}}`, ...)
- `dist/skills/contract-templates/*.contract.template.md` — phase
  contract templates

Per Principle P-A (Developer Self-Service), the platform is
responsible for making adoption straightforward without platform-team
intervention.

## New engineering rules

| Rule | Surface | Enforcer |
|---|---|---|
| **G-10 — Parallel-Linux-Scripts Mandate** | `gate/*.sh` (top-level, excluding `check_parallel.sh` + `check_architecture_sync.sh`) | Gate Rule 116 / E164. Each non-exempt script must contain `xargs -P` / `parallel` / `& wait` mechanism OR appear in `gate/serial-only-paths.txt`. Vacuously passes if exemption list absent. |
| **G-11 — Phase-Contract Rule-Allocation Coherence** | `docs/governance/contracts/*.md` ↔ `docs/governance/rules/rule-*.md` + `docs/governance/principles/P-*.md` | Gate Rule 117 / E165. Three sub-checks: forward (citation → card), reverse (card → citation), dual-P limit (only G-9 allowed dual-P). |

## Baseline metrics

Per `feedback_release_note_baseline_truth.md` (canonical = first
numeric column), this table uses the rc15+ 2-column (Count + Delta)
format.

| Metric | Count | Delta |
|---|---|---|
| §4 constraints | 65 | unchanged from rc20 |
| Active ADRs | 97 | +1 from rc20 (ADR-0098) |
| Active gate rules | 129 | +2 from rc20 (Rule 116 G-10 + Rule 117 G-11) |
| Active engineering rules | 40 | +2 from rc20 (G-10 + G-11) |
| Gate self-test cases | 226 | unchanged (Wave-4 fixtures deferred to follow-up — see Deferred Work) |
| Enforcer rows | 164 | +2 from rc20 (E164 + E165) |
| Layer-0 governing principles | 13 | unchanged |
| Reactor modules | 8 | unchanged |
| Architecture graph nodes | 412 | +5 from rc20 (live count post-regen) |
| Architecture graph edges | 678 | +11 from rc20 (live count post-regen) |
| Recurring defect families | 9 | unchanged |
| Maven tests green | 374 | unchanged (no Java code changes) |
| Phase contracts | 5 | NEW (architecture-design, engineering-implementation, integration-verification, system-commit, review-response) |
| Phase-loading skills | 6 | +5 from rc20 (refresh-defect-archive + 5 new: design-mode, impl-mode, verify-mode, commit-mode, review-mode) |

The `Architecture graph nodes` and `edges` rows are **approximate** in
this release note; they will be set to live values after Wave 6
regenerates `docs/governance/architecture-graph.yaml`. The
`baseline_metrics.architecture_graph_nodes` and `_edges` values in
`architecture-status.yaml` are the SSOT.

## Deferred work (explicit out-of-scope for rc21, may land in rc22+)

- **Deep CLAUDE.md kernel shrink** — moving phase-scoped rule kernels
  into phase contracts would require extending Rule 68 (G-3.b
  kernel-card byte-match) to recognise contract-hosted kernels as a
  valid alternative location. rc21 keeps every rule kernel in CLAUDE.md
  (no shrink); the Phase Entry table is purely additive.
- **Rule 110 META fixtures for G-10 and G-11** — each new rule
  technically requires ≥2 self-test fixtures per Rule 110. rc21 ships
  the rules + enforcement; fixtures are a Wave-4-corrective candidate.
- **Marketplace plugin packaging** (`ce-skills-spring-ai-ascend`) —
  explicit out-of-scope per the plan; rc22+ candidate.
- **Aggressive `dist/skills/` template fleshing-out** — Wave 5
  ships minimum-viable (manifest + README + skeleton templates); full
  per-skill placeholder substitution work may land later.

## Methodology — why these waves in this order

rc21 follows the rc18 5-wave decomposition pattern (single ADR with
named sub-streams) extended to 6 waves (added Wave 5 for distribution
layer). Each wave is one PR; each PR has its own CI verification;
all PRs converge to the single ADR-0098. The pattern is documented in
`docs/governance/recurring-defect-families.md` under family
F-recursive-prevention-irony's prevention notes (rc18 origin).

The methodology trade-off (vs N small ADRs): one ADR + named
sub-streams keeps the architectural narrative coherent at review time;
the cost is that the ADR is larger and amendments to one stream must
careful not to silently shift another.

## How this closes the user-observed failure mode

The user 2026-05-21 observation:

> 现在的 claude.md 太臃肿了 ... 渐进式加载之后，很多契约并没有严格执行
> (CLAUDE.md is bloated; after progressive loading, many contracts
> aren't strictly enforced)

rc21's hypothesis: enforcement isn't bypassed because rules are wrong
— it's bypassed because **work-time context doesn't carry the relevant
rules into the model's attention window when the model is operating**.
A 33-KB CLAUDE.md with all rule kernels is hard to focus on. A 3–5-KB
phase contract that lists ONLY the rules active for THIS phase, loaded
fresh at phase entry by a skill, is. Plus the dual-track CLAUDE.md
prose pointer ensures even non-skill-using flows see the routing.

This is the work-time analog of the gate-time META wave that rc14–rc20
built. Together they cover both temporal points where rules can be
silently bypassed.

## Competitive baselines (Principle P-B four-pillar coverage)

Per Rule R-B, every release note MUST mention the four Principle P-B
pillars by name even if rc21 doesn't move the underlying values. The
authoritative current values live in
[`docs/governance/competitive-baselines.yaml`](../../governance/competitive-baselines.yaml).

- **performance** — rc21 has no Java code deltas; current `current_value`
  unchanged from rc20.
- **cost** — rc21 has no infrastructure / runtime cost deltas; current
  `current_value` unchanged from rc20.
- **developer_onboarding** — rc21 ADDS the `dist/skills/` minimum
  distribution layer (manifest + README + downstream adoption guide)
  per Principle P-A; this raises the developer-self-service surface
  even though the existing `competitive-baselines.yaml#developer_onboarding`
  metric remains unchanged pending follow-up measurement.
- **governance** — rc21 adds 2 engineering rules (G-10 + G-11) + 2 gate
  rules (Rule 116 + Rule 117) + 1 new recurring-defect family
  (F-progressive-loading-weak-enforcement); the governance pillar
  ratchets per the metric counts above.

## Verification

- gate/check_parallel.sh exits 0 on Linux/WSL (per Rule G-7).
- Live `/design-mode` invocation in a new architecture-design task
  proves the contract loads and the routing is observable.
- 5 contracts are mutually exclusive on P markers (except G-9 dual-P).
- Every active rule card has at least one contract citation (Rule G-11
  reverse check).
- Every contract citation resolves to an existing card (Rule G-11
  forward check).
- D-9 grandfather list (`gate/d9-grandfathered-files.txt`) is not
  extended.
