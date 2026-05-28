---
affects_level: L0-L1
affects_view: [logical, development, process, scenarios]
proposal_status: recommendation
authors: ["Codex, based on product-owner discussion"]
primary_audience: ["architecture team", "engineering team", "product engineering leads"]
related_sources:
  - product/PRODUCT.md
  - product/claims.yaml
  - architecture/workspace.dsl
  - architecture/README.md
  - architecture/docs/L1/README.md
  - architecture/features/README.md
  - architecture/features/features.dsl
  - architecture/features/function-points.dsl
  - architecture/facts/generated/module-build.json
  - architecture/facts/generated/contract-surfaces.json
  - docs/contracts/contract-catalog.md
  - D:/ai-research/agent-platforms-survey
---

# Dual-Track Architecture and Engineering Skeleton Recommendations

This document records a design recommendation for strengthening the
`spring-ai-ascend` architecture model. It is not an authority change by itself.
It should be reviewed by the architecture and engineering teams before any DSL,
gate, renderer, or module documentation is changed.

The recommendation is simple:

1. Keep product definition, product claims, and product features on the demand
   and value axis.
2. Keep technical architecture, modules, engineering skeletons, and function
   points on the structural engineering axis.
3. Treat Feature as a driver that threads through skeleton-anchored function
   points, not as a child node inside the module decomposition tree.
4. Use Technical Architecture as the anchor and Engineering Skeleton as the
   working support for agile iteration.

The immediate goal is to prevent the AI and the team from being overwhelmed by a
large feature inventory that reads like user stories. The durable goal is to
make product development and customer-demand response two reinforcing loops
that share the same architecture anchor without letting each customer request
reshape the architecture.

## 1. Executive Recommendation

The repository should introduce an explicit **Engineering Skeleton** concept
between Module and FunctionPoint.

Recommended structural chain:

```text
Product Definition
  -> Technical Architecture
    -> Module Topology
      -> Engineering Skeleton
        -> FunctionPoint
          -> Contract
            -> CodeFact / TestFact
```

Recommended demand chain:

```text
Customer Demand / Product Feature
  -> Feature Slice / Feature List
    -> selected FunctionPoint(s)
      -> Contract / Code / Test work
```

The two chains meet at **FunctionPoint**. FunctionPoint is the join point
between demand-driven delivery and architecture-driven engineering structure.

Do not model the structural chain as:

```text
Module -> Skeleton -> Feature -> FunctionPoint
```

That shape is tempting because the repository already has an `SAA Feature`
inventory, but it is semantically wrong once Engineering Skeleton exists.
Feature is not a structural child of a module skeleton. Feature is a value or
delivery thread that selects, sequences, and validates function points that are
anchored in one or more skeletons.

Corrected model:

```text
Structural axis:
Module -> EngineeringSkeleton -> FunctionPoint

Value and delivery axis:
ProductFeature -> FeatureSlice -> FunctionPoint(s)

Common join:
FunctionPoint -> Contract -> CodeFact / TestFact -> Rule / Enforcer
```

## 2. Why This Is Necessary

### 2.1 Current repository model has a missing middle anchor

The workspace already models the main modules as Structurizr containers:
`agent-client`, `agent-bus`, `agent-service`, `agent-execution-engine`,
`agent-middleware`, `agent-evolve`, and the graphmemory starter. See
`architecture/workspace.dsl`, especially the module container declarations for
the edge, bus, compute-control, middleware, evolution, and starter modules.

Generated build facts also confirm the module topology and dependency direction:

- `build-module/agent-client`: edge plane, no allowed dependencies.
- `build-module/agent-bus`: bus_state plane, no allowed dependencies.
- `build-module/agent-service`: compute_control plane, allowed dependencies on
  `agent-execution-engine`, `agent-bus`, and `agent-middleware`.
- `build-module/agent-execution-engine`: compute_control plane, allowed
  dependencies on `agent-middleware` and `agent-bus`.
- `build-module/agent-middleware`: compute_control plane, no allowed
  dependencies.
- `build-module/agent-evolve`: evolution plane, no allowed dependencies.

Source: `architecture/facts/generated/module-build.json`.

The feature registry, however, currently exposes only three primary authored
feature layers:

- `capabilities.dsl`: coarse capability inventory.
- `features.dsl`: `SAA Feature` inventory.
- `function-points.dsl`: concrete API verbs and workflow steps.

Source: `architecture/features/README.md`.

This means the current model has a strong module topology and a growing
feature/function-point inventory, but it lacks an explicit module-internal
engineering skeleton that answers:

