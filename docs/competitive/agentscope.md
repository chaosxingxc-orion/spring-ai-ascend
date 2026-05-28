---
analysis_id: COMPETITIVE-AGENTSCOPE
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 1
repo_clone_at: D:\ai-research\agent-platforms-survey\agentscope\
---

# Competitive Analysis: agentscope-ai/agentscope

Source-grounded analysis at commit `fca8f16` (2026-05-26, tip of `main`),
PyPI version dynamically derived from `agentscope._version.__version__`
(`pyproject.toml:123`). AgentScope is authored by the SysML team of
Alibaba Tongyi Lab (`pyproject.toml:8`) and shares the same corporate
gravity well as Spring AI Alibaba (DashScope/Qwen are the documented
default models — `README.md:155-159`), but unlike SAA the framing is
explicitly **research-oriented / multi-agent simulation** rather than
production agent-loop development.

## 1. Tagline & positioning

The repository's own pitch, verbatim from `README.md:60-71`:

> "AgentScope 2.0 is a production-ready, easy-to-use agent framework
> with essential abstractions that work with rising model capability
> and built-in support for finetuning. We design for increasingly
> agentic LLMs. Our approach leverages the models' reasoning and tool
> use abilities rather than constraining them with strict prompts and
> opinionated orchestrations."

