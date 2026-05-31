# AI Governance and Knowledge System Rebalancing Proposal

Date: 2026-05-31
Status: proposal
Audience: engineering team, architecture owners, AI-agent workflow owners, governance maintainers
Decision posture: reduce governance load without discarding useful product, architecture, ADR, contract, fact, and gate assets

## 1. Executive Proposal

The current repository governance model has likely crossed a critical boundary: it treats the AI engineering knowledge system as if it were the AI governance system.

That distinction matters.

A knowledge system helps humans and AI agents retrieve the right context at the right time. It can be broad, historical, rich, searchable, and occasionally redundant. It should preserve product intent, architecture rationale, ADR history, implementation facts, contract evidence, review findings, and operational lessons.

A governance system constrains work. It should be small, current, explicit, testable, and expensive to expand. It should protect only the invariants whose violation would cause real product, security, compatibility, correctness, or delivery risk.

The repository currently contains valuable assets, but the operating model has made too many of those assets behave like mandatory governance. This causes low token efficiency, slow task startup, excessive pre-work, and decision lock-in. The problem is not that the project has too much knowledge. The problem is that too much knowledge is being treated as binding precondition, active authority, or gate material.

This proposal recommends a deliberate rebalancing:

1. Separate the AI knowledge system from the AI governance system.
2. Stop treating every ADR as a candidate for a gate.
3. Classify ADR authority state explicitly: historical, informational, current decision, governed invariant.
4. Classify rules and gates by enforcement value: blocking, changed-files blocking, advisory, retired.
5. Replace broad mandatory reading with task-routed context packs.
6. Keep generated facts as implementation truth, but read them by claim type and task scope, not as a universal startup tax.
7. Freeze new governance expansion until the existing rule and gate corpus is rationalized.

The intended result is not "less discipline." The intended result is better discipline: fewer hard constraints, higher signal, faster agent startup, clearer human ownership, and gates that engineering teams trust because they protect real invariants.

## 2. Current Evidence

This proposal is based on the current repository state, not a generic critique of governance-heavy engineering.

The repository already acknowledges a transition away from governance-forward loading:

- `CLAUDE.md:97` says the repository is in a transition "from a governance-forward auto-load to a product-forward Tier-1 + progressive disclosure model."

At the same time, the entry path still imposes broad reading obligations:

- `docs/governance/ai-reading-path.yaml:51-53` says agents must load the path in order unless the task is explicitly narrower, and broad architecture-touching or product-touching tasks start at the top.
- `docs/onboarding/ai-understanding-path.md:24-28` mirrors that broad-task behavior and the generated-facts-before-prose rule.
- `AGENTS.md:67` says that for any architecture decision, agents should walk the ADR corpus from the first ADR through the highest-numbered ADR.

The active governance corpus is already large:

- `docs/governance/architecture-status.yaml:157` records 63 active engineering rules.
- `docs/governance/architecture-status.yaml:159` records 165 active gate checks.
- `docs/governance/architecture-status.yaml:163` records 139 ADRs.
- `docs/governance/architecture-status.yaml:174` states that the architecture-sync gate is implemented, the rule loop is large, and many baselines are now part of release evidence.

These assets are not inherently wrong. They are evidence that the repository has accumulated a mature governance substrate. The defect is that the substrate has become too tightly coupled to ordinary AI work.

## 3. Problem Statement

The original governance intent was reasonable:

- AI agents forget rules across long sessions.
- AI agents may overfit to nearby code and miss product intent.
- AI agents may treat stale prose as current truth.
- AI agents may produce changes that pass local tests but violate architecture contracts.
- Architecture decisions need durable memory.
- Generated facts should outrank hand-authored prose when claiming implementation truth.

The current implementation overcorrects these risks.

Instead of giving AI agents the smallest sufficient context for the task, the system pushes them toward a long, mandatory learning path. Instead of treating ADRs as decision records with lifecycle state, the system allows ADRs to accumulate as current pressure. Instead of treating gates as rare hard controls, the system has allowed many historical rules, cleanup checks, and layer-purity constraints to become part of the normal delivery path.

The result is a governance model with high ceremony and uncertain marginal value.

The symptoms are:

1. High token cost before useful work begins.
2. Slow engineering throughput for small tasks.
3. Reduced AI accuracy because context is large, mixed-altitude, and historically noisy.
4. Human fatigue because every correction tends to add a new rule, enforcer, baseline, or gate.
5. ADR lock-in, where early decisions about dependencies, framework choices, module shapes, or future implementation plans become difficult to revise.
6. Gate distrust, because the gate corpus protects many things whose current risk level is unclear.
7. Rule inflation, because recurring defects are often converted into permanent process rather than temporary remediation knowledge.

