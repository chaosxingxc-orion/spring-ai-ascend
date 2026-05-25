---
affects_level: L0
affects_view: logical
proposal_status: review
authors: ["Codex"]
related_adrs: [ADR-0119, ADR-0120, ADR-0121, ADR-0122, ADR-0123, ADR-0124, ADR-0125, ADR-0126, ADR-0127, ADR-0128]
related_rules: [Rule-D-1, Rule-R-C, Rule-R-D, Rule-R-G, Rule-G-1, Rule-G-9, Rule-G-13, Rule-M-2]
affects_artefact:
  - docs/logs/releases/2026-05-25-l0-rc48-agentic-contract-surface.en.md
  - docs/contracts/contract-catalog.md
  - docs/adr/0121-model-gateway-spi.yaml
  - agent-middleware/src/main/java/com/huawei/ascend/middleware/model/spi/ModelGateway.java
---

# L0 rc48 Agentic Contract Surface Architecture Review

> **Date:** 2026-05-25  
> **Status:** Pending architecture-team response  
> **Affects:** L0 logical authority, release readiness, Java SPI contract truth

## 1. Executive Decision

Do **not** close L0 RC state on the current rc48 material yet.

The rc43-rc48 wave is directionally correct: adding first-class Agent,
ModelGateway, Skill, Memory, Vector/Retrieval/Embedding, Planner, and Spring
AI boundary contracts is the right L0 move and is not over-design by itself.
However, the current publication is not release-ready because several
authority surfaces disagree about what was shipped, where it lives, which
signature is authoritative, and whether formal release evidence exists.

The issues below are not cosmetic. They would let future L1/W2 work implement
against incompatible interpretations of the same L0 contract.

## 2. Root Cause / Strongest Interpretation

1. **Observed failure / motivation**: rc48 claims to close the L0 Agentic
   Contract Surface, but its release note, ADRs, Java SPI code, contract
   catalog, and evidence bundle state do not form a single authoritative
   truth.
2. **Execution path**: the rc41 senior-architect reopening recommendation was
   converted into ADRs + Java SPI shells + catalog updates + an rc48 release
   note in one large wave.
3. **Root cause**: the wave fixed the missing primitive family by adding many
   surfaces at once, but did not run a final cross-authority truth pass over
   the newly-added surfaces themselves.
4. **Evidence**: see P0/P1 findings below with `file:line` references.

## 3. Findings

### P0-1 — rc48 declares release closure while explicitly lacking formal evidence

`docs/logs/releases/2026-05-25-l0-rc48-agentic-contract-surface.en.md:3`
still says `release_candidate_commit: pending-formal-validator-run`, and
lines 40-44 say the formal-release evidence bundle is "TO BE GENERATED".
Lines 231-233 also leave the formal validator command commented as a future
step.

That contradicts the same file's release decision at lines 36-37, which says
the L0 Agentic Contract Surface gap is closed and the baseline advances to
"agent-platform contract layer complete".

**Impact:** L0 cannot be closed from this release note. The current gate passes
because rc48 does not declare `formal_release: true`, so the formal-release
validator does not require the missing rc48 evidence bundle.

**Required fix:**

- Generate `gate/release-ci-evidence/2026-05-25-l0-rc48-agentic-contract-surface.evidence.yaml`
  from the frozen candidate commit.
- Update rc48 or publish a new rc49 final note with:
  - `formal_release: true`
  - real `evidence_bundle`
  - real `release_candidate_commit`
  - generated evidence rows copied from the evidence bundle, not typed by hand.
- Add or widen a gate so any release note that says "Release Decision: close",
  `status: *shipped*`, or "release transaction" cannot carry pending evidence
  placeholders.

### P0-2 — ADR-0121 and Java code disagree on ModelGateway package and signature

ADR-0121 is explicit:

- `docs/adr/0121-model-gateway-spi.yaml:32-38` says the SPI lands in
  `com.huawei.ascend.service.model.spi.ModelGateway` in `agent-service` and
  has signature `Mono<ModelResponse> invoke(ModelInvocation)`.
- `docs/adr/0121-model-gateway-spi.yaml:75-79` explains why blocking Spring AI
  calls must be wrapped in `Mono.fromCallable(...).subscribeOn(...)`.
- `docs/adr/0121-model-gateway-spi.yaml:84-86` rejects blocking
  `ModelResponse invoke(...)` because it violates Rule R-G.

The Java implementation does something else:

- `agent-middleware/src/main/java/com/huawei/ascend/middleware/model/spi/ModelGateway.java:29-39`
  defines `ModelGateway` in `agent-middleware`, not `agent-service`, and uses
  synchronous `ModelResponse invoke(ModelInvocation)`.
- `docs/contracts/contract-catalog.md:47` follows the Java package
  `com.huawei.ascend.middleware.model.spi`, contradicting ADR-0121.

**Impact:** this is a direct L0 authority conflict. A W2 LLM gateway implementer
cannot know whether the authoritative contract is reactive service-owned
`Mono<ModelResponse>` or synchronous middleware-owned `ModelResponse`.

