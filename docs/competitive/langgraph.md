---
analysis_id: COMPETITIVE-LANGGRAPH
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 2
repo_clone_at: D:\ai-research\agent-platforms-survey\langgraph\
---

# Competitive Analysis: langchain-ai/langgraph

Source-grounded analysis at commit `6e4a295` (2026-05-27, tip of
`main`, `fix(cli): pin internal_docker deploy images by digest`).
Package version `1.2.2` declared in `libs/langgraph/pyproject.toml:7`.
LangGraph is the most architecturally-comparable system to
spring-ai-ascend in this entire Tranche-1+Tranche-2 corpus — it ships
a **state-machine-as-runtime** with explicit interrupts, checkpoint
durability, and human-in-the-loop semantics. The analysis below
focuses heavily on the dimensions where direct comparison is
meaningful.

## 1. Tagline & positioning

The README headline at `README.md:12` reads "Low-level orchestration
framework for building stateful agents.". The opening body
(`README.md:32`) names the customers ("Klarna, Replit, Elastic")
and the four-pillar value proposition (`README.md:38-52`):

> "Durable execution — Build agents that persist through failures and
> can run for extended periods, automatically resuming from exactly
> where they left off. Human-in-the-loop — Seamlessly incorporate
> human oversight by inspecting and modifying agent state at any
> point during execution. Comprehensive memory — Create truly
> stateful agents with both short-term working memory for ongoing
> reasoning and long-term persistent memory across sessions.
> Debugging with LangSmith — Gain deep visibility into complex agent
> behavior with visualization tools. Production-ready deployment —
> Deploy sophisticated agent systems confidently with scalable
> infrastructure."

Positioning: **the durable state-machine runtime that any stateful
agent in Python should sit on top of**. The README positions LangChain
as the integration layer above and LangSmith Deployment as the hosted
runtime — LangGraph itself is the open-source kernel. Sponsor is
**LangChain, Inc.** (`LICENSE:3`, `Copyright (c) 2024 LangChain,
Inc.`), licence **MIT** (`LICENSE:1`). The four pillars map almost
1:1 to spring-ai-ascend's design intent: durable execution =
RunStateMachine + checkpoint persistence; human-in-the-loop = SuspendSignal
+ S2C callback; memory = GraphMemory; deployment = five-plane topology.
The deep difference is governance — LangGraph ships the runtime, not
the governance kernel.

## 2. Architecture skeleton

The reactor is a uv workspace of nine libs (`libs/{checkpoint,
checkpoint-conformance, checkpoint-postgres, checkpoint-sqlite, cli,
langgraph, prebuilt, sdk-js, sdk-py}`). The kernel runtime lives in
**`libs/langgraph/langgraph/`** with the following layout:

```
_internal/      # private helpers (cache, runnable adapter, retry policy, types)
channels/       # state-channel taxonomy (LastValue, BinaryOp, Topic, Delta, …)
func/           # functional-API entry points (entrypoint, task)
graph/          # StateGraph + MessageGraph + branch/node spec
managed/        # ManagedValue / RemainingSteps
pregel/         # Pregel-style superstep executor (main.py is the core)
stream/         # streaming primitives
runtime.py      # Runtime context type
types.py        # Interrupt, Command, Send, Durability, StreamMode, …
constants.py    # START/END node literals
errors.py       # ParentCommand, GraphInterrupt, InvalidUpdateError, …
```

The **central abstraction is `StateGraph`** declared at
`libs/langgraph/langgraph/graph/state.py:130`:
`class StateGraph(Generic[StateT, ContextT, InputT, OutputT])`. A
developer:

1. defines a state schema (TypedDict / Pydantic BaseModel /
   dataclass — see line 130 generic parameters);
2. adds nodes via `add_node(name, callable)` (overloaded — six
   overloads at lines 375–662);
3. adds edges (static or conditional);
4. calls `compile(checkpointer=...)` at line 1164 which produces a
   `CompiledStateGraph` (a Pregel-style runtime).

