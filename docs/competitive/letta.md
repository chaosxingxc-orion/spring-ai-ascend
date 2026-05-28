---
analysis_id: COMPETITIVE-LETTA
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 4
repo_clone_at: D:\ai-research\agent-platforms-survey\letta\
---

# Infrastructure Analysis: letta-ai/letta

Source-grounded analysis at commit
`1131535716e8a31c9a437f8695e25ac98f203a24` (2026-05-14, last commit
`fix(security): use JSON instead of pickle for sandbox->server tool
result transport (#3343)`). `letta-ai/letta` is the **successor**
repository to `cpacker/MemGPT` — Letta Inc.'s productized continuation of
the original MemGPT research artifact. Python `pyproject.toml:3` declares
`version = "0.16.8"`. While the MemGPT analysis focused on the
**conceptual contributions** (three-tier memory, sleeptime agents),
this analysis examines Letta as a **shipping product** — what its agent
runtime, multi-agent groups, and ADE (Agent Development Environment) look
like in 2026 for spring-ai-ascend integration purposes.

## 1. Tagline & positioning

Letta is **infrastructure** for our purposes — a stateful-agent runtime
with first-class memory that we either embed via SPI under PC-005 or
borrow shapes from. We do not compete with Letta; their problem space
(give an agent durable memory across sessions) is a substrate concern
beneath our run-spine. The README's pitch (`README.md:3`):

> "Build AI with advanced memory that can learn and self-improve over
> time."

Two product surfaces (`README.md:5-6`):

> "**Letta Code**: run agents locally in your terminal. **Letta API**:
> build agents into your applications."

Letta positions itself at a layer **below** orchestrators like LangGraph
or AutoGen — it owns the `(agent, memory, tool, message)` substrate, not
the multi-agent flow language. The 2026 evolution from the original
MemGPT v1 loop is visible in the AgentType enum
(`letta/schemas/enums.py:81-89`): four eras coexist
(`memgpt_agent` → `memgpt_v2_agent` → `letta_v1_agent` → react /
workflow / sleeptime / voice variants). For spring-ai-ascend, Letta sits
in `bus_state` (memory + state) with thin tendrils into `compute_control`
(the agent loop), and we'd consume it via its HTTP API at `:8083`.

## 2. Architecture skeleton

The repository decomposes into roughly 22 top-level `letta/*` directories
(`ls letta/`). Key subsystems:

- **Agent execution** (`letta/agents/` — 13 files): `LettaAgentV2`,
  `LettaAgentV3`, `EphemeralAgent`, `EphemeralSummaryAgent`,
  `VoiceAgent`, `VoiceSleeptimeAgent` — versioned loop implementations
  selected by `AgentLoop.load()` (`letta/agents/agent_loop.py:19-46`).
- **Multi-agent groups** (`letta/groups/`): `DynamicMultiAgent`,
  `RoundRobinMultiAgent`, `SupervisorMultiAgent`, plus four `SleeptimeMultiAgent`
  versions (V1..V4). Sleeptime is the background evolution loop.
- **Services layer** (`letta/services/` — 25+ files): `AgentManager`,
  `ArchiveManager`, `BlockManager`, `BlockManagerGit`,
  `ConversationManager`, `FileManager`, `FileProcessor`,
  `IdentityManager`, `JobManager`, `GroupManager`,
  `CreditVerificationService`, `ClickhouseOtelTraces`. This is the
  **biggest surface** — service-class CRUD over the ORM.
- **ORM** (`letta/orm/` — 30+ tables): `Agent`, `Archive`, `Block`,
  `BlockHistory`, `Conversation`, `Group`, `Identity`, `Message`, `Run`,
  `Source`, `Tool`, `User`, `Organization`, plus 10 join tables
  (`agents_tags`, `archives_agents`, `blocks_agents`, `files_agents`,
  `groups_agents`, `identities_agents`, etc.).
- **LLM API clients** (`letta/llm_api/` — 25+ files): per-provider
  Anthropic / Azure / Baseten / Bedrock / DeepSeek / Fireworks /
  GoogleAI / GoogleVertex / Groq / MiniMax / Mistral / OpenAI / SGLang
  native + WebSocket session.
- **Server** (`letta/server/rest_api/`): FastAPI app exposing `/v1/*` +
  `/v1/admin/*` endpoints + `/openai` compatibility prefix
  (`letta/constants.py:31-34`).