The central failure mode is:

> The repository has modeled "what an AI could need to know" as "what an AI must load and obey before working."

Those are different systems.

## 4. Root Cause

The mechanical root cause is authority coupling:

```text
ADR history
  -> active decision authority
  -> rule card
  -> enforcer row
  -> gate check
  -> baseline count
  -> mandatory AI reading path
```

This chain is too easy to extend and too hard to retire.

When a defect is found, the system often responds by adding another durable artifact. That artifact may be valid during remediation, but it then becomes part of the permanent governance surface. Over time, the repository accumulates a large set of historically justified controls without an equally strong retirement mechanism.

The underlying conceptual mistake is treating governance as knowledge preservation.

Knowledge preservation should optimize for recall and explanation. Governance should optimize for risk reduction per unit of friction.

## 5. Desired Operating Model

The repository should preserve its hard-won knowledge while making governance smaller and sharper.

The target model is:

```text
AI knowledge system
  - broad
  - historical
  - searchable
  - task-routed
  - non-blocking by default
  - optimized for recall and explanation

AI governance system
  - narrow
  - current
  - explicit
  - testable
  - blocking only when justified
  - optimized for preventing high-cost failure
```

This shift should not remove product authority, generated facts, ADRs, or gates. It should change their default role.

Knowledge is available unless needed.

Governance applies only when justified.

## 6. Proposed Separation: Knowledge System vs Governance System

### 6.1 AI Knowledge System

The AI knowledge system should contain all material that helps an agent understand the repository:

- Product claims and requirements.
- Personas and user journeys.
- Architecture overview and rationale.
- ADR history and tradeoffs.
- Current ADR index and supersession state.
- EngineeringFrame and FunctionPoint maps.
- Contract catalog.
- Generated implementation facts.
- Test facts.
- Review logs.
- Recurring defect families.
- Lessons learned.
- Deprecated decisions and why they changed.

The knowledge system may be large. It should not be loaded in full by default.

Its key responsibilities are:

1. Help agents answer "what context do I need for this task?"
2. Preserve historical reasoning without making it binding.
3. Make current authority discoverable.
4. Let humans and agents trace from product intent to implementation evidence.
5. Support targeted retrieval, summarization, and task-specific context packs.

The knowledge system should be governed for quality, but not enforced as a universal work gate.

### 6.2 AI Governance System

The AI governance system should contain only the constraints that must actively shape work:

- Security invariants.
- Compatibility invariants.
- Public contract invariants.
- Data isolation and tenant safety.
- Release evidence requirements.
- Build and test honesty requirements.
- Generated-fact integrity.
- Architecture boundaries that prevent real coupling risk.
- A small set of collaboration rules that prevent recurrent high-cost agent failure.

The governance system should not contain:

- Every ADR.
- Every historical cleanup rule.
- Every past reviewer concern.
- Every implementation preference.
- Every dependency or framework choice.
- Every future roadmap assertion.
- Every documentation consistency preference.

The governance system must be difficult to expand. A new blocking rule or gate should require a clear risk statement, a current authority source, a scoped enforcement target, a retirement condition, and self-tests.

## 7. Human and AI Collaboration Model

The team should stop treating an AI agent like a new employee who must complete full onboarding before each task.

A better model is: the AI agent is a fast, stateless collaborator that needs a task-specific context pack and explicit verification responsibilities.

### 7.1 Human Responsibilities

Humans should own:

1. Product direction.
2. Risk appetite.
3. Architectural tradeoffs.
4. Which decisions remain current.
5. Which invariants deserve blocking enforcement.
6. Acceptance criteria for ambiguous or high-impact work.
7. Approval for dependency, framework, runtime, or public-contract shifts.

Humans should not have to manually police every historical rule, ADR, and gate after each task. The system should make the current decision surface clear.

### 7.2 AI Responsibilities

AI agents should own:

1. Task classification.
2. Context pack selection.
3. Surfacing assumptions.
4. Naming the likely root cause before making changes.
5. Reading generated facts when making implementation claims.
6. Keeping edits scoped.
7. Running the relevant verification commands.
8. Reporting residual risk honestly.
9. Suggesting rule or gate retirement when a control no longer protects a live invariant.

