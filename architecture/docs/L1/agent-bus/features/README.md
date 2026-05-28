---
level: L1
view: scenarios
status: shipped
authority: "ADR-0151 (L1 Feature Registry canonical schema) + ADR-0152 (uniform L1 + W3 catalog rendering)"
---

<!-- DO NOT HAND-EDIT. Rendered from architecture/features/features.dsl by gate/lib/render_features_catalog.py. Re-emit via that script; render-idempotency is enforced by Rule G-13.b. -->

# `agent-bus` — L1 Feature Catalog (9-section)

This catalog is the **rendered** human-readable view of the
`agent-bus`-owned features registered in
[`architecture/features/features.dsl`](../../../../features/features.dsl).
The structured source is the DSL; this Markdown is byte-identical
on re-emit. The 9 sections follow the user-supplied L1 Feature
Catalog template (ADR-0151).

## 1. Feature Metadata

| Feature ID | Name | Status | Capability Domain |
|---|---|---|---|
| `FEAT-EDGE-COMPUTE-INGRESS` | Edge to Compute Ingress | `design_only` | `edge-compute-routing` |
| `FEAT-SERVER-CLIENT-CALLBACK` | Server to Client Callback | `shipped` | `s2c-callback-protocol` |

## 2. Architecture Binding

### `FEAT-EDGE-COMPUTE-INGRESS`

**Development paths:**
- `agent-bus/src/main/java/com/huawei/ascend/bus/spi/ingress`

**Source ADR:** `ADR-0049`

### `FEAT-SERVER-CLIENT-CALLBACK`

**Development paths:**
- `agent-bus/src/main/java/com/huawei/ascend/bus/spi/s2c`
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/s2c`

**Source ADR:** `ADR-0088`

## 3. Functional Decomposition

This module's features and their function-point membership are wired
by `contains` relationships in
[`architecture/features/features.dsl`](../../../../features/features.dsl).
Walk the workspace projection from each feature ID to traverse the
function-point inventory.

- `FEAT-EDGE-COMPUTE-INGRESS` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.
- `FEAT-SERVER-CLIENT-CALLBACK` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.

## 4. Contract Surface

Runtime promise surfaces touched by this module's features. For the
full catalog, see
[`docs/contracts/contract-catalog.md`](../../../../../docs/contracts/contract-catalog.md).

## 5. Runtime Behavior

### `FEAT-EDGE-COMPUTE-INGRESS`

Owns the edge-plane to compute_control ingress channel: every edge-originated request reaches the runtime exclusively through IngressGateway SPI, with the wire envelope governed by ingress-envelope.v1.yaml. Rule R-I.1 forbids edge modules from directly importing service/engine/middleware production code; the gateway is the single hop. W1 enforcement is ArchUnit-only at design_only status; full runtime adapter ships when the agent-client SDK lands (post-W3 per ADR-0049).

### `FEAT-SERVER-CLIENT-CALLBACK`

Owns the server-to-client callback path: when a Run needs a client-side capability invocation, the runtime emits S2cCallbackEnvelope through S2cCallbackTransport SPI; the calling Run suspends via SuspendSignal.forClientCallback and the client response (validated against s2c-callback.v1.yaml) resumes the Run. The transport SPI lives in agent-bus to keep service free of edge-direction transport concerns; the envelope schema is the runtime promise surface.

## 6. DFX Requirements

DFX dimensions for `agent-bus` are declared in
[`docs/dfx/agent-bus.yaml`](../../../../../docs/dfx/agent-bus.yaml).
Per-feature DFX deltas (if any) are tracked alongside the FEAT-
element in `architecture/features/features.dsl`.

## 7. AI Execution Boundary

Machine-readable AI boundary per feature (5 saa.aiBoundary.* sub-keys).
AI agents acting on this module MUST consult these before auto-modifying:

| Feature | Can modify code | Can modify contracts | Allowed transitions | Requires human review at | Sandbox policy |
|---|---|---|---|---|---|
| `FEAT-EDGE-COMPUTE-INGRESS` | `true` | `false` | `design_only->ready_for_impl, ready_for_impl->implemented_unverified, implemented_unverified->test_verified, test_verified->shipped` | `test_verified, shipped, deprecated` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-SERVER-CLIENT-CALLBACK` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |

## 8. Verification Matrix

Tests + commands that verify each feature. AI agents MUST run these
commands after auto-modifying the feature's owning code.

### `FEAT-EDGE-COMPUTE-INGRESS`

**Verification test FQNs:**
- `com.huawei.ascend.bus.spi.ingress.EdgeToComputeDirectLinkArchTest`

**Verification commands:**
- `./mvnw -pl agent-bus -am verify`
- `bash gate/check_architecture_sync.sh`

### `FEAT-SERVER-CLIENT-CALLBACK`

**Verification test FQNs:**
- `com.huawei.ascend.service.runtime.s2c.S2cCallbackRoundTripIT`
- `com.huawei.ascend.service.runtime.s2c.S2cFailureTransitionsRunToFailedIT`
- `com.huawei.ascend.service.runtime.s2c.S2cCallbackEnvelopeValidationTest`

**Verification commands:**
- `./mvnw -pl agent-service -am verify`

## 9. Lifecycle / Governance

Feature lifecycle state machine (Rule G-14):

```
proposed -> accepted -> design_only -> ready_for_impl
                                    -> implemented_unverified
                                    -> test_verified -> shipped
                                    -> deprecated -> removed
```

Current state per feature:

- `FEAT-EDGE-COMPUTE-INGRESS` — `design_only`
- `FEAT-SERVER-CLIENT-CALLBACK` — `shipped`

Status transitions are governed by Rule G-14 (advisory at W1, blocking
at W5 after soak). Forward-only by default; backward transitions
require an ADR `extends:` or `relates_to:` the feature's source ADR.

