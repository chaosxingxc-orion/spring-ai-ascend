---
level: L1
view: logical
module: agent-evolve
status: active
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0075 (Evolution scope default boundary); ADR-0102 (online/offline evolution duality); Layer-0 principle P-I (Five-Plane Distributed Topology); Rule R-M.e (Evolution Scope Default Boundary)"
---

# agent-evolve — L1 architecture

> **Altitude discipline (L1).** This module-root file is the
> **shipped-state grounding** surface — purpose, the evolution boundary
> identity, the public-SPI surface (named, with generated-fact refs),
> dependencies, deployment loci, and status. It deliberately does NOT
> carry code-level detail: the `RunEvent` field that carries the export
> discriminator, the Slow-Track judge call chain and its latency/confidence
> thresholds, the `ReflectionEnvelope` wire shape, and concrete test-class
> inventories are **L2 / contract / verification** material. The export
> discriminator schema lives in
> [`evolution-scope.v1.yaml`](../../../../docs/governance/evolution-scope.v1.yaml),
> the online/offline modality SSOT in
> [`evolution-modalities.yaml`](../../../../docs/governance/evolution-modalities.yaml),
> and the online-evolution envelope in
> [`reflection-envelope.v1.yaml`](../../../../docs/contracts/reflection-envelope.v1.yaml).

## Status

`agent-evolve` is **active**: the `SlowTrackJudge` SPI is shipped (the
LLM-as-Judge contract for online evolution, ADR-0102), so the module carries
`active` status per Rule M-1 (a module with extracted production code MUST
NOT carry `skeleton` status). The Evolution plane still hosts the Python ML
/ offline improvement loops externally; the Java side here ships the online
evolution interface and the export discriminator. The bulk Java adapter
(offline export + reflection patch handling) is deferred per the legacy
entries in `docs/governance/escalations.md` and the archived design under
`docs/v6-rationale/agent-runtime/evolve/`.

What *is* shipped today:

- The `EvolutionExport` discriminator (`IN_SCOPE | OUT_OF_SCOPE | OPT_IN`)
  declared in
  [`evolution-scope.v1.yaml`](../../../../docs/governance/evolution-scope.v1.yaml)
  (Rule R-M.e / P-M, gate Rule 59). It is carried on the run-event surface
  owned by `agent-service` (the consolidated runtime module per ADR-0078 /
  ADR-0079); the field-level binding is a contract concern, not restated
  here.
- The `SlowTrackJudge` SPI under `com.huawei.ascend.evolve.online.spi`.

## 0.4 Layered 4+1 view map

Only the **logical** view is meaningful at this stage.

| Section | View | Notes |
|---|---|---|
| §1 Role | logical | Evolution plane Java adapter |
| §2 Scope | logical | `EvolutionExport` discriminator + online judge SPI |

## 1. Role (target)

`agent-evolve` is the **Java-side adapter** between the runtime's emitted
run events and the Python ML pipeline. It:

- honours the `EvolutionExport` discriminator (in-scope events flow to the
  evolution plane; out-of-scope events stay on the compute-control plane);
- forwards opt-in events to the future telemetry-export contract (planned);
- hosts the online `SlowTrackJudge` SPI (LLM-as-Judge trajectory critique);
- provides health probes the Python ML pipeline can observe.

## 2. Forbidden today

- No Python in this module — Python lives in a sibling sub-project not
  managed by Maven.
- No direct LLM gateway calls — evolution is offline by default.
- No runtime-state mutations — read-only consumer of emitted events.

## Online / Offline modality (ADR-0102)

Two modalities coexist per
[`evolution-modalities.yaml`](../../../../docs/governance/evolution-modalities.yaml):

| Modality | Triggers | Update mechanism |
|---|---|---|
| **Offline (T+1)** | run completion + scheduled batch | explicit version release |
| **Online (Dual-Track)** | per-run trajectory critique | reflection-envelope S2C |

Online mode runs two tracks: a **Fast Track (System 1)** for rapid
user-facing execution, and a **Slow Track (System 2 / LLM-as-Judge)** for
real-time trajectory critique. Critique results are injected back into the
active agent's memory via `ReflectionEnvelope` S2C updates carried over the
`agent-bus` S2C transport (ADR-0074; contract
[`reflection-envelope.v1.yaml`](../../../../docs/contracts/reflection-envelope.v1.yaml)).
The envelope wire shape and the judge's outcome chain are owned downstream
and are not restated here.

### Mode × Modality matrix

| Modality | Platform-Centric (Mode A) | Business-Centric (Mode B) |
|---|---|---|
| Offline (T+1) | Fully centralized | Edge collect / cloud optimize |
| Online (Dual-Track) | In-cluster async | Fast on edge, Slow in cloud, `ReflectionEnvelope` hot-patches edge |

