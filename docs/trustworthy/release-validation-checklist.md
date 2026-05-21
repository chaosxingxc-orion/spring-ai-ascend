---
level: L0
view: process
status: draft
authority: "Derived from verification-matrix.md and trustworthy prompt playbook"
---

# Release Validation Checklist

## Purpose

This checklist turns the verification matrix into a practical PR/release flow.
Use it to decide whether evidence can move upward:

- L2 evidence validates a feature.
- L2-to-L1 validation decides whether the module contract still holds.
- L1 evidence validates a module/interface.
- L1-to-L0 validation decides whether system claims still hold.

## L2 PR Checklist

- [ ] Layer is declared as L2.
- [ ] Related L1 module/interface/schema is named.
- [ ] Scope and non-scope are explicit.
- [ ] Public SPI/schema/enum/config/error taxonomy changes are declared.
- [ ] Tenant, data, credential, network, model, tool, and memory scope changes
      are declared.
- [ ] Timeout, retry, cancellation, idempotency, ordering, and rollback behavior
      are covered or marked not applicable.
- [ ] Happy, negative, contract, security, and recovery tests are present or
      explicitly deferred with reason.
- [ ] AI-specific risk families are assessed.
- [ ] Verification commands are recorded.
- [ ] Release verdict is `PASS`, `PARTIAL`, or `BLOCKED`.

## L2-to-L1 Validation Checklist

- [ ] L2 stayed inside the L1 responsibility boundary.
- [ ] Public contract metadata still matches implementation.
- [ ] Module dependencies still satisfy allowed/forbidden rules.
- [ ] DFX claims are updated when behavior changes.
- [ ] Security/privacy controls are still valid.
- [ ] AI risk controls are still valid.
- [ ] Release note does not overclaim L1 readiness.
- [ ] Verdict is `PASS`, `PASS_WITH_L1_UPDATES_REQUIRED`, or `BLOCKED`.

## L1 Release Checklist

- [ ] Module responsibility and non-responsibility are current.
- [ ] Runtime role and deployment plane are current.
- [ ] SPI/schema/catalog/module metadata agree.
- [ ] Interface contract metadata is complete for production-facing surfaces.
- [ ] DFX statuses use approved vocabulary.
- [ ] Boundary, dependency, security, AI risk, and observability evidence is
      cited.
- [ ] Included L2 releases passed L2-to-L1 validation.
- [ ] Rollback and compatibility are documented.

## L1-to-L0 Validation Checklist

- [ ] Module still maps to the same L0 capability block.
- [ ] Deployment plane and cross-plane routes did not silently change.
- [ ] Trust boundary, failure boundary, and data boundary did not silently
      expand.
- [ ] Continuity class and SLO/RTO/RPO assumptions still hold.
- [ ] Governance surfaces are synchronized: ADR, rule, DFX, contract, graph,
      release note.
- [ ] No design-only/deferred control is represented as shipped.
- [ ] Verdict is `PASS`, `PASS_WITH_L0_UPDATES_REQUIRED`, or `BLOCKED`.

## Release Verdict Definitions

| Verdict | Meaning |
|---|---|
| `PASS` | Evidence is sufficient for the stated layer and scope |
| `PARTIAL` | Evidence is useful but cannot support full release claim |
| `PASS_WITH_L1_UPDATES_REQUIRED` | L2 is acceptable, but L1 docs/contracts/DFX must update |
| `PASS_WITH_L0_UPDATES_REQUIRED` | L1 is acceptable, but L0/ADR/rule/release surfaces must update |
| `BLOCKED` | Critical evidence or contract alignment is missing |

## Minimal PR Comment Template

```text
Layer:
Scope:
Related L1 contract:
Public contract changes:
Trust boundary changes:
DFX impact:
AI risk impact:
Tests / verification:
Deferred evidence:
Rollback:
Upward validation verdict:
```

