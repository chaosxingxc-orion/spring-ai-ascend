---
level: L1
view: logical
module: agent-evolve
status: skeleton
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0075 (Evolution scope default boundary); Layer-0 principle P-I (Five-Plane Distributed Topology); Rule R-M.e (Evolution Scope Default Boundary, formerly Rule 47)"
---

# agent-evolve — L1 architecture (skeleton)

> Owner: AgentEvolve team | Wave: W3+ | Maturity: skeleton (deferred)
> Created: 2026-05-17 (six-module materialization PR)

## Status

**This module is a deferred skeleton.** The Evolution plane hosts
Python ML / offline improvement loops; the Java side is just an adapter
shell. Bulk implementation is deferred indefinitely per
`CLAUDE-deferred.md` and the archived design under
`docs/v6-rationale/agent-runtime/evolve/`.

What *is* shipped today: the `EvolutionExport` discriminator
(`IN_SCOPE | OUT_OF_SCOPE | OPT_IN`) declared in
`docs/governance/evolution-scope.v1.yaml` (Rule R-M.e (formerly Rule 47) / P-M, gate Rule 59).
Pre-Phase-C this lived in `agent-runtime/evolution/`; post-ADR-0078 / ADR-0079
the EvolutionExport discriminator is consumed by `agent-service` (the
consolidated runtime module) and the eventual Java adapter for the evolution
plane will land here in `agent-evolve` once the W3 evolution scope ships.

## 0.4 Layered 4+1 view map

Only the **logical** view is meaningful at this stage.

| Section | View | Notes |
|---|---|---|
| §1 Role | logical | Evolution plane Java adapter |
| §2 Scope | logical | EvolutionExport discriminator + telemetry-export ref |

## 1. Role (target)

`agent-evolve` will be the **Java-side adapter** between the runtime's
emitted `RunEvent`s and the Python ML pipeline. It will:

- Honour the `EvolutionExport` discriminator (in-scope events flow to
  the evolution plane; out-of-scope events stay on the
  compute/control plane).
- Forward opt-in events to the future `telemetry-export.v1.yaml`
  contract (W3 placeholder).
- Provide health probes the Python ML pipeline can observe.

## 2. Forbidden today

- No Python in this module — Python lives in a sibling sub-project not
  managed by Maven.
- No direct LLM gateway calls — evolution is offline.
- No runtime-state mutations — read-only consumer of emitted events.

## Reading order for new contributors

1. `module-metadata.yaml` — identity + dependency promises.
2. `docs/governance/evolution-scope.v1.yaml` — discriminator schema.
3. `docs/governance/evolution-modalities.yaml` — Offline / Online modality SSOT (rc22 / ADR-0102).
4. `docs/contracts/reflection-envelope.v1.yaml` — online-evolution S2C envelope (rc22 / ADR-0102, status: design_only).
5. `docs/dfx/agent-evolve.yaml` — Design-for-X declarations.
6. ADR-0075 + ADR-0102 + `docs/v6-rationale/agent-runtime/evolve/` — historical design notes + online/offline duality.

---

## 3. Online / Offline Modality (rc22 / ADR-0102)

Two modalities coexist per `docs/governance/evolution-modalities.yaml`:

| Modality | Triggers | Update mechanism | Implementation wave |
|---|---|---|---|
| **Offline (T+1)** | run_completion + scheduled_batch | explicit_version_release | W2 |
| **Online (Dual-Track)** | per-run trajectory critique | reflection_envelope_s2c | W3 |

Online mode runs two tracks:
- **Fast Track (System 1)** — rapid user-facing execution.
- **Slow Track (System 2 / LLM-as-Judge)** — real-time trajectory critique.

Optimizations injected back into the active agent's short/long-term memory dynamically via `ReflectionEnvelope` S2C updates (over `agent-bus` transport per ADR-0074; contract `docs/contracts/reflection-envelope.v1.yaml`).

### 3.1 Mode × Modality 2×2 matrix

| Modality | Platform-Centric (Mode A) | Business-Centric (Mode B) |
|---|---|---|
| Offline (T+1) | Fully Centralized | Edge Collect / Cloud Optimize |
| Online (Dual-Track) | In-Cluster Async | **Heaven-Earth Coordination** (Fast on edge, Slow in cloud, ReflectionEnvelope hot-patches edge) |

Authority: `docs/governance/evolution-modalities.yaml#matrix`.

---

## 4. Development View (Rule G-1.1.a — rc22 / ADR-0099)

Target directory tree (current namespace; rc22.5 migrates to `com.huawei.ascend.*` per ADR-0104):

```text
agent-evolve/
└── src/main/java/
    └── ascend/springai/evolve/    <!-- root-migration-target: com.huawei.ascend.agent.evolve -->
        ├── package-info.java                # placeholder; W3 implementation
        └── (W3+: SlowTrackJudge, ReflectionPatchHandler, OfflineExportAdapter; see ADR-0102 timeline)
```

Mode-A (Platform-Centric per ADR-0101): `agent-evolve` on the platform.
Mode-B (Business-Centric per ADR-0101): `agent-evolve` STILL on the platform; only the data flow changes (business edge emits PII-filtered trace logs to cloud; cloud Slow Track judges; ReflectionEnvelope flows back to edge via S2C).

## *SPI Interface Appendix* (Rule G-1.1.b — rc22 / ADR-0099)

`agent-evolve` produces NO Java SPI today. Its current shipped surface is the `EvolutionExport` discriminator declared in `docs/governance/evolution-scope.v1.yaml` (enum: `IN_SCOPE | OUT_OF_SCOPE | OPT_IN`, consumed via `RunEvent.evolutionExport()` field per Rule R-M.e).

Future SPI surface (rc26 per ADR-0102 timeline):

| Future FQN | SPI package | Purpose |
|---|---|---|
| `ascend.springai.evolve.online.spi.SlowTrackJudge` | `evolve.online.spi` | LLM-as-Judge contract; fires on AFTER_LLM_INVOCATION hook |
| `ascend.springai.evolve.online.spi.ReflectionEnvelopeRouter` | `evolve.online.spi` | S2C delivery of ReflectionEnvelope to active Session |
| `ascend.springai.evolve.offline.spi.OfflineExportAdapter` | `evolve.offline.spi` | PII-filtered trace log emission (T+1 batch) |

## *L2 Constraint Linkage* (Rule G-1.1.c — rc22 / ADR-0099)

Vacuously green at rc22. The W3 online evolution work (Slow Track judge + ReflectionEnvelope routing) WILL warrant an L2 design — it MUST include Boundary Contracts (input: full trajectory; output: ReflectionEnvelope; DFX: ≤500ms median Slow-Track latency, ≥0.7 confidence threshold for auto-apply).

## Deployment loci (rc22 / ADR-0101)

`deployment_loci: [platform_centric]` — `agent-evolve` always lives on the platform regardless of mode.
