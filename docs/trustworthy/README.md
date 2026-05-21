---
level: L0
view: scenarios
status: draft
authority: "Derived from docs/reports/trustworthy-ai-code-architecture-design.zh.md"
---

# Trustworthy Architecture Corpus

This directory collects trustworthy architecture design material for
spring-ai-ascend. It does not replace the existing L0/L1/L2 corpus, ADRs,
DFX declarations, contract catalog, or governance gates. It provides a
cross-cutting review lens over them.

## Why This Exists

The repository already has strong governance machinery:

- L0 root architecture with 65 constraints.
- Layered 4+1 discipline across L0, L1, and L2.
- ADRs, rule cards, enforcers, architecture graph, baseline metrics, and
  recurring-defect-family tracking.
- Module metadata and DFX YAML per reactor module.
- Contract catalog and schema-first YAML contracts.

The remaining trustworthy gap is not absence of governance. The gap is that
trustworthy concerns are distributed across many authority surfaces and are
not yet easy to review as one operating model.

## Document Map

| Document | Purpose |
|---|---|
| [architecture-assessment.md](architecture-assessment.md) | Architecture issue review across trustworthy, architecture, deployment, development, and operations dimensions. |
| [deployment-plane-contract.md](deployment-plane-contract.md) | L0 deployment-plane contract covering deployable units, state ownership, failure domains, recovery, rollback, and continuity classes. |
| [trust-boundary-matrix.md](trust-boundary-matrix.md) | Cross-plane and cross-module trust-boundary index for caller/callee, credentials, tenant binding, replay control, audit, and AI-specific boundary rules. |
| [l0-l1-decomposition.md](l0-l1-decomposition.md) | L0 and L1 decomposition model, with module-level trust responsibilities. |
| [interface-contract-metadata.md](interface-contract-metadata.md) | L1 runtime metadata expected for SPI/schema contracts, including timeout, retry, idempotency, cancellation, audit, metrics, and compatibility. |
| [ai-risk-control-map.md](ai-risk-control-map.md) | Mapping of AI-specific risk families to L0 rules, L1 module controls, and L2 evidence. |
| [dfx-evidence-status-policy.md](dfx-evidence-status-policy.md) | Status vocabulary and release rules for turning DFX pending/deferred claims into evidence-backed states. |
| [operating-model.md](operating-model.md) | Trustworthy lifecycle model across plan, design, build, review, release, deploy, operate, and improve. |
| [prompt-playbook.md](prompt-playbook.md) | Copy-ready prompts for L0 design, L1 design, L2 plan/design/build/review/release, L2-to-L1 validation, L1 release, and L1-to-L0 validation. |
| [release-validation-checklist.md](release-validation-checklist.md) | Practical PR/release checklist for L2 evidence, L2-to-L1 validation, L1 release, and L1-to-L0 validation. |
| [verification-matrix.md](verification-matrix.md) | Evidence matrix for L2-to-L1 and L1-to-L0 validation. |

## Relationship to Existing Authority

- L0 authority remains `ARCHITECTURE.md`.
- L1 authority remains each `agent-*/ARCHITECTURE.md`, `module-metadata.yaml`,
  and `docs/dfx/<module>.yaml`.
- Contract authority remains `docs/contracts/contract-catalog.md` and
  `docs/contracts/*.yaml`.
- Governance authority remains `CLAUDE.md`, `docs/governance/*.yaml`, rule
  cards, enforcers, and architecture graph.
- This directory is a review and decomposition layer. Any normative change
  discovered here must be promoted through ADR/rule/contract/DFX updates.

## Current Assessment

The repository has a strong L0 governance engine, but the next trustworthy
architecture step is to make L1 and L2 grounding stricter:

1. L0 must remain system-level and avoid absorbing module-internal protocol
   detail.
2. L1 documents must become the binding bridge between L0 claims and L2
   implementation.
3. L2 releases must prove upward compatibility with L1 before being treated as
   module evidence.
4. L1 releases must prove upward compatibility with L0 before being treated as
   system evidence.
5. Deployment and operations controls must be reviewed as first-class
   trustworthy concerns, not just implementation follow-ups.