The execution engine is **Pregel** (Google's bulk-synchronous graph
model, named after Christopher Wren's invention of the discipline) —
`libs/langgraph/langgraph/pregel/main.py`. Nodes process state
updates in supersteps; channels mediate state propagation between
supersteps. Checkpoint persistence happens once per superstep, with
durability mode controlled by `types.py:88` (`Durability = Literal["sync", "async", "exit"]`).

The **checkpoint persistence SPI** is split across three packages:
`libs/checkpoint/langgraph/checkpoint/base/` (the abstract
`BaseCheckpointSaver`), `libs/checkpoint-postgres/langgraph/checkpoint/postgres/` (Postgres implementation with embedded DDL at
`base.py:44-80`), `libs/checkpoint-sqlite/` (SQLite). The
`checkpoint-conformance/` package is a shared test suite that any
custom saver must pass — directly analogous to a TCK.

## 3. Developer experience

The README quickstart at lines 30-33 is `pip install -U langgraph`.
The canonical first-agent example (documented at `libs/prebuilt/langgraph/prebuilt/chat_agent_executor.py:278`) uses the
`create_react_agent` factory:

```python
from langgraph.prebuilt import create_react_agent
agent = create_react_agent(model="anthropic:claude-3-5-sonnet",
                            tools=[search_tool],
                            checkpointer=InMemorySaver())
result = agent.invoke({"messages": [("user", "...")]}, config={"configurable": {"thread_id": "1"}})
```

The single `create_react_agent(...)` call at
`chat_agent_executor.py:278-998` builds a `StateGraph` of three nodes
(`agent → tools → agent → END`), compiles it with the provided
checkpointer, and returns it. The full surface is **builder +
compile** rather than annotation-driven DI. Configuration passes
through `RunnableConfig` (inherited from langchain-core) — the
`thread_id` field in `config["configurable"]` is the **only required
scoping primitive**. There is no posture (dev/prod) split, no
`@RequiredConfig` boot guard, no project scaffold. The doc-driven
upgrade path is "start with `create_react_agent`, drop down to
`StateGraph` when you need branching, drop down to `Pregel` /
`NodeBuilder` for full control".

For human-in-the-loop, the developer calls `from langgraph.types
import interrupt` at `types.py:801` inside a node:

```python
def my_node(state):
    decision = interrupt(value={"question": "approve?"})
    return {"answer": decision}
```

The graph pauses; the calling code receives an `Interrupt` object
(`types.py:525`), gathers the human reply, then resumes via
`graph.invoke(Command(resume=...))` (`Command` at `types.py:749`).
This is the same conceptual shape as our `SuspendSignal` — but the
LangGraph implementation is **single-process, checkpoint-anchored**
rather than bus-channel-routed.

## 4. Multi-tenancy & governance

**There is no tenant model in the kernel.** The Postgres checkpoint
DDL at `libs/checkpoint-postgres/langgraph/checkpoint/postgres/base.py:47-80` declares four tables:

```sql
checkpoints (thread_id TEXT, checkpoint_ns TEXT, checkpoint_id TEXT, ...)
checkpoint_blobs (thread_id TEXT, checkpoint_ns TEXT, channel TEXT, ...)
checkpoint_writes (thread_id TEXT, checkpoint_ns TEXT, ...)
checkpoint_migrations (v INTEGER PRIMARY KEY)
```

`thread_id` is the *only* scoping primitive — no `tenant_id`, no
`workspace_id`, no `user_id` column, no `CREATE POLICY` Row-Level
Security statement anywhere in the codebase. The repository-wide grep
for `tenant` returns hits only in `libs/cli/` (LangGraph CLI deploy
config) and `libs/sdk-py/` (LangSmith SDK encryption schema) — neither
is part of the framework kernel.

The intended pattern is "the calling application namespaces
`thread_id` to include the tenant" — fundamentally the same posture
as LangChain: tenancy is an application-level convention, not a
kernel-enforced invariant. The `Store` SPI
(`libs/langgraph/langgraph/store/base.py`) does carry a `namespace`
tuple — but that is a logical organisational primitive, not a
security boundary.

Governance surfaces are absent: no posture split, no fail-closed boot
guard, no idempotency spine, no audit MDC, no recurring-defect ledger.
By contrast, spring-ai-ascend enforces tenant isolation at the storage
engine (Rule R-J, RLS in every `tenant_id`-bearing Flyway migration),
HTTP edge (cancel re-validation), Run record level
(Rule R-C.2.a requires `Objects.requireNonNull(tenantId)`). This is
the **central dimension of differentiation** even though LangGraph is
otherwise the closest architectural sibling.

## 5. Engine pluggability

LangGraph's engine is **fixed and monolithic**: every agent compiles
to a `CompiledStateGraph` (`graph/state.py`) which is itself a Pregel
graph (`pregel/main.py:2523`). The four agent shapes (single-loop,
conditional, parallel fan-out, supervisor) are all `StateGraph`
patterns — there is no "alternative engine" SPI. The only escape
hatch is the **functional API** at `libs/langgraph/langgraph/func/`
(`@entrypoint` + `@task` decorators) which is sugar over the same
Pregel engine, not a different engine.

Cross-cutting policy is expressed via:

1. **Channel reducers** (`channels/` directory: `last_value.py`,
   `binop.py`, `topic.py`, `named_barrier_value.py`, `delta.py`) —
   state propagation semantics declared per-key on the state schema.
2. **Interrupts** (`types.py:525`, `types.py:801`) — pause-and-resume
   via `interrupt(value)` in a node.
3. **Commands** (`types.py:749`, `class Command(Generic[N], ToolOutputMixin)`) — node return type that combines state update +
   next-node directive + interrupt resume in one object.
4. **`Send`** (`types.py:654`) — fan-out primitive that sends one
   payload to one node-instance for parallel execution.

There is no envelope, no `engine_type` discriminator, no
`EngineMatchingException`, no `EngineRegistry.resolve(...)` call.
Adding a third engine shape (e.g. CrewAI-style hierarchical
delegation) means building it as a higher-level abstraction on top
of `StateGraph` — exactly how `create_react_agent` is structured. The
LangGraph **prebuilt catalog** (`libs/prebuilt/langgraph/prebuilt/`)
ships `chat_agent_executor.py`, `tool_node.py`, `interrupt.py`,
`tool_validator.py` as canonical patterns. This is a cleaner shape
than SAA (no `BaseAgent` inheritance contract) but still a single-engine
runtime — by contrast, spring-ai-ascend's dual-mode design (graph +
agent-loop sharing one `SuspendSignal` primitive) routes both shapes
through `EngineRegistry`.

