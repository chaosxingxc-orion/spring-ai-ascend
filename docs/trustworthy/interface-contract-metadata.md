---
level: L1
view: logical
status: draft
authority: "Derived from docs/contracts, module-metadata.yaml, DFX YAML, and L1 readiness review"
---

# Interface Contract Metadata

## Purpose

The repository already has schema-first contracts. This document defines the
additional runtime metadata expected for enterprise-grade L1 interfaces.

Schema shape answers "what is the message?" Contract metadata answers "how is
the message safely operated?"

## Required Metadata Fields

| Field | Required Meaning |
|---|---|
| `owner_module` | L1 module accountable for this contract |
| `exposure_tier` | external, edge, internal-control, internal-data, extension, offline |
| `data_classification` | tenant, operational, audit, model, tool, memory, public |
| `authn` | identity source and verification method |
| `authz` | policy or permission boundary |
| `tenant_binding` | where tenant id comes from and how mismatch is rejected |
| `idempotency_key` | required, optional, or not applicable with reason |
| `ordering_key` | ordering scope if messages can race |
| `timeout_budget` | caller timeout and callee expectation |
| `retry_budget` | max retries, backoff, and retryable errors |
| `cancellation` | cancellation propagation and terminal behavior |
| `error_taxonomy` | typed error families and compatibility rules |
| `audit_event` | audit event name and required fields |
| `metric` | metric name and labels |
| `compatibility` | backward/forward compatibility expectation |
| `deprecation` | deprecation and removal policy |
| `rollback` | behavior if a new contract version is disabled |

## Initial Contract Metadata Sketch

| Contract | Owner | Exposure | Critical Metadata To Add |
|---|---|---|---|
| `openapi-v1.yaml` | `agent-service` | external / customer-facing | authn/authz, tenant binding, idempotency, error taxonomy, audit event per endpoint |
| `ingress-envelope.v1.yaml` | `agent-bus` | edge to bus | idempotency key, cursor semantics, ordering key, retry budget, audit fields |
| `s2c-callback.v1.yaml` | `agent-bus` | bus to client callback | callback id, timeout, retry, terminal behavior, cancellation, audit |
| `engine-envelope.v1.yaml` | `agent-execution-engine` | internal extension | strict matching, no fallback reinterpretation, failure taxonomy, metric |
| `engine-hooks.v1.yaml` | `agent-middleware` | internal extension | mandatory hook points, hook outcome semantics, audit/telemetry emission |
| `plan-projection.v1.yaml` | future planner/bus owner | design-only | runtime binding, state ownership, recovery, versioning |

## Contract Metadata Template

```yaml
contract: ""
version: ""
owner_module: ""
exposure_tier: ""
data_classification: []
authn: ""
authz: ""
tenant_binding:
  source: ""
  mismatch_behavior: ""
idempotency_key:
  required: true
  scope: ""
ordering_key:
  required: false
  scope: ""
timeout_budget: ""
retry_budget:
  max_attempts: 0
  retryable_errors: []
  backoff: ""
cancellation: ""
error_taxonomy: []
audit_event:
  name: ""
  required_fields: []
metric:
  name: ""
  labels: []
compatibility: ""
deprecation: ""
rollback: ""
evidence:
  tests: []
  gates: []
  docs: []
```

## L2 Change Triggers

An L2 change must trigger L2-to-L1 validation when it changes:

- public SPI method signature;
- schema field, enum, requiredness, or compatibility;
- error taxonomy;
- idempotency behavior;
- timeout, retry, ordering, or cancellation behavior;
- tenant, data, credential, model, tool, or network scope;
- audit event, metric, or log field used as release evidence.

## Acceptance Rule

A contract may be design-complete but not production-ready. Production-facing
L1 release requires both schema truth and metadata truth.