The README claims three pillars: **Simple** (5-minute first agent),
**Extensible** (MCP + A2A + message-hub multi-agent orchestration),
and **Production-ready** (local / serverless / K8s deployment with
built-in OpenTelemetry — `README.md:68-70`). Two arXiv citations are
prominently displayed (`README.md:231-251`): the 2024 multi-agent
platform paper (cs.MA/2402.14034) and the 2025 developer-centric
paper (2508.16279) — a strong signal that the project is paper-first,
implementation-second. The PyPI classifier list explicitly names
"Intended Audience :: Science/Research" alongside Developers
(`pyproject.toml:17-19`), and Development Status is "4 - Beta"
(`pyproject.toml:14`), not stable. Compared to LangGraph (whose
positioning is "production-grade graph execution") and AutoGen
(Microsoft's "conversation-driven multi-agent framework"), AgentScope
sits between them: it ships a *single* `Agent` class (`src/agentscope/
agent/_agent.py:94`) that subsumes the ReAct loop, with explicit hooks
for **finetuning**, **realtime voice**, and **message-hub** multi-agent
chat. Positioning vs spring-ai-ascend: AgentScope is **Python**,
**research-leaning**, **single-user** (no tenant), and **DashScope-
default**; spring-ai-ascend is **Java/Spring**, **enterprise**,
**multi-tenant** with **sovereign-hardware** as the differentiator.

## 2. Architecture skeleton

The Python package layout is flat and large — 198 `.py` files under
`src/agentscope/` (verified by `find -name "*.py" | wc -l`). The 20
top-level subpackages are visible in the directory listing:

```
src/agentscope/
  agent/        # Agent class (~2390 lines in _agent.py)
  app/          # FastAPI-based "Agent Service" backend
    _router/    # session, agent, chat, schedule, workspace, credential routers
    _middleware/# tool-offload middleware + protocol
    _manager/   # session, workspace, scheduler, background-task managers
    storage/    # Redis-backed storage with abstract base
    _schema/    # Pydantic request/response models
  credential/   # API-key + provider credential abstraction
  embedding/    # embedding model adapters
  event/        # 20+ AgentEvent subtypes (ModelCallStart, ToolCallDelta, ...)
  formatter/    # prompt formatters per provider
  mcp/          # MCP protocol client
  message/      # Msg / UserMsg / AssistantMsg / ToolCallBlock / ...
  middleware/   # MiddlewareBase + OpenTelemetry tracing setup
  model/        # 9 provider adapters (DashScope, OpenAI, Anthropic, Gemini, ...)
  permission/   # PermissionEngine + ALLOW/DENY/ASK rules
  skill/        # Skill loader (filesystem + base)
  state/        # AgentState + Task
  tool/         # Toolkit + 6 built-in tools (Bash, Grep, Glob, Read, Write, Edit)
  types/        # type-only aliases
  workspace/    # local / docker / e2b workspace sandboxes
```

**Counterpart mapping to spring-ai-ascend**:

| spring-ai-ascend                  | AgentScope counterpart                                | Notes |
|-----------------------------------|-------------------------------------------------------|-------|
| `agent-service`                   | `agentscope.app` (FastAPI router + session manager)   | No Run aggregate, no idempotency, no tenant_id |
| `agent-bus`                       | `agentscope.event` (in-process AsyncGenerator stream) | Single channel, no bus separation |
| `agent-execution-engine`          | `agentscope.agent._agent.Agent` (2390-line god class) | One executor; no envelope/registry split |
| `agent-middleware`                | `agentscope.middleware` + `agentscope.app.storage`    | Redis-only storage backend |
| `agent-client`                    | `examples/web_ui` (separate pnpm/Vite UI)             | No edge↔compute split |
| `agent-evolve`                    | (none — no Python evolution plane, no trajectory)     | Finetune is documented as a feature but ships zero finetune code in-tree |
| `spring-ai-ascend-graphmemory-starter` | (none — chat history only via `ChatHistoryMemory`) | No graph-memory primitive |

The headline difference is the monolithic `Agent` class. The single
file `src/agentscope/agent/_agent.py` is 2390 lines and contains
toolkit binding, reasoning loop, model-call middleware dispatch,
tool-result offloading, permission engine, and event streaming all
inline (`_agent.py:94-183` for the constructor alone wires 8 named
collaborators including 5 middleware filter lists).

## 3. Developer experience

The README "5-minute first agent" example (`README.md:138-188`) is
**29 lines of Python**, declares `DashScopeCredential` from
`os.environ["DASHSCOPE_API_KEY"]`, builds an `Agent(name=...,
system_prompt=..., model=DashScopeChatModel(...), toolkit=Toolkit(
tools=[Bash, Grep, Glob, Read, Write, Edit]))` and consumes events via
`async for evt in agent.reply_stream(UserMsg("Tony", "Hi, Friday!"))`.
The event taxonomy is rich — 24 `EventType` constants enumerated in
the `match evt.type` block (`README.md:172-185`), spanning
`REPLY_START`, `MODEL_CALL_START`, `TEXT_BLOCK_DELTA`,
`TOOL_CALL_START/END`, `THINKING_BLOCK_DELTA`, etc.

For server-side use, the `examples/agent_service/` directory
(`README.md:190-211`) ships a FastAPI backend (`main.py`) plus the
`examples/web_ui/` Vite/Next.js frontend. Onboarding is two
commands: `python main.py` in one terminal, `pnpm install && pnpm
dev` in another. The README explicitly markets it as "multi-tenancy,
multi-session" (`README.md:192`), but the storage interface
(`src/agentscope/app/storage/_base.py:21-80`) accepts `user_id: str`,
not `tenant_id` — every method is keyed by user, not by tenant. So
"multi-tenancy" here means "multiple end-users via different
`user_id`s sharing one server", not architectural isolation.

The default model provider is **DashScope** (Alibaba Cloud's Qwen
service) — the README example hardcodes `model="qwen3.6-plus"`
(`README.md:158`). Other providers exist as adapter dirs under
`src/agentscope/model/` (9 providers: Anthropic, DeepSeek, Gemini,
Moonshot, Ollama, OpenAI chat/response, xAI). Compared to
spring-ai-ascend's Spring-Boot `application.yml` posture-aware
defaults, AgentScope onboarding is **environment-variable + Python
code**; there is no posture concept, no Flyway migration, no audit
spine, and no fail-closed startup.

## 4. Multi-tenancy & governance

**There is a user model, not a tenant model.** Every storage method in
the `StorageBase` abstract class (`src/agentscope/app/storage/_base.py:
21-80`) is keyed by `user_id: str`. Examples: `upsert_credential(
user_id, credential_data)` at line 41-46, `list_credentials(user_id)`
at line 60-66, `get_credential(user_id, credential_id)` at line 74-78.
The session router (`src/agentscope/app/_router/_session.py:30-58`)
gates every operation by `user_id: str = Depends(get_current_user_id)`
and returns 404 if the requested `agent_id` does not belong to that
user. A repository-wide `Grep -i "tenant"` returns **zero matches in
`src/`** (verified — only unrelated occurrences in elsewhere).

What does exist is a `PermissionEngine` (`src/agentscope/permission/
_engine.py:16-80`) that gates **tool execution** at the agent boundary
— evaluating `Bash`, `Write`, `Read` calls against ALLOW/DENY/ASK
rules with PermissionMode.{BYPASS, EXPLORE, ACCEPT_EDITS}. This is a
**developer-machine-safety** mechanism (echoing Claude Code's
permission UX), not a tenant-isolation boundary. There is no Postgres
RLS, no `tenant_id NOT NULL` column anywhere, no posture-aware
fail-closed defaults, no audit MDC, and no idempotency spine.

Compared to spring-ai-ascend, which enforces tenant isolation at three
layers (storage RLS per Rule R-J.a, HTTP edge re-validation per Rule
R-J.b, Run record per Rule R-C.2.a), AgentScope's governance surface
is a developer-permission gate, not an enterprise-tenancy boundary.
The "multi-tenancy" claim in `README.md:192` is marketing language
for "multi-user via authentication cookie", not architectural tenant
isolation.

## 5. Engine pluggability

There is exactly one execution shape: the `Agent` class
(`src/agentscope/agent/_agent.py:94`). It is not subclassed in the
production tree — search for `class.*Agent.*:.*Agent` returns the
single declaration. Customisation flows entirely through composition:

- **Tools**: registered through `Toolkit()` with built-in
  `Bash/Grep/Glob/Read/Write/Edit` (`README.md:160-167`) plus MCP
  servers and skills.
- **Middlewares**: a `MiddlewareBase` (`src/agentscope/middleware/
  _base.py`) declares five hook points — `on_reply`, `on_reasoning`,
  `on_acting`, `on_model_call`, `on_system_prompt` (constructor
  filters at `_agent.py:167-181`). This is conceptually similar to
  spring-ai-ascend's `RuntimeMiddleware + HookPoint` contract (Rule
  R-M.c), but the hook points are agent-loop-specific, not
  engine-agnostic.
