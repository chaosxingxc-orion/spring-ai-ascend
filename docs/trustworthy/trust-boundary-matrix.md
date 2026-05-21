---
level: L0
view: physical
status: draft
authority: "Derived from ARCHITECTURE.md threat model, contracts, DFX YAML, and trustworthy review"
---

# Trust Boundary Matrix

## Purpose

This matrix makes cross-plane and cross-module trust boundaries reviewable in
one place. It does not replace the contract catalog or module architecture
documents. It indexes the boundaries that must be enforced by L1 contracts and
proved by L2 evidence.

## Boundary Classification

| Class | Meaning | Default Rule |
|---|---|---|
| `external_untrusted` | User, client app, browser, external business system | parse, authenticate, authorize, rate-limit, audit |
| `platform_edge` | Platform-owned edge SDK or gateway surface | still untrusted for policy decisions |
| `internal_control` | Cross-plane control traffic | signed/correlated, tenant-bound, replay-controlled |
| `internal_data` | Payload or artifact movement | pointer preferred, classify data, redact where needed |
| `runtime_extension` | SPI, middleware, engine adapter, memory adapter | no hidden authority; least privilege |
| `offline_evolution` | training/evaluation/improvement path | opt-in, retention-bound, poisoning-aware |
| `operator_control` | admin/runbook/emergency action | strong identity, approval, immutable audit |

## Current Boundary Matrix

| Boundary | Caller | Callee | Class | Required Controls | Evidence Today | Gap |
|---|---|---|---|---|---|---|
| Client request to ingress | external caller / SDK | `IngressGateway` / HTTP edge | `external_untrusted` | authn, authz, tenant binding, idempotency, replay control, audit | OpenAPI, ingress envelope, filters | SDK-side runtime evidence pending |
| Edge to bus ingress | `agent-client` | `agent-bus.spi.ingress` | `platform_edge` | cursor, correlation, tenant, timeout, retry budget | ingress contract | W3 SDK contract pending |
| Bus to compute control | `agent-bus` | `agent-service` / runtime | `internal_control` | route ownership, ordering, cancellation, saturation, audit | ADRs/contracts | runtime bus implementation deferred |
| Compute to engine | `agent-service` | `agent-execution-engine` | `runtime_extension` | engine envelope, strict matching, no fallback reinterpretation | engine-envelope schema, EngineRegistry tests | failure-to-Run-state mapping needs integrated evidence |
| Compute to middleware | runtime paths | `agent-middleware` | `runtime_extension` | mandatory hook emission, deterministic hook outcome, audit/telemetry path | hook SPI, engine-hooks schema | conformance coverage must grow with W2 runtime |
| Compute to S2C callback | `agent-service` / `agent-bus` | client callback transport | `internal_control` | tenant/correlation, callback id, retry, cancellation, audit | S2C schema | delivery semantics pending |
| Runtime to memory | `agent-service` | GraphMemory SPI/starter | `runtime_extension` | tenant scope, data classification, retention, redaction | GraphMemoryRepository SPI, DFX | real adapter data-governance evidence pending |
| Runtime to tools/sandbox | compute control | sandbox executor / tool | `runtime_extension` | sandbox, permission envelope, output isolation, audit | ADR-0018, DFX | production sandbox deferred |
| Runtime to evolution | compute control | `agent-evolve` | `offline_evolution` | explicit export, opt-in, retention, poisoning checks | evolution-scope schema | skeleton; runtime evidence absent |
| Operator action | human/operator | platform control surfaces | `operator_control` | strong identity, approval, tamper-evident audit, runbook | runbooks exist | audit vertical not fully runtime-enforced |

## Required Metadata Per Boundary

Every boundary should eventually declare:

- caller and callee;
- owning L1 module;
- public contract or SPI;
- trust class;
- credential type;
- authorization policy;
- tenant binding rule;
- data classification;
- timeout and retry budget;
- idempotency or replay control;
- cancellation behavior;
- error taxonomy;
- metric name;
- audit event;
- redaction/retention rule;
- release evidence path.

## AI-Specific Boundary Rules

1. Untrusted context must not modify policy, permissions, system prompts, tool
   scope, release verdicts, or audit conclusions.
2. Model output must be treated as data until parsed, validated, and bound to
   an approved action.
3. Tool input must be derived from validated state, not raw prompt text.
4. Fallback to another model/provider/engine must preserve tenant, policy,
   audit, budget, and safety controls.
5. Any context crossing into memory or evolution must carry classification,
   retention, redaction, and provenance.

## L1 Review Questions

1. Did this module add a new boundary?
2. Did this module widen credential, network, tenant, data, model, or tool
   scope?
3. Is the boundary represented in contract catalog, schema, DFX, or module
   metadata?
4. Is there at least one negative/security test for the boundary?
5. Is the boundary auditable in production posture?

