---
level: L0
view: process
status: draft
authority: "Derived from trustworthy report, DFX declarations, and release process evidence"
---

# Trustworthy Operating Model

## Operating Principle

Trustworthy architecture is not a document type. It is a lifecycle discipline:
plan, design, build, review, release, deploy, operate, and improve must each
carry explicit evidence.

## Lifecycle Model

| Phase | Trustworthy Question | Evidence Required |
|---|---|---|
| Plan | Is the change scoped to the correct layer? | L0/L1/L2 scope statement, assumptions, non-scope |
| Design | Are trust boundaries and failure paths explicit? | boundary contract, failure model, security model, DFX impact |
| Build | Does implementation preserve the contract? | code diff, tests, schema checks, ArchUnit/gate output |
| Review | Did we find bugs, drift, or missing evidence? | review findings, accepted/rejected evidence |
| Release | What exactly is shipped at this layer? | release evidence, test commands, known gaps |
| Deploy | Is the deployment plane safe for this posture? | plane controls, secrets, network, rollout/rollback |
| Operate | Can operators observe, audit, and respond? | metrics, traces, audit events, runbook, alerts |
| Improve | Did incidents or reviews update the system? | ADR/rule/DFX updates, recurring-defect-family entries |

## Architecture Dimension

Architecture trust is satisfied when:

- L0 stays system-level.
- L1 binds module responsibilities and public contracts.
- L2 proves implementation and test evidence.
- Architecture graph, ADRs, rule cards, DFX, and contract catalog remain
  synchronized.

Failure pattern:

- L0 claims a capability as shipped, but L1/L2 evidence is design-only.
- L1 names an SPI, but the SPI is absent from module metadata or contract
  catalog.
- L2 changes public behavior without L1 release review.

## Deployment Dimension

Deployment trust is satisfied when every deployment plane declares:

- allowed ingress and egress;
- credential and secret scope;
- tenant/data isolation mechanism;
- fail-closed posture;
- scaling and blast-radius limit;
- rollout and rollback path;
- operational owner.

Plane-specific notes:

| Plane | Deployment Trust Focus |
|---|---|
| edge | no direct compute-control binding, trace/cursor propagation, client credential boundaries |
| compute_control | Run lifecycle, idempotency, tenant isolation, runtime posture, policy hooks |
| bus_state | ordering, backpressure, DLQ, callback correlation, mailbox fairness |
| sandbox | untrusted code execution, filesystem/network limits, resource caps |
| evolution | export filtering, opt-in, retention, poisoning resistance |
| none | build-time or BOM-only; must not imply runtime exposure |

## Development Dimension

Development trust is satisfied when:

- every change starts with layer scope;
- public contract changes are schema-first;
- tests and gates are updated in the same change;
- L2 release evidence is validated against L1;
- L1 release evidence is validated against L0;
- missing evidence blocks release.

Recommended developer rule:

> A feature is not done when code compiles. A feature is done when the layer it
> changes can prove its contract still holds.

## Operations Dimension

Operations trust is satisfied when:

- every high-risk action emits an audit event;
- every long-running path has cancellation and recovery semantics;
- every critical path has metrics and alerting;
- every deployment plane has a runbook;
- every incident or adversarial review becomes a recurring-defect-family or
  ADR/rule update when the pattern can recur.

Operational evidence states:

| State | Meaning |
|---|---|
| design_only | intent is documented; no runtime enforcement |
| implemented_unverified | code exists; evidence is insufficient |
| test_verified | tests/gates prove the behavior in CI/local verification |
| runtime_enforced | production path enforces the control |
| operationalized | alerts/runbooks/incident workflows exist |

## AI-Specific Operating Risks

| Risk | L0 Responsibility | L1 Responsibility | L2 Responsibility |
|---|---|---|---|
| Prompt injection | name system-wide forbidden bypasses | isolate untrusted context per interface | add adversarial tests |
| Tool poisoning | define tool trust boundary | validate tool schemas and grants | test poisoned tool output |
| Context leakage | define context/data classifications | enforce context minimization | test redaction and provenance |
| Model fallback bypass | define provider routing invariant | enforce fallback policy at interface | test fallback denial/approval |
| Hallucinated code/config | require evidence-first release | constrain config/schema surfaces | test generated claims against files |
| Cost exhaustion | define budget/quotas | expose cost controls per module | test retry/budget limits |

## Release Discipline

Layer release claims must be narrow:

- L2 release: "this feature evidence is ready."
- L1 release: "this module/interface contract is ready."
- L0 release: "this system claim is ready."

No lower layer may claim readiness for a higher layer without the upward
validation step.
