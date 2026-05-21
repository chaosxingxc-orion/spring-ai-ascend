---
level: L0
view: scenarios
status: draft
authority: "Derived from ARCHITECTURE.md, module metadata, DFX declarations, contract catalog, and rc19 release note"
---

# Trustworthy Architecture Assessment

## Executive Verdict

spring-ai-ascend is not weak on governance. It is unusually strong on
governance mechanics: rule cards, enforcers, ADRs, release-note truth gates,
architecture graph generation, recurring-defect-family tracking, module
metadata, DFX declarations, and schema-first contracts.

The main architecture risk is therefore different: the repository can become
governance-heavy while still leaving trustworthy execution concerns fragmented
across L0 prose, L1 module documents, DFX YAML, contracts, and release notes.
The next improvement should make trustworthy decomposition explicit across
architecture, deployment, development, and operations.

## Repository Facts Used

- Current reactor count: 8 modules.
- Six substantive planes/modules: `agent-client`, `agent-service`,
  `agent-execution-engine`, `agent-bus`, `agent-middleware`, `agent-evolve`.
- Support modules: `spring-ai-ascend-dependencies`,
  `spring-ai-ascend-graphmemory-starter`.
- L0 root: `ARCHITECTURE.md`, Layered 4+1, 65 constraints.
- L1 roots: per-module `ARCHITECTURE.md`, `module-metadata.yaml`, and
  `docs/dfx/<module>.yaml`.
- Contract roots: `docs/contracts/contract-catalog.md` and schema YAML files.
- Latest L0 release reviewed: rc19, focused on recursive governance hardening.

## Core Architecture Issues

### A1. Trustworthy Concerns Are Present but Scattered

Security, reliability, resilience, audit, tenant isolation, posture, telemetry,
and governance are all present. However, reviewers must jump between
`ARCHITECTURE.md`, `CLAUDE.md`, ADRs, DFX YAML, contract catalog, enforcers, and
release notes to answer a simple question:

"For this system capability, what makes it trustworthy, and what evidence
proves it?"

Risk: trustworthy claims become correct locally but hard to audit globally.

Recommended design response:

- Maintain this `docs/trustworthy/` corpus as the cross-cutting index.
- Promote only stable obligations into ADR/rule/DFX/contract authority.
- Use the verification matrix to force evidence movement upward from L2 to L1
  and from L1 to L0.

### A2. L0 Is Strong but Overloaded

L0 already contains system boundaries, cross-cutting verticals, module layout,
threat model, 65 architectural constraints, roadmap pointers, and release
history. It is valuable, but it also carries a risk: L0 may absorb details that
belong at L1 or L2.

Risk: system-level architecture becomes a catch-all ledger, making future L1/L2
changes slower and harder to reason about.

Recommended design response:

- Keep L0 limited to capability blocks, system invariants, trust boundaries,
  deployment planes, and cross-module failure/security rules.
- Move interface mechanics, concrete package structure, and per-module DFX
  evidence to L1.
- Move class-level sequences and implementation choices to L2.

### A3. L1 Grounding Is Uneven

`agent-service` has a mature and detailed L1 architecture document. Other L1
surfaces are thinner, especially skeleton or extraction-stage modules. A
proposal already exists for L1 architecture depth and grounding. This is the
right direction.

Risk: L2 implementation can become the de facto source of truth when L1 is too
thin to constrain it.

Recommended design response:

- Each L1 document should contain:
  - module responsibility and explicit non-responsibility;
  - runtime plane and exposure tier;
  - SPI/interface appendix;
  - development-view package map;
  - DFX evidence links;
  - L2 boundary contracts for delegated features.

### A4. DFX Exists but Some Declarations Are Still Placeholder-Heavy

DFX YAML exists for the major modules. Some modules still carry `pending`
status across availability, vulnerability, observability, or resilience.

Risk: DFX becomes a compliance stub instead of an executable release gate.

Recommended design response:

- Convert each pending DFX field into one of:
  - `implemented`;
  - `design_only`;
  - `deferred_with_trigger`;
  - `not_applicable_with_reason`.
- Require L1 release review to reject vague `pending` for production-facing
  paths.

### A5. Deployment Trust Is Partly Encoded but Not Yet Unified

The architecture has deployment planes (`edge`, `compute_control`, `bus_state`,
`sandbox`, `evolution`, `none`) and posture (`dev`, `research`, `prod`). These
are strong primitives. What is missing is a single deployment trust model that
connects plane, posture, blast radius, secret access, network scope, and
rollback expectations.

Risk: a module can be correctly assigned to a plane while its deployment
controls remain implicit.

Recommended design response:

- Define deployment trust per plane:
  - allowed ingress/egress;
  - credential scope;
  - data classification;
  - scaling and isolation model;
  - fail-closed conditions;
  - rollback and emergency-disable mechanism.

### A6. Operations Controls Need Stronger Runtime Closure

Telemetry, audit, metrics, and trace propagation are described. Some runtime
audit and hook enforcement remains deferred. This is acceptable while honestly
marked, but it must not be treated as shipped operational control.

Risk: release notes and architecture prose may accidentally treat design-only
operational controls as runtime-enforced controls.

Recommended design response:

- Split every operational control into:
  - design obligation;
  - implementation path;
  - runtime enforcement;
  - operational runbook;
  - alert/SLO evidence;
  - incident replay evidence.

### A7. AI-Specific Threats Need a Systematic L0/L1/L2 Mapping

Prompt injection, tool poisoning, context leakage, model fallback, hallucinated
code/config, and cost exhaustion are present in the trustworthy report, but
not yet mapped across all modules and deployment planes.

Risk: AI threats remain generic guidance rather than module-specific release
criteria.

Recommended design response:

- L0 owns system-level AI threat families and forbidden bypasses.
- L1 owns module-specific controls and interface constraints.
- L2 owns implementation tests and red-team regressions.

## Dimension-by-Dimension Review

| Dimension | Current Strength | Primary Gap | Next Design Move |
|---|---|---|---|
| Trustworthy | Strong principles and gates | Cross-cutting index missing | Maintain `docs/trustworthy/` and promote stable rules |
| Architecture | Strong L0 + ADR graph | L0 overloaded; L1 uneven | Enforce L1 depth and L2 linkage |
| Deployment | Planes and posture exist | Plane-to-control matrix missing | Add deployment trust model |
| Development | Rules and gates strong | L2-to-L1 evidence closure informal | Use release validation matrix |
| Operations | Telemetry/audit concepts present | Runtime enforcement uneven/deferred | Split design vs runtime vs runbook evidence |
| Security | Tenant/posture/SPI controls strong | AI threat mapping incomplete | Add per-module AI threat controls |
| Governance | Extremely strong | Risk of governance recursion | Keep rules tied to runtime or review evidence |

## Recommended Priority

1. Establish L0/L1 trustworthy decomposition.
2. Enforce L1 depth and grounding.
3. Add deployment trust model per plane.
4. Add L2-to-L1 and L1-to-L0 validation gates/checklists.
5. Convert DFX pending fields into explicit evidence states.
6. Map AI-specific threat families to module-level controls.