AI agents should not have to load the full product, architecture, ADR, fact, rule, enforcer, and gate corpus for ordinary work.

### 7.3 Collaboration Flow

The target interaction should look like this:

```text
Human gives task
  -> AI classifies task type and risk
  -> AI loads task-specific context pack
  -> AI states assumptions and likely root cause
  -> Human approves direction when risk is high
  -> AI edits or writes proposal
  -> AI runs scoped verification
  -> AI reports evidence and residual risk
  -> Durable learning is stored as knowledge by default
  -> Only repeated high-cost failures become governance
```

This flow preserves control without forcing every task through the full repository learning curve.

## 8. ADR Governance Reform

The ADR corpus should remain valuable, but ADRs need lifecycle state. Without lifecycle state, a historical decision can continue to behave like current authority long after its context has expired.

### 8.1 Proposed ADR Authority States

Each ADR should have exactly one authority state:

| State | Meaning | Can guide work? | Can create blocking gate? |
|---|---|---:|---:|
| `historical` | Kept as decision history; no current authority | No, except as background | No |
| `informational` | Useful rationale or context; not binding | Yes, as context | No |
| `current_decision` | Current team decision; may guide implementation | Yes | Not automatically |
| `governed_invariant` | Current decision that protects a high-risk invariant | Yes | Yes, if machine-checkable |
| `superseded` | Replaced by another ADR or policy | No | No |
| `retired` | Explicitly removed from active decision space | No | No |

The important change is that `current_decision` does not automatically mean "gate required."

### 8.2 ADR-to-Gate Rule

The previous over-designed rule can be replaced:

```text
Old rule:
Every ADR should have a corresponding gate so it takes effect.

New rule:
Every blocking gate must be backed by a current governed invariant, but not every ADR becomes a gate.
```

This keeps gates accountable without forcing every decision into executable enforcement.

### 8.3 ADR Cleanup Questions

For each ADR, ask:

1. Is this still a current decision?
2. Is it product, architecture, implementation, dependency, process, or remediation history?
3. Does it protect a live invariant or just record a past choice?
4. Does it prematurely select software, dependencies, implementation shape, or future work?
5. Has the decision been superseded by product direction, generated facts, contracts, or newer ADRs?
6. If it is current, should it guide work through context, review, or gate?
7. If it has a gate, what failure would the gate actually prevent today?

Any ADR that cannot answer these questions should be demoted out of active authority until reviewed.

## 9. Rule and Gate Cleanup

Gate and rule cleanup is not optional. Without it, reading-path reform will be cosmetic.

The repository can reduce startup cost, but if 165 active gate checks and 63 active engineering rules remain equally authoritative, the operating model will still push every correction toward more process.

### 9.1 Rule Classification

Each rule card should be classified:

| Rule class | Meaning | Default enforcement |
|---|---|---|
| `collaboration_kernel` | Small daily rules for human/AI work | Always visible, few in number |
| `safety_invariant` | Security, compatibility, tenant, release, or data-safety rule | Blocking if machine-checkable |
| `architecture_invariant` | Current structural boundary that prevents coupling or drift | Changed-files blocking or blocking by scope |
| `quality_standard` | Desired engineering habit | Advisory or review checklist |
| `remediation_scaffold` | Temporary rule introduced to clean up historical drift | Time-boxed, retired after cleanup |
| `historical_lesson` | Lesson from past defect, useful but not enforceable | Knowledge base only |
| `duplicate_or_subsumed` | Covered by another rule or gate | Retire or merge |

Rules should be retired or demoted aggressively when they do not protect a current invariant.

### 9.2 Gate Classification

Each gate check should be classified:

| Gate class | Meaning | Enforcement |
|---|---|---|
| `blocking` | Must pass for all relevant delivery because failure is high-risk | Blocks |
| `changed_files_blocking` | Applies only when touched files enter scope | Blocks scoped changes |
| `advisory` | Reports risk or drift, but does not block | Warns |
| `baseline_monitor` | Tracks counts or trends, not correctness | Reports |
| `migration_scaffold` | Temporary check for a cleanup wave | Expires |
| `retired` | Preserved for history or removed | Does not run |

The default for new gates should be `advisory`, not `blocking`.

### 9.3 Gate Admission Criteria

A gate may become blocking only if all criteria are met:

1. It protects a current `governed_invariant`.
2. The invariant has a named human owner.
3. The failure mode is concrete and high-cost.
4. The check is deterministic.
5. The check has self-tests.
6. The check has a bounded scope.
7. The check has an escape hatch or documented remediation path.
8. The check has a retirement or review condition.
9. The check does not require broad reading by the agent to understand its failure.