## 6. Evolution substrate

LangGraph ships a **`Store` SPI** (`libs/langgraph/langgraph/store/base.py`, `libs/checkpoint-postgres/langgraph/store/postgres/base.py`) — a
key-value+vector store scoped by `namespace: tuple[str, ...]` and
`key: str`. The Postgres implementation at
`store/postgres/base.py:64-110` declares two tables — `store` (kv) and
`store_vectors` (embedding index). This is the **long-term memory
surface** — distinct from the short-term checkpoint store. It is the
closest LangGraph analogue to a "memory substrate".

However:

- **No trajectory schema**. A repo-wide grep for `Trajectory`
  returns hits only in test fixtures and doc examples. Past-task
  retrieval is the application's responsibility — `store.search(namespace, query, ...)` returns documents, not typed trajectory
  records.
- **No fine-tune / preference-data export path**. There is no
  evolution plane equivalent to spring-ai-ascend's `agent-evolve`
  module.
- **No skill registry**. Tools are passed as a `list[BaseTool]` to
  `create_react_agent`; there is no per-skill capacity matrix, no
  rate-limit declaration, no resource-arbitration concept.

The `BaseStore` + namespacing model is genuinely well-designed —
multi-namespace search, TTL refresh, vector + keyword combined — but
it is *one storage abstraction*, not an evolution plane. By contrast,
spring-ai-ascend's design intent ships memory as a separate substrate
(`agent-evolve`) on a distinct `deployment_plane: evolution` so the
data path of evolution can be physically isolated from runtime.

## 7. Deployment model

The OSS runtime is **library-only**, but LangGraph ships a CLI
(`libs/cli/langgraph_cli/`) that knows how to package a project as a
container for the hosted **LangSmith Deployment** product (formerly
LangGraph Platform). The CLI's `deploy.py:1+` references tenant-IDs
as part of LangSmith's hosted multi-tenancy — but those tenant
boundaries live in the *hosted product*, not the OSS framework. The
repo ships no Helm chart (`find -name "Chart.yaml"` returns nothing)
and no Kubernetes manifests.

**No Chinese-silicon support.** Repo-wide grep for `Ascend`/`Kunpeng`
returns zero hits. Python target is generic CPython 3.10–3.13
(`libs/langgraph/pyproject.toml:11-20`). Checkpoint backends ship as
Postgres + SQLite + in-memory only — no Chinese-database adapter, no
NPU model-serving partner. For Ascend sovereignty, LangGraph runs as
a generic Python process on the platform; there is no first-class
adapter for MindIE / Ascend-CL / ATB.

The most important deployment difference vs spring-ai-ascend: LangGraph
is **single-process by default** with checkpointer-mediated durability.
A graph runs in one Python process; durable resume after process
restart is handled by reading the checkpoint from Postgres on the next
invocation. spring-ai-ascend's five-plane topology
(edge/compute_control/bus_state_hub/sandbox/evolution) is physically
isolated across modules — a different architectural answer to the
same durability question. LangGraph wins on "single-process latency",
spring-ai-ascend wins on "blast radius isolation".

