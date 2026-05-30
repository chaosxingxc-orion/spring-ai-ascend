---
level: L1
view: scenarios
status: active
authority: "ADR-0157 (EngineeringFrame Ontology / dual-track) + ADR-0161 (Card-over-DSL) + ADR-0154 (Fact-Layer Authority)"
---

# AI Understanding Map — readable companion

This is the **prose surface** of
[`ai-understanding-map.yaml`](ai-understanding-map.yaml). The YAML is the
queryable structured form; this file is the human/RAG-readable rendering of the
same content. They MUST stay consistent (same IDs, same statuses); on any
divergence the YAML — and above it the DSL and generated facts — win.

> **This map is a readable interpretation layer, not an authority.** It invents
> no ID and no relationship. Every ID it carries is declared upstream in the
> authority cascade and is verified to resolve there. Conflicts resolve toward
> generated facts: **generated facts > DSL > Card/prose > this map** (ADR-0161).

It fulfils [`docs/governance/ai-reading-path.yaml`](../../docs/governance/ai-reading-path.yaml)
step 5 (`demand_to_behavior_mapping`): *"Explicit queryable dual-track map
(value/structure/join/evidence/decision/governance axes per FunctionPoint)."*

## The three axes (and the dual-track join)

| Axis | Chain | Authority surface |
|---|---|---|
| **Value** | ProductClaim → Requirement (ISO 29148) → Feature → FunctionPoint | `architecture/features/features.dsl` (`requires` edge) |
| **Structure** | Module → EngineeringFrame → FunctionPoint | `architecture/features/engineering-frames.dsl` (`anchors` edge) |
| **Evidence** | FunctionPoint → Contract → Generated Fact → Gate | `architecture/facts/generated/*.json` (cite fact IDs) |

The **FunctionPoint** is the join object where the value axis meets the
structure axis. A **Feature drives** FunctionPoints (`requires`); it does **not**
structurally own them. An **EngineeringFrame anchors** FunctionPoints; it is the
structural home. A Feature **traverses** the frames its FunctionPoints land in
(`traverses`) — the value axis read as a route across the structural map
(ADR-0157 §2). The structural axis is **claim-agnostic** (ADR-0157 §3): frames
serve no ProductClaim directly; product claims bind to the value axis.

## Structure axis — Module → EngineeringFrame → FunctionPoint

The six fixed domain modules + the BoM + the graphmemory starter own the
EngineeringFrames. A `design_only` frame with zero anchored shipped
FunctionPoints is legitimate (ADR-0157 §3).

| EngineeringFrame | Owner module | Status | Source ADR | Anchors FunctionPoints |
|---|---|---|---|---|
| EF-INGRESS-GATEWAY | agent-bus | shipped | ADR-0157 | FP-INGRESS-ENVELOPE |
| EF-S2C-TRANSPORT | agent-bus | shipped | ADR-0157 | FP-S2C-CALLBACK |
| EF-CHANNEL-ISOLATION | agent-bus | design_only | ADR-0157 | — |
| EF-ENGINE-PORT | agent-bus | design_only | ADR-0158 | — |
| EF-ORCHESTRATION-SPI | agent-bus | design_only | ADR-0157 \| ADR-0158 | — |
| EF-ENGINE-REGISTRY | agent-execution-engine | shipped | ADR-0157 | FP-ENGINE-DISPATCH |
| EF-HOOK-SURFACE | agent-middleware | shipped | ADR-0157 | FP-HOOK-DISPATCH |
| EF-CAPABILITY-SPI | agent-middleware | design_only | ADR-0157 | — |
| EF-CLIENT-INGRESS-ADAPTER | agent-client | design_only | ADR-0157 | — |
| EF-EVOLUTION-EXPORT | agent-evolve | design_only | ADR-0157 | — |
| EF-GRAPHMEMORY-AUTOCONFIG | graphmemory-starter | design_only | ADR-0157 | FP-GRAPH-MEMORY-STORE |
| EF-ACCESS-ADMISSION | agent-service | shipped | ADR-0157 | FP-CREATE-RUN, FP-GET-RUN-STATUS, FP-IDEMPOTENCY-CLAIM, FP-TENANT-CROSS-CHECK, FP-POSTURE-BOOT-GUARD, FP-A2A-MESSAGE-SEND·, FP-A2A-TASKS-CANCEL·, FP-A2A-TASKS-RESUBSCRIBE·, FP-MQ-INBOUND· |
| EF-ENGINE-DISPATCH | agent-service | design_only | ADR-0157 | — |
| EF-INTERNAL-EVENT-QUEUE | agent-service | design_only | ADR-0157 | — |
| EF-SESSION-TASK-STATE | agent-service | shipped | ADR-0157 | FP-RUN-STATE-TRANSITION |
| EF-TASK-CONTROL | agent-service | shipped | ADR-0157 | FP-CANCEL-RUN, FP-SUSPEND-RESUME, FP-CHILD-RUN-SPAWN |
| EF-TRANSLATION-INTERCEPT | agent-service | design_only | ADR-0157 | — |