If a gate cannot meet these criteria, it should be advisory or knowledge-only.

### 9.4 Rule Admission Criteria

A rule may become active only if:

1. It prevents a repeated or high-cost failure.
2. It is phrased as a current behavior requirement, not a historical story.
3. It has a clear scope.
4. It does not duplicate another rule.
5. It has a known enforcement mode: human review, AI checklist, changed-files gate, full gate, or advisory.
6. It has a named owner.
7. It has a review date.

Rules without review dates should be treated as suspect during cleanup.

### 9.5 Immediate Cleanup Targets

The first cleanup pass should target:

1. Rules created to close one-time remediation waves.
2. Gates that enforce documentation altitude rather than product, runtime, safety, or compatibility risk.
3. Gates that exist only to keep baseline counts synchronized.
4. ADR-derived gates tied to early dependency or framework choices.
5. Duplicated rules across `CLAUDE.md`, `AGENTS.md`, rule cards, contracts, and templates.
6. Rules that force broad corpus reading instead of task-scoped retrieval.
7. Gates whose failure requires human interpretation but still blocks mechanically.

The cleanup should be tracked as a product-quality improvement, not as a weakening of engineering discipline.

## 10. Agent Context Loading Reform

The current reading path should be replaced or amended with task-routed context packs.

### 10.1 Current Issue

The current path is useful as a map, but too expensive as a broad default. A bugfix in a known module, a small test update, a documentation proposal, and a major architecture decision do not need the same startup context.

### 10.2 Proposed Task Classes

Each task should route to one context pack:

| Task class | Load first | Load only if needed |
|---|---|---|
| Small code fix in known file | Nearby code, generated facts for touched symbols, relevant tests | Product, ADRs, L0/L1 |
| Feature implementation | Product claim, relevant Feature/FunctionPoint, contracts, code facts, tests | Full ADR history |
| Architecture decision | Product, current ADR index, L0/L1, relevant contracts | Raw ADR prose |
| Governance cleanup | Current rule/gate inventory, ADR authority state, architecture-status | Product details |
| Contract/API change | Contract catalog, generated contract facts, compatibility tests, relevant ADRs | Full reading path |
| Documentation-only change | Target docs, authority lane policy, affected source surfaces | Generated code facts unless factual claim is made |
| Debug/regression | Failing test/log evidence, code facts, relevant tests, runtime config | Architecture prose after evidence |
| Release/commit | Changed files, required verification, release checklist | Full ADR corpus |

### 10.3 Context Loading Rule

The replacement rule should be:

```text
Load the smallest context pack that can answer the task.
Escalate context only when the task makes a claim outside the loaded authority.
Generated facts are mandatory for implementation claims, not for every conversation.
```

This preserves the fact-layer authority model while reducing token waste.

## 11. Dependency and Early Implementation Choice Policy

The repository should explicitly separate strategic constraints from early implementation choices.

An ADR may record that a dependency, framework, or module shape was selected at a point in time. That does not mean the selection must remain governed indefinitely.

### 11.1 Dependency Decision Classes

Dependency and framework decisions should be classified:

| Class | Meaning | Governance level |
|---|---|---|
| `hard_constraint` | Required by product, platform, license, security, or compatibility | Can be governed |
| `current_default` | Current team choice, replaceable with approval | Current decision, usually not gated |
| `experiment` | Used for learning or validation | Knowledge only |
| `implementation_detail` | Local choice hidden behind stable interface | Not governed |
| `retired_choice` | Historical selection no longer active | Historical |

Premature selection becomes dangerous when it is recorded as architecture authority and then protected by gates. The cleanup should find these cases and demote them unless they protect a current invariant.

### 11.2 Stable Boundary Before Stable Tool

The governance system should prefer stable boundaries over stable tools.

For example, governing an SPI boundary, public contract, data isolation rule, or compatibility promise is often valid. Governing a specific internal library, package arrangement, or early implementation technique is usually not valid unless there is a documented risk.

## 12. Proposed Remediation Workstreams

### Workstream A: Freeze Governance Expansion

Decision:

No new blocking rules, blocking gates, baseline counters, or mandatory reading-path requirements until the cleanup inventory is complete, except for security or release-critical emergencies approved by the owner.

Deliverables:

1. Temporary governance freeze note.
2. Exception process.
3. Owner for cleanup program.