## 8. License + corporate sponsor

License: **MIT** (`LICENSE:1`, `Copyright (c) 2024 LangChain, Inc.`).
No BSL, no SSPL, no copyleft, no field-of-use restriction. The
`pyproject.toml:14-20` declares `license = "MIT"` and `license-files
= ['LICENSE']`. The sibling distribution packages
(`langgraph-checkpoint`, `langgraph-prebuilt`, `langgraph-sdk`,
`langgraph-checkpoint-postgres`, `langgraph-checkpoint-sqlite`,
`langgraph-cli`) inherit MIT.

Corporate sponsor: **LangChain, Inc.** (US, venture-backed) — same
parent as LangChain. The revenue model is the hosted **LangSmith /
LangSmith Deployment** product; the OSS runtime is the funnel.
Maintainers are the company's engineers. Latest commit
`6e4a295ba5003353c55a3ba53fecf4780590ae7e` dated **2026-05-27**
("fix(cli): pin internal_docker deploy images by digest"). Release
cadence is weekly+ — `pyproject.toml` shows v1.2.2 at the tip; the
sibling repos release in lockstep. Dependency declared upstream:
`langchain-core>=1.4.0,<2` (`pyproject.toml:32`) — LangGraph and
LangChain are version-locked.

The strategic implication for spring-ai-ascend: depending on LangGraph
binds us to LangChain-Inc.'s release cadence and the LangSmith-shaped
telemetry/governance assumptions. The same logic that bars SAA Maven
coordinates (per `feedback_saa_competitor.md`) applies — for a
sovereign Spring runtime, LangGraph is an upstream-adapter target, not
a runtime peer.

## 9. What we LEARN

Patterns worth absorbing into spring-ai-ascend:

1. **`Durability = Literal["sync", "async", "exit"]` as an explicit
   knob** (`libs/langgraph/langgraph/types.py:88`). Three named
   durability levels — sync (persist before next step), async
   (persist concurrent with next step), exit (only on graph exit) —
   directly expose the latency/safety tradeoff. spring-ai-ascend's
   checkpoint contract could declare the same three levels in
   `engine-envelope.v1.yaml` rather than hardcoding sync persistence.

2. **`interrupt(value) -> Any` as a node-local pause primitive**
   (`types.py:801`). The developer writes plain Python; the runtime
   handles persistence + resume. The resume payload is the function's
   return value when invoked with `Command(resume=...)`. This is more
   ergonomic than our typed `SuspendSignal.forClientCallback(...)` —
   though our typed envelope is more auditable. Worth borrowing the
   *ergonomic shape* in our SDK while keeping the typed envelope on
   the wire.

3. **Channel-based reducer taxonomy** (`channels/{last_value,
   binop,topic,named_barrier_value,delta}.py`) — state-merge
   semantics declared per-key. The `Delta` channel
   (`channels/delta.py`) ships state diffs over the wire rather than
   full snapshots, with snapshot-frequency policy declared in
   `checkpoint/base/__init__.py:60-85`. A direct ergonomic win for
   our Run state propagation.

4. **`Send(node, payload)` as fan-out primitive** (`types.py:654`).
   A node can return `[Send("worker", item) for item in items]` to
   trigger parallel sub-tasks. Spring-ai-ascend's parallel fan-out
   pattern would benefit from a similar typed primitive in the SPI.

5. **`Command(update=..., goto=..., resume=...)` as compound node
   return** (`types.py:749`). One return shape carries state update +
   next-node directive + interrupt resume. The merger of "control
   flow" and "data flow" into one typed object is a cleaner DX than
   multiple return surfaces.

6. **Conformance test pack** (`libs/checkpoint-conformance/`) — every
   custom `BaseCheckpointSaver` implementation runs the same shared
   suite. Direct template for our TCK strategy under Rule R-D.

7. **`Store` SPI with namespace + vector + TTL** (`libs/langgraph/langgraph/store/base.py`, postgres impl at `store/postgres/base.py:64-110`)
   — a clean small SPI for long-term memory with TTL refresh
   semantics built in. Our `GraphMemory` SPI is broader; the LangGraph
   `Store` is the minimum-viable shape worth aligning.

## 10. Where we DIFFER