- **Schemas** (`letta/schemas/` — 40+ pydantic models).
- **Run aggregate** (`letta/orm/run.py:23-50`): per-agent-invocation
  durable record. Fields: `id` (`run-<uuid>`), `status` (RunStatus
  enum), `completed_at`, `stop_reason` (StopReasonType: `end_turn`,
  `error`, `tool_call`, etc.), `background` (bool — whether the run
  was created in background mode, for sleeptime evolution), `metadata_`
  JSON, `request_config` (`LettaRequestConfig` JSON). Indexes:
  `(created_at, id)`, `agent_id`, `organization_id`, `conversation_id`.
  This is **structurally close** to our `Run.java` (Rule R-C.2.a) but
  scoped to conversation rather than workflow. Letta does **not**
  enforce a state machine on Run transitions; status changes are
  free-form. Our Rule R-C.2.b state-machine validation is the
  governance gap we'd add at the adapter.

Counterpart mapping into spring-ai-ascend's reactor:

| spring-ai-ascend module | Letta counterpart | Role |
|---|---|---|
| `agent-service` runtime | `letta/services/agent_manager.py` + `letta/orm/run.py` | Letta's `Run` is per-agent-invocation, ours is the durable workflow |
| `agent-execution-engine` | `letta/agents/letta_agent_v3.py` | one engine variant (the MemGPT loop) |
| `agent-middleware` | `letta/orm/` (pgvector schema) | substrate adjacency |
| `spring-ai-ascend-graphmemory-starter` | `letta/orm/{archive,block,passage}.py` | direct conceptual parent |

## 3. Developer experience

Letta's developer journey is **container-first** (`compose.yaml:1-30`):

1. `docker compose up` — boots `letta_db` (pgvector v0.5.1) + `letta_server`.
2. Server exposes `:8083` (API) and `:8283` (ADE).
3. Programmatic agent creation via the `letta-client` SDK
   (`pyproject.toml:36`, `letta-client>=1.7.12`).

The CLI experience now leads with Letta Code, a Node-based agent runner
(`README.md:7-9`, "Requires Node.js 18+"). The Python-side install
remains the canonical server, and the README describes connecting to
either a self-hosted server or the hosted SaaS at `inference.letta.com`
(`letta/constants.py:8`). API key ingestion is environment-variable
based (`compose.yaml` lines 39-48 enumerate `OPENAI_API_KEY`,
`ANTHROPIC_API_KEY`, `GROQ_API_KEY`, `GEMINI_API_KEY`, `OLLAMA_BASE_URL`,
`VLLM_API_BASE`, etc.). There is **no posture-aware default** equivalent
to our Rule D-6 — the `LETTA_DEBUG=True` flag is a single boolean.
**One major DX win**: `letta/orm/block_history.py` plus
`letta/services/block_manager_git.py` provide git-style memory-block
versioning, enabling agents to roll back core-memory edits.

## 4. Multi-tenancy & governance

Letta uses **organization** as the tenant key, not a tenant_id.
`letta/orm/mixins.py:19-24`:

```python
class OrganizationMixin(Base):
    __abstract__ = True
    organization_id: Mapped[str] = mapped_column(String, ForeignKey("organizations.id"))
```

The hardcoded default is `org-00000000-0000-4000-8000-000000000000`
(`letta/constants.py:54`). A repository-wide grep for `tenant` against
the ORM returns zero hits. The isolation pattern is **FK-based WHERE
filtering**, not storage-engine RLS: there are no `CREATE POLICY`
statements in any Alembic migration. There is a `CreditVerificationService`
(`letta/services/credit_verification_service.py`) suggesting per-org
quota enforcement at the service layer, but no Rule-R-K-style capacity
matrix declaration. By contrast, spring-ai-ascend enforces tenant
isolation at the **storage engine** (Rule R-J) and re-validates
tenant scope at the HTTP edge (`POST /v1/runs/{runId}/cancel`). For
integration: we'd treat Letta's `organization_id` as our `tenantId` and
add an RLS-enforcing shim in `agent-middleware` so cross-org data leakage
is impossible at the engine level, not just at the service-method level.

## 5. Engine pluggability

`letta/agents/agent_loop.py:19-46` is a typed factory keyed by
`AgentType`:

```python
class AgentLoop:
    @staticmethod
    def load(agent_state: AgentState, actor: "User") -> BaseAgentV2:
        if agent_state.agent_type in [AgentType.letta_v1_agent, AgentType.sleeptime_agent]:
            if agent_state.enable_sleeptime:
                ...
                return SleeptimeMultiAgentV4(agent_state=..., group=...)
            return LettaAgentV3(agent_state=..., actor=actor)
```

This is **the closest thing in our Tranche-4 cohort to spring-ai-ascend's
EngineRegistry pattern** — an explicit discriminator field
(`agent_type`) routes to a typed implementation. However:

- No `EngineEnvelope` schema (Rule R-M.a). Routing is on a single field.
- No `EngineMatchingException` (Rule R-M.b). Mismatch falls through to
  `LettaAgentV3` as default — silent default-engine behavior.
- No `HookPoint` contract (Rule R-M.c). Cross-cutting policy (rate-limit,
  PII scrub) is implemented as inline service-method logic, not declarative
  middleware.
- No S2C callback contract (Rule R-M.d). Server-to-client invocation goes
  through OpenAI-style WebSocket sessions
  (`letta/llm_api/openai_ws_session.py`), bespoke per session.

Conclusion: Letta has stronger engine discrimination than SAA but weaker
than our R-M contract. The `AgentLoop.load` pattern is a useful v0
reference but lacks the typed-mismatch + envelope-validation that makes
our engine contract enforceable at gate time.

## 6. Evolution substrate (memory + self-improvement)

This is Letta's strongest dimension and the one we most want to absorb:

- **Three-tier memory**: core (in-context `Block`s), recall (message
  buffer), archival (`letta/orm/archive.py:25-58` — pgvector-backed
  passages with per-archive `embedding_config`). See `MemGPT.md` analysis
  for the canonical breakdown.
- **Block history / git-style versioning** —
  `letta/services/block_manager_git.py` plus `letta/orm/block_history.py`.
  Agents can roll back core-memory edits with full audit trail. This is a
  **direct prior** for our audit-grade Run spine being extended into memory.
- **Sleeptime multi-agent** (`letta/groups/sleeptime_multi_agent_v4.py`):
  background agent that reviews and reorganizes core memory between user
  turns. Maps cleanly to our `agent-evolve` deployment plane
  (`deployment_plane: evolution`).
- **Identities** (`letta/orm/identity.py` + `identities_agents` +
  `identities_blocks`): a separate concept from User — represents a
  person/persona an agent talks to, enabling per-recipient memory
  partitioning.
- **Conversations** (`letta/orm/conversation.py` + `conversation_messages.py`):
  durable conversation threads with versioned message lists.
- **ContextWindowOverview** (`letta/schemas/memory.py:23-65`): 13
  token-accounting fields — **audit-grade memory accounting** out of the
  box. Our Run-event contract should adopt this granularity.

- **Tool rules** (`letta/schemas/tool_rule.py`, ORM column
  `ToolRulesColumn`): structured constraints on tool invocation
  (which tools an agent may call, which sequences are required, max
  invocations). The `ToolRule` shape is **a direct prior** for our
  `RuntimeMiddleware` at the `BEFORE_TOOL` HookPoint (Rule R-M.c)
  enforcing tool authorization. Rather than ad-hoc Python checks,
  Letta declaratively expresses tool constraints in the agent's
  configuration — exactly the design we want.

- **Conversation isolation from Run** — Letta separates
  `Conversation` (durable message thread, `letta/orm/conversation.py`)
  from `Run` (per-invocation execution record). A Conversation can span
  many Runs. This is the **right** separation we should mirror — our
  current model conflates "what the user is doing" with "what the
  engine is executing", and Letta's two-aggregate split is cleaner.

- **CompactionSettings** (`letta/services/summarizer/summarizer_config.py`,
  ORM column `CompactionSettingsColumn`): declarative configuration of
  when to summarize/compress message history (token threshold, message
  count threshold, summarization model). This is a clean policy
  surface we should adopt for our memory compaction story.

## 7. Deployment model

Letta runs as a multi-container application. The reference deployment
in `compose.yaml:1-50` declares two services: `letta_db` (Postgres
15 with pgvector v0.5.1 extension) and `letta_server` (the FastAPI
service on ports 8083 + 8283 ADE). Environment variables enumerate
provider API keys plus optional vLLM/Ollama endpoints. ClickHouse for
OTel traces is optional — `CLICKHOUSE_ENDPOINT` + `CLICKHOUSE_DATABASE`
in the env block. The `dev-compose.yaml` ships hot-reload bind mounts;
`docker-compose-vllm.yaml` adds a vLLM service for self-hosted model
serving. The `nginx.conf` reverse-proxy template lets multiple
servers sit behind a single hostname with TLS termination.