- **Models**: 9 provider adapters under `src/agentscope/model/`, each
  with its own `_model.py` + optional `_models/*.yaml` model card.
- **Permission rules**: behaviour-keyed (ALLOW/DENY/ASK) per tool
  name (`_engine.py:53-79`).

There is **no Engine Registry, no `EngineEnvelope`-shaped dispatch,
no `engine_type` discriminator, no `EngineMatchingException`**. Adding
a second engine (e.g., a graph-style executor or an external framework
like LangGraph) means writing a `MiddlewareBase` that intercepts
`on_reply` and rewrites the loop — or forking the `Agent` class. By
contrast, spring-ai-ascend's `EngineRegistry.resolve(envelope)` (Rule
R-M.a/.b) lets a downstream module register a wholly different engine
without touching the runtime aggregate. The structural intent of
AgentScope is "one good ReAct agent abstraction"; spring-ai-ascend's
is "dual-mode runtime that admits any engine that fits the envelope".

The `app/_middleware/_tool_offload_middleware.py` is an interesting
sub-pattern — context offloading (moving large tool results out of
the chat context) is implemented as middleware, which spring-ai-ascend
could absorb as a HookPoint listener.

## 6. Evolution substrate

AgentScope advertises **"built-in support for finetuning"** as a
headline feature (`README.md:60-64`), but a repository-wide grep for
`finetun|Finetune|FineTune` under `src/agentscope/` returns **zero
matches**. The finetuning capability appears to live in
`docs/NEWS.md`, paper references, and roadmap items — not in shipped
Python code. There is no trajectory store, no replay buffer, no
GRPO/DPO/SFT pipeline visible in-tree.

What does exist around evolution:

- **Skills** (`src/agentscope/skill/_base.py:1-30`): a `Skill`
  dataclass with `name`, `description`, `dir`, `markdown`,
  `updated_at`. The `_local_loader.py` walks a filesystem directory
  loading per-skill `SKILL.md` files. This is the same convention
  Spring AI Alibaba uses, and it works for **prompt injection** —
  not for capacity-arbitrated skill resolution as in spring-ai-
  ascend's Rule R-K skill capacity matrix.
