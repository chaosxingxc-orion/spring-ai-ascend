---
analysis_id: COMPETITIVE-OPENMANUS
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 3
repo_clone_at: D:\ai-research\agent-platforms-survey\OpenManus\
---

# Competitive Analysis: FoundationAgents/OpenManus

Source-grounded analysis at `main` cloned 2026-05-28. OpenManus is **not** a
competitor — it is a **contrast project**. It targets the single-developer
"open-source Devin-style autonomous coding agent" niche: Python CLI, single
process, single user, no tenant model, no server-side SPI library shape.

## 1. Tagline & positioning (contrast)

The repo's own elevator pitch, verbatim from `README.md:14-22`:

> "👋 OpenManus — Manus is incredible, but OpenManus can achieve any idea
> without an *Invite Code* 🛫! Our team members @Xinbin Liang and @Jinyu
> Xiang (core authors), along with @Zhaoyang Yu, @Jiayi Zhang, and @Sirui
> Hong, we are from @MetaGPT. The prototype is launched within 3 hours and
> we are keeping building! It's a simple implementation, so we welcome any
> suggestions, contributions, and feedback! Enjoy your own agent with
> OpenManus!"

The framing is research-prototype: a permissionless re-implementation of
Manus.im (a closed-beta autonomous-coding-agent product), built by
MetaGPT-affiliated researchers in 3 hours. The README continues
(`README.md:24`):

> "We're also excited to introduce [OpenManus-RL], an open-source project
> dedicated to reinforcement learning (RL)- based (such as GRPO) tuning
> methods for LLM agents, developed collaboratively by researchers from
> UIUC and OpenManus."

**Customer profile**: a single technical user — researcher or solo
developer — who installs Python 3.12 via conda or uv, edits a `config.toml`
to drop in their OpenAI / Anthropic / Bedrock / Azure / Ollama / Google
API key (the config supports all eight providers per the
`config/config.example-model-*.toml` files), and runs `python main.py` to
get a CLI agent loop. There is no UI server, no multi-user mode, no
production deployment story.

**How this differs from spring-ai-ascend**: spring-ai-ascend is a JVM
library + BoM shipped via Maven Central, deployed as a tenant-isolated
server-side platform. OpenManus is a Python script with a `requirements.txt`
that requires playwright, browser-use, docker, and 30+ other heavy
dependencies — closer to a Jupyter-notebook research artefact than a
shippable service. The audiences do not overlap.

## 2. Architecture skeleton

OpenManus is a small Python codebase. The `app/` package contains the
runtime (verified by `ls`):

```
app/
  agent/      # BaseAgent + ReActAgent + Manus + ToolCallAgent + SWE + DataAnalysis
  flow/       # BaseFlow + PlanningFlow + FlowFactory (multi-agent orchestration)
  tool/       # PythonExecute, BrowserUseTool, StrReplaceEditor, AskHuman, Terminate, MCP, ...
  sandbox/    # client.py + core/ (Docker-based sandbox)
  mcp/        # MCP server.py
  prompt/     # system + next-step prompt templates
  llm.py      # LLM provider abstraction (OpenAI-shape; supports Anthropic, Bedrock, Azure, Ollama)
  config.py   # config.toml loader
  schema.py   # Memory, Message, AgentState enum, ROLE_TYPE
  daytona/    # remote-sandbox integration with Daytona
  bedrock.py  # Bedrock provider helper
```

Entry points (root):

- `main.py` — single-agent CLI (`python main.py --prompt "..."`).
- `run_flow.py` — multi-agent flow runner.
- `run_mcp.py` + `run_mcp_server.py` — MCP client / server modes.
- `sandbox_main.py` — sandboxed execution entry point.

**Class hierarchy**: `BaseAgent` (`app/agent/base.py:13`) is an abstract
Pydantic `BaseModel`+`ABC` with `name`, `description`, `system_prompt`,
`memory: Memory`, `state: AgentState`, `max_steps: int = 10`,
`current_step: int`, and `@asynccontextmanager state_context()` for
state transitions (lines 13-80). `ReActAgent` (`app/agent/react.py:11`)
extends BaseAgent with abstract `think()` and `act()` methods and a
concrete `step()` that orchestrates think→act. `ToolCallAgent`
(`app/agent/toolcall.py`) extends ReActAgent with tool-call parsing.
`Manus` (`app/agent/manus.py:18`) is the user-facing default — a
versatile general-purpose agent with tools `{PythonExecute,
BrowserUseTool, StrReplaceEditor, AskHuman, Terminate}` and MCP client
integration. Specialised siblings: `swe.py` (software-engineering
specialised), `data_analysis.py`, `browser.py`, `sandbox_agent.py`,
`mcp.py`.

