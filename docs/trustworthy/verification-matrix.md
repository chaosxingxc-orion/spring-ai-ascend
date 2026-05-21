---
level: L0
view: process
status: draft
authority: "Derived from trustworthy prompt design and Layered 4+1 release discipline"
---

# Trustworthy Verification Matrix

## Purpose

This matrix defines how evidence moves upward:

- L2 release evidence validates one feature implementation.
- L2-to-L1 validation decides whether that feature can count as module
  evidence.
- L1 release evidence validates one module or interface contract.
- L1-to-L0 validation decides whether that module release can count as system
  evidence.

## L2 Release Evidence

| Evidence Item | Required Question | Accept / Reject Rule |
|---|---|---|
| Scope | Is this one feature or fix? | reject if it silently changes L1/L0 |
| Related L1 contract | Which module/interface/schema does it implement? | reject if no L1 owner |
| Code diff | Are changed files inside approved scope? | reject if unrelated modules changed |
| Tests | Are happy, negative, contract, security, and recovery paths covered? | partial if some paths are honestly deferred |
| Trust controls | Are idempotency, cancellation, timeout, authz, audit, and tenant scope handled? | reject if high-risk action lacks control |
| Verification commands | Were commands run and recorded? | partial if not run with reason |
| Rollback | Can the feature be reverted or disabled? | reject for irreversible risky changes |

## L2-to-L1 Validation

| L1 Surface | Validation Question | Evidence |
|---|---|---|
| Responsibility | Did L2 move responsibility across module boundary? | L2 diff, L1 architecture doc |
| Public API/SPI | Did L2 alter interface, schema, enum, error taxonomy, config keys? | contract catalog, schema, Java interface diff |
| Dependencies | Did L2 add forbidden dependency or indirect import path? | module metadata, dependency gate, ArchUnit |
| DFX | Did L2 change releasability/resilience/availability/vulnerability/observability? | DFX YAML, tests, runbook |
| Security/privacy | Did L2 expand data, credential, network, tenant, model, or tool scope? | security tests, config, audit events |
| AI risk | Did L2 introduce prompt/tool/context/model risk? | red-team tests, threat notes |
| Release truth | Did L2 overclaim module readiness? | release note review |

Verdict values:

- `PASS`: L1 remains valid; no L1 update needed.
- `PASS_WITH_L1_UPDATES_REQUIRED`: implementation is acceptable, but L1 docs,
  DFX, schema, or tests must update before L1 release.
- `BLOCKED`: L2 violates L1 or lacks critical evidence.

## L1 Release Evidence

| Evidence Item | Required Question | Accept / Reject Rule |
|---|---|---|
| Module scope | Which module/interface is being released? | reject if mixed with system release |
| L2 rollup | Which L2 releases are included? | reject if L2 evidence was not validated |
| Contract truth | Do SPI/schema/catalog/module metadata agree? | reject on drift |
| DFX truth | Are DFX fields evidence-backed? | reject vague pending on production path |
| Boundary truth | Do allowed/forbidden dependencies and deployment plane hold? | reject if boundary moved silently |
| Security truth | Are tenant/data/credential/audit controls valid? | reject if expanded without review |
| AI-risk truth | Are AI threats constrained or promoted to L0? | reject unowned threat |

## L1-to-L0 Validation

| L0 Surface | Validation Question | Evidence |
|---|---|---|
| Capability mapping | Does the module still map to the same L0 block? | L0 architecture, L1 release |
| Topology | Did plane or cross-plane route change? | module metadata, architecture graph |
| Composition resilience | Did timeout/retry/backpressure/DLQ/recovery semantics change? | contracts, DFX, tests |
| Security boundary | Did trust, credential, model, tool, context, or network boundary expand? | threat model, policy/gate evidence |
| Tenant/data isolation | Did tenant/data classification or residency change? | tests, schema, audit |
| Governance truth | Are ADRs, rules, graph, baseline metrics, release notes synchronized? | gate output, graph header |
| Competitive pillars | Did performance/cost/onboarding/governance regress? | competitive-baselines.yaml |
| Release truth | Does L0 prose overclaim shipped status? | release note review |

Verdict values:

- `PASS`: L0 still holds; L1 evidence can support system claims.
- `PASS_WITH_L0_UPDATES_REQUIRED`: L1 is valid, but L0/ADR/rule/graph/release
  surfaces must update before system release.
- `BLOCKED`: L1 release contradicts or weakens L0.

## Evidence Promotion Rules

1. Evidence may move upward only after explicit validation.
2. Design-only evidence may support planning but not shipped claims.
3. A test name without a runnable or cited test path is not release evidence.
4. A rule without an enforcer or review checklist is governance intent, not a
   runtime control.
5. A release note must not convert future-tense design into present-tense
   shipped capability.

## Minimal Review Checklist

Use this checklist for every release:

- [ ] Layer is declared: L0, L1, or L2.
- [ ] Scope and non-scope are explicit.
- [ ] Public contracts are listed.
- [ ] DFX impact is stated.
- [ ] Trust boundary changes are stated.
- [ ] Deployment plane impact is stated.
- [ ] AI-specific risks are stated.
- [ ] Evidence paths resolve.
- [ ] Verification commands are recorded.
- [ ] Upward validation verdict is recorded.