Acceptance criteria:

1. New ADRs can still be written.
2. New knowledge can still be added.
3. New governance cannot become blocking without explicit exception.

### Workstream B: ADR Authority Reclassification

Decision:

Classify all ADRs by current authority state.

Deliverables:

1. ADR state taxonomy update.
2. ADR index with authority state for every ADR.
3. List of ADRs that contain dependency or premature implementation choices.
4. List of ADRs that currently back gates.
5. List of ADRs proposed for historical, informational, superseded, or retired state.

Acceptance criteria:

1. No agent is required to walk raw ADR prose for ordinary architecture work.
2. Current ADR authority is visible from an index.
3. Historical ADRs cannot create new gates.

### Workstream C: Rule Corpus Rationalization

Decision:

Classify every active rule by rule class and enforcement mode.

Deliverables:

1. Rule inventory table.
2. Owner for each active rule.
3. Review date for each active rule.
4. Retire/merge/demote proposal for duplicate or remediation-only rules.
5. Minimal collaboration kernel for daily AI work.

Acceptance criteria:

1. Daily AI collaboration rules fit in a small kernel.
2. Rules that are useful knowledge but not enforceable are moved out of active governance.
3. Every remaining active rule has scope, owner, authority, and enforcement mode.

### Workstream D: Gate Corpus Rationalization

Decision:

Classify every gate check by gate class and current risk.

Deliverables:

1. Gate inventory table.
2. Mapping from blocking gates to governed invariants.
3. Demotion list for advisory or baseline-monitor gates.
4. Retirement list for migration scaffolds and duplicate checks.
5. Changed-files scope plan for gates that should not run globally.

Acceptance criteria:

1. Every blocking gate can explain the concrete failure it prevents.
2. Gates tied only to historical cleanup are demoted or given expiration dates.
3. Gate failures are understandable without full corpus reading.
4. Baseline counters do not masquerade as correctness gates.

### Workstream E: Context Pack Redesign

Decision:

Replace broad default reading with task-routed context packs.

Deliverables:

1. Revised AI reading path.
2. Task-class to context-pack routing table.
3. Generated-fact trigger rules by claim type.
4. Updated `AGENTS.md` and `CLAUDE.md` entry instructions.
5. Examples for small bugfix, feature implementation, architecture decision, governance cleanup, and release work.

Acceptance criteria:

1. A small known-file bugfix does not require product, L0, L1, full ADR, full gate, and full fact reading.
2. Architecture decisions still load product and current ADR authority.
3. Implementation claims still cite generated facts.
4. The path remains safe but becomes proportional.

### Workstream F: Governance Admission and Retirement Policy

Decision:

Make governance harder to add and easier to retire.

Deliverables:

1. Rule admission template.
2. Gate admission template.
3. Retirement criteria.
4. Review cadence.
5. Owner model.

Acceptance criteria:

1. New blocking governance requires explicit owner, risk, scope, enforcement, self-test, and review date.
2. Retired rules and gates remain discoverable as knowledge but no longer block work.
3. Governance growth is tracked as a cost, not as an automatic sign of maturity.

## 13. Proposed Phased Plan

### Phase 0: Alignment

Goal:

Agree that the defect is authority coupling, not missing discipline.

Outputs:

1. Approve this proposal direction.
2. Name one owner for ADR cleanup, one owner for gate cleanup, and one owner for agent workflow redesign.
3. Freeze new non-emergency blocking governance.

### Phase 1: Inventory

Goal:

Build the facts needed to clean up safely.

Outputs:

1. ADR authority inventory.
2. Rule classification inventory.
3. Gate classification inventory.
4. Mandatory-reading inventory.
5. Dependency and premature-choice inventory.

No retirements are required in this phase. The purpose is visibility.

### Phase 2: Demote and Retire Low-Risk Governance

Goal:

Reduce active governance load without touching safety-critical controls.

Targets:

1. Remediation-only rules.
2. Historical cleanup gates.
3. Duplicates.
4. Baseline-only checks.
5. Documentation altitude checks that no longer protect active release risk.

Outputs:

1. Retired rule list.
2. Advisory gate list.
3. Changed-files gate list.
4. Updated architecture-status baselines.

### Phase 3: Rewrite Agent Entry Model

Goal:

Make AI startup proportional to task risk.

Outputs:

1. Revised AI reading path.
2. Revised AGENTS/CLAUDE entry instructions.
3. Context pack definitions.
4. Examples for common task types.