**Counterpart mapping**: there is no counterpart to any spring-ai-ascend
module. No `tenantId`, no Run aggregate, no idempotency, no
multi-tenancy. The closest analogue is `BaseAgent` → spring-ai-ascend's
`com.huawei.ascend.engine.spi.Executor`; but OpenManus's design is
sub-classing-by-Python-MRO whereas ascend uses typed envelope dispatch.

## 3. Developer experience

Two install paths per `README.md:31-83`: conda (Method 1) or uv
(Method 2 — recommended for speed). Both end in `pip install -r
requirements.txt` and a config copy: `cp config/config.example.toml
config/config.toml` then edit to add `api_key`. Then `python main.py
--prompt "Build me a python web app that..."`.

The DX is **prompt-first** — there is no scaffolding, no project
template, no IDE integration. The agent has 20-step max default
(`Manus.max_steps = 20` at `manus.py:28`) and consults its tool
collection at each step. Tools auto-import via the constructor
default factory (`ToolCollection(PythonExecute(), BrowserUseTool(),
StrReplaceEditor(), AskHuman(), Terminate())`, `manus.py:34-42`).

There is no programmatic SDK to embed OpenManus inside another product
— the API surface is the CLI plus the `Manus.create()` factory
(`manus.py:60-65`). Compared to spring-ai-ascend's Spring-Boot-starter
shape (a developer adds a Maven dependency and writes
`@ConfigurationProperties` to drive behaviour), OpenManus's shape is
"clone the repo, edit `config.toml`, run python".

## 4. Multi-tenancy & governance (contrast)

**There is no tenant model.** Repository-wide search finds:

- No `tenant_id` / `tenantId` / `TenantContextHolder` symbols.
- No Row-Level Security migrations (no `*.sql` files; persistence
  layer is in-process `Memory` per `app/schema.py`).
- No audit MDC, no `RunStateMachine`, no idempotency table.
- No JWT validation, no posture (dev/research/prod) split.

The `AgentState` enum in `schema.py` is `IDLE | RUNNING | FINISHED |
ERROR` — a per-instance lifecycle, not a persisted state machine.
`Memory` (`app/schema.py`) is an in-process list of `Message`s with
optional `images`. There is no concept of "this run vs that tenant's
run"; the binary serves a single user with single configuration.

**Why this matters**: OpenManus deliberately optimises for *researcher
hackability* (the README brags about a 3-hour prototype), which is
mutually exclusive with the multi-tenant audit-grade discipline that
spring-ai-ascend's Rule R-C.2 / Rule R-J / Rule D-6 demand. These are
opposite quality vectors.

## 5. Engine pluggability

OpenManus has **agent specialisation by Python sub-classing**. The
hierarchy in `app/agent/`:

```
BaseAgent (ABC)
  └── ReActAgent (think/act loop)
        └── ToolCallAgent (tool-call parsing)
              ├── Manus (general-purpose default)
              ├── SWEAgent (SWE-specialised)
              ├── DataAnalysisAgent
              ├── BrowserAgent
              ├── SandboxAgent
              └── MCPAgent (MCP-mediated)
```

There is no `EngineRegistry.resolve()` analogue, no envelope schema, no
typed `EngineMatchingException`. Selecting a different "engine" means
instantiating a different `BaseAgent` subclass. The flow layer
(`app/flow/`) is a separate multi-agent orchestrator — `FlowFactory`
(`flow_factory.py`) constructs `PlanningFlow` (`planning.py`) which
holds a dict of named agents and routes between them. This is closer
to "manual agent supervisor" than the structured workflow / agent-loop
dichotomy in spring-ai-ascend's Engine Contract envelope.

Tool extensibility: `ToolCollection` (`app/tool/`) holds a list of
`BaseTool` instances. Adding a custom tool means subclassing `BaseTool`
(`app/tool/base.py`) and adding it to the agent's `available_tools`
field. MCP support (`app/tool/mcp.py` + `app/mcp/server.py` +
`run_mcp.py` + `run_mcp_server.py`) lets the agent connect to MCP
servers over `sse` or `stdio` transports per `manus.py:67-90` —
arguably the cleanest external-engine bridge in OpenManus.

## 6. Evolution substrate

OpenManus has *prompt + memory* but no built-in evolution plane. The
key surfaces:

- **Memory**: `app/schema.py` defines `Memory(BaseModel)` as an
  in-process list of `Message`s with `max_messages` cap. No durable
  long-term memory; every `python main.py` invocation starts fresh.
- **Prompts**: `app/prompt/` has hand-tuned system + next-step prompts
  per agent class (`prompt/manus.py`, `prompt/swe.py`,
  `prompt/data_analysis.py`).