| # | Dimension | LangGraph evidence | spring-ai-ascend evidence |
|---|-----------|--------------------|---------------------------|
| 1 | **Multi-tenancy depth** — LangGraph: `thread_id` is the only scoping primitive; no tenant column, no RLS. Ascend: tenant_id NOT NULL on Run, RLS at Postgres, re-validation at HTTP edge. | `libs/checkpoint-postgres/langgraph/checkpoint/postgres/base.py:47-80` (no `tenant_id` column) | Rule R-C.2.a + Rule R-J + `agent-service/.../Run.java` |
| 2 | **Engine Contract envelope vs single-engine runtime** — LangGraph: every agent IS a StateGraph IS a Pregel graph; no engine dispatch. Ascend: `EngineRegistry.resolve(envelope)` routes by typed `engine_type`. | `libs/langgraph/langgraph/graph/state.py:130` + `pregel/main.py` (single engine) | `docs/contracts/engine-envelope.v1.yaml` + Rule R-M.a/.b |
| 3 | **Spring-native ergonomics** — LangGraph is Python-only (no JVM port). Ascend ships Spring Boot starters as first-class developer surface. | `libs/langgraph/pyproject.toml:11-20` (Python 3.10–3.13) | `D:\chao_workspace\spring-ai-ascend\pom.xml` |
| 4 | **中台+能力复用 dual deployment** — LangGraph: single-process runtime with checkpoint-mediated durability + hosted LangSmith Deployment. Ascend: five-plane physical topology with `deployment_plane` per module. | `pregel/main.py` single Python process + no Helm chart in repo | Rule R-I + per-module `module-metadata.yaml#deployment_plane` |
| 5 | **Sandbox subsumption** — LangGraph: no sandbox-permission model; tool calls run in-process. Ascend: `docs/governance/sandbox-policies.yaml` with six required keys per policy, runtime refusal of over-wide grants. | `libs/prebuilt/langgraph/prebuilt/tool_node.py` (direct call) | Rule R-L + `docs/governance/sandbox-policies.yaml` |
| 6 | **Evolution substrate scope** — LangGraph: `Store` SPI is one storage abstraction; no trajectory schema, no fine-tune export. Ascend: dedicated `agent-evolve` module on `evolution` deployment plane + `EvolutionExport` discriminator on events. | `libs/langgraph/langgraph/store/base.py` (single Store SPI) | `D:\chao_workspace\spring-ai-ascend\agent-evolve\module-metadata.yaml` + Rule R-M.e |
| 7 | **Ascend+Kunpeng sovereignty** — LangGraph: generic CPython, no NPU adapter. Ascend: ARM64+NPU design target. | `pyproject.toml:11-20` (Python 3.10–3.13 only) | `D:\chao_workspace\spring-ai-ascend\README.md:3-10` |
| 8 | **Run state machine as enforced contract** — LangGraph: state transitions are channel-reducer applications, no validate(from, to) gate. Ascend: `RunStateMachine.validate(from, to)` called on every `Run.withStatus(...)`. | `libs/langgraph/langgraph/channels/last_value.py` (`update(...)` is unchecked merge) | Rule R-C.2.b + `agent-service/.../runtime/runs/RunStateMachine.java` |
| 9 | **License + sponsor posture** — LangGraph: MIT + LangChain Inc. (US venture-backed, hosted SaaS funnel via LangSmith Deployment). Ascend: Apache 2.0 + Huawei (no SaaS funnel). | `LICENSE:3` (LangChain, Inc.) | `D:\chao_workspace\spring-ai-ascend\LICENSE` (Apache 2.0, Huawei) |
| 10 | **Governance / Code-as-Contract** — LangGraph: comprehensive unit/integration tests + conformance suite for checkpoint sub-SPI; no architectural enforcers, no ADRs, no recurring-defect ledger. Ascend: 144+ gate rules + ArchUnit + governance YAML kernel. | `libs/checkpoint-conformance/` (TCK-style, but only for one SPI) | `CLAUDE.md` (Rule kernel index) + `gate/check_architecture_sync.sh` |

The LangGraph analysis is the most important in this Tranche because
it is the closest *architectural* sibling. Both projects start from
"agents need durable state machines + human-in-the-loop". The
divergence is governance: LangGraph trusts the application layer for
tenancy, posture, audit, sandbox; spring-ai-ascend enforces them in
the kernel. For an enterprise running sovereign agents on Ascend
silicon, the LangGraph approach forces the integrator to rebuild
half the spring-ai-ascend governance corpus on top of LangGraph.
Conversely, LangGraph's Pregel-style state machine and its
`interrupt`/`Command`/`Send` primitives are the most polished agent
runtime primitives in the open-source corpus — they belong in our
inspiration set for the next iteration of the Engine Contract envelope.