- What stable structures does this module consist of?
- Which function points are anchored by each structure?
- Which function points are reused by different product features?
- Which customer demand should be handled by composing existing function points
  rather than changing the architecture?

Without that anchor, `SAA Feature` is forced to carry too much meaning. It
becomes product intent, scenario narrative, lifecycle state, AI boundary,
verification path, and sometimes a pseudo-module decomposition. That is exactly
why the feature list starts to feel like a user-story list.

### 2.2 The ProductClaim chain is correct but incomplete for engineering work

`product/PRODUCT.md` establishes the product authority and requires every ADR,
contract, rule, or gate to answer which Product Claim it serves, or to mark
itself as governance infrastructure. It also states that `architecture/features`
is the L1 Feature Registry and that every `SAA Feature` must declare a
`saa.productClaim` resolving to a product claim or governance marker.

This is directionally correct. The missing piece is not product authority. The
missing piece is the engineering structure used to fulfill product claims.

The repository needs both:

```text
ProductClaim -> ProductFeature / Requirement -> FeatureSlice -> FunctionPoint
```

and:

```text
TechnicalArchitecture -> Module -> EngineeringSkeleton -> FunctionPoint
```

The current traceability chain is strong at the product and governance levels,
but engineering work still needs a stable skeleton layer to keep feature work
from becoming a list of user stories.

### 2.3 Open-source survey signals support skeleton-first thinking

The local source survey under `D:/ai-research/agent-platforms-survey` shows the
same pattern in mature agent platforms and frameworks: durable engineering
structures are separated from feature narratives.

Examples:

- LangGraph separates `checkpoint`, `checkpoint-postgres`,
  `checkpoint-sqlite`, `langgraph`, `prebuilt`, `sdk-js`, and `sdk-py`.
  Source: `D:/ai-research/agent-platforms-survey/langgraph/CLAUDE.md`.
- AutoGen separates high-level AgentChat, event-driven Core, extension
  components, MCP workbench, Docker code execution, and distributed gRPC
  runtime. Source:
  `D:/ai-research/agent-platforms-survey/autogen/python/docs/src/index.md`.
- Semantic Kernel exposes agents, connectors, memory, processes, functions,
  filters, services, prompt templates, and reliability as distinct source
  areas. Source:
  `D:/ai-research/agent-platforms-survey/semantic-kernel/python/semantic_kernel/`.
- Dify separates backend API, frontend web, and agent backend, and describes
  backend architecture as DDD and Clean Architecture. Source:
  `D:/ai-research/agent-platforms-survey/dify/AGENTS.md`.
- OpenHands separates backend, frontend, runtime, enterprise extensions,
  sandbox-related state, settings, and test guidance. Source:
  `D:/ai-research/agent-platforms-survey/OpenHands/AGENTS.md`.
- Spring AI Alibaba separates BOM, graph core, agent framework, studio,
  sandbox, and Spring Boot starters. Source:
  `D:/ai-research/agent-platforms-survey/spring-ai-alibaba/pom.xml`.
- LiteLLM separates proxy/gateway concerns, UI, deploy/helm, migrations,
  enterprise, tests, and provider integration. Source:
  `D:/ai-research/agent-platforms-survey/litellm/`.
- mem0 separates memory engine, server, CLI, plugins, SDK integrations,
  evaluation, examples, and tests. Source:
  `D:/ai-research/agent-platforms-survey/mem0/`.

The lesson is not to copy these structures directly. The lesson is that durable
engineering skeletons should be explicit before feature demand is mapped onto
them.

## 3. Agreed Model From the Discussion

### 3.1 Feature belongs outside the structural module tree

The team should treat Feature as a demand and delivery concept, not as a
structural module child.

Wrong mental model:

```text
Module -> Skeleton -> Feature -> FunctionPoint
```

Why it is wrong:

- It makes Feature look like a module-internal decomposition unit.
- It mixes customer value with module responsibility.
- It encourages user-story-shaped feature authoring.
- It hides the fact that one product feature often crosses several modules and
  skeletons.
- It makes AI read feature prose before understanding the engineering anchor.

Recommended mental model:

```text
Module -> Skeleton -> FunctionPoint

Feature -> drives FunctionPoint(s)
Feature -> traverses Skeleton(s)
ProductClaim -> justifies Feature
```

Feature remains essential, but it should be a route across the engineering map,
not a node inside the engineering tree.

### 3.2 Product development loop and demand response loop are different loops

There should be two coordinated loops.

Loop A: Product Development Loop

