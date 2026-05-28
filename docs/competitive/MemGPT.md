---
analysis_id: COMPETITIVE-MEMGPT
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 4
repo_clone_at: D:\ai-research\agent-platforms-survey\MemGPT\
---

# Infrastructure Analysis: cpacker/MemGPT (now letta-ai/letta)

Source-grounded analysis of the historical `cpacker/MemGPT` repository, cloned
shallow at commit `1131535716e8a31c9a437f8695e25ac98f203a24` (2026-05-14, last
commit `fix(security): use JSON instead of pickle for sandbox->server tool
result transport (#3343)`). Important note: the GitHub URL
`https://github.com/cpacker/MemGPT` now redirects to `letta-ai/letta` — the
README states "Letta (formerly MemGPT)" at `README.md:1`. The MemGPT project
has been renamed and continues under Letta. This analysis focuses on the
**original MemGPT contributions** — the long-term-memory architecture
introduced in the 2023 arXiv paper "MemGPT: Towards LLMs as Operating
Systems" — preserved as the historical `memgpt_agent` / `memgpt_v2_agent`
agent types in the current codebase (`letta/schemas/enums.py:86-87`).

## 1. Tagline & positioning

MemGPT is **infrastructure** for our purposes — a memory-substrate research
artifact, not a competing agent platform. The MemGPT contribution to the
ecosystem is the *paged memory* metaphor for LLM agents: treat the context
window as RAM, treat external storage as disk, and have the LLM call
function tools to swap between them. spring-ai-ascend would consume this as
an SPI adapter under the memory substrate (PC-005), not duplicate it.

