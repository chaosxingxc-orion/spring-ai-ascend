---
level: L1
view: process
status: draft
authority: "Derived from docs/dfx/*.yaml and L1 trustworthy review"
---

# DFX Evidence Status Policy

## Purpose

The repository already maintains DFX YAML per module. This policy tightens DFX
status vocabulary so DFX cannot become a vague compliance placeholder.

## Status Vocabulary

| Status | Meaning | May Support Production Claim? |
|---|---|---|
| `implemented` | Implementation exists, but evidence may be limited | only with cited evidence |
| `test_verified` | Implementation has runnable tests or gate evidence | yes |
| `design_only` | Architecture/design is accepted, runtime is not shipped | no |
| `deferred_with_trigger` | Deferred with owner, trigger, wave, and impact | no |
| `not_applicable_with_reason` | Not applicable and reason is explicit | yes, for exclusion only |
| `blocked_evidence_gap` | Required evidence is missing | no |

Avoid open-ended `pending` for any production-facing path.

## Required Fields For Deferred Items

Every deferred DFX claim must state:

- owner;
- target wave or trigger;
- reason for deferral;
- production impact;
- release blocker status;
- evidence required for promotion;
- related ADR/rule/contract.

## DFX Promotion Rules

1. `design_only` can move to `implemented` only after code path exists.
2. `implemented` can move to `test_verified` only after runnable evidence is
   cited.
3. `deferred_with_trigger` must not be described as shipped.
4. `pending` must be converted before L1 release.
5. Production-facing modules cannot pass L1 release with `blocked_evidence_gap`.

## Current High-Priority Conversions

| Module | Current Concern | Suggested Conversion |
|---|---|---|
| `agent-bus` | multiple pending availability/resilience/observability fields | `design_only` or `deferred_with_trigger` until runtime bus lands |
| `agent-client` | SDK skeleton pending retry/timeout/cancel/trace | `deferred_with_trigger` tied to W3 SDK release |
| `agent-evolve` | skeleton pending offline controls | `deferred_with_trigger` tied to first evolution activation |
| `agent-service` | audit/hook/durable readiness deferred | split by field into `implemented`, `design_only`, or `deferred_with_trigger` |
| graph memory starter | adapter/data governance deferred | `design_only` until real adapter evidence exists |

## L1 Release Gate

An L1 release should reject:

- vague `pending` on customer-facing or operator-critical paths;
- DFX claims with no evidence link;
- design-only controls used as runtime controls;
- deferred controls without trigger, owner, or production impact;
- observability claims that are only logs but not metrics/audit where audit is
  required.

