---
analysis_id: COMPETITIVE-ADK-PYTHON
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 2
repo_clone_at: D:\ai-research\agent-platforms-survey\adk-python\
---

# Competitive Analysis: google/adk-python

Source-grounded analysis at commit `da1d8f1` (2026-05-21, tip of
`main`, "feat(interactions): update ADK to support Google GenAI SDK
v2.0.0"). The README at `README.md:1`:

> "# Agent Development Kit (ADK) 2.0
> An open-source, code-first Python framework for building, evaluating,
> and deploying sophisticated AI agents with flexibility and control."

ADK is Google's official agent development framework — distribution
name **google-adk** (`pyproject.toml:14`), namespaced under
`google.adk` (`src/google/adk/`). The 2.0 release banner at
`README.md:23-28` declares "Breaking changes from 1.x" with two
flagship additions:

> "**Workflow Runtime**: A graph-based execution engine for composing
> deterministic execution flows for agentic apps, with support for
> routing, fan-out/fan-in, loops, retry, state management, dynamic
> nodes, human-in-the-loop, and nested workflows.
> **Task API**: Structured agent-to-agent delegation with multi-turn
> task mode, single-turn controlled output, mixed delegation patterns,
> human-in-the-loop, and task agents as workflow nodes."

## 1. Tagline & positioning

The README's "code-first Python framework" framing
(`README.md:9-10`) directly competes with LangGraph (positioning at
`langgraph/README.md:12` is "low-level orchestration framework for
building stateful agents"). The two flagship ADK 2.0 features —
**Workflow Runtime** (graph executor) and **Task API**
(agent-to-agent delegation) — map directly to LangGraph's StateGraph
+ interrupts. The competitive intent is explicit.

ADK 2.0 ships:
- **Top-level package `google.adk`** at `src/google/adk/__init__.py:21-28`
  exposing `Agent`, `Context`, `Event`, `Runner`, `Workflow`;
- 30+ sub-modules including `agents/`, `apps/`, `workflow/`,
  `sessions/`, `tools/`, `memory/`, `evaluation/`, `optimization/`,
  `models/`, `events/`, `plugins/`, `a2a/`, `code_executors/`,
  `cli/` with `cli_deploy.py`, `runners.py`;
- An `llms-full.txt` (1.24 MB) artefact at the repo root — an
  LLM-readable concatenation of the full documentation for direct
  in-context loading. This is unique in the corpus — Google is
  explicitly designing for AI-assisted development of agent code.

License is **Apache 2.0** (`pyproject.toml:15`,
`License :: OSI Approved :: Apache Software License`). Corporate
sponsor: **Google LLC** (`pyproject.toml:16`,
`googleapis-packages@google.com`). The hosted target is **Google
Cloud Agent Engine** (per `cli_deploy.py:38`:
`'google-cloud-aiplatform[adk,agent_engines]'`) — the SaaS-funnel
pattern, but anchored in Google Cloud Vertex AI.

## 2. Architecture skeleton

The kernel `src/google/adk/` declares ~30 sub-modules. Top-level
exports (`__init__.py:21-28`):

```python
from .agents.context import Context
from .agents.llm_agent import Agent
from .events.event import Event
from .runners import Runner
from .workflow import Workflow

__version__ = version.__version__
__all__ = ["Agent", "Context", "Event", "Runner", "Workflow"]
```

The five canonical surface concepts are `Agent`, `Context`, `Event`,
`Runner`, `Workflow`. Sub-modules of architectural note:

```
agents/           # llm_agent.py, sequential_agent.py, loop_agent.py,
                  # parallel_agent.py, langgraph_agent.py (!), remote_a2a_agent.py
apps/             # App = top-level container, EventsCompactionConfig, ResumabilityConfig
workflow/         # NEW in 2.0 — _workflow.py (DynamicNodeScheduler),
                  # _graph.py (Graph, EdgeItem, RouteValue), _base_node.py,
                  # _function_node.py, _llm_agent_wrapper.py, _tool_node.py,
                  # _parallel_worker.py, _retry_config.py, _node_runner.py,
                  # _node_state.py, _node_status.py, _schedule_dynamic_node.py
sessions/         # database_session_service.py (SQLAlchemy schema),
                  # sqlite_session_service.py, in_memory_session_service.py,
                  # vertex_ai_session_service.py (HOSTED), state.py
events/           # Event + EventActions
runners.py        # Runner (~execution coordinator)
plugins/          # plugin registry
a2a/              # agent-to-agent protocol (agent, converters, executor)
code_executors/   # sandboxed code execution
memory/           # short/long memory
models/           # model registry + base LLM
tools/            # tools + toolsets
auth/             # credential services
artifacts/        # artifact storage
evaluation/       # eval harness
optimization/     # optimisation routines (closest to evolution substrate)
labs/             # experimental
platform/         # thread/time/uuid abstractions for portability
cli/              # adk command (~14 subcommand modules + cli_deploy.py)
```

The **central abstractions are `App`** (`apps/app.py:50`,
`class App(BaseModel)` — top-level container with either
`root_agent: BaseAgent` or `root_node: BaseNode`) and **`Workflow`**
(`workflow/_workflow.py:73`, `Workflow(BaseNode)` with `_run_impl()`
as the orchestration loop). Sessions persist to a SQLAlchemy-backed
relational schema (`sessions/database_session_service.py`) with
tables keyed by `(app_name, user_id, session_id)` — a more developed
identity model than peers (covered in §4).

## 3. Developer experience

The "first agent" surface is the `Agent` class (
`agents/llm_agent.py:68`):

```python
from google.adk import Agent, Runner
agent = Agent(model="gemini-2.0-pro",
              instruction="You are a helpful assistant",
              tools=[search_tool])
runner = Runner(agent=agent)
response = await runner.run_async(...)
```

ADK ships **five pre-built agent shapes** under `agents/`:
`llm_agent.py` (LLM-driven), `sequential_agent.py`, `parallel_agent.py`,
`loop_agent.py`, `remote_a2a_agent.py` (remote agent over A2A
protocol). Each has a **config sibling** (`llm_agent_config.py`,
`sequential_agent_config.py`, `parallel_agent_config.py`,
`loop_agent_config.py`) — config-as-data discipline that lets agents
be declared in YAML/JSON and rehydrated. This is **the strongest
config-as-data discipline in this Tranche** alongside ChatDev 2.0.

Notably, `agents/langgraph_agent.py` exists — ADK ships a wrapper
that lets a LangGraph graph run as an ADK agent. This is an explicit
interop adapter — Google's framework accepts LangGraph as a guest
runtime, not a competitor to displace.

The CLI is extensive: `cli.py`, `cli_create.py`, `cli_deploy.py`,
`cli_eval.py`, `dev_server.py`, `api_server.py`, `adk_web_server.py`.
A user runs `adk create my_agent` → `adk run` → `adk eval` →
`adk deploy`. The deploy path targets **Google Cloud Vertex AI
Agent Engine** (`cli_deploy.py:38`) or a local Docker container.

There is no posture split (`dev/prod`) annotated on configs, but the
config-as-data discipline + the multi-environment session services
(`vertex_ai_session_service.py` vs `database_session_service.py` vs
`sqlite_session_service.py` vs `in_memory_session_service.py`)
implicitly provide deployment-target substitution. Significant
absence: no `@RequiredConfig`-style fail-closed boot guard explicit
to ADK code.

## 4. Multi-tenancy & governance

**ADK has the strongest identity-model surface of any framework in
this Tranche** — though it stops short of multi-tenancy in the
spring-ai-ascend sense.

The `DatabaseSessionService` schema
(`sessions/database_session_service.py:81`) declares:

> "(app_name, user_id, session_id)"

as the **three-part primary key** for sessions. Tables include
`StorageSession` (PK `app_name, user_id, session_id`),
`StorageEvent` (filtered by `app_name`, `user_id`, `session_id`),
`StorageAppState` (PK `app_name`), `StorageUserState` (PK
`app_name, user_id`). The schema explicitly separates app-scope,
user-scope, and session-scope state. This is closer to
multi-tenancy than any peer in the Tranche — `app_name` functions
as a "tenant lite" namespace.

However:

- **No RLS / row-level security**. The schema uses SQLAlchemy
  filters (`schema.StorageEvent.app_name == session.app_name` at
  line 400-402), not Postgres RLS. A bug in any query could leak
  cross-app data; storage-engine isolation is not enforced.
- **`app_name` is not `tenant_id`**. A multi-tenant deployment
  would need to namespace `app_name` per tenant — ADK does not
  enforce that.
- **No posture (dev/prod) defaults**. The validator at
  `validate_app_name(name)` (`apps/app.py:48`) only enforces format
  (alphanum + underscore/hyphen + not equal to "user").
- **No audit MDC**, no `(runId, tenantId, fromStatus→toStatus)`
  envelope on events.

Governance surfaces present:

- **Schema-versioned session storage** — `_SchemaClasses` at
  `database_session_service.py:176` supports V0 and V1 schemas
  (line 363-367), with migration auto-detection. This is the
  cleanest schema-migration discipline in this Tranche.
- **Resumability config** on App (`apps/_configs.py`,
  `ResumabilityConfig`) — declarative durable execution.
- **Events compaction config** — declarative event-log compaction.
- **Plugin registry** (`plugins/`) — typed plugin contract.
- **Credential service abstraction** (`auth/credential_service/`).
- **Conformance harness** (`cli/conformance/`) — TCK-style suite
  for framework features.

ADK's governance posture is **enterprise-shape but not enterprise-strength**:
the schema discipline is the strongest in the Tranche, but the actual
isolation guarantees (RLS, posture, audit MDC, idempotency spine) are
not in the kernel. By contrast, spring-ai-ascend enforces tenant
isolation at the storage engine (Rule R-J RLS) and Run record
(Rule R-C.2.a `Objects.requireNonNull(tenantId)`).

## 5. Engine pluggability

ADK 2.0 ships **two parallel runtime shapes**:

1. **Agent runtime** (`agents/{llm_agent, sequential_agent, parallel_agent,
   loop_agent}.py`) — five built-in agent shapes; agents compose via
   sub-agent fields.

2. **Workflow runtime** (NEW in 2.0, `workflow/`) — graph-based
   executor with `Workflow(BaseNode)` (`_workflow.py:73`) supporting:
   - **Static graph** (`_graph.py` Graph + EdgeItem + RouteValue);
   - **Dynamic nodes** (`_dynamic_node_scheduler.py`,
     `_schedule_dynamic_node.py`) — nodes added at runtime;
   - **Parallel execution** (`_parallel_worker.py`);
   - **Join nodes** (`_join_node.py`) for fan-in;
   - **Function nodes** (`_function_node.py`),
     **tool nodes** (`_tool_node.py`),
     **LLM-agent wrapper nodes** (`_llm_agent_wrapper.py`);
   - **Retry config** (`_retry_config.py`),
     **node state + status** (`_node_state.py`, `_node_status.py`).

Plus the **LangGraph adapter** at `agents/langgraph_agent.py` — a
guest-runtime adapter.

Plus the **Task API** (referenced in `README.md:31` "Structured
agent-to-agent delegation") — multi-turn structured delegation with
`FINISH_TASK_TOOL_NAME` constant (`runners.py:48`).

Plus the **A2A protocol** (`a2a/` — agent, converters, executor,
experimental.py) — agent-to-agent network communication.

This is the **richest engine surface in the Tranche** by far —
ADK ships agent-as-LLM, agent-as-workflow-graph, agent-as-LangGraph-wrapper,
agent-as-remote-A2A, agent-as-task-delegate. The `App.root_agent` vs
`App.root_node` `Optional` field at `apps/app.py:60` (XOR validated
at `apps/app.py:50` `model_validator`) cleanly separates the two
top-level mounting points.

Cross-cutting policy:

- **`CallbackContext`** (`agents/callback_context.py`),
  **`InvocationContext`** (`agents/invocation_context.py`),
  **`ReadonlyContext`** (`agents/readonly_context.py`) — three context
  scopes for plugin/callback authorship;
- **Plugin registry** (`plugins/`);
- **Before/after model callbacks** (`agents/llm_agent.py:68` —
  `BeforeModelCallback` + `_SingleAfterModelCallback` TypeAlias);
- **Service registry** (`cli/service_registry.py`).

There is no `engine_type` envelope and no `EngineMatchingException`.
The discriminator is the `BaseAgent` subclass for agents and the
`BaseNode` subclass for workflow nodes. spring-ai-ascend's typed
envelope dispatch is a different discipline; ADK's polymorphism +
plugin pattern is the closest analogue ergonomically.

## 6. Evolution substrate

ADK 2.0 ships an **`optimization/`** sub-module — the closest
explicit evolution-substrate sub-package in this Tranche. The
directory exists; per the README "Workflow Runtime" + "Task API"
overlap, optimisation appears to handle workflow-graph optimisation
rather than agent-level learning.

The **`evaluation/`** sub-module + the `adk eval` CLI subcommand
(`cli/cli_eval.py`) provide a structured **evaluation harness** —
unique in this Tranche at this depth. The harness produces
score-graded artefacts that an evolution loop could consume.

Memory substrate:

- **`memory/`** module — short/long memory primitives;
- Session state segregated at three scopes (`app_state`, `user_state`,
  `session_state`) by the SQLAlchemy schema — long-term cross-session
  state at user-level is first-class;
- **Artifacts** (`artifacts/`) — typed artifact storage abstraction.

ADK does not ship a `EvolutionExport` discriminator on events, no
isolated evolution deployment plane, no fine-tune export. The
`optimization/` + `evaluation/` pair is the closest substrate; it
remains co-located with runtime rather than physically isolated.
For spring-ai-ascend, ADK validates the **evaluation harness as a
first-class framework concern** — Rule R-D sub-clause .a's TCK
conformance discipline could absorb the ADK `adk eval` pattern.

## 7. Deployment model

ADK ships the **most-developed deployment CLI in this Tranche** at
`cli/cli_deploy.py:1+`. The CLI targets multiple destinations:

- **Local Docker** (`cli_deploy.py` references `_GCLOUD_CMD` and
  containerisation);
- **Google Cloud Vertex AI Agent Engine** (`_AGENT_ENGINE_REQUIREMENT
  = 'google-cloud-aiplatform[adk,agent_engines]'` at `cli_deploy.py:38`)
  — the hosted Google product;
- **Local storage flag for offline mode** (`_LOCAL_STORAGE_FLAG_MIN_VERSION`
  at `cli_deploy.py:37`).

The `dev_server.py` + `api_server.py` + `adk_web_server.py` cover
the local-dev → API → web-UI progression. The `fast_api.py` provides
a FastAPI wrapper for production HTTP exposure. The
`built_in_agents/` directory bundles canonical example agents
shipped with the framework.

**No Helm chart in repo** (`find -name "Chart.yaml"` returns nothing).
**No Kubernetes manifests** beyond the Docker container shape. The
deployment posture is **local-dev → Google Cloud Vertex AI hosted**.
For non-Google-Cloud production, ADK is a library + Docker container,
self-managed.

**No Chinese-silicon support.** Repo-wide grep for `Ascend`/`Kunpeng`
returns zero hits. Python target is 3.10+ (`pyproject.toml:21`,
`Programming Language :: Python :: 3.10/3.11/3.12/3.13`). LLM
adapter via `google.genai` (Google GenAI SDK) — first-class Google
model integration; OpenAI / Anthropic available through `models/`
sub-module's registry. There is no ARM64/NPU adapter; the
expectation is Vertex AI handles model serving.

The **strategic positioning** is "Google's official agent framework
+ Vertex AI as hosted runtime". For spring-ai-ascend's sovereignty
mandate, this is structurally incompatible — Vertex AI is a
non-sovereign hosted service. ADK as a library outside Google Cloud
is usable but loses the deployment-target funnel.

## 8. License + corporate sponsor

License: **Apache 2.0** (`LICENSE` file, 11.5 KB; `pyproject.toml:15`,
`License :: OSI Approved :: Apache Software License`). All Python
files carry the Apache 2.0 header (`Copyright 2026 Google LLC`).

Corporate sponsor: **Google LLC** (`pyproject.toml:16`,
`googleapis-packages@google.com`). Authorship is concentrated under
Google engineers per commit history. Latest commit
`da1d8f15529bf6c741bb32a86c380d5cb3633ed1` dated **2026-05-21**
("feat(interactions): update ADK to support Google GenAI SDK v2.0.0").
Release cadence: ADK 2.0 (the active code path) shipped in 2026
with a major breaking-change banner at `README.md:23-28`. The
`CHANGELOG.md` is 240 KB — extensive release history including the
v1.x → v2.0 migration path.

The strategic implication for spring-ai-ascend: ADK is the **most
production-credible** framework in this Tranche after LangGraph —
Google's engineering rigour shows in the schema-versioned session
service, the conformance harness, the eval harness, the extensive
CLI. ADK is also the **closest competitor to spring-ai-ascend in
positioning** ("code-first Python framework for sophisticated AI
agents") — Google and Huawei target adjacent enterprise audiences.
The differentiating axes for spring-ai-ascend remain Spring-native
ergonomics, sovereign hardware (Ascend+Kunpeng), kernel-enforced
multi-tenancy with RLS, and the financial-vertical-first v1.0
mandate. ADK pulls developers into Vertex AI; spring-ai-ascend keeps
deployment on-prem.

## 9. What we LEARN

Patterns worth absorbing into spring-ai-ascend:

1. **Three-scope session schema (`app_name, user_id, session_id`
   primary key)** (`sessions/database_session_service.py:81`,
   `apps/app.py` schema). The three-table separation
   (`StorageAppState`, `StorageUserState`, `StorageSession`) with
   compound PK gives a clean shape for "app-scope vs user-scope vs
   session-scope" state. spring-ai-ascend's Run + Idempotency tables
   should adopt this discipline (add an `app_name` column adjacent
   to `tenant_id` for in-tenant scoping).

2. **Schema-versioned session service** (`_SchemaClasses` at
   `database_session_service.py:176-192` with V0/V1 detection at
   line 363-367). Automatic schema-version detection + selection is
   stronger than spring-ai-ascend's current Flyway-driven discipline
   for backward compatibility. Worth importing the pattern at the
   storage-adapter layer.

3. **Workflow graph + Task API as two co-existing runtimes**
   (`workflow/_workflow.py` + `runners.py`'s task-mode references).
   ADK explicitly ships **graph-based deterministic execution** and
   **agent-to-agent task delegation** as separate first-class
   primitives. spring-ai-ascend's dual-mode (graph + agent-loop)
   already follows this shape; the **`langgraph_agent.py` adapter**
   is the cleanest cross-framework interop pattern in this Tranche
   — worth mirroring as a `LangGraphAgentAdapter` in our
   agent-execution-engine.

4. **Conformance harness + eval harness as framework concerns**
   (`cli/conformance/`, `cli/cli_eval.py`, `evaluation/`). Two
   structured test surfaces — conformance for spec compliance,
   evaluation for score-graded behaviour. Direct prior art for
   spring-ai-ascend's TCK + DFX-driven testing under Rule R-D.

5. **`llms-full.txt` as in-context documentation** (1.24 MB
   pre-concatenated for LLM consumption). A novel artefact:
   AI-assisted development is a first-class concern, and the
   framework ships a paste-ready full-doc bundle. spring-ai-ascend
   could ship `governance/llms-full.txt` (rule corpus + ADR catalog
   concatenated) for the same purpose.

6. **App.validate_app_name(name)** (`apps/app.py:48`) — runtime
   format-validation of identifiers with reserved-keyword check
   (`name == "user"` forbidden). Cleaner than allowing arbitrary
   strings. spring-ai-ascend should adopt a similar
   `validateTenantId(...)` discipline alongside the NOT NULL
   constraint.

7. **`platform/` sub-module for portability** (`platform/{thread.py,
   time.py, uuid.py}`). A clean abstraction layer letting different
   deployment substrates (containerized Linux vs Google Cloud Run
   vs serverless) swap thread / time / uuid implementations.
   Worth absorbing as a `agent-platform-portability` adapter for
   Ascend-specific ARM64 substrates.

## 10. Where we DIFFER

| # | Dimension | ADK Python evidence | spring-ai-ascend evidence |
|---|-----------|---------------------|---------------------------|
| 1 | **Multi-tenancy depth** — ADK: three-scope `(app_name, user_id, session_id)` schema with SQLAlchemy filters; closer to multi-tenancy than peers but no RLS. Ascend: tenant_id NOT NULL on Run + Postgres RLS at storage engine + re-validation at HTTP edge. | `sessions/database_session_service.py:81` (three-scope PK, no RLS) | Rule R-C.2.a + Rule R-J + `agent-service/.../Run.java` |
| 2 | **Engine Contract envelope vs polymorphic agents** — ADK: dispatch by `BaseAgent`/`BaseNode` subclass polymorphism; LangGraph wrapper as guest runtime. Ascend: typed `EngineEnvelope` + `EngineRegistry.resolve()` with `EngineMatchingException`. | `agents/llm_agent.py:68 (Agent)` + `workflow/_base_node.py (BaseNode)` | `docs/contracts/engine-envelope.v1.yaml` + Rule R-M.a/.b |
| 3 | **Spring-native ergonomics** — ADK is Python-only (Google's Java equivalent is `google.cloud.aiplatform` SDK in a different repo). Ascend ships Spring Boot starters as first-class. | `pyproject.toml:21` (Python 3.10-3.13) | `D:\chao_workspace\spring-ai-ascend\pom.xml` |
| 4 | **中台+能力复用 dual deployment** — ADK: local-dev → Google Cloud Vertex AI Agent Engine hosted; no on-prem topology beyond Docker. Ascend: five-plane physical topology with `deployment_plane` per module. | `cli/cli_deploy.py:38` (Vertex AI hosted target) | Rule R-I + `module-metadata.yaml#deployment_plane` |
| 5 | **Ascend+Kunpeng sovereignty** — ADK: generic Python + Google GenAI SDK + Vertex AI; no ARM64/NPU adapter, sovereign-incompatible by design (Vertex AI funnel). Ascend: ARM64+NPU as design target. | `pyproject.toml:38` (`google.genai>=...`) + Vertex AI focus | `D:\chao_workspace\spring-ai-ascend\README.md:3-10` |
| 6 | **Posture-aware fail-closed defaults** — ADK: schema-versioned migration + config-as-data + `validate_app_name`, but no explicit dev/prod posture split or PostureBootGuard. Ascend: dev/research/prod defaults per knob + PostureBootGuard. | `apps/app.py:48` (format validation only) | Rule D-6 |
| 7 | **Evolution substrate** — ADK: `evaluation/` + `optimization/` + structured `adk eval` CLI; co-located with runtime. Ascend: dedicated `agent-evolve` module on `evolution` deployment plane + `EvolutionExport` discriminator on events. *(ADK richer eval harness; Ascend cleaner architectural boundary.)* | `cli/cli_eval.py` + `evaluation/` + `optimization/` | `D:\chao_workspace\spring-ai-ascend\agent-evolve\module-metadata.yaml` + Rule R-M.e |
| 8 | **Sandbox subsumption** — ADK: `code_executors/` ship sandboxed code-execution backends (`BuiltInCodeExecutor` referenced at `runners.py:50`). Ascend: `docs/governance/sandbox-policies.yaml` with six required keys; physical sandbox plane. *(ADK ships executable sandboxes; Ascend ships logical policy contract.)* | `code_executors/` + `BuiltInCodeExecutor` import | Rule R-L + `docs/governance/sandbox-policies.yaml` |
| 9 | **License + sponsor posture** — ADK: Apache 2.0 + Google LLC + Vertex AI hosted funnel. Ascend: Apache 2.0 + Huawei + no SaaS funnel. | `LICENSE` + Vertex AI integration | `D:\chao_workspace\spring-ai-ascend\LICENSE` |
| 10 | **Governance / Code-as-Contract** — ADK: schema-versioned migrations + Pydantic configs + conformance harness + eval harness + extensive CLI; no architectural enforcers / recurring-defect ledger / ADR catalog. Ascend: 144+ gate rules + ArchUnit + governance YAML kernel + ADR catalog + recurring-defect ledger. | `cli/conformance/` + schema-versioned sessions | `CLAUDE.md` + `gate/check_architecture_sync.sh` + `docs/governance/recurring-defect-families.yaml` |

ADK Python is the **most production-credible Python framework in
this Tranche** — Google's engineering discipline produces a
codebase with schema-versioned sessions, conformance + eval
harnesses, an extensive CLI, and a clean three-scope identity model.
It is also the **closest direct competitor in positioning** to
spring-ai-ascend: "code-first framework for sophisticated AI agents".
The defensible differentiation for spring-ai-ascend is the
**sovereignty axis** — Vertex AI integration makes ADK structurally
incompatible with on-prem Chinese-silicon deployments, the financial-
vertical-first v1.0 mandate, and the kernel-enforced multi-tenancy
discipline. ADK validates many architectural patterns
spring-ai-ascend independently arrived at (three-scope state,
conformance harness, dual runtime modes, plugin registry); the
**Spring-native + sovereign** combination remains the orthogonal
position no Python+Cloud framework can occupy.