- **Trajectories**: `workspace/` directory and an evals path exist but
  there is no formal trajectory store. Grep finds no `Trajectory`
  class. The OpenManus-RL sister project (referenced at
  `README.md:24`) does collect trajectories for GRPO training, but
  *that lives in a separate repository* — OpenManus the agent does
  not ship the evolution loop.
- **Vector store / RAG**: not first-class. The README does not mention
  retrieval. Tools like `BrowserUseTool` provide live web access; the
  agent does not have an opinionated RAG retriever.

The evolution gap is real but architecturally consistent: OpenManus is
*the prototype*; OpenManus-RL is *the training loop*; they federate
loosely. Spring-ai-ascend integrates the equivalent of both into a
single five-plane topology (Rule R-I) with `EvolutionExport` scope
discriminators (Rule R-M.e) — more cohesive but heavier.

## 7. Deployment model

OpenManus is **local-only**. Install paths:

- Conda `conda create -n open_manus python=3.12` + `pip install -r
  requirements.txt` (`README.md:36-54`).
- uv `uv venv --python 3.12 && uv pip install -r requirements.txt`
  (`README.md:58-83`).

There is a `Dockerfile` at the repo root for containerised
execution but no `docker-compose.yml`, no Kubernetes manifests, no Helm
chart. The `daytona/` directory provides remote-sandbox integration
with the Daytona cloud workspace service. The `app/sandbox/` directory
wraps Docker for tool sandboxing (`app/sandbox/client.py` +
`app/sandbox/core/`).

**No Ascend / Kunpeng support.** A repository-wide grep for
`Ascend|Kunpeng|昇腾|鲲鹏` returns zero source-code hits. The Python
runtime is generic CPython 3.12; the Docker base image is generic
Python. The architectural focus is provider-agnostic via the
OpenAI-shape `LLM` abstraction (`app/llm.py`) — eight provider configs
ship as `config.example-model-{anthropic,azure,google,jiekouai,ollama,
ppio}.toml` (`config/`).

By contrast, spring-ai-ascend is positioned for Ascend NPU + Kunpeng
ARM64 sovereign deployment with five-plane topology declared per
module. Different optimisation targets.

## 8. License + corporate sponsor