Self-host via `compose.yaml`. The compose file specifies pgvector v0.5.1
+ `letta/letta:latest` + ClickHouse for OTel traces
(`compose.yaml:48-49`, env vars `CLICKHOUSE_ENDPOINT`, `CLICKHOUSE_DATABASE`).
SaaS option: `inference.letta.com` (Letta-managed, requires API key).
**No Helm chart** — `find -name "Chart.yaml"` returns zero. No
Kubernetes manifests. ARM64 / NPU support: **none indicated** — the
Docker image is x86_64 default, no ARM64-specific Dockerfile, no Ascend
adapter. For our Ascend+Kunpeng sovereignty positioning, integrating
Letta means either (a) running it on x86 alongside our ARM64 nodes
(acceptable for `bus_state` neighbors), or (b) building our own ARM64
container from source (Python 3.11+ on Kunpeng is well-supported). Five-
plane placement: Letta sits in `bus_state` as a memory-substrate
side-service; we never run Letta inside `compute_control`.

## 8. License + corporate sponsor

Letta Inc. emerged from the MemGPT research project (UC Berkeley
Sky Computing Lab) as a YC-backed commercial entity. The product
roadmap visible in the codebase indicates dual emphasis on **OSS
runtime** (the `letta` package on PyPI, current 0.16.8) and
**hosted SaaS** (`inference.letta.com`). The OSS runtime is fully
functional standalone — no managed-tier dependency for any core
feature. This is **a strong open-core signal** distinguishing
Letta from companies that gate critical functionality behind
commercial tiers. For our adoption purposes, the OSS-completeness
means we can integrate Letta as a substrate without committing to
their managed tier — a critical sovereignty property for
enterprise customers.

License: **Apache 2.0** (`LICENSE:1-3`). Corporate sponsor: **Letta Inc.**
(`pyproject.toml:5-7` — `authors = [{name = "Letta Team", email =
"contact@letta.com"}]`). Founded by Charles Packer + Sarah Wooders
(MemGPT paper authors). Funding: YC-backed. Commercial model: open-core
+ hosted SaaS at letta.com. Lead developers visible in commit history.
Package on PyPI (`letta` 0.16.8); HEAD commit dated 2026-05-14. The
license is permissive and dependency-safe; the runtime is Python +
Postgres + ChromaDB so we can only consume it as a **network
boundary** (HTTP API), never as a JVM-classpath dependency. This is
actually a **defensibility benefit** — embedding Letta does not couple
our Maven release cadence to theirs, unlike a JVM-side competitor.

## 9. What we LEARN

1. **Git-style memory-block versioning** —
   `letta/services/block_manager_git.py` + `letta/orm/block_history.py`.
   Apply diff-and-rollback semantics to core memory so an agent's
   misedit can be reverted with a trace. Our `spring-ai-ascend-graphmemory-
   starter` should ship a `BlockHistorySpi` with the same shape.