- **Memory**: chat history only, through `agentscope.message.Msg`
  and the agent's internal context. No long-term memory store, no
  graph-memory, no episodic / semantic split.
- **Tracing**: OpenTelemetry instrumentation under `src/agentscope/
  middleware/_tracing/` — `_attributes.py`, `_converter.py`,
  `_extractor.py`, `_setup.py`, `_trace.py`. Spans are emitted but
  there is no separate "evolution plane" consuming them.
- **Workspace** (`src/agentscope/workspace/`): three sandbox
  implementations — local Python (`_local_workspace.py`), Docker
  (`_docker/` with five Dockerfile templates), E2B
  (`_e2b/`). Workspaces are tool execution environments, not
  evolution surfaces.

There is no equivalent to spring-ai-ascend's `agent-evolve` module
(`deployment_plane: evolution`), no `EvolutionExport` scope
discriminator on emitted events, and no Python ML hand-off path.
AgentScope's "finetuning" remains a marketing claim, not a shipped
substrate at this commit.

## 7. Deployment model + sovereign-hardware support

The README claims three deployment shapes: **local**, **serverless**,
**K8s** (`README.md:70`). What ships in-tree:

- **Local**: `uv pip install agentscope` then run Python
  (`README.md:113-118`). No `application.yml`, no posture knobs.
- **Agent Service backend**: `python main.py` from
  `examples/agent_service/` (`README.md:194-201`). FastAPI +
  uvicorn (`pyproject.toml:62-65`).
- **Workspace Docker images**: five `Dockerfile.*.template` files
  under `src/agentscope/workspace/_docker/` — for **tool execution
  sandboxes**, not for the agent server itself.
- **No Helm chart, no Kubernetes manifests** in the repo
  (verified by `find -name "Chart.yaml"` and `find -name
  "kustomization.yaml"` returning nothing). The K8s deployment claim
  in the README is a forward-looking statement, not a shipped
  artefact.

**No Chinese-silicon support.** A repository-wide grep for
`Ascend|Kunpeng|昇腾|鲲鹏` returns zero matches across the entire
`agentscope` source tree. The target runtime is generic CPython 3.11+
(`pyproject.toml:21`); model serving is delegated to external
providers (DashScope, OpenAI, Anthropic, Gemini, Ollama). There is no
NPU model-serving adapter, no ARM64-specific path, no
`deployment_plane` discriminator on modules.

By comparison, spring-ai-ascend's positioning is explicitly **Ascend
NPU + Kunpeng ARM64** sovereignty per the root `README.md:3-10`, with
a five-plane deployment topology declared per-module in
`module-metadata.yaml` (Rule R-I). AgentScope is comfortable with
the assumption that model serving lives in the cloud — its DashScope
defaults bake that assumption into the quickstart.

## 8. License + corporate sponsor

License: **Apache 2.0** (`LICENSE`, `pyproject.toml:10`). No
copyleft, no field-of-use restrictions, fully permissive. The PyPI
classifier "License :: OSI Approved :: Apache Software License" is
implicit (the SPDX identifier in pyproject is canonical).

Corporate sponsor: **SysML team of Alibaba Tongyi Lab** — declared in
`pyproject.toml:7-9` with the corresponding `gaodawei.gdw@alibaba-
inc.com` author email. The 2025 paper authorship is dominated by Ant
Group / Alibaba Cloud researchers (Dawei Gao, Zitao Li, Yuexiang Xie,
Bolin Ding, Jingren Zhou, listed in `README.md:236-237`). The PyPI
package name is `agentscope` under `agentscope-ai` GitHub org. Latest
commit on `main`: `fca8f160c6cc25bc27ddf497781465e48cf1a613` dated
**2026-05-26** ("fix(readmd): update the ding talk qr code (#1662)").
Like Spring AI Alibaba, AgentScope ships from the Alibaba research
gravity well — its default credential type is `DashScopeCredential`,
the DingTalk QR code is in the README community section, and the
docs domain is `docs.agentscope.io`.

For spring-ai-ascend, the implication is the same as for SAA:
AgentScope is a competitor surface from a parent ecosystem (Alibaba)
that conflicts directly with our Ascend/Kunpeng positioning. Embedding
its Python package would tie us to its release cadence and to
DashScope as the default model surface.