`·` marks the four `design_only` FunctionPoints (ADR-0155 A2A/MQ ingress) that
EF-ACCESS-ADMISSION anchors but that no value-thread Feature drives yet.

## Value axis — Feature → FunctionPoint (`requires`) → EngineeringFrame (`traverses`)

Nine value-thread Features (the six former agent-service "Layer features" are now
EngineeringFrames, not Features — ADR-0157 §4). Each Feature satisfies an
ISO-29148 requirement.

| Feature | Owner | Status | Req | Requires FunctionPoints | Traverses Frames |
|---|---|---|---|---|---|
| FEAT-RUN-LIFECYCLE-CONTROL | agent-service | shipped | REQ-001 | FP-CREATE-RUN, FP-CANCEL-RUN, FP-GET-RUN-STATUS, FP-RUN-STATE-TRANSITION | EF-ACCESS-ADMISSION, EF-TASK-CONTROL, EF-SESSION-TASK-STATE |
| FEAT-EDGE-COMPUTE-INGRESS | agent-bus | design_only | REQ-002 | FP-INGRESS-ENVELOPE | EF-INGRESS-GATEWAY |
| FEAT-SERVER-CLIENT-CALLBACK | agent-bus | shipped | REQ-003 | FP-S2C-CALLBACK | EF-S2C-TRANSPORT |
| FEAT-SUSPEND-RESUME-CONTROL | agent-service | shipped | REQ-004 | FP-SUSPEND-RESUME, FP-CHILD-RUN-SPAWN | EF-TASK-CONTROL, EF-ORCHESTRATION-SPI† |
| FEAT-IDEMPOTENCY-AND-REPLAY | agent-service | shipped | REQ-005 | FP-IDEMPOTENCY-CLAIM | EF-ACCESS-ADMISSION |
| FEAT-TENANT-ISOLATION | agent-service | shipped | REQ-006 | FP-TENANT-CROSS-CHECK | EF-ACCESS-ADMISSION |
| FEAT-POSTURE-BOOTSTRAP | agent-service | shipped | REQ-007 | FP-POSTURE-BOOT-GUARD | EF-ACCESS-ADMISSION |
| FEAT-GRAPH-MEMORY | graphmemory-starter | design_only | REQ-008 | FP-GRAPH-MEMORY-STORE | EF-GRAPHMEMORY-AUTOCONFIG |
| FEAT-ENGINE-DISPATCH-AND-HOOKS | agent-service | shipped | REQ-009 | FP-ENGINE-DISPATCH, FP-HOOK-DISPATCH | EF-ENGINE-DISPATCH†, EF-HOOK-SURFACE, EF-ENGINE-REGISTRY, EF-TRANSLATION-INTERCEPT†, EF-ENGINE-PORT† |

`†` marks a `traverses` edge that is **authored-direct**: the target frame is
`design_only` and anchors no shipped FunctionPoint, so the route rests on the
authored DSL edge (ADR-0157 §2) and is **not** independently re-derivable from
the `requires`/`anchors` join. See the reconciliation below.