```text
Product Definition
  -> Technical Architecture
    -> Engineering Skeleton
      -> FunctionPoint development
        -> Integration verification
          -> System verification
            -> Customer release
```

This loop is design-state driven. It builds a complete product slice and
self-verifies it before customer delivery. It should be relatively independent
from individual customer requests.

Loop B: Demand Response and Acceptance Loop

```text
Customer Demand
  -> Product Feature review
    -> Feature decomposition
      -> FunctionPoint selection or addition
        -> component adjustment within architecture
          -> verification
            -> accepted product increment
```

This loop starts from product usage and customer demand. It should inspect and
enrich product features, decompose demand into feature slices, and create
function-point development tasks based on the engineering skeleton.

The key rule: **customer-demand response may adjust components and add function
points, but it should not change the architecture design casually.** If a demand
truly requires architecture change, it must enter the architecture decision
process explicitly.

### 3.3 Technical Architecture is the anchor; Engineering Skeleton is the support

The two loops reinforce each other:

- Technical Architecture anchors what should remain stable.
- Engineering Skeleton provides the concrete support for iterative work.
- FunctionPoint is the unit where demand and engineering meet.
- Feature drives which function points matter for a product increment.
- ProductClaim explains why the feature exists.

This gives AI and engineers a progressive learning sequence:

1. Read Product Definition for intent.
2. Read Technical Architecture for constraints and topology.
3. Read Engineering Skeleton for module-internal anchors.
4. Read FunctionPoint inventory for executable and verifiable behaviors.
5. Read Feature slices to understand how product value threads through the
   skeleton.

## 4. Proposed Ontology

### 4.1 Terms

**Product Definition**

The product authority: audience, claims, personas, journey, positioning, and
non-goals. Current authority lives under `product/`.

**ProductClaim**

A stable product promise, such as Spring-native developer ergonomics,
production-grade operation, heterogeneous engine substrate, or self-evolving
agents. Current authority lives in `product/claims.yaml`.

**ProductFeature / Requirement**

A product-facing capability or customer-facing requirement that advances one or
more ProductClaims. It can be planned internally or requested by a customer.

**FeatureSlice**

A delivery-sized decomposition of a ProductFeature. It chooses a minimal set of
FunctionPoints and acceptance evidence for one iteration.

**Technical Architecture**

The architecture-of-record: system boundary, module topology, deployment
planes, constraints, contracts, and rules. Current root is
`architecture/workspace.dsl`, with L0 and L1 docs under `architecture/docs/`.

**Module**

A Maven module and architecture container. The current generated facts identify
the active module topology in `architecture/facts/generated/module-build.json`.

**EngineeringSkeleton**

A stable module-internal structure that anchors function points. It names the
module's durable responsibility slices, such as access, run lifecycle,
orchestration, engine dispatch, model gateway, memory, skill governance, bus
ingress, callback, sandbox, evolution, or starter wiring.

**FunctionPoint**

A concrete behavior that can be implemented, verified, and traced to contracts,
code facts, and tests. Existing examples include `FP-CREATE-RUN`,
`FP-INGRESS-ENVELOPE`, `FP-S2C-CALLBACK`, `FP-ENGINE-DISPATCH`, and
`FP-HOOK-DISPATCH` in `architecture/features/function-points.dsl`.

**Contract**

The runtime promise surface. Existing contract facts include
`contract-op/createrun`, `contract-op/getrun`, `contract-op/cancelrun`,
`contract-yaml/engine-envelope`, `contract-yaml/engine-hooks`,
`contract-yaml/model-invocation`, `contract-yaml/memory-store`,
`contract-yaml/run-event`, and `contract-yaml/skill-definition`.

**CodeFact / TestFact**

Generated factual ground truth extracted under `architecture/facts/generated/`.
Per Rule G-15, these facts outrank prose for claims about code, contracts,
tests, dependencies, runtime behavior, and verification.

### 4.2 Relationship model

Recommended relationship vocabulary:

```text
ProductClaim -> ProductFeature           "justifies"
ProductFeature -> FeatureSlice           "decomposes_into"
FeatureSlice -> FunctionPoint            "requires"

TechnicalArchitecture -> Module          "defines"
Module -> EngineeringSkeleton            "contains"
EngineeringSkeleton -> FunctionPoint     "anchors"

FunctionPoint -> Contract                "specified_by"
FunctionPoint -> CodeFact                "implemented_by"
FunctionPoint -> TestFact                "verified_by"

FeatureSlice -> EngineeringSkeleton      "traverses"
ProductFeature -> EngineeringSkeleton    "impacts"
```