2. **Identity as a first-class join key** — `letta/orm/identity.py` plus
   `identities_agents` + `identities_blocks` lets memory be partitioned
   per-recipient (an agent's memory of Alice differs from its memory of
   Bob, sharing only what's tagged shared). Direct prior for our
   per-tenant per-user memory partitioning.

3. **Multi-agent group taxonomy** — `letta/groups/` ships
   `DynamicMultiAgent`, `RoundRobinMultiAgent`, `SupervisorMultiAgent`,
   four `SleeptimeMultiAgent` versions. Adopting these as named patterns
   in our `agent-execution-engine` documentation gives developers a
   shared vocabulary.

4. **ClickHouse for OTel traces** — `letta/services/clickhouse_otel_traces.py`
   + `clickhouse_provider_traces.py`. Letta has chosen ClickHouse over
   Postgres for high-cardinality trace storage. For our v2 observability,
   this is a worthwhile bake-off target — Postgres + jsonb vs ClickHouse
   columnar.

5. **AgentType factory dispatch** — `letta/agents/agent_loop.py:19-46`
   is a reasonable structural reference for `EngineRegistry.resolve()`,
   but the lack of envelope-schema validation and typed-mismatch
   exception is exactly the gap our Rule R-M fills.

6. **Sleeptime evolution loop pattern** — Letta's V4 sleeptime agent
   reorganizes core memory between user turns. We should formalize this
   as an `EvolutionTrigger` event on our `evolution_export: IN_SCOPE`
   channel (Rule R-M.e), with the sleeptime agent running on our
   `agent-evolve` deployment plane.

7. **Run/Conversation separation** —
   `letta/orm/run.py` + `letta/orm/conversation.py` are distinct
   aggregates. Conversations persist across many Runs; Runs are per-
   invocation. Our `agent-service` currently fuses these into a single
   Run; introducing a `Conversation` aggregate would unblock the
   "agent remembers across sessions" requirement without adding
   per-Run-record cost.

8. **Tool Rules as declarative constraint** —
   `letta/schemas/tool_rule.py` provides a constraint language for
   tool invocation (allow/deny/sequence/max-uses). Our skill-capacity.yaml
   (Rule R-K) currently models capacity but not invocation constraints;
   adopting the Letta `ToolRule` shape under
   `docs/governance/tool-rules.yaml` would complete the matrix.

9. **Multi-version-coexistence pattern** — Letta keeps four eras of
   agent types coexisting in one binary (`memgpt_agent`,
   `memgpt_v2_agent`, `letta_v1_agent`, newer variants). This is the
   shape our Rule R-M.a/.b enforces — multiple engines on one runtime,
   selected by envelope. Letta's lived-in implementation is proof the
   design scales to four+ engine variants without conflict.

## 10. Integration surfaces (five-plane placement)

| Surface | What we adapt | spring-ai-ascend SPI | Five-plane home |
|---|---|---|---|
| **Memory CRUD** | `letta/orm/{archive,block,passage}.py` | `ArchivalMemorySpi`, `CoreMemorySpi`, `BlockHistorySpi` | `bus_state` |
| **Block history** | `block_manager_git.py` diff-and-rollback | `BlockHistorySpi.revert(blockId, atRevision)` | `bus_state` |
| **Identity partitioning** | `letta/orm/identity.py` + join tables | `IdentitySpi.lookup(tenantId, identityId)` | `bus_state` |
| **Sleeptime evolution** | `SleeptimeMultiAgentV4` reorg loop | `EvolutionRunner.reorganize(archiveId)` emitting `evolution_export: IN_SCOPE` | `evolution` |
| **ClickHouse traces** | OTel sink for high-cardinality traces | optional `agent-middleware` ClickHouseTraceSpi adapter | `bus_state` |
| **HTTP API consumption** | Letta `:8083` REST endpoints | `LettaMemoryAdapter` using Reactive `WebClient` (Rule R-G) | `compute_control` (caller) → `bus_state` (Letta) |
| **Org→tenant remap** | `organization_id` to our `tenantId` | shim in `agent-middleware`; add RLS at our DB layer | `bus_state` middleware |

**Data shape we adapt**: `Archive` + `Block` + `BlockHistory` map to
Java records under `com.huawei.ascend.middleware.memory.spi`. The
`Memory` pydantic shape with 13 token-counting fields becomes
`MemoryView` carrying `int numTokensCore`, `int numTokensRecall`, etc.

**Operational pattern**: Letta deployed as a sidecar service in
`bus_state`, addressable by `compute_control` nodes via `WebClient`.
Our adapter handles tenant-mapping (`tenantId → organization_id`), adds
RLS at our middleware layer (because Letta has none), and emits
`MemoryStats` events on the `control` channel (Rule R-E) for cost
attribution. The sleeptime evolution loop runs on our `agent-evolve`
plane and emits `evolution_export: IN_SCOPE` events that the evolution
sink consumes for trajectory training.

**Where it sits**: `bus_state` plane, with one bridge into `evolution`
(sleeptime) and one into `compute_control` (caller). Letta is a
substrate, never a runtime peer; the engine that runs the user-facing
agent loop is ours.

**Migration story for adopters**: a team already on Letta can adopt
spring-ai-ascend incrementally. Step 1: deploy spring-ai-ascend's
`agent-service` in front of their existing Letta deployment;
spring-ai-ascend's HTTP API takes user-facing traffic and dispatches
each Run to a `LettaEngineAdapter` that translates `EngineEnvelope`
into Letta's `POST /v1/agents/{id}/messages`. Step 2: introduce
governance — RLS at the spring-ai-ascend Postgres layer, tenant
re-validation at the cancel endpoint, posture-aware fail-closed
defaults at boot. Step 3: gradually migrate engine variants to native
spring-ai-ascend implementations (`LettaAgentV3` → our LangChain4j-
backed engine), with Letta retained only for memory CRUD. End state:
Letta runs as a pure memory substrate in `bus_state`; all engine
logic lives on our JVM stack with audit-grade Run spine + tenant
isolation. This is **the canonical adoption path** for any
Tranche-4 substrate — wrap → integrate → selectively retain.

**Cost-attribution alignment**: Letta's `UsageStatistics` field set
(prompt_tokens, completion_tokens, total_tokens) aligns with the
OpenAI API standard. Our `RunEvent` cost payload should ship the same
field names verbatim so cross-engine cost-aggregation queries work
without translation. The added fields from Letta worth absorbing:
`memory_tokens_in_context`, `tools_definitions_tokens` — these break
out where the prompt-tokens are being spent, enabling per-tenant
"memory cost vs reasoning cost" reporting that vanilla OpenAI usage
records cannot answer.