The MemGPT paper (arXiv:2310.08560, "MemGPT: Towards LLMs as Operating
Systems", October 2023) introduced two foundational concepts that are
now visible across the AI-memory landscape: **(a)** explicit
`function_call`-based memory management where the LLM is responsible
for deciding what to remember and when to retrieve, and **(b)** the
**main-context vs external-context** split where in-prompt content is
treated as scarce RAM and out-of-prompt content as paged disk. The
2026 Letta-renamed codebase preserves both concepts. Today's
`memgpt_agent` / `memgpt_v2_agent` AgentTypes still operate this way;
the newer `letta_v1_agent` simplified the loop by removing heartbeats
but kept the memory-management-as-tool-calls invariant.

The repository's positioning, verbatim from `README.md:1-4`:

> "Letta (formerly MemGPT) — Build AI with advanced memory that can learn
> and self-improve over time."

The architecture description from the original MemGPT paper survives in the
agent-type taxonomy (`letta/schemas/enums.py:85-89`): `memgpt_agent` ("the
OG set of memgpt tools"), `memgpt_v2_agent` ("memgpt style tools, but
refreshed"), `letta_v1_agent` ("simplification of the memgpt loop, no
heartbeats or forced tool calls"). The three-tier memory model — **core
memory** (in-context blocks, edited by tool calls), **recall memory**
(message-window archive), and **archival memory** (vector-store-backed
passages) — is the foundational pattern. From spring-ai-ascend's
five-plane topology, this work sits **squarely in `bus_state`**: it is a
durable knowledge substrate, not a compute_control engine. We do not
compete with MemGPT; we either embed it via SPI or borrow its memory
shape into our own `spring-ai-ascend-graphmemory-starter`.

## 2. Architecture skeleton

The original MemGPT architecture decomposes into:

- **Agent loop** (`letta/agent.py` legacy + `letta/agents/letta_agent.py`):
  the OG MemGPT control loop, which the paper described as a "main context"
  + "external context" cycle with explicit `core_memory_append` /
  `core_memory_replace` / `archival_memory_insert` /
  `archival_memory_search` tool calls.
- **Three-tier memory** (`letta/schemas/memory.py:71-86`): the `Memory`
  pydantic model carries `blocks: List[Block]` for core memory and
  `file_blocks: List[FileBlock]` for attached files; `ContextWindowOverview`
  (lines 23-65) is the introspection contract — `num_tokens_core_memory`,
  `num_tokens_messages`, `num_archival_memory`, `num_recall_memory`,
  `num_tokens_external_memory_summary`.
- **Archival store** (`letta/orm/archive.py:25-58`): `Archive` SQLAlchemy
  ORM table backed by pgvector; each `Archive` carries an
  `embedding_config: EmbeddingConfigColumn` plus a `vector_db_provider`
  enum (`VectorDBProvider.NATIVE` default).
- **Function tools** (`letta/functions/function_sets/`): the OG tools live
  as Python modules wired through `letta/constants.py:42-50` — base, voice,
  multi_agent, builtin, files.
- **Persistence** (`letta/orm/`): SQLAlchemy ORM with `pgvector` + Postgres
  per `compose.yaml:3` (`image: ankane/pgvector:v0.5.1`).
- **Function-call sandbox** (`sandbox/` directory): the most recent
  security fix (HEAD commit message) replaces pickle with JSON for
  `sandbox→server` tool result transport.

Counterpart mapping into spring-ai-ascend's reactor:

| spring-ai-ascend module | MemGPT counterpart | Notes |
|---|---|---|
| `spring-ai-ascend-graphmemory-starter` | `letta/schemas/memory.py` + `letta/orm/archive.py` | direct conceptual parent |
| `agent-execution-engine` | `letta/agents/letta_agent.py` (memgpt loop) | one engine variant we could ship behind R-M.b |
| `agent-middleware` | `letta/orm/` (pgvector schema) | substrate, not contract |
| `agent-evolve` | (none — Letta doesn't fine-tune) | out of MemGPT scope |

The MemGPT-original `Run` ORM (`letta/orm/run.py:23-45`) is per-agent-
invocation: `id` prefixed `run-<uuid>`, with `status` (RunStatus enum),
`completed_at`, `stop_reason` (StopReasonType), `background` boolean,
`request_config` JSON. This is a thin wrapper around "an agent
processed N messages and then stopped" — **not** the durable
workflow-level Run that our `agent-service/.../runtime/runs/Run.java`
represents. MemGPT's Run is closer to our `Step` aggregate; the
analogue mismatch is informative — MemGPT models conversation-as-Run,
we model workflow-as-Run. Both can coexist if we layer MemGPT
beneath our agent-service.

## 3. Developer experience

The original MemGPT was a **CLI-first** developer experience — a Python
REPL where the user runs `memgpt run` and gets a single chatbot session
with persistent core memory. The Letta-renamed README now leads with a
Node-based CLI (`README.md:7` requires "Node.js 18+"). The legacy
Python-side install via `pyproject.toml:1-3` (`name = "letta"`, `version
= "0.16.8"`) is still the runtime — first agent reaches "hello world"
in three steps:

1. `docker compose up` — boots `letta_db` (pgvector) + `letta_server`
   (`compose.yaml:1-30`).
2. Open `localhost:8283` for the ADE (Agent Development Environment).
3. Create an agent with default `memgpt_v2_agent` type — auto-provisions
   core memory blocks + archival store.

The user-facing memory primitives are exposed as **tool calls the LLM can
emit**: `core_memory_append`, `core_memory_replace`,
`archival_memory_insert`, `archival_memory_search`. This "LLM-calls-its-own-
memory-tools" pattern is the most absorbable MemGPT contribution — it
elevates memory management from an invisible RAG sidecar to a
**first-class agent capability** with auditable tool-call traces. Our
`spring-ai-ascend-graphmemory-starter` should expose the same four tool
shapes via a `MemoryToolSpi`.

## 4. Multi-tenancy & governance

MemGPT/Letta's tenant model is **organization-scoped**, not enterprise-
graded. Every ORM mixin inherits `OrganizationMixin`
(`letta/orm/mixins.py:19-24`):

```python
class OrganizationMixin(Base):
    __abstract__ = True
    organization_id: Mapped[str] = mapped_column(String, ForeignKey("organizations.id"))
```

`letta/constants.py:54-55` declares a hardcoded `DEFAULT_ORG_ID =
"org-00000000-0000-4000-8000-000000000000"`. A repository-wide grep for
`tenant` against the ORM layer returns zero hits — Letta uses
**organization** as its isolation key, similar to GitHub-style accounts.
There is no Row-Level Security in the migrations (`alembic/` directory
contains only schema diffs, no `CREATE POLICY`). For our purposes, this
is **single-tenant-grade with org-level partition by FK**, equivalent to
WHERE-clause isolation rather than storage-engine isolation. Our Rule R-J
(Postgres RLS for every `tenant_id` migration) is strictly stronger;
if we adopt Letta as a memory substrate, we'd wrap it behind a
tenant-mapping shim that pins `organization_id := tenant_id` and add
RLS to our middleware layer.

## 5. Engine pluggability

MemGPT/Letta exposes a **factory-style** agent dispatcher in
`letta/agents/agent_loop.py:19-46`:

```python
class AgentLoop:
    @staticmethod
    def load(agent_state: AgentState, actor: "User") -> BaseAgentV2:
        if agent_state.agent_type in [AgentType.letta_v1_agent, AgentType.sleeptime_agent]:
            ...
            return LettaAgentV3(agent_state=agent_state, actor=actor)
```

The dispatcher branches on `AgentType` enum (`letta/schemas/enums.py:81-91`)
into `LettaAgentV2` / `LettaAgentV3` / `SleeptimeMultiAgentV3` /
`SleeptimeMultiAgentV4` concrete classes. This is **closer to our Rule
R-M.b strict-matching contract than SAA** — there is an explicit
discriminator field and a factory that resolves to a typed
implementation. However, there is **no `EngineEnvelope` contract** and no
`EngineMatchingException` — a mismatch is a Python `KeyError` or a
fall-through to `LettaAgentV3` as default. Adding a new agent type
requires extending the enum + extending the `AgentLoop.load`
if-chain — there is no SPI registration point. Our `EngineRegistry`
(Rule R-M.a) with envelope-schema validation is structurally cleaner;
Letta's factory is a reasonable v0 reference but lacks the typed-mismatch
semantic.

## 6. Evolution substrate (memory + self-improvement)

This is **MemGPT's core contribution** and the dimension we most want to
learn from. The architecture:

- **Core memory blocks** (`letta/schemas/memory.py:71-86`): in-context
  textual blocks the LLM rewrites via tool calls. Persisted in the `blocks`
  table (`letta/orm/block.py`), shared via `blocks_agents` join.
- **Archival memory** (`letta/orm/archive.py:25-58`): pgvector-backed
  passage store with `embedding_config` per archive. Search via
  `archival_memory_search` tool.
- **Recall memory**: message-window history persisted in
  `letta/orm/message.py`.
- **Sleeptime agents** (`letta/groups/sleeptime_multi_agent_*.py`): an
  evolution loop where a background "sleeptime" agent reviews and
  reorganizes core memory between user-facing turns — closest mapping to
  our `agent-evolve` deployment plane.

The `Memory` pydantic model carries 13 token-accounting fields
(`memory.py:23-65`) — `num_tokens_core_memory`, `num_tokens_messages`,
`num_tokens_summary_memory`, `num_tokens_functions_definitions`. This is
**audit-grade memory accounting**, not a back-of-envelope estimate.
Adopting this token-accounting contract into our
`spring-ai-ascend-graphmemory-starter` would give us cost-attribution
parity with Letta out of the box.

The OG MemGPT loop (`letta/agents/letta_agent.py`, predating the v3
rewrite) was the canonical implementation of the paper's Figure 2 —
**heartbeat-driven self-correction**. The agent emits a `function_call`
intent on every step; if the function call sets a heartbeat flag,
control returns to the model immediately without user input. This
allows multi-step memory-management without user interaction. The
v3 successor (`letta_agent_v3.py`) drops heartbeats in favor of a
cleaner tool-loop, but the heartbeat pattern remains in
`memgpt_agent` + `memgpt_v2_agent` types for backward compatibility.
For our `agent-execution-engine` design, the heartbeat-as-suspend-
resume primitive maps cleanly onto our `SuspendSignal` (Rule R-M.d):
the engine emits a `SuspendSignal.forHeartbeat(reason)`, the Tick
Engine resumes it on the next bus cycle, and the model continues
without consuming a user-message slot. This is structurally
identical to the dual-mode (graph + agent-loop) shape declared in
our architecture (project memory `project_dual_mode_runtime.md`).

## 7. Deployment model

Self-host via `compose.yaml:1-30` — Postgres 15+pgvector + the
`letta/letta:latest` container exposing ports 8083 (API) and 8283 (ADE).
There's a separate `dev-compose.yaml`, `docker-compose-vllm.yaml` (for
self-hosted LLM serving), and an `nginx.conf` reverse-proxy template.
**No Helm chart, no Kubernetes manifests** — `find -name "Chart.yaml"`
returns zero hits. The Letta-hosted SaaS (`inference.letta.com`,
`letta/constants.py:8`) is the company-managed counterpart, but the OSS
deployment is single-node docker-compose. Five-plane placement: **the
entire Letta stack maps to one node on `bus_state`** (it is durable
storage + a single API server). No edge tier, no compute_control / sandbox
/ evolution separation. For our integration, Letta sits **next to**
Postgres in the bus_state plane, exposing an HTTP API the
`spring-ai-ascend-graphmemory-starter` adapter calls.

## 8. License + corporate sponsor

The MemGPT codebase migrated from academic-research provenance (UC
Berkeley, the Sky Computing Lab) to a YC-backed commercial entity
(Letta Inc.) over 2024-2025. The repository URL changes
(`cpacker/MemGPT` → `letta-ai/letta`) and the GitHub redirect
preserve link continuity for the 2023-era papers and tutorials.
This **research-to-product migration** is a useful pattern to
study: the OG MemGPT code remained operational while the
production rewrite (V2, V3) happened alongside. There was no
flag-day cutover — old agent types continued to work, new types
arrived, users opted in. The same incremental-replacement
discipline is what our Rule G-1.b architecture-workspace truth
mandate enforces for our own codebase evolution.

License: **Apache 2.0** (`LICENSE:1-3`). Corporate sponsor: **Letta Inc.**
(`pyproject.toml:5` — `authors = [{name = "Letta Team", email =
"contact@letta.com"}]`). Original lead: Charles Packer / Sarah Wooders
(MemGPT paper authors). Project funded as a YC-style startup; commercial
offering at letta.com. Package on PyPI (`letta` 0.16.8). Permissive
license, no field-of-use restrictions — safe to depend on as a Maven /
HTTP-API integration target, but **not safe to embed as a transitive
runtime dependency on the Spring-side**, because the entire stack is
Python + Postgres-pgvector + ChromaDB. We can only consume it via the
network boundary, never as a compile-time JAR.

## 9. What we LEARN

1. **Three-tier memory contract (core / recall / archival)** —
   `letta/schemas/memory.py:23-65` is the most-portable contribution.
   `spring-ai-ascend-graphmemory-starter` should declare a parallel
   tri-tier SPI (`CoreMemorySpi`, `RecallMemorySpi`, `ArchivalMemorySpi`)
   with the same token-accounting shape (`numTokensCore`,
   `numTokensRecall`, `numTokensArchival`).

2. **LLM-as-memory-manager tool shapes** — `core_memory_append`,
   `core_memory_replace`, `archival_memory_insert`,
   `archival_memory_search` are the four canonical tool calls
   (`letta/functions/function_sets/base.py`). Exposing identical tool
   shapes via our `EngineHook` contract (Rule R-M.c) gives any engine
   implementation memory-management capability without rewriting the loop.

3. **Sleeptime agent pattern** (`letta/groups/sleeptime_multi_agent_v*.py`)
   — a background "evolution" loop that reorganizes core memory between
   user turns. Direct architectural prior for our `agent-evolve`
   deployment plane (Rule R-I `deployment_plane: evolution`).

4. **Per-archive embedding configuration** —
   `letta/orm/archive.py:43-58` lets each archive declare its own
   `embedding_config`. We should mirror this so a tenant can swap from
   `text-embedding-3-small` to `bge-large-zh` per workspace without a
   global config flip.

5. **Token-accounting introspection** (`ContextWindowOverview`,
   `letta/schemas/memory.py:23-65`) — 13 token-account fields surfaced
   to the developer. Our run-event v1 contract
   (`docs/contracts/run-event.v1.yaml`) currently does not emit this
   granularity; adding it would close a major observability gap.

6. **Sandbox JSON-not-pickle invariant** — HEAD commit
   `1131535716e8a31` is literally `fix(security): use JSON instead of
   pickle for sandbox->server tool result transport (#3343)`. The
   MemGPT/Letta team replaced pickle (Python's RCE-prone object
   serialization) with JSON for the tool-execution sandbox return
   path. This is a deeply important security invariant we should mirror
   in our `SandboxExecutor` (Rule R-L): **no pickle, no Java
   serialization, no JS eval — always parse-and-validate via a
   schema**. The fact that MemGPT had to issue this as a security fix
   means the original implementation shipped with the vulnerability;
   adopting JSON-only-transport from day one in our sandbox is a
   prevention move worth a Rule R-L sub-clause.

7. **Per-agent prompt templating with `IN_CONTEXT_MEMORY_KEYWORD`** —
   `letta/constants.py:64` declares `IN_CONTEXT_MEMORY_KEYWORD =
   "CORE_MEMORY"`, a template token replaced at runtime with the
   serialized core-memory blocks. This is a clean separation between
   static system prompt and dynamic in-context memory. Our
   `engine-hooks.v1.yaml` should include a `RENDER_SYSTEM_PROMPT`
   HookPoint where adapters can inject equivalent template-token
   replacement.

## 10. Integration surfaces (five-plane placement)

| Surface | What we adapt | spring-ai-ascend SPI | Five-plane home |
|---|---|---|---|
| **Memory write API** | `archival_memory_insert` + `core_memory_append` tool shapes | `ArchivalMemorySpi.insert(tenantId, archiveId, passage)` under `spring-ai-ascend-graphmemory-starter` | `bus_state` |
| **Memory search API** | `archival_memory_search(query, k)` | `ArchivalMemorySpi.search(tenantId, query, k)` returning `Passage[]` | `bus_state` |
| **Memory introspection** | `ContextWindowOverview` 13-field model | `MemoryStatsSpi.snapshot(runId)` emitting `MemoryStats` event on the `control` channel (Rule R-E) | `compute_control` (emits) → `bus_state` (stores) |
| **Embedding swap** | per-archive `embedding_config` | `EmbeddingConfigPolicy` declared in `module-metadata.yaml` per archive | `bus_state` config |
| **Sleeptime evolution** | background memory-reorg agent | `EvolutionRunner.reorganize(archiveId)` emitting `RunEvent` with `evolution_export: IN_SCOPE` (Rule R-M.e) | `evolution` |
| **Operational pattern** | Letta runs as a separate HTTP service alongside Postgres | Deploy Letta on `bus_state` plane; our SPI adapter calls it over `WebClient` (Rule R-G compliant) | `bus_state` neighbor of `compute_control` |

**Data shape we adapt**: the `Memory` pydantic model
(`letta/schemas/memory.py:71-86`) becomes a Java record under
`com.huawei.ascend.middleware.memory.spi.MemoryView` carrying
`List<Block> coreBlocks`, `int numTokensCore`, `int numTokensRecall`, `int
numTokensArchival`. The four canonical tool shapes become entries in our
`EngineHookCatalog` so any `ExecutorAdapter` can claim memory-management
capability declaratively.

**Operational pattern**: Letta is deployed once per tenant cluster as a
side-service in `bus_state`; our `agent-middleware` module ships a
`LettaMemoryAdapter` (Reactive `WebClient`, Rule R-G) that maps
`tenantId → organization_id`, applies RLS at our middleware layer
(because Letta itself does not enforce RLS), and emits `MemoryStats`
events on the `control` channel for cost-attribution.

**Where it sits**: `bus_state` plane — a durable-knowledge substrate,
adjacent to Postgres + Redis. The engine that runs the agent loop lives
in `compute_control`; the memory store Letta provides lives in
`bus_state`; the bridge is our `MemoryToolSpi` SPI shape, modeled on
Letta's four canonical tool calls.

**Risk surface to manage**: the OG MemGPT pickle-vulnerability
(closed by HEAD commit `1131535716e8a31` on 2026-05-14) is a reminder
that any tool-execution sandbox we adopt or build MUST enforce
**parse-and-validate via schema** on all sandbox→server transport.
Our `SandboxExecutor` (Rule R-L) should ship with an explicit
"no-pickle, no-Java-deserialization, no-JS-eval" invariant
verifiable by gate at PR time. If we proxy MemGPT/Letta as our
memory substrate, we keep our own Java-side sandbox separate from
their Python sandbox so the transport-format invariant is enforced
twice. This is **defense in depth**: even if Letta's Python sandbox
re-introduces a pickle path, our Java boundary only accepts
JSON-against-schema, preventing escalation across the planes.

**Cost ledger considerations**: Letta tracks
`UsageStatistics` (`letta/schemas/openai/chat_completion_response.py`)
per-run with prompt_tokens, completion_tokens, total_tokens. Aligning
our `RunEvent` cost-payload to these field names (rather than our
own ad-hoc names) makes federation with Letta-issued runs
trivial — a Letta sleeptime run on our `evolution` plane can emit
the same shape our compute_control runs emit, simplifying downstream
cost-aggregation queries.