The important difference from the current model is that `Feature -> FunctionPoint`
should be a demand relationship, not a structural containment relationship.

## 5. Proposed Engineering Skeleton Map

This section proposes an initial skeleton map. It is intentionally conservative:
it reuses the existing module topology instead of introducing new modules.

### 5.1 `agent-service`

Module role: northbound facade, runtime API, tenant/idempotency, run
orchestration. Generated fact: `build-module/agent-service`.

Proposed skeletons:

1. **Access and Admission Skeleton**
   - Owns HTTP/A2A/MQ ingress normalization, tenant validation, identity bridge,
     idempotency admission, and posture admission.
   - Anchors function points such as Create Run, Cancel Run, Get Run Status,
     A2A message send, A2A tasks cancel, A2A resubscribe, and MQ inbound
     consume.
   - Existing anchors: `FP-CREATE-RUN`, `FP-CANCEL-RUN`,
     `FP-GET-RUN-STATUS`.
   - Existing contracts: `contract-op/createrun`, `contract-op/getrun`,
     `contract-op/cancelrun`, access-intent schemas.

2. **Run Lifecycle and State Skeleton**
   - Owns Run aggregate, RunStatus DFA, RunRepository, idempotency record,
     cancellation semantics, status transition, and terminal-state rules.
   - Anchors function points such as Run State Transition and Suspend Resume.
   - Existing anchors: `FP-RUN-STATE-TRANSITION`, `FP-SUSPEND-RESUME`.

3. **Task, Session, and Context Skeleton**
   - Owns task-centric control, session snapshots, context projection, child-run
     correlation, and recoverable checkpoint records.
   - Anchors future or design-only function points from the agent-service M1-M6
     decomposition.

4. **Engine Adapter and Dispatch Integration Skeleton**
   - Owns the service-side bridge into engine dispatch, executor invocation, and
     the boundary between platform runtime and engine-specific execution.
   - Traverses `agent-execution-engine` skeletons rather than replacing them.
   - Existing feature pressure: `Agent Service Engine Dispatch Execution`.

5. **Translation and Tool Intercept Skeleton**
   - Owns model/tool translation, tool result normalization, intercept request,
     platform chat client, memory provider, retriever, and tool callback
     boundaries.
   - Existing feature pressure: `Agent Service Translation Tool Intercept`.

6. **Audit, Identity, Cost, and Compliance Skeleton**
   - Owns immutable audit events, identity propagation, tenant evidence,
     cost-governance hooks, and regulatory evidence carriers.
   - Product pressure: PC-003.
   - Contracts: `audit-trail.v1.yaml`, `iam-bridge.v1.yaml`,
     `cost-governance.v1.yaml`.

### 5.2 `agent-execution-engine`

Module role: engine adapter and orchestration SPIs. Generated fact:
`build-module/agent-execution-engine`.

Proposed skeletons:

1. **Engine Contract Skeleton**
   - Owns engine envelope, engine matching, ExecutorAdapter, GraphExecutor,
     AgentLoopExecutor, EngineRegistry, and EngineMatchingException.
   - Existing anchor: `FP-ENGINE-DISPATCH`.
   - Existing contract: `contract-yaml/engine-envelope`.

2. **Orchestration SPI Skeleton**
   - Owns Orchestrator, RunContext, SuspendSignal, Checkpointer,
     ExecutorDefinition, TraceContext, and RunMode.
   - Anchors suspension, resume, child run, checkpoint, and long-horizon
     execution function points.

3. **Planner Skeleton**
   - Owns goal-to-plan DAG generation, planning requests, plan output, and
     planner-to-engine handoff.
   - Contracts: planning-request and plan schemas.

4. **Engine Adapter Library Skeleton**
   - Owns graph-state, ReAct, supervisor-worker, debate, hierarchical, and
     external SDK adapter slots.
   - Product pressure: PC-004.
   - This skeleton should support multiple engine adapters without forcing new
     platform rules per adapter.

### 5.3 `agent-middleware`

Module role: cross-cutting runtime middleware and policy SPIs. Generated fact:
`build-module/agent-middleware`.

Proposed skeletons:

1. **Hook Dispatch Skeleton**
   - Owns RuntimeMiddleware, HookDispatcher, HookPoint, HookContext, and
     HookOutcome.
   - Existing anchor: `FP-HOOK-DISPATCH`.
   - Existing contract: `contract-yaml/engine-hooks`.

