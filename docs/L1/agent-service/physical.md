---
level: L1
view: physical
module: agent-service
status: active
authority: "ADR-0143 (rc55 — canonical 4+1 source moved here) + ADR-0078 (consolidation) + ADR-0101 (rc26 — Mode A platform-centric + Mode B business-centric deployment loci) + ADR-0138 (rc53 — 5-layer L1 ratification) + ADR-0141 (rc55 — Internal Event Queue physical-channel binding per bus-channels.yaml) + Rule R-E (three-track channel isolation) + Rule R-I (five-plane manifest) + Rule R-J.a (Postgres RLS per tenant_id column) + Rule R-L (sandbox policy subsumption)"
---

# agent-service — Physical View

> Authoring source: rc53 review file §17 (`docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md`), ported in rc55 W4. The canonical bindings to `bus-channels.yaml` (Rule R-E), `sandbox-policies.yaml` (Rule R-L), and Flyway migrations under `agent-service/src/main/resources/db/migration/` are made explicit.

## 1. Five-Plane Deployment Mapping (Rule R-I)

| Plane | Modules deployed | agent-service responsibility | Posture defaults |
|---|---|---|---|
| **Edge Access** | (agent-client SDK, W3+ — not yet shipped) | Receives inbound HTTP/gRPC/A2A. Layer 1 Access Layer is the inbound boundary FOR THIS MODULE. No edge-only code lives in agent-service. | n/a (agent-service is not deployed on the edge plane) |
| **Compute & Control** | `agent-service` (this module), `agent-execution-engine` (consumed cross-module) | Hosts Layer 1-5. Runs the Run aggregate, RunStateMachine CAS, all Orchestrator/ExecutorAdapter execution. `deployment_plane: compute_control` per `agent-service/module-metadata.yaml`. | Posture-gated startup per `PostureBootGuard` (ADR-0058): research/prod fail-closed if any required-config matrix entry is missing. |
| **Bus & State Hub** | `agent-bus` (consumed cross-module) | Layer 3 Internal Event Queue `(design_only — ADR-0141)` is a BINDING layer over agent-bus's three physical channels. No bus-side code lives in agent-service yet. | n/a (agent-service binds to but does not host the bus plane) |
| **Sandbox Execution** | `agent-middleware` (consumed cross-module — Sandbox SPI) | agent-service routes untrusted code execution requests to the Sandbox SPI in agent-middleware. Layer 4 RuntimeMiddleware chain enforces the route. | Posture-gated; research/prod reject any sandbox-bypass attempt at boot per `PostureBootGuard`. |
| **Evolution** | `agent-evolve` (consumed cross-module — SlowTrackJudge SPI rc26) | agent-service emits RunEvent variants (per ADR-0145) with `EvolutionExport` discriminator; the Evolution plane consumes IN_SCOPE / OPT_IN events for the SlowTrackJudge feedback loop. OUT_OF_SCOPE events MUST NOT be persisted by the evolution plane (Rule R-M.e). | n/a (agent-service produces; agent-evolve consumes) |

**Dual-Locus Deployment** (per ADR-0101):

- **Mode A (Platform-Centric)**: `agent-service` deploys on platform
  infrastructure (Kubernetes / VM / serverless per W4+). The
  Compute & Control plane is hosted by the platform team. This is the
  default deployment mode.
- **Mode B (Business-Centric)**: `agent-service` deploys on the
  business department's servers / client devices alongside
  `agent-execution-engine` for zero-latency local execution loops.
  The Compute & Control plane is hosted by the business unit. Used
  for latency-critical or data-sovereignty-sensitive scenarios.

In both modes the Run aggregate ownership pinning (ADR-0142) holds:
Layer 2 is the SINGLE writer; Layer 4 invokes via
`RunRepository.updateIfNotTerminal(...)`. The Layer 2 backend (Postgres
in Mode A; SQLite or embedded in Mode B) MUST implement the
`updateIfNotTerminal` abstract method as an atomic primitive
(`ON CONFLICT` / `UPDATE … WHERE` / `ConcurrentHashMap.computeIfPresent`
respectively).

## 2. Database Schema + RLS Policy (Rule R-J.a)