**Required fix:** choose one authority and update all surfaces together:

- If ADR-0121 remains authoritative, move/rename code and catalog to the
  service package and reactive signature.
- If the implementation is the intended correction, amend ADR-0121 and explain
  why Rule R-G no longer requires a reactive SPI signature.
- Add a gate that checks ADR package/signature claims against active Java SPI
  package/signature truth for newly accepted L0 SPI ADRs.

### P0-3 — rc48 SPI counts disagree across release note, catalog, and actual wave scope

rc48 release note lines 73-85 claim "12 new Java SPI interfaces" and a total
of 31 SPIs. It lists `agent-middleware` as 10 SPIs at line 82 and says the
contract catalog was refreshed from 19 to 31 at lines 130-132 and 160.

The contract catalog says something different:

- `docs/contracts/contract-catalog.md:22-24` says 33 active SPI interfaces:
  19 pre-rc43 plus 14 rc43 agentic-contract-surface SPI surfaces.
- `docs/contracts/contract-catalog.md:62-70` says the module totals sum to 33,
  with `agent-middleware` at 12 SPIs.

The release note also omits `MemoryReader` and `MemoryWriter` from the
Phase-B count, even though the catalog includes them as active SPI interfaces
at lines 51-52.

**Impact:** the L0 public extension surface is numerically ambiguous. This is
the same defect family as prior numeric drift, but now applied to the most
important new agentic contract surface.

**Required fix:**

- Decide whether the canonical total is 31/12-new or 33/14-new.
- Update rc48 release note, contract catalog, architecture-status comments,
  module architecture appendices, and DFX SPI package claims in one lockstep
  refresh.
- Extend Rule G-13 or the release-note template context so SPI totals and
  module counts are rendered from the contract catalog/module metadata rather
  than retyped.

### P1-1 — rc48 baseline delta column mixes rc41 and rc42 baselines

The rc48 release note table is labelled "Delta vs rc41" at lines 46-59, but
several deltas are actually relative to the rc42 W0 single-source-rendering
baseline or are simply incorrect against rc41:

- ADRs: line 51 says 113 is `+9` vs rc41. rc41 had 103 ADRs; rc42 added
  ADR-0119 and rc43 added ADR-0120..0128, so the delta vs rc41 is +10.
- Active gate rules: line 52 says 140 is `0` vs rc41, but rc41 had 139 and
  rc42 added Rule 126.
- Active engineering rules: line 53 says 43 is `0` vs rc41, but rc41 had 42
  and rc42 added Rule G-13.
- Gate self-tests: line 54 says 252 is `0` vs rc41, but rc41 had 249 and
  rc42 added three Rule 126 fixtures.
- Enforcer rows: line 55 says 173 is `0` vs rc41, but rc41 had 172 and rc42
  added E174.
- Architecture graph nodes/edges: lines 56-57 describe deltas from rc42
  baseline 478/865, not rc41 baseline 475/852.

**Impact:** the note reintroduces baseline drift immediately after rc42 created
single-source rendering to eliminate this class.

**Required fix:** change the column label to "Delta vs rc42 W0" where true, or
regenerate the table against rc41 and state the real deltas.

### P1-2 — Contract catalog marks newly active SPIs as deferred in the same file

The active SPI table lists `Skill` and `AgentRegistry` as rc43 active
design-only interfaces:

- `docs/contracts/contract-catalog.md:48-49` lists `Skill` and `SkillRegistry`.
- `docs/contracts/contract-catalog.md:59-60` lists `Agent` and `AgentRegistry`.

But the later "Design-named SPIs (deferred W2+)" table still says:

- `docs/contracts/contract-catalog.md:115` — `Skill` + `SkillContext` +
  `SkillResourceMatrix` planned W2.
- `docs/contracts/contract-catalog.md:118` — `AgentRegistry` +
  `RemoteAgentClient` planned post-W4.

**Impact:** a reader cannot tell whether `Skill` and `AgentRegistry` are
current L0 contract-shape surfaces or still future names. This is a direct
current-vs-forward boundary conflict.

**Required fix:** replace the deferred table with a historical/superseded table
that says which old design names were promoted, renamed, or remain deferred.
For example: `Skill` and `AgentRegistry` promoted at rc43; `SkillResourceMatrix`,
`RunDispatcher`, `CapabilityRegistry`, and `RemoteAgentClient` remain deferred.

### P1-3 — Hook-chain guard is still vacuous for the shipped Spring AI adapter

rc48 claims:

- `docs/logs/releases/2026-05-25-l0-rc48-agentic-contract-surface.en.md:116-119`
  says `LlmGatewayHookChainOnlyTest` arms automatically when the W1 LLM
  package appears.
- `docs/logs/releases/2026-05-25-l0-rc48-agentic-contract-surface.en.md:163`
  says `LlmGatewayHookChainOnlyTest` is no longer vacuous.