## Evidence axis — per shipped FunctionPoint

Evidence cites **generated fact IDs** (`architecture/facts/generated/*.json`),
never prose. An empty cell means *no fact of that kind exists for this FP yet*
(the platform is mid-build) — it is never invented. Only the three HTTP run
FunctionPoints have contract/code/test facts extracted today; the internal
orchestration FPs are proven through the run-lifecycle facts and their ADRs.

| FunctionPoint | Status | Contract fact | Code-symbol fact | Test facts |
|---|---|---|---|---|
| FP-CREATE-RUN | shipped | `contract-op/createrun` | `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller` | `test/…-runhttpcontractit`, `test/…-runstatemachinetest` |
| FP-CANCEL-RUN | shipped | `contract-op/cancelrun` | `code-symbol/…-runcontroller` | `test/…-runhttpcontractit`, `test/…-runstatemachinetest` |
| FP-GET-RUN-STATUS | shipped | `contract-op/getrun` | `code-symbol/…-runcontroller` | `test/…-runhttpcontractit` |
| (all other shipped FPs) | shipped | — | — | — |

(Full kebab fact IDs are in the YAML; truncated here for readability.)

## Decision axis — ADR authority per FunctionPoint

Each FunctionPoint's decision authority is its DSL `saa.sourceAdr`, carried here
**verbatim** with how it resolves. The generated fact layer (`adrs.json`) covers
**ADR-0068+**; older ADRs resolve to a `docs/adr/*.md` file; and the DSL cites
two ADR IDs (**ADR-0020**, **ADR-0027**) that exist in **neither** — a
pre-existing upstream gap the reconcile / DSL owner owns. The map reports the
gap; it does not fabricate a target.

| FunctionPoint | Source ADR (DSL) | Resolves to |
|---|---|---|
| FP-CREATE-RUN, FP-GET-RUN-STATUS | ADR-0020 | **unresolved** (not in facts, not on disk) |
| FP-CANCEL-RUN | ADR-0108 | fact `adr/0108-tenant-reauth-widening-and-graph-isolation` |
| FP-INGRESS-ENVELOPE | ADR-0089 | fact `adr/0089-edge-plane-ingress-gateway-mandate` |
| FP-S2C-CALLBACK | ADR-0088 | fact `adr/0088-agent-runtime-core-dissolution` |
| FP-RUN-STATE-TRANSITION | ADR-0118 | fact `adr/0118-rc38-audit-corrective-latent-correctness-and-deploy-packaging` |
| FP-SUSPEND-RESUME | ADR-0137 | fact `adr/0137-suspendsignal-canonical-interruptsignal-glossary` |
| FP-CHILD-RUN-SPAWN | ADR-0145 | fact `adr/0145-run-event-sealed-hierarchy` |
| FP-IDEMPOTENCY-CLAIM | ADR-0027 | **unresolved** (not in facts, not on disk) |
| FP-TENANT-CROSS-CHECK | ADR-0056 | disk `docs/adr/0056-jwt-validation-and-tenant-claim-cross-check.md` |
| FP-POSTURE-BOOT-GUARD | ADR-0058 | disk `docs/adr/0058-posture-boot-guard.md` |
| FP-GRAPH-MEMORY-STORE | ADR-0081 | fact `adr/0081-resilience-contract-dual-surface-reconciliation` |
| FP-ENGINE-DISPATCH | ADR-0140 | fact `adr/0140-engine-adapter-layer-split` |
| FP-HOOK-DISPATCH | ADR-0073 | fact `adr/0073-engine-hooks-and-runtime-middleware` |
| FP-A2A-MESSAGE-SEND, FP-A2A-TASKS-CANCEL, FP-A2A-TASKS-RESUBSCRIBE, FP-MQ-INBOUND | ADR-0155 | fact `adr/0155-agent-service-l1-v1-2-internal-module-design` |

## Derived `Feature → traverses → EngineeringFrame` (reconciliation)