## 9. What we LEARN

Patterns worth absorbing into spring-ai-ascend, with concrete file
paths in AgentScope:

1. **Rich event taxonomy as the user-facing contract** —
   `src/agentscope/event/_event.py` declares ~24 distinct
   `AgentEvent` subtypes (`ReplyStart/End`, `ModelCallStart/End`,
   `TextBlockStart/Delta/End`, `ThinkingBlockStart/Delta/End`,
   `ToolCallStart/Delta/End`, `ToolResult{Start,DataDelta,TextDelta,
   End}`, `RequireUserConfirm`, `RequireExternalExecution`,
   `ExternalExecutionResult`, `UserConfirmResult`, `DataBlockStart/
   Delta/End`, `ExceedMaxIters`). Each event is a typed object,
   consumable via `match evt.type:`. Our `RunEvent` schema (Rule
   R-M.e + `docs/contracts/run-event.v1.yaml`) could borrow this
   granularity — particularly the
   `RequireUserConfirm / RequireExternalExecution` events as
   first-class S2C callback signals (Rule R-M.d).

2. **Per-tool permission engine with developer-facing ALLOW/DENY/ASK
   semantics** — `src/agentscope/permission/{_engine,_rule,_decision,
   _context,_types}.py` implements a permission ladder that's a
   strong UX pattern for the **Sandbox Policy** layer (Rule R-L).
   spring-ai-ascend's sandbox policy is yaml-only today; absorbing
   the AgentScope `PermissionMode.{BYPASS, EXPLORE, ACCEPT_EDITS}`
   triad as a posture-aware default gives our `dev`/`research`/`prod`
   modes a richer surface.

3. **Tool-offload middleware** — `src/agentscope/app/_middleware/
   _tool_offload_middleware.py` is a clean pattern: large tool
   results are persisted out-of-context, with a small reference token
   replacing them in the prompt. This belongs in our middleware
   layer as a `HookPoint.afterToolResult` listener.

4. **Three workspace sandbox adapters behind one base** —
   `src/agentscope/workspace/_base.py` declares an `Offloader`
   abstraction implemented by `_local_workspace.py`,
   `_docker_workspace.py`, and `_e2b/`. Our sandbox layer ships
   one (host) adapter today; the three-adapter shape is the right
   structural target for posture-graded sandbox choice
   (dev=local, research=docker, prod=e2b-style sealed).

5. **Filesystem-based skill loader convention** — same as Spring AI
   Alibaba — `src/agentscope/skill/_local_loader.py` walks a
   directory tree loading `SKILL.md` per directory. We should mirror
   this convention; it gives skills a markdown-native author surface.

6. **Storage abstraction keyed by an identity field** — even though
   AgentScope's identity is `user_id` (not `tenant_id`), the
   abstract base class shape at `src/agentscope/app/storage/
   _base.py:21-80` (every method takes the identity field as the
   first positional argument) is the right structural pattern for
   defence-in-depth tenant scoping in our middleware module.

7. **Middleware filter-by-implemented-hook pattern** —
   `src/agentscope/agent/_agent.py:167-181` partitions a flat
   `middlewares: list[MiddlewareBase]` into per-hook sub-lists at
   construction time, using `is_implemented("on_xxx")`. This avoids
   per-call dispatch checks. Our `RuntimeMiddleware` resolver should
   adopt the same partition-at-construction pattern for hot-path
   performance.

## 10. Where we DIFFER

Each row cites one evidence file in **each** repo.