2. **Model Gateway Skeleton**
   - Owns ModelGateway, streaming, model invocation, structured output,
     provider routing, retries, fallback, and budget hooks.
   - Contracts: `model-invocation.v1.yaml`, `model-streaming.v1.yaml`,
     `structured-output.v1.yaml`.

3. **Skill and Tool Governance Skeleton**
   - Owns Skill, SkillRegistry, capability scoping, sandbox policy references,
     tool result normalization, capacity admission, and execution permission
     checks.
   - Contracts: `skill-definition.v1.yaml`, `tool-result.v1.yaml`.

4. **Memory and Knowledge Skeleton**
   - Owns MemoryStore, MemoryReader, MemoryWriter, SemanticMemoryStore,
     KnowledgeMemoryStore, ConversationMemory, VectorStore, Retriever, and
     EmbeddingModel.
   - Contracts: `memory-store.v1.yaml`, `vector-store.v1.yaml`.
   - Product pressure: PC-001 and PC-005.

5. **Prompt and Advisor Skeleton**
   - Owns PromptTemplate, ChatAdvisor, AdvisorChain, streaming advisors, and
     advisor ordering around model invocation.
   - Contracts: prompt-template and chat-advisor schemas.

### 5.4 `agent-bus`

Module role: Bus and State Hub plane, ingress, S2C, and channel isolation.
Generated fact: `build-module/agent-bus`.

Proposed skeletons:

1. **Ingress Routing Skeleton**
   - Owns IngressGateway, IngressEnvelope, IngressResponse, and edge-to-compute
     control ingress.
   - Existing anchor: `FP-INGRESS-ENVELOPE`.
   - Existing contract: `ingress-envelope.v1.yaml`.

2. **Server-to-Client Callback Skeleton**
   - Owns S2cCallbackTransport, S2cCallbackEnvelope, callback response, and
     client capability invocation.
   - Existing anchor: `FP-S2C-CALLBACK`.
   - Existing contract: `s2c-callback.v1.yaml`.

3. **Control, Data, and Rhythm Channel Skeleton**
   - Owns physical channel isolation for control, data, and rhythm traffic.
   - Product pressure: PC-003, PC-004.

4. **Federation and Backpressure Skeleton**
   - Owns FederationGateway, backpressure request, reflection envelope, and
     cross-runtime traffic shaping.
   - Contracts: federation-envelope, backpressure-request, reflection-envelope.

### 5.5 `agent-client`

Module role: edge SDK and client integration. Generated fact:
`build-module/agent-client`.

Proposed skeletons:

1. **Client SDK Invocation Skeleton**
   - Owns client-side API shape, request construction, tenant propagation,
     idempotency propagation, and TaskCursor handling.

2. **Client Capability Publication Skeleton**
   - Owns client-published capabilities, callback handlers, and S2C response
     binding.

3. **Client Observability Skeleton**
   - Owns client-side trace propagation, error surface, retry semantics, and
     developer diagnostics.

### 5.6 `agent-evolve`

Module role: evolution plane. Generated fact: `build-module/agent-evolve`.

Proposed skeletons:

1. **Evolution Export Skeleton**
   - Owns RunEvent export scope, trajectory shaping, and tenant-scoped export
     policy.
   - Product pressure: PC-005.

2. **Online Evaluation Skeleton**
   - Owns SlowTrackJudge, feedback capture, score events, and post-run quality
     evaluation.

3. **Knowledge, Memory, and Skill Evolution Skeleton**
   - Owns evolution loops over knowledge, memory, and skill assets, in
     cooperation with `agent-middleware`.

4. **RL Trajectory Skeleton**
   - Owns trajectory export formats and downstream fine-tuning integration.

### 5.7 `spring-ai-ascend-graphmemory-starter`

Module role: Spring Boot starter for graph memory wiring.

Proposed skeletons:

1. **Starter Auto-Configuration Skeleton**
   - Owns conditional bean wiring, property validation, and disabled-by-default
     posture behavior.

2. **Graph Memory Adapter Skeleton**
   - Owns repository adapter binding, tenant-scoped graph operations, and
     backend-specific implementation slots.

3. **Developer Ergonomics Skeleton**
   - Owns Spring-native onboarding and starter-based integration patterns for
     memory features.

## 6. Recommended File and Model Changes

This section gives concrete changes with target files. The sequence is designed
to avoid a disruptive big-bang migration.

### Phase 0: Record the decision as a non-authoritative review proposal

Current file:

- `docs/logs/reviews/2026-05-28-dual-track-architecture-engineering-skeleton-recommendations.en.md`

Purpose:

- Preserve the design rationale and agreed model.
- Give reviewers a stable text to comment on.
- Avoid changing the architecture authority before team approval.

### Phase 1: Add an Engineering Skeleton concept as documentation first

Recommended new file:

- `architecture/docs/L1/engineering-skeletons.md`

Content:

- Define EngineeringSkeleton.
- State that Feature is outside the structural module tree.
- Define the dual-track loop.
- List initial skeletons per module.
- Link each skeleton to existing FunctionPoints, contracts, generated facts, and
  product claims.

Why documentation first:

- It gives AI and humans the anchor immediately.
- It avoids profile/gate churn while the model is still being reviewed.
- It lets the team test whether the skeleton map reduces confusion before
  committing to DSL enforcement.

### Phase 2: Update the L1 module index to make the skeleton reading path explicit

Target file:

- `architecture/docs/L1/README.md`

Recommended changes:

- Add `engineering-skeletons.md` to the L1 reading path.
- Explain that module docs describe design, while engineering skeletons describe
  the stable module-internal anchors used to place function points.
- Add a short rule: "Read Module -> Skeleton -> FunctionPoint before reading
  Feature slices for implementation planning."

Reason:

- Current L1 index lists module docs and per-module entries, but it does not
  provide a stable "module skeleton first" reading path.

### Phase 3: Amend the feature authoring guide to move Feature out of the structural tree

Target file:

- `architecture/features/README.md`

Recommended changes:

- Clarify that `SAA Feature` is a demand/value/delivery thread.
- Clarify that `SAA FunctionPoint` is the join point between Feature and
  EngineeringSkeleton.
- Add a section named "Feature is not a module skeleton".
- Replace language that implies Feature is the structural middle layer between
  Capability and FunctionPoint with a more precise explanation:

```text
Feature groups and drives FunctionPoints for product delivery.
EngineeringSkeleton anchors FunctionPoints for module design.
```

Reason:

- The current guide calls `features.dsl` "the middle layer between Capability
  and FunctionPoint". That wording is usable inside the existing feature
  registry, but it is misleading after EngineeringSkeleton is introduced.

### Phase 4: Add optional skeleton refs to FunctionPoint entries

Target file:

- `architecture/features/function-points.dsl`

Recommended changes:

- Add a non-breaking optional property such as:

```text
"saa.skeletonRef" "agent-service/access-admission"
```

or, for multi-skeleton cases:

```text
"saa.skeletonRefs" "agent-service/access-admission|agent-service/run-lifecycle"
```

Recommended initial mappings:

- `FP-CREATE-RUN` -> `agent-service/access-admission`
- `FP-CANCEL-RUN` -> `agent-service/access-admission`,
  `agent-service/run-lifecycle`
- `FP-GET-RUN-STATUS` -> `agent-service/access-admission`,
  `agent-service/run-lifecycle`
- `FP-INGRESS-ENVELOPE` -> `agent-bus/ingress-routing`
- `FP-S2C-CALLBACK` -> `agent-bus/server-client-callback`
- `FP-RUN-STATE-TRANSITION` -> `agent-service/run-lifecycle`
- `FP-SUSPEND-RESUME` -> `agent-service/run-lifecycle`,
  `agent-execution-engine/orchestration-spi`
- `FP-CHILD-RUN-SPAWN` -> `agent-service/run-lifecycle`,
  `agent-execution-engine/orchestration-spi`
- `FP-IDEMPOTENCY-CLAIM` -> `agent-service/access-admission`
- `FP-TENANT-CROSS-CHECK` -> `agent-service/access-admission`
- `FP-POSTURE-BOOT-GUARD` -> `agent-service/access-admission`
- `FP-GRAPH-MEMORY-STORE` -> `graphmemory-starter/graph-memory-adapter`
- `FP-ENGINE-DISPATCH` -> `agent-execution-engine/engine-contract`
- `FP-HOOK-DISPATCH` -> `agent-middleware/hook-dispatch`

Reason:

- This adds the skeleton anchor without changing the lifecycle model or
  existing Feature relationships.

Important constraint:

- Do not modify files under `architecture/facts/generated/`. Generated facts
  are produced only by deterministic extractors.

### Phase 5: Reclassify current `SAA Feature` relationships

Target file:

- `architecture/features/features.dsl`

Recommended changes:

- Keep current Feature elements.
- Rename the interpretation of Feature -> FunctionPoint relationships from
  structural containment to feature delivery composition in prose first.