ADR-0157 §2 makes `traverses` a first-class **authored** value-axis edge — the
DSL is authoritative. This map additionally exposes the **join-derivable**
subset so an AI can confirm the authored edges independently:

> **Derivation rule:** *feature **traverses** frame iff there exists a
> FunctionPoint `fp` such that (feature **requires** `fp`) AND (frame **anchors**
> `fp`).*

Where the authored edge cannot be re-derived (the target frame anchors no
shipped FunctionPoint), the edge is **authored-direct** — legitimate, but resting
solely on the DSL. The join is an advisory cross-check, never a replacement.

| Feature | Authored `traverses` | Join-derivable (confirmed) | Authored-direct only |
|---|---|---|---|
| FEAT-RUN-LIFECYCLE-CONTROL | EF-ACCESS-ADMISSION, EF-TASK-CONTROL, EF-SESSION-TASK-STATE | EF-ACCESS-ADMISSION, EF-TASK-CONTROL, EF-SESSION-TASK-STATE | — |
| FEAT-EDGE-COMPUTE-INGRESS | EF-INGRESS-GATEWAY | EF-INGRESS-GATEWAY | — |
| FEAT-SERVER-CLIENT-CALLBACK | EF-S2C-TRANSPORT | EF-S2C-TRANSPORT | — |
| FEAT-SUSPEND-RESUME-CONTROL | EF-TASK-CONTROL, EF-ORCHESTRATION-SPI | EF-TASK-CONTROL | EF-ORCHESTRATION-SPI |
| FEAT-IDEMPOTENCY-AND-REPLAY | EF-ACCESS-ADMISSION | EF-ACCESS-ADMISSION | — |
| FEAT-TENANT-ISOLATION | EF-ACCESS-ADMISSION | EF-ACCESS-ADMISSION | — |
| FEAT-POSTURE-BOOTSTRAP | EF-ACCESS-ADMISSION | EF-ACCESS-ADMISSION | — |
| FEAT-GRAPH-MEMORY | EF-GRAPHMEMORY-AUTOCONFIG | EF-GRAPHMEMORY-AUTOCONFIG | — |
| FEAT-ENGINE-DISPATCH-AND-HOOKS | EF-ENGINE-DISPATCH, EF-HOOK-SURFACE, EF-ENGINE-REGISTRY, EF-TRANSLATION-INTERCEPT, EF-ENGINE-PORT | EF-HOOK-SURFACE, EF-ENGINE-REGISTRY | EF-ENGINE-DISPATCH, EF-TRANSLATION-INTERCEPT, EF-ENGINE-PORT |

## How to query this map (AI recipes)

- **"What proves Feature X is real?"** → in the YAML, read `features[X]`, follow
  `requires_function_points` into `function_points[*]`, and read each FP's
  `evidence` (cite the fact IDs) + `decision`.
- **"Which module/frame owns behaviour Y?"** → read `function_points[Y].structure`
  (`anchored_by_frames`, `owner_module`); the frame's `card_path` is the readable
  boundary description.
- **"Does demand Z route through an existing frame?"** (Track B, ADR-0157 §5) →
  read the demand's FunctionPoints, check each lands on a frame via
  `function_points[*].structure.anchored_by_frames`; if it cannot land on an
  existing frame, STOP and escalate to an ADR (Track A).
- **"Confirm a `traverses` edge"** → cross-check `derived_feature_traversals`:
  if the frame is in `join_derivable` it is independently confirmed; if only in
  `authored_direct_only` it rests on the authored DSL edge (a design_only frame).

## Maintenance

This map is **hand-authored interpretation** and is kept ID-consistent with the
DSL by the reconcile step. When the DSL's frames / features / function-points /
`anchors` / `requires` / `traverses` change, this map and its YAML are updated in
lockstep. The schema at
[`schema/ai-understanding-map.schema.yaml`](schema/ai-understanding-map.schema.yaml)
is the structural contract; see [`README.md`](README.md) for the validation
recipe.