| # | Dimension | AgentScope evidence | spring-ai-ascend evidence |
|---|-----------|---------------------|---------------------------|
| 1 | **Tenant isolation depth** — AgentScope: user-keyed storage, no tenant column anywhere. Ascend: tenant_id mandatory on Run + RLS + edge re-auth. | `src/agentscope/app/storage/_base.py:21-80` (every method keyed by `user_id: str`) | `D:\chao_workspace\spring-ai-ascend\docs\governance\rules\rule-R-J.md` + `agent-service/.../service/runtime/runs/Run.java` |
| 2 | **Engine Registry vs monolithic Agent** — AgentScope: one 2390-line `Agent` class subsumes the entire ReAct loop. Ascend: `EngineRegistry.resolve(envelope)` admits any engine matching the envelope. | `src/agentscope/agent/_agent.py:94-183` (single Agent class with 5 inline middleware filters) | `D:\chao_workspace\spring-ai-ascend\docs\contracts\engine-envelope.v1.yaml` + Rule R-M.a |
| 3 | **Evolution substrate** — AgentScope: "finetuning" is marketing copy with zero in-tree code. Ascend: dedicated `agent-evolve` module on `evolution` deployment plane. | `Grep -r "finetune\|FineTune" src/agentscope/` returns zero matches | `D:\chao_workspace\spring-ai-ascend\agent-evolve\module-metadata.yaml` |
| 4 | **Sovereign hardware** — AgentScope: generic CPython 3.11, DashScope-cloud default, x86 Docker for sandboxes. Ascend: Ascend NPU + Kunpeng ARM64 as design target. | `Grep -r "Ascend\|Kunpeng" src/agentscope/` returns zero matches; default model `qwen3.6-plus` via DashScope (`README.md:158`) | `D:\chao_workspace\spring-ai-ascend\README.md:3-10` + Rule R-I five-plane manifest |
| 5 | **Posture-aware defaults** — AgentScope: env-var driven, no posture concept. Ascend: every config knob declares `dev`/`research`/`prod` defaults, fail-closed startup. | `os.environ["DASHSCOPE_API_KEY"]` is the only config path in the quickstart (`README.md:155`) | `D:\chao_workspace\spring-ai-ascend\docs\governance\rules\rule-D-6.md` (PostureBootGuard) |
| 6 | **Audit-grade Run spine** — AgentScope: session storage keyed by `(user_id, agent_id, workspace_id)`, no state machine validation, no idempotency. Ascend: Run aggregate + `RunStateMachine.validate(from, to)` + durable idempotency. | `src/agentscope/app/_router/_session.py:67-100` (no state machine call site) | Rule R-C.2.b + `agent-service/.../service/runtime/runs/RunStateMachine.java` |
| 7 | **Bus channel isolation** — AgentScope: in-process AsyncGenerator stream is the only channel. Ascend: control/data/rhythm channels physically isolated per Rule R-E. | `src/agentscope/agent/_agent.py:188-211` (single `async for` event stream) | Rule R-E + `D:\chao_workspace\spring-ai-ascend\docs\governance\bus-channels.yaml` |
| 8 | **Permission semantics target** — AgentScope: developer-machine ALLOW/DENY/ASK over Bash/Write tools. Ascend: tenant-/skill-scoped capacity arbitration via Rule R-K. | `src/agentscope/permission/_engine.py:16-80` (PermissionMode + per-tool patterns) | `D:\chao_workspace\spring-ai-ascend\docs\governance\skill-capacity.yaml` |
| 9 | **License + supplier posture** — AgentScope: Tongyi Lab (Alibaba) PyPI artifact `agentscope`; embedding it ties us to a sibling cloud provider. Ascend: zero AgentScope dependency. | `pyproject.toml:7-9` (author email `@alibaba-inc.com`) | `feedback_saa_competitor.md` (project memory: Alibaba-Cloud-aligned artifacts are competitor surfaces) |
| 10 | **Governance / Code-as-Contract** — AgentScope: ruff + pre-commit only; no ADRs, no architectural enforcers, no governance ledger. Ascend: 144+ gate rules, ArchUnit tests, governance YAML kernel. | `pyproject.toml:84-97` (pytest + pre-commit are the only enforcement tools) | `D:\chao_workspace\spring-ai-ascend\CLAUDE.md` + `gate/check_architecture_sync.sh` |

These ten dimensions cleanly separate the two projects: AgentScope
is a **Python single-tenant research-friendly ReAct framework** with
strong event-streaming and permission UX; spring-ai-ascend is a
**Java enterprise multi-tenant audit-grade dual-mode runtime** with
hardware sovereignty. The architectural intent overlap is narrow
enough that AgentScope's existence does not pressure spring-ai-
ascend's positioning, but its event taxonomy and permission ladder
are real DX wins worth absorbing into our middleware and HookPoint
contracts.