Authority: `evolution-modalities.yaml#matrix`.

## Dependencies

Dependency versions are managed by the parent POM and the
`spring-ai-ascend-dependencies` BoM; module files do not duplicate version
pins.

| Direction | Dependency | Reason |
|---|---|---|
| forbidden | `agent-service`, `agent-execution-engine`, `agent-middleware`, `agent-bus`, `agent-client` | the evolution plane is a downstream read-only consumer of emitted events; direct inbound dependencies are forbidden per `module-metadata.yaml#forbidden_dependencies` (Rule R-C module dependency direction). The `ReflectionEnvelope` S2C transport is owned by `agent-bus`, consumed across the contract, not via a Maven edge. |

## Deployment loci

`deployment_loci: [platform_centric]` — `agent-evolve` always lives on the
platform regardless of mode (ADR-0101). In Mode-B only the data flow
changes: the business edge emits PII-filtered trace logs to the cloud, the
cloud Slow Track judges, and the `ReflectionEnvelope` flows back to the edge
over S2C.

## Tests

The test inventory is **verification material**, owned by the verification
layer and the generated facts `architecture/facts/generated/tests.json`; it
is not enumerated here. Three-layer testing discipline per Rule D-4.

## Reading order for new contributors

1. `module-metadata.yaml` — identity + dependency promises.
2. [`evolution-scope.v1.yaml`](../../../../docs/governance/evolution-scope.v1.yaml) — the export discriminator schema.
3. [`evolution-modalities.yaml`](../../../../docs/governance/evolution-modalities.yaml) — Offline / Online modality SSOT.
4. [`reflection-envelope.v1.yaml`](../../../../docs/contracts/reflection-envelope.v1.yaml) — online-evolution S2C envelope (status `design_only`).
5. `docs/dfx/agent-evolve.yaml` — Design-for-X declarations.
6. ADR-0075 + ADR-0102 + `docs/v6-rationale/agent-runtime/evolve/` — module authority + online/offline duality.

---

## 3. Development View (Rule G-1.1.a)

Package decomposition (the type inventory under each package is owned by the
generated code facts, `architecture/facts/generated/code-symbols.json`, and
is not restated here):

```text
agent-evolve/
└── src/main/java/
    └── com/huawei/ascend/evolve/
        └── online/
            └── spi/        # online-evolution SPI (SlowTrackJudge, ADR-0102)
```

Mode-A (Platform-Centric per ADR-0101): `agent-evolve` on the platform.
Mode-B (Business-Centric per ADR-0101): `agent-evolve` STILL on the
platform; only the data flow changes (business edge emits PII-filtered
trace logs to the cloud; cloud Slow Track judges; `ReflectionEnvelope` flows
back to the edge via S2C).

## *SPI Interface Appendix* (Rule G-1.1.b)

`agent-evolve` publishes the online-evolution SPI package. This table names
the boundary interface and its generated-fact ref; method signatures and
carrier field sets are owned by the generated code facts and are not
restated here:

| Interface FQN | SPI package | Generated-fact ref | Status |
|---|---|---|---|
| `com.huawei.ascend.evolve.online.spi.SlowTrackJudge` | `evolve.online.spi` | [`code-symbol/com-huawei-ascend-evolve-online-spi-slowtrackjudge`](../../../../architecture/facts/generated/code-symbols.json) | shipped |

The `EvolutionExport` discriminator is a governance-vocabulary surface
declared in
[`evolution-scope.v1.yaml`](../../../../docs/governance/evolution-scope.v1.yaml)
(enum `IN_SCOPE | OUT_OF_SCOPE | OPT_IN`), consumed on the run-event surface
owned by `agent-service` per Rule R-M.e; it is not a Java SPI of this module.
`ReflectionEnvelopeRouter` is owned by `agent-bus`
(`com.huawei.ascend.bus.spi.s2c`) — it is the S2C transport surface, not an
evolution-plane SPI. A future offline-export adapter SPI is contemplated but
not declared in `module-metadata.yaml#spi_packages`; when shipped it
registers there first, then appears here.

## *L2 Constraint Linkage* (Rule G-1.1.c)

No L2 design is authored for this module today (vacuously green). The online
evolution work (Slow Track judge + `ReflectionEnvelope` routing) is the
likely first L2 zone; when authored it MUST declare its inputs (the full
trajectory), its output (the `ReflectionEnvelope`), and its DFX obligations
(Slow-Track latency budget + the confidence threshold for auto-apply) as a
Boundary Contract.