| Table | Primary key | tenant_id column | RLS policy | Flyway migration | Status |
|---|---|---|---|---|---|
| `idempotency_dedup` | `(tenant_id, idempotency_key)` | NOT NULL | grandfathered in `gate/rls-baseline-grandfathered.txt` pending W2 retrofit (Rule R-J.a.b deferred) | `agent-service/src/main/resources/db/migration/V2__idempotency_dedup.sql` | **shipped W1** |
| `runs` | `run_id (UUID)` | NOT NULL | RLS policy required at table-creation time per Rule R-J.a | `V?__runs.sql` (W2 — when durable RunRepository lands) | **W2-deferred** |
| `tasks` | `task_id (UUID)` | NOT NULL | RLS policy required | `V?__tasks.sql` (W2 — when Task persistence lands) | **W2-deferred** |
| `sessions` | `session_id (UUID)` | NOT NULL | RLS policy required | `V?__sessions.sql` (W2 — when Session persistence lands) | **W2-deferred** |
| `lifecycle_state_audit` | `(run_id, occurred_at)` or `(task_id, occurred_at)` | NOT NULL (derived from FK) | RLS policy required; emission shape per `RunStateTransitionEvent` (ADR-0145) | `V?__lifecycle_state_audit.sql` (W2+) | **W2-deferred** |

**Session-level tenant binding** (W2-deferred): `SET LOCAL app.tenant_id`
GUC via R2DBC at the start of every transaction; RLS policies use
`current_setting('app.tenant_id')` to scope rows. Cross-tenant access
returns empty rowset (NOT an error) — the access fails at the
authorization layer (Rule R-J.b) BEFORE the row would be visible
anyway, so cross-tenant SELECTs are defense-in-depth.

**Atomic CAS contract** (Rule R-C.2.b + ADR-0142 + ADR-0118): the
durable `RunRepository.updateIfNotTerminal` MUST implement the CAS
as a single SQL `UPDATE … WHERE tenant_id = ? AND run_id = ? AND status
NOT IN (CANCELLED, SUCCEEDED, FAILED, EXPIRED)` statement. The
in-memory `InMemoryRunRegistry` (W0 reference impl) uses
`ConcurrentHashMap.computeIfPresent` for the equivalent atomic
primitive.

## 3. Three-Track Bus Bindings (Rule R-E + bus-channels.yaml)

The Layer 3 Internal Event Queue `(design_only — ADR-0141; no code
home at rc55)` binds to the canonical three physical channels declared
in [`docs/governance/bus-channels.yaml`](../../governance/bus-channels.yaml).
Per Rule R-E + Principle P-E, **physical isolation** (channel choice)
and **durability tier** (per-channel backend choice) are ORTHOGONAL
axes; PR #71's single-tier-queue-with-3-storage-modes design was
rejected at the L1 design layer (ADR-0138 §3 red-line c).

| Channel | `physical_channel` id | Intent | Durability tier (W0/W1) | Durability tier (W2+) | RunEvent variants routed here (per ADR-0145) |
|---|---|---|---|---|---|
| `control` | (per bus-channels.yaml) | Highest priority; out-of-band. Cancel, resume, suspend, S2C callback requests. | In-memory stub | Durable (e.g. Kafka topic w/ priority) | `SuspendRequestedEvent`, `ResumeRequestedEvent`, `CancelRequestedEvent`, `S2cCallbackRequestedEvent` |
| `data` | (per bus-channels.yaml) | In-band; heavy-load. Payload-bearing events: state transitions, S2C responses, child-run completion. | In-memory stub | Durable (e.g. Kafka topic w/ partitioning) | `RunCreatedEvent`, `RunStateTransitionEvent`, `S2cCallbackCompletedEvent`, `ChildRunSpawnedEvent`, `ChildRunCompletedEvent`, `TerminalTransitionEvent` |
| `rhythm` | (per bus-channels.yaml) | Heartbeat / liveness ticks; Tick Engine for long-horizon Run timers (per Rule R-H Chronos Hydration). | In-memory tick scheduler | Durable timer service (e.g. Temporal) | None — rhythm channel carries Tick Engine pulses, not aggregate-level RunEvents |