License: **MIT** (`LICENSE:1-3`, "Copyright (c) 2025 manna_and_poem"
— the core author's GitHub handle).

Corporate sponsor: **none formally declared.** The README credits
five MetaGPT-affiliated researchers (Liang, Xiang, Yu, Zhang, Hong)
and the OpenManus-RL collaboration with UIUC. There is a Discord
community + a Hugging Face demo space + a Zenodo DOI
(`10.5281/zenodo.15186407`, `README.md:12`). Funding model: open-source
research project, no corporate steward.

This is a meaningful contrast: spring-ai-ascend's positioning depends on
Huawei's sovereignty narrative; OpenManus has no comparable corporate
backing — it is a researcher-led prototype that scaled. Both ship MIT-like
permissive licenses (ascend is Apache 2.0), but the maintenance models
are opposite (corporate-led vs research-collective).

## 9. What we LEARN

Patterns worth absorbing despite the customer-mismatch:

1. **MCP-as-first-class-engine-bridge** — `app/tool/mcp.py` plus
   `manus.py:67-90` shows clean MCP integration: agent reads
   `config.mcp_config.servers`, loops over each server config, calls
   `connect_mcp_server()` with either SSE URL or stdio command. The
   pattern is sharper than what spring-ai-ascend currently has in
   `agent-bus` MCP plans — we should mirror the per-server config
   block + transport-type discriminator (`type: sse | stdio`).

2. **Eight named provider configs as docs** — `config/config.example-
   model-{anthropic,azure,google,jiekouai,ollama,ppio}.toml` ships one
   example per provider. Spring-ai-ascend's `application.yml`
   examples should follow this naming convention so a developer can
   `cp` a known-good template for their stack rather than read the
   full schema and guess.

3. **Agent-state context manager** — `BaseAgent.state_context()`
   (`app/agent/base.py:58-80`) is a clean async context manager that
   transitions state on entry, transitions to `ERROR` on exception,
   and restores on exit. Conceptually equivalent to ascend's
   `RunStateMachine.validate(from, to)` but expressed as a Python
   `@asynccontextmanager`. Worth absorbing as the Java-idiomatic
   reactive analogue (a `Mono.using(...)` wrapping the state
   transition).

4. **Per-agent prompt directory** — `app/prompt/{manus,swe,
   data_analysis,...}.py` keeps system + next-step prompts beside the
   agent class. Spring-ai-ascend's hook taxonomy is implicit; making
   the prompt-per-engine convention explicit (e.g.
   `agent-execution-engine/src/main/resources/prompts/<engine>/`)
   would let prompt tuning live with the code.

5. **`Terminate` tool as explicit halt** — `app/tool/terminate.py` is
   a no-op tool the agent calls to signal completion. Spring-ai-ascend
   currently uses suspension-or-success states; an explicit terminate
   tool that the agent self-invokes is a cleaner halting predicate.

6. **PlanningFlow as orchestrator** — `app/flow/planning.py` plus
   `FlowFactory.create_flow(FlowType.PLANNING, agents={"manus":
   agent})` is the multi-agent orchestrator. Compared to LangGraph's
   `StateGraph`, this is much simpler — a planner agent picks the
   next worker agent from a registered dict. Worth absorbing as the
   "minimal viable orchestration" pattern for ascend's executor
   layer.

7. **Sandbox dual-mode** — `app/sandbox/` (Docker-local) + `app/daytona/`
   (Daytona-cloud) gives the agent two sandbox backends with the same
   client interface. Spring-ai-ascend's Rule R-L sandbox-permission
   subsumption assumes a single backend; making the backend
   pluggable (local-docker + cloud-managed) without policy change is
   a useful affordance.

## 10. Where we DIFFER

| # | Dimension | OpenManus evidence | spring-ai-ascend evidence |
|---|-----------|-------------------|---------------------------|
| 1 | **Runtime substrate** — OpenManus: CPython 3.12 + Pydantic + asyncio. Ascend: JVM 21 + Spring Boot 3.x + Reactor. | `requirements.txt:1` (`pydantic~=2.10.6`), `pyproject.toml` | `D:\chao_workspace\spring-ai-ascend\pom.xml` (Java 21) |
| 2 | **Tenant model** — OpenManus: none. Ascend: tenantId NOT NULL on every Run. | grep `tenant\|TenantContextHolder` returns zero source hits | Rule R-C.2.a + `Run.java` |
| 3 | **Deployment shape** — OpenManus: `python main.py` from a clone. Ascend: Maven dependency consumed by JVM apps. | `README.md:31-83` (conda/uv install paths) | `spring-ai-ascend-dependencies/pom.xml` (BoM) |
| 4 | **Audience** — OpenManus: researchers + solo developers. Ascend: enterprises with multi-tenant workloads. | `README.md:14-22` (3-hour prototype framing) | `D:\chao_workspace\spring-ai-ascend\README.md` |
| 5 | **Hardware sovereignty** — OpenManus: x86_64 Linux/Mac CPython. Ascend: Ascend NPU + Kunpeng ARM64. | `Dockerfile` (generic python:3.12), grep `Ascend\|Kunpeng` zero hits | Rule R-I five-plane |
| 6 | **Engine pluggability** — OpenManus: Python sub-classing of BaseAgent. Ascend: typed `EngineEnvelope` + `EngineRegistry`. | `app/agent/{react,toolcall,manus,swe,...}.py` MRO chain | `docs/contracts/engine-envelope.v1.yaml` + Rule R-M.a |
| 7 | **Persistent durability** — OpenManus: in-process `Memory` list; no DB. Ascend: Run aggregate + idempotency + checkpoint backends. | `app/schema.py` (Memory in-process) | `agent-service/.../runtime/runs/` + Flyway migrations |
| 8 | **Posture / fail-closed** — OpenManus: no posture concept. Ascend: every config knob declares `dev/research/prod` defaults. | `config/config.toml` (single config) | Rule D-6 PostureBootGuard |
| 9 | **Sandbox topology** — OpenManus: Docker-local or Daytona-cloud, dual-backend. Ascend: per-skill sandbox-policy YAML + RuleR-L. | `app/sandbox/`, `app/daytona/` | `docs/governance/sandbox-policies.yaml` + Rule R-L |
| 10 | **Governance** — OpenManus: pytest + minimal CI. Ascend: 144+ gate rules, ArchUnit, ADR-driven, recurring-defect ledger. | `pyproject.toml`, `tests/sandbox/` (one test dir) | `D:\chao_workspace\spring-ai-ascend\CLAUDE.md` |

**Closing positioning note**: OpenManus reinforces the empirical signal
— the open-source "autonomous coding agent" space has chosen **Python
3.12 + asyncio + Pydantic** as its substrate. This is the same gap
OpenClaw / aider / SWE-agent occupy. None of the seven coding-agent
projects in this tranche is JVM. Spring-ai-ascend's positioning gap for
JVM enterprises (multi-tenant audit-grade governance on sovereign
hardware) is structurally un-served by these tools. OpenManus
optimises for *fast research iteration*; ascend optimises for *durable
multi-tenant operation*. Both are valid, and they do not compete.