### Phase 4: Harden Remaining Governance

Goal:

Make the remaining blocking gates more trusted.

Outputs:

1. Gate-to-invariant map.
2. Owner and review date for each blocking gate.
3. Self-tests for remaining blocking gates.
4. Clear remediation messages for gate failures.

### Phase 5: Operating Cadence

Goal:

Prevent the same drift from recurring.

Outputs:

1. Quarterly ADR authority review.
2. Monthly gate/rule retirement review.
3. Governance budget: a target maximum for active blocking gates and always-visible rules.
4. Exception process for emergency controls.

## 14. Proposed Acceptance Metrics

The team should judge the cleanup by operational outcomes, not by document count.

Recommended metrics:

1. Median number of files an agent must read before starting a small known-file bugfix.
2. Median tokens spent before first useful action.
3. Number of always-visible collaboration rules.
4. Number of blocking gates.
5. Number of changed-files blocking gates.
6. Number of advisory gates.
7. Number of retired or demoted gates.
8. Number of ADRs classified as governed invariants.
9. Number of ADRs classified as historical or informational.
10. Number of gate failures whose error message directly names the violated invariant and remediation path.

Suggested initial target:

```text
Always-visible collaboration rules: fewer than 12
Blocking gates: only safety, compatibility, release, generated-fact integrity, and core architecture invariants
All other checks: changed-files blocking, advisory, baseline monitor, or retired
ADR governed invariants: minority of ADR corpus
Small bugfix context pack: under 8 primary files unless escalation is needed
```

The exact numbers should be decided by the team after inventory. The direction is more important than the first target.

## 15. Risks and Mitigations

### Risk: Cleanup weakens engineering discipline

Mitigation:

The proposal does not remove generated facts, contracts, tests, ADRs, or gates. It changes which controls block work. High-risk invariants remain protected. Low-risk historical controls move to knowledge, advisory checks, or scoped checks.

### Risk: Historical knowledge is lost

Mitigation:

Historical ADRs, review logs, and retired rules remain searchable. They stop acting as current authority unless re-promoted.

### Risk: Agents skip important context

Mitigation:

Task-routed context packs include escalation rules. If an agent makes a product, architecture, contract, implementation, or verification claim, it must load the relevant authority lane. Generated facts remain mandatory for implementation claims.

### Risk: Teams disagree on which gates remain blocking

Mitigation:

Use gate admission criteria. If a gate cannot name a current governed invariant, concrete failure mode, owner, self-test, and remediation path, it should not be blocking.

### Risk: Cleanup itself becomes another governance wave

Mitigation:

Time-box the work, prohibit new non-emergency blocking gates during cleanup, and measure reductions. The goal is to delete, demote, and simplify before adding anything.

## 16. Engineering Team Buy-In Argument

This proposal should be acceptable to an engineering team because it does not ask engineers to trust AI more. It asks the system to make trust cheaper and more explicit.

Engineers should not have to choose between:

- a high-ceremony governance system that slows every task, and
- an ungoverned AI workflow that produces drift.

The better option is proportional governance.

Small tasks get small context and scoped checks.

High-risk tasks get product authority, architecture authority, generated facts, contract checks, and human approval.

Historical knowledge remains available, but it does not block work by default.

Rules and gates earn their place by preventing real failures.

ADR history remains valuable, but current authority is explicit.

This is not a retreat from engineering rigor. It is a move from maximal governance to high-signal governance.

## 17. Proposed Decisions for Approval

The team should approve these decisions:

1. AI knowledge and AI governance are separate systems.
2. ADRs do not automatically create gates.
3. Blocking gates must map to current governed invariants.
4. Rules and gates must have owners, scope, enforcement mode, and review dates.
5. Historical and informational ADRs remain searchable but do not govern current work.
6. Agent startup should use task-routed context packs, not a universal broad reading path.
7. Generated facts remain authoritative for implementation claims, but are loaded by claim type and scope.
8. Gate and rule cleanup is a mandatory part of the governance rebalancing.
9. New non-emergency blocking governance is frozen until the cleanup inventory is complete.

## 18. First Concrete Next Step

Create a short inventory document with three tables:

1. ADRs by authority state.
2. Rules by rule class and enforcement mode.
3. Gates by gate class and current invariant.

Do not rewrite the full governance system first. Do not add new gates first. Do not start by arguing about every ADR.

Start by making the current authority load visible.

Once visible, the team can retire historical pressure, preserve knowledge, and keep only the governance that still earns its cost.