**Inline payload cap** (Rule R-E §4 #13): the `data` channel inherits
the 16 KiB inline-payload cap. Larger payloads (e.g. large S2C
response payloads) MUST be stored out-of-band (object store) with the
channel carrying only the reference URI.

**Routing discipline** (per `docs/contracts/run-event.v1.yaml`
`channel_routing` block): every RunEvent variant declares its channel
routing at the contract layer; the future Layer 3 code home enforces
the routing via the channel-specific Producer SPI.

## 4. Sandbox Isolation Boundary (Rule R-L)

agent-service does NOT host any sandbox execution code; it routes
untrusted-code execution requests to the Sandbox SPI in
`agent-middleware`. The boundary is enforced at the L4 RuntimeMiddleware
chain via `HookPoint.before_tool` events.

**Sandbox policy subsumption** (per
[`docs/governance/sandbox-policies.yaml`](../../governance/sandbox-policies.yaml)):
the per-skill policy MUST NOT widen the default policy beyond what
the physical sandbox can enforce. Rule R-L declares the 6 required
keys (`outbound_network`, `filesystem_read`, `filesystem_write`,
`cpu_cap_millicores`, `memory_cap_megabytes`, `wall_clock_cap_seconds`).

**Runtime refusal of over-wide grants** (Rule R-L.b):
*(W2-deferred — runtime SandboxExecutor refusal not yet
implemented; the design-time enforcement is the YAML subsumption
check at boot)*. At W0/W1, the design discipline is review-time
only; W2 ships the runtime `SandboxExecutor.refuseOverWideGrant(...)`
check.

## 5. Cross-Plane Tenant Propagation (Rule R-J.a + R-C.2.a)

```mermaid
flowchart LR
    edge["Edge Access<br/>(W3+ agent-client SDK)"]
    cc["Compute & Control<br/>(agent-service + agent-execution-engine)"]
    bus["Bus & State Hub<br/>(agent-bus 3-track channels)"]
    sandbox["Sandbox Execution<br/>(agent-middleware)"]
    evolution["Evolution<br/>(agent-evolve)"]

    edge -- "X-Tenant-Id header + JWT tenant_id claim<br/>(cross-check per ADR-0040)" --> cc
    cc -- "tenant_id propagated via RunContext.tenantId()<br/>(NEVER TenantContextHolder per Rule R-C.e)" --> cc
    cc -- "RunEvent.tenantId field (Rule R-C.2.a)<br/>+ IngressEnvelope.tenant_id<br/>+ S2cCallbackEnvelope.tenant_id" --> bus
    cc -- "ToolInvocationRequest.tenantId<br/>(future SPI; W2+)" --> sandbox
    cc -- "RunEvent.tenantId + EvolutionExport=IN_SCOPE/OPT_IN<br/>(Rule R-M.e)" --> evolution
    bus -- "tenant_id in every envelope" --> cc
    sandbox -- "ToolResult with original tenantId echo" --> cc
    evolution -- "SlowTrackJudge feedback bound to tenantId" --> cc
```

**Tenant propagation invariants** (cross-plane red lines):

1. **No anonymous events**: every cross-plane envelope carries
   `tenant_id`. Validated at producer side by `Objects.requireNonNull`
   (Rule R-C.2.a discipline applied to envelope construction).
2. **No TenantContextHolder leakage**: the `service.runtime.**`
   sub-package MUST NOT import `TenantContextHolder` (Rule R-C.e;
   enforced by `TenantPropagationPurityTest` ArchUnit +
   `ServiceRuntimeMustNotDependOnServicePlatformTest`). Tenant
   propagation in async / cross-plane paths sources from
   `RunContext.tenantId()` (sourced from persisted Run, not ThreadLocal).
3. **No tenant inference**: cross-plane consumers MUST NOT infer
   tenant from any other field; the `tenant_id` field is the
   authoritative source.
4. **Cross-tenant blocked at boundary**: cross-tenant access collapses
   to 404 not_found at W0 per Rule R-J.b (the W1 widening to 403
   `tenant_mismatch` + WARN audit is deferred per ADR-0108).

## 6. Cross-references

- Scenarios: [`scenarios.md`](scenarios.md) — S1-S5 cross-plane
  interactions and red-line invariants.
- Logical: [`logical.md`](logical.md) §1 — 5-layer model + 5a/5b split
  per ADR-0140; the Compute & Control plane hosts all 5 layers.
- Process: [`process.md`](process.md) — sequence diagrams P1-P6 show
  cross-plane interaction at the message level.
- Development: [`development.md`](development.md) — package tree +
  layer↔package matrix.
- Module-root grounding: [`agent-service/ARCHITECTURE.md`](../../../agent-service/ARCHITECTURE.md)
  §4 OSS dependencies + §6 Posture-aware defaults.
- Governance manifests: [`bus-channels.yaml`](../../governance/bus-channels.yaml),
  [`sandbox-policies.yaml`](../../governance/sandbox-policies.yaml),
  [`skill-capacity.yaml`](../../governance/skill-capacity.yaml).