- Consider a future relationship rename from `contains` to `requires`,
  `drives`, or `realizes_through`, but only after reviewing
  `architecture/profile/relationship-types.yaml` and gate impact.

Reason:

- Existing Feature entries already carry `saa.productClaim`.
- The problem is not that Feature exists. The problem is that Feature is
  overloaded as both value thread and structure.

### Phase 6: Only after review, add a formal DSL element

Candidate file:

- `architecture/features/skeletons.dsl`

Candidate tag:

- `SAA EngineeringSkeleton`

Candidate required properties:

- `saa.id`
- `saa.kind`
- `saa.level`
- `saa.view`
- `saa.status`
- `saa.owner`
- `saa.module`
- `saa.plane`
- `saa.responsibility`
- `saa.nonGoals`
- `saa.contractRefs`
- `saa.primaryFunctionPoints`
- `saa.sourceAdr`

Candidate relationships:

```text
Module -> EngineeringSkeleton      "contains"
EngineeringSkeleton -> FunctionPoint "anchors"
Feature -> EngineeringSkeleton     "traverses"
```

Required follow-up files if DSL is introduced:

- `architecture/workspace.dsl`: include `features/skeletons.dsl` in the
  authored zone before `function-points.dsl` or before `features.dsl`,
  depending on relationship direction.
- `architecture/profile/required-properties.yaml`: add required properties for
  `SAA EngineeringSkeleton`.
- `architecture/profile/relationship-types.yaml`: add `anchors` and `traverses`
  if missing.
- `gate/lib/render_features_catalog.py`: decide whether Feature Catalog should
  display skeleton traversal.
- A new renderer may be needed for a skeleton catalog.
- Workspace validation and architecture-sync gates must be run after changes.

Recommendation:

- Do not start here. Start with documentation and optional FunctionPoint
  properties. Promote to DSL only after the skeleton map proves stable.

## 7. How Work Should Flow After This Change

### 7.1 Product Development Loop

Use this loop for planned product increments.

1. Start from Product Definition and ProductClaims.
2. Confirm which Technical Architecture constraints already anchor the product
   direction.
3. Identify affected Modules.
4. Identify affected EngineeringSkeletons.
5. Identify existing FunctionPoints.
6. Add only missing FunctionPoints.
7. Bind FunctionPoints to Contracts.
8. Implement code behind the contract surface.
9. Verify with generated facts, focused tests, integration verification, and
   system verification.
10. Release to customer-facing delivery.

Review question:

> Are we completing a designed product slice, or are we letting one demand
> reshape the architecture?

### 7.2 Demand Response and Acceptance Loop

Use this loop for customer-originated demand.

1. Capture the customer demand.
2. Map it to ProductClaim or mark it as outside product scope.
3. Decide whether it is a new ProductFeature, a FeatureSlice of an existing
   feature, or a request already covered by existing FeatureSlices.
4. Traverse the EngineeringSkeleton map.
5. Select existing FunctionPoints where possible.
6. Add new FunctionPoints only when no existing function point can express the
   behavior.
7. Adjust components inside the existing Technical Architecture.
8. If architecture must change, stop and open an architecture decision path.
9. Verify and accept the increment.

Review question:

> Is this a product feature decomposition over the architecture, or an
> architecture change disguised as a customer request?

### 7.3 AI reading path

For AI-assisted work, use this sequence:

1. `product/PRODUCT.md`
2. `product/claims.yaml`
3. `architecture/facts/generated/*.json`
4. `architecture/workspace.dsl`
5. `architecture/docs/L1/engineering-skeletons.md`
6. Relevant module L1 docs
7. `architecture/features/function-points.dsl`
8. `architecture/features/features.dsl`
9. Contract catalog and specific contract YAML
10. Code and tests

This sequence makes AI learn the anchor before the feature list.

## 8. Cost and Trade-Offs

### 8.1 If the team does nothing

Expected consequences:

- Feature entries will continue drifting toward user-story prose.
- AI sessions will keep loading broad feature and rule context without a stable
  module skeleton anchor.
- New demand will be decomposed directly into features, bypassing engineering
  structure.
- Cross-module features such as audit trail, model gateway, memory, skill
  governance, and evolution will keep pulling module boundaries back and forth.
- The repository will accumulate more ProductClaim links without a clear
  engineering support model.

This is not a near-term runtime failure. It is a compounding architecture
comprehension and decomposition failure.

### 8.2 Cost of the recommended documentation-first path

Estimated cost:

- 0.5 day: review this recommendation with architecture and engineering leads.
- 1 day: author `architecture/docs/L1/engineering-skeletons.md`.
- 0.5 day: update `architecture/docs/L1/README.md` and
  `architecture/features/README.md`.