But the test imports only `com.huawei.ascend.service.runtime` and checks only
classes under `com.huawei.ascend.service.runtime.llm..`. There are currently
zero production files under `agent-service/src/main/java/.../service/runtime/llm`.
The actual Spring AI model adapter is
`agent-service/src/main/java/com/huawei/ascend/service/integration/springai/SpringAiChatModelGateway.java`
and imports `org.springframework.ai.chat.model.ChatModel`.

**Impact:** the release note overstates the enforcement state. The first real
Spring AI adapter is not covered by the claimed hook-chain ArchUnit guard.

**Required fix:**

- Either update the test to cover `service.integration.springai` adapter shells,
  or weaken the release note to say the old runtime-llm guard remains staged
  and a new integration-shell guard is still required.
- Add a non-vacuity assertion for any claim that a previously vacuous ArchUnit
  guard became armed.

### P1-4 — New public SPI carrier records expose mutable collections and arrays

Several new contract carrier records validate non-null inputs but do not
defensively copy mutable collections or arrays:

- `ModelInvocation` stores `List<Message>`, `List<String>`, and two maps
  without `List.copyOf` / `Map.copyOf`
  (`agent-middleware/.../ModelInvocation.java:27-41`).
- `AgentDefinition` stores `Set`, `Map`, `Optional`, and metadata without
  defensive copies (`agent-service/.../AgentDefinition.java:35-56`).
- `Plan` stores lists and maps directly (`agent-execution-engine/.../Plan.java:23-37`).
- `PlanningRequest` stores context and available resource lists directly
  (`agent-execution-engine/.../PlanningRequest.java:24-40`).
- `AgentInvocation` stores context directly and also omits non-blank validation
  for `tenantId` / `agentId` (`agent-service/.../AgentInvocation.java:20-31`).
- `VectorQuery` stores `float[] queryEmbedding` and provider hints directly
  (`agent-middleware/.../VectorQuery.java:31-41`).

This conflicts with the existing corrective direction from the rc39/rc40 wave,
where public carriers such as `AgentInvokeRequest` and `StateDelta` were made
immutable at the boundary.

**Impact:** L0 contract carriers can be mutated after construction, changing
tenant-scoped execution inputs, planner DAGs, model/tool calls, memory metadata,
or vector queries after validation. For an agent platform, this is a latent
correctness and security problem, not just style.

**Required fix:**

- Add focused tests proving post-construction mutation cannot alter each public
  carrier.
- Defensively copy all collection fields in compact constructors.
- Clone arrays on construction and accessor return, or use immutable `List<Float>`
  / `DoubleBuffer`-style carriers with explicit copy semantics.
- Add a gate or ArchUnit/Reflection test: public records under `.spi` packages
  with `List`, `Set`, `Map`, or array components must have defensive-copy tests
  or an explicit documented mutability exception.

## 4. Over-Design Assessment

The conceptual L0 architecture is **not** over-designed. The newly-added
agentic primitives are the correct contract-shape layer for a platform named
`spring-ai-ascend` and for Audience B external Spring developers.

The current problem is the opposite: the wave is broad but not internally
consistent enough to serve as authority. Contract-shape SPIs are appropriate,
but they must be exact because they will constrain W2-W4 implementation work.
The current ambiguity would push complexity downstream into adapters,
registries, and runtime bridges.

## 5. Recommended Corrective Wave

### Wave R1 — Freeze and evidence

1. Pick the frozen candidate commit for the agentic contract surface.
2. Run full verification and generate a real formal evidence bundle.
3. Publish a new rc49 final release note, or update rc48 if the team's frozen
   artefact policy allows it.

### Wave R2 — Cross-authority correction

1. Resolve the ModelGateway package/signature conflict.
2. Normalize SPI counts to one value across release note, contract catalog,
   architecture-status comments, module metadata, DFX, and L1 appendices.
3. Remove or rewrite the stale deferred-SPI table.
4. Fix the hook-chain guard claim or implement the guard.

### Wave R3 — Boundary immutability

1. Add immutability regression tests for new public carrier records.
2. Add defensive copies / array clone semantics.
3. Add a prevention gate for future `.spi` carrier mutability.

### Wave R4 — Gate hardening

1. Add release-note evidence-placeholder guard.
2. Add ADR-vs-Java SPI package/signature truth guard.
3. Add SPI count single-source rendering from catalog/module metadata.
4. Add ArchUnit non-vacuity assertion for claims that a guard became armed.

## 6. Verification Observed

The latest `main` CI for commit `8b4f244f628cd142075e3593825086b8474e230d`
is green, including Maven build, architecture graph regeneration,
architecture-sync parallel gate, self-tests, and quickstart smoke. That is
useful evidence, but it does not cover the authority conflicts above.

Local formal-release scaffold validation also passes, but only because the
rc48 note does not currently declare `formal_release: true`.

## 7. Release Recommendation

Current recommendation: **do not close RC state from rc48**.

Close RC only after the corrective wave produces one of:

1. a formal rc49 final release note with generated evidence and corrected
   authority surfaces; or
2. an amended rc48 release note plus generated evidence, if the team explicitly
   decides the latest release note is not frozen.

Until then, classify rc48 as an important corrective implementation wave, not
as final L0 release closure.