- 1 day: add optional skeleton refs to existing FunctionPoints and run
  validation.
- 0.5-1 day: update rendered feature catalog behavior if needed.

Total: approximately 3 to 4 engineering days for a low-risk first stage.

### 8.3 Cost of formal DSL enforcement

Estimated cost:

- 1-2 days: define `SAA EngineeringSkeleton` profile and relationship
  vocabulary.
- 1-2 days: author `skeletons.dsl` and wire `workspace.dsl`.
- 1-2 days: update validators, renderers, and gates.
- 1 day: regenerate projections and reconcile baselines.
- 1 day: review drift and correct documentation.

Total: approximately 1 to 2 weeks, depending on gate and renderer impact.

Recommendation:

- Do not pay the DSL cost until the documentation-first skeleton has survived
  at least one real feature decomposition cycle.

## 9. Acceptance Criteria

The first stage should be considered successful when:

1. Every active domain module has named EngineeringSkeletons.
2. Every shipped FunctionPoint has at least one skeleton anchor.
3. Every new ProductFeature or FeatureSlice lists the FunctionPoints it drives.
4. Feature documentation no longer implies that Feature is a structural child of
   Module or Skeleton.
5. AI onboarding can answer these questions before implementation:
   - Which module owns this behavior?
   - Which engineering skeleton anchors it?
   - Which function point expresses it?
   - Which contract specifies it?
   - Which fact or test verifies it?
6. Customer demand can be accepted or rejected without changing architecture by
   default.
7. Architecture changes triggered by demand are explicit ADR-level events, not
   silent feature edits.

## 10. Concrete Review Checklist for Engineering

Before implementing any new product feature:

- Identify the ProductClaim.
- Decide whether the request is product roadmap work or customer-demand
  response.
- Name the FeatureSlice.
- List existing FunctionPoints.
- List missing FunctionPoints.
- Name the EngineeringSkeleton for each FunctionPoint.
- Confirm no architecture change is required.
- If architecture change is required, stop and open the architecture decision
  path.
- Add or update contracts before implementation.
- Verify against generated facts, not prose memory.

Before adding a new FunctionPoint:

- Confirm which Module owns it.
- Confirm which EngineeringSkeleton anchors it.
- Confirm whether the behavior is already represented by an existing
  FunctionPoint.
- Confirm the contract reference.
- Confirm the code entrypoint and test reference can be produced or generated.
- Confirm the ProductFeature that drives it.

Before changing a Module or Skeleton:

- Confirm whether the change is structural or feature-level.
- Confirm affected ProductClaims.
- Confirm affected FunctionPoints and Contracts.
- Confirm whether ADR coverage exists.
- Confirm whether module dependency direction changes.
- Confirm verification commands derived from generated facts.

## 11. Recommended Next Steps

1. Review this recommendation with architecture and engineering leads.
2. Approve or revise the ontology:
   - ProductClaim
   - ProductFeature
   - FeatureSlice
   - EngineeringSkeleton
   - FunctionPoint
3. Author `architecture/docs/L1/engineering-skeletons.md`.
4. Update `architecture/docs/L1/README.md` to include skeleton-first reading.
5. Update `architecture/features/README.md` to clarify that Feature is a
   delivery driver, not a structural middle layer.
6. Add optional skeleton refs to current FunctionPoint entries.
7. Use one real demand item as a pilot:
   - Recommended pilot: financial-grade audit trail, because it exercises
     product claim PC-003, `agent-service`, `agent-middleware`, contracts,
     identity, cost, and compliance.
8. After the pilot, decide whether to formalize `SAA EngineeringSkeleton` in
   DSL.

## 12. Summary Position

The team should perform a directional hardening refactor, but the first refactor
should be semantic and architectural, not code-heavy.

The durable model should be:

```text
Product Definition -> Technical Architecture -> Module -> EngineeringSkeleton -> FunctionPoint
```

and, in parallel:

```text
Customer Demand -> ProductFeature -> FeatureSlice -> FunctionPoint
```

The two loops should reinforce each other:

- Product development builds complete product slices from design-state
  architecture.
- Demand response decomposes customer needs through the same architecture and
  skeleton map.
- Technical Architecture remains the anchor.
- Engineering Skeleton is the support.
- Feature drives delivery.
- FunctionPoint is the join point.

This change should make the repository easier for engineers to evolve, easier
for AI to learn progressively, and safer for customer-driven iteration without
architecture drift.

