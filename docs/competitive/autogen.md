---
analysis_id: COMPETITIVE-AUTOGEN
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 2
repo_clone_at: D:\ai-research\agent-platforms-survey\autogen\
---

# Competitive Analysis: microsoft/autogen

Source-grounded analysis at commit `027ecf0` (2026-04-06, tip of `main`,
"Update maintenance mode banner in readme"). The repository carries an
explicit maintenance-mode banner at `README.md:20-26`:

> "AutoGen is now in maintenance mode. It will not receive new features
> or enhancements and is community managed going forward. New users
> should start with Microsoft Agent Framework. Existing users are
> encouraged to migrate using the AutoGen → Microsoft Agent Framework
> migration guide."

Despite the maintenance posture, the codebase remains a well-formed
reference for the **conversation-as-message-bus** style of multi-agent
orchestration that influenced an entire generation of frameworks (and
Microsoft's successor MAF). The python kernel version is `0.7.5`
declared at `python/packages/autogen-agentchat/pyproject.toml:7`.

## 1. Tagline & positioning

The README opens (`README.md:16`):

> "**AutoGen** is a framework for creating multi-agent AI applications
> that can act autonomously or work alongside humans."

The positioning is **multi-agent conversation as the primary abstraction**:
agents communicate by sending messages to one another (a programmable
group chat), with a *user proxy agent* representing the human in the
loop. The two flagship higher-level patterns are:

- **AgentChat** — declarative agent + team API (`autogen-agentchat`,
  v0.7.5);
- **AutoGen Core** — the message routing + serialization runtime
  (`autogen-core`, v0.7.5).

Plus **Magentic-One** (a generalist multi-agent system in `python/packages/autogen-magentic-one/`) and **AutoGen Studio** — a no-code GUI
(`python/packages/autogen-studio/`, separate package). License is **MIT
for code** (`LICENSE-CODE:1`, `Copyright (c) Microsoft Corporation`)
with the `LICENSE` file itself being Creative Commons Attribution 4.0
for documentation/samples. Corporate sponsor: **Microsoft Corporation**
— originally a Microsoft Research project, now community-managed.
There is no notion of multi-tenant isolation, posture-aware fail-closed
defaults, or hardware sovereignty in the kernel; the framework
prioritises agent-pattern expressivity.

## 2. Architecture skeleton

The Python workspace declares ten packages under `python/packages/`
(`README.md:35`, verified via `ls`):

```
agbench                  # multi-agent benchmark harness
autogen-agentchat        # high-level Agent + Team declarative API
autogen-core             # message-passing runtime + Agent/AgentId/Topic primitives
autogen-ext              # extensions: model clients, code executors, tools
autogen-magentic-one     # generalist multi-agent system
autogen-studio           # no-code GUI
autogen-test-utils       # test fixtures
component-schema-gen     # schema codegen for Component config
magentic-one-cli         # CLI wrapper for Magentic-One
pyautogen                # proxy package for back-compat
```

Plus a `dotnet/` sibling reactor (`AutoGen.sln`) for the .NET port —
out of scope for this analysis since the active code path is Python.

The **central abstraction is `AgentRuntime`** declared at
`python/packages/autogen-core/src/autogen_core/_agent_runtime.py:18`:
`class AgentRuntime(Protocol)` with three core methods —
`send_message(message, recipient, ...)` (line 20),
`publish_message(message, topic_id, ...)` (line 51),
`register_factory(type, agent_factory, ...)` (line 78). The default
implementation is `SingleThreadedAgentRuntime`
(`python/packages/autogen-core/src/autogen_core/_single_threaded_agent_runtime.py`) — a single-process asyncio event loop with a message queue.

Agents are addressed by `AgentId` (type + key), can subscribe to
`TopicId` patterns (`_topic.py`, `_subscription.py`), and receive
strongly-typed `MessageContext`s (`_message_context.py`). Telemetry
wires through OpenTelemetry (`_telemetry/`) — the runtime is
trace-instrumented out of the box. The `_intervention.py` file
(line 1-40) declares `InterventionHandler` Protocol with a `DropMessage`
sentinel — the runtime supports message-level interception/modification.

The AgentChat layer above adds: `_assistant_agent.py` (LLM-driven
agent with tool use), `_user_proxy_agent.py` (human-in-the-loop
proxy), `_code_executor_agent.py`, `_society_of_mind_agent.py`,
`_message_filter_agent.py`. Teams live under `teams/_group_chat/`:
`_round_robin_group_chat.py`, `_selector_group_chat.py`,
`_swarm_group_chat.py`, `_magentic_one/`, plus a base
`_base_group_chat.py` + `_base_group_chat_manager.py`.

## 3. Developer experience

The README quickstart at lines 36-47 is:

```bash
pip install -U "autogen-agentchat" "autogen-ext[openai]"
```

then in Python:

```python
from autogen_agentchat.agents import AssistantAgent
from autogen_ext.models.openai import OpenAIChatCompletionClient

agent = AssistantAgent("assistant", model_client=OpenAIChatCompletionClient(model="gpt-4o"))
result = await agent.run(task="Say 'Hello World!'")
```

The pattern is **async-first by default** — `run()` is a coroutine.
For multi-agent, the developer composes a team:

```python
team = RoundRobinGroupChat([writer, reviewer])
await team.run(task="Write a short poem.")
```

API key ingestion is env-var driven (`OPENAI_API_KEY`). No project
scaffold, no YAML config, no posture split. The `Component[Config]`
generic protocol (used by `AssistantAgent` at
`_assistant_agent.py:101: class AssistantAgent(BaseChatAgent, Component[AssistantAgentConfig])`) lets every primitive declare a Pydantic
config schema and round-trip to JSON — meaning every agent / tool /
team configuration can be serialised and rehydrated, which is the
backbone of the AutoGen Studio no-code GUI. This is a stronger
declarative-config story than LangChain or CrewAI's Python-builder
pattern.

The human-in-the-loop pattern is the `UserProxyAgent`
(`_user_proxy_agent.py:37`). The `agents/_user_proxy_agent.py:44`
docstring states: "Using UserProxyAgent puts a running team in a
temporary blocked state, and that the underlying agent is suspended."
The framework collects user input via a callback supplied at agent
construction — analogous to spring-ai-ascend's S2C callback envelope,
but the suspension is in-process Python `await`, not a durable
checkpoint.

## 4. Multi-tenancy & governance

**There is no tenant model in the kernel.** A repo-wide grep for
`tenant` across `python/packages/autogen-core/src/` and
`python/packages/autogen-agentchat/src/` returns zero matches. The
agent identity model is `AgentId(type: str, key: str)` — no tenant
column, no workspace_id, no Row-Level Security. The intended pattern
is that the calling application namespaces the agent `key` to include
tenant context.

Governance surfaces are absent: no posture (dev/research/prod) split,
no fail-closed boot guard, no audit MDC, no idempotency spine. The
`Component[Config]` declarative configuration is the closest thing to
a contract surface — every agent serialises its config + version
+ provider, enabling configuration-as-code. But this is *schema
declaration*, not *invariant enforcement*. The `InterventionHandler`
Protocol at `_intervention.py:23` lets a developer interpose on
messages (drop, modify, log) — useful for ad-hoc policy but not a
governed enforcement layer.

The maintenance-mode banner makes this dimension moot for production
use: new tenancy-bearing applications are explicitly directed to
Microsoft Agent Framework (MAF), not AutoGen. By contrast,
spring-ai-ascend enforces tenant isolation in the kernel (Rule R-J,
Rule R-C.2.a) and treats governance as a first-class concern — the
inverse of AutoGen's "expressivity first, governance later" posture.

## 5. Engine pluggability

The framework's engine is the **`AgentRuntime` Protocol**
(`_agent_runtime.py:18`). The default `SingleThreadedAgentRuntime` is
one implementation; the codebase reserves the namespace for a future
distributed runtime (the gRPC-based runtime that powers the .NET port
and was a stated direction before maintenance mode). The protocol
defines the *message bus contract* — send/publish/register — and the
agent factory pattern. Adding a new runtime means implementing the
protocol; agents written against the protocol remain portable.

Cross-cutting policy mechanisms:

1. **Subscriptions** (`_subscription.py`, `_default_subscription.py`)
   — typed agent-subscribes-to-topic pattern with topic prefix matching.
2. **Topics** (`_topic.py`, `_default_topic.py`) — message routing
   primitive separate from direct send.
3. **Intervention handlers** (`_intervention.py`) — message-level
   modify/drop/log hook with `DropMessage` sentinel.
4. **Component[Config]** generic (`_component_config.py`) —
   declarative serialisation contract that every primitive implements.
5. **MessageContext** (`_message_context.py`) — per-message context
   passed to handlers, includes sender, topic, cancellation token,
   trace metadata.

The conversation/group-chat pattern is built on top: a `GroupChat`
is a coordinating agent that orchestrates message flow between
participant agents. Different policies (round-robin, selector,
swarm, Magentic-One) are different group-chat-manager implementations
(`_base_group_chat_manager.py` plus four subclasses in
`teams/_group_chat/`).

There is no `engine_type` envelope, no `EngineRegistry.resolve(...)`,
no typed dispatch failure path. Engine pluggability is achieved by
implementing the runtime protocol — clean conceptually, but the
absence of an envelope means an integrator cannot route a single Run
to one of several engines based on declared metadata. spring-ai-ascend's
Engine Contract is a different shape: envelope + registry + typed
mismatch, vs. AutoGen's protocol + subscription + topic.

## 6. Evolution substrate

There is **no evolution plane and no trajectory store** in the AutoGen
kernel. The closest surface is:

- **Memory** — declared in `autogen-core/src/autogen_core/memory/`
  with a `Memory` Protocol and a few simple stores (per
  `from autogen_core.memory import Memory` import at
  `_assistant_agent.py:25`). The `AssistantAgent` accepts a
  `memory: List[Memory]` config option for per-agent message storage.
  This is conversation-buffer memory, not cross-task learning.
- **Model context** — `ChatCompletionContext` + `UnboundedChatCompletionContext` (`autogen_core/model_context/`) manage the conversation
  history that gets included in each LLM call.

There is no per-skill capacity matrix, no rate-limit declaration, no
sandbox subsumption, no fine-tune export. The `autogen-ext/code_executors/` package ships *executable code-execution sandboxes*
(Jupyter, local-Docker, etc.) — but these are concrete tool
implementations, not a generalised sandbox-permission contract.

The `autogen-magentic-one/` package experimentally explores
generalist agents with learned dispatcher policies, but this lives in
a separate package and is not a substrate the rest of the framework
sits on. AutoGen's research lineage (Microsoft Research) means the
codebase is heavy on **agent-pattern expressivity** and lighter on
**production-grade learning infrastructure**. The maintenance-mode
announcement explicitly defers evolution-grade work to MAF.

## 7. Deployment model

The OSS runtime is **library-only**. The repo ships AutoGen Studio
as a separate package (`python/packages/autogen-studio/`) — a
FastAPI + React no-code GUI that wraps the framework — but no Helm
chart, no Kubernetes manifests, no production deployment harness.
`find -name "Chart.yaml"` returns nothing.

The `.dotnet/` sibling reactor (`AutoGen.sln`) suggests a planned
distributed runtime on top of .NET / gRPC, but the Python side
remains single-process by default. The `protos/` directory at the
repo root declares gRPC service definitions for a future distributed
runtime (out of scope for this analysis).

**No Chinese-silicon support.** Repo-wide grep for `Ascend`/`Kunpeng`
returns zero hits. The framework runs on generic Python
3.10+ (`autogen-agentchat/pyproject.toml:11`); model clients ship in
`autogen-ext` (OpenAI, Azure OpenAI, Anthropic, Gemini, local Ollama,
…) with no Chinese-NPU provider in the bundled set.

The intended deployment story for production was **gRPC distributed
runtime + Azure Container Apps**, per Microsoft's blog post history.
With maintenance mode active, new deployments are directed to MAF,
which inherits the architecture lineage but is shipped as a fresh
codebase. For spring-ai-ascend, AutoGen represents the **conversation-as-bus
prior art** worth learning from architecturally, not a deployment
target to interoperate with.

## 8. License + corporate sponsor

License: **MIT for code** (`LICENSE-CODE:1-3`, `Copyright (c)
Microsoft Corporation`), Creative Commons Attribution 4.0 for
non-code (`LICENSE:1`). Every Python package declares `license =
{file = "LICENSE-CODE"}` (e.g. `autogen-agentchat/pyproject.toml:7`,
`pyautogen/pyproject.toml:7`).

Corporate sponsor: **Microsoft Corporation** — originally Microsoft
Research, now community-managed under the maintenance banner at
`README.md:20-26`. The successor product is **Microsoft Agent
Framework (MAF)** at `https://github.com/microsoft/agent-framework`.
Latest commit `027ecf0a379bcc1d09956d46d12d44a3ad9cee14` dated
**2026-04-06** ("Update maintenance mode banner in readme") — the
framework is functionally frozen.

The strategic implication for spring-ai-ascend: AutoGen as a
*dependency* is unsafe (no new features, security patches only),
AutoGen as a *learning corpus* remains valuable. The conversation-bus
+ user-proxy + intervention-handler patterns directly inspired both
MAF and a generation of multi-agent frameworks. spring-ai-ascend's
bus channel isolation (Rule R-E) and S2C callback envelope take a
more architecturally-disciplined cut at the same problem space —
typed channels + typed envelopes vs. Python protocols + topic strings.

## 9. What we LEARN

Patterns worth absorbing into spring-ai-ascend:

1. **`Component[Config]` declarative serialisation contract**
   (`autogen-core/src/autogen_core/_component_config.py`) — every
   agent, tool, model client, team carries a Pydantic config schema
   that round-trips to JSON. This is the backbone of AutoGen Studio
   no-code config. spring-ai-ascend's `@ConfigurationProperties` +
   `@Valid` is the Spring analogue; the AutoGen pattern of
   *every primitive declares a config schema* is worth applying
   uniformly across our SPI surface.

2. **`UserProxyAgent` as the human-in-the-loop primitive**
   (`autogen-agentchat/src/autogen_agentchat/agents/_user_proxy_agent.py:37`) — a first-class agent type whose role is to represent
   the human. Cleaner than scattering "if human-in-the-loop"
   conditionals through agent code. spring-ai-ascend's S2C callback
   envelope is the kernel primitive; the *UserProxyAgent* pattern is
   the developer-facing abstraction worth shipping on top of it.

3. **`InterventionHandler` Protocol with `DropMessage` sentinel**
   (`autogen-core/src/autogen_core/_intervention.py:14-29`) — a typed
   message-level interceptor where returning the `DropMessage`
   sentinel cleanly distinguishes "drop" from "no change" (the latter
   triggers a warning). The discipline of "use a sentinel type, not
   `None`, to disambiguate intent" is a clean ergonomic lesson for
   our `RuntimeMiddleware` return semantics.

4. **`AgentRuntime` Protocol as the runtime contract**
   (`_agent_runtime.py:18`) — three methods (`send_message`,
   `publish_message`, `register_factory`) define a portable surface
   that the single-threaded runtime and a future gRPC runtime both
   implement. The narrow surface is a model for our `EngineRegistry`
   contract — keep the kernel narrow, push complexity into adapters.

5. **Subscription + Topic pattern** (`_topic.py`, `_subscription.py`)
   — pub/sub messaging with typed prefix matching. spring-ai-ascend's
   bus channel discipline (Rule R-E) is physical-channel-isolated;
   the AutoGen subscription model is the logical layer above. Worth
   importing the *typed prefix matching* ergonomics into our
   `agent-bus` topic vocabulary.

6. **Per-message `MessageContext`** (`_message_context.py`) — every
   handler receives sender, topic, cancellation token, trace metadata
   in one struct. Cleaner than passing five positional arguments or
   inspecting thread-local state. spring-ai-ascend's `RunContext`
   carries equivalent info; the *single typed context object per
   handler invocation* discipline is reinforced.

7. **`Magentic-One`-style supervisor pattern** (per the
   `autogen-magentic-one/` package + `teams/_group_chat/_magentic_one/`)
   — a stateful orchestrator agent that plans, dispatches, and
   monitors a team. Worth absorbing as a higher-level adapter on top
   of the Engine Contract envelope.

## 10. Where we DIFFER

| # | Dimension | AutoGen evidence | spring-ai-ascend evidence |
|---|-----------|------------------|---------------------------|
| 1 | **Multi-tenancy depth** — AutoGen: `AgentId(type, key)` is the only scoping primitive; no tenant column. Ascend: tenant_id NOT NULL on Run, RLS at Postgres, re-validation at HTTP edge. | `_agent_id.py` (no tenant field); zero `tenant` hits in `autogen-core/src/` | Rule R-C.2.a + `agent-service/.../runtime/runs/Run.java` |
| 2 | **Engine Contract envelope vs runtime protocol** — AutoGen: `AgentRuntime` Protocol with send/publish/register; no envelope dispatch. Ascend: typed `EngineEnvelope` + `EngineRegistry.resolve()` with `EngineMatchingException`. | `_agent_runtime.py:18` (protocol-based dispatch) | `docs/contracts/engine-envelope.v1.yaml` + Rule R-M.a/.b |
| 3 | **Spring-native ergonomics** — AutoGen is Python-only (the .NET sibling at `dotnet/` is a separate code path). Ascend ships Spring Boot starters. | `autogen-agentchat/pyproject.toml:11` (Python 3.10+) | `D:\chao_workspace\spring-ai-ascend\pom.xml` |
| 4 | **中台+能力复用 dual deployment** — AutoGen: single-process Python runtime + AutoGen Studio GUI; gRPC distributed runtime planned but not shipped. Ascend: five-plane physical topology with `deployment_plane` per module. | `_single_threaded_agent_runtime.py` (only shipping runtime) + no Helm chart | Rule R-I + `module-metadata.yaml#deployment_plane` |
| 5 | **Project status posture** — AutoGen: explicit maintenance mode, no new features. Ascend: active multi-wave development with governance rules + ADR catalog. | `README.md:20-26` (maintenance banner) | `CLAUDE.md` (live rule kernel) + recent commits |
| 6 | **Evolution substrate** — AutoGen: per-agent `memory: List[Memory]` is conversation buffer; no trajectory or evolution plane. Ascend: dedicated `agent-evolve` module on `evolution` plane. | `autogen-core/src/autogen_core/memory/` (buffer only) | `D:\chao_workspace\spring-ai-ascend\agent-evolve\module-metadata.yaml` |
| 7 | **Ascend+Kunpeng sovereignty** — AutoGen: generic Python, no NPU adapter. Ascend: ARM64+NPU design target. | `autogen-agentchat/pyproject.toml:11` (Python only) | `D:\chao_workspace\spring-ai-ascend\README.md:3-10` |
| 8 | **Posture-aware fail-closed defaults** — AutoGen: env-var driven `OPENAI_API_KEY`, no posture split. Ascend: dev/research/prod defaults declared per knob with PostureBootGuard. | env-var pattern at `README.md:42` | Rule D-6 + `docs/governance/rules/rule-D-6.md` |
| 9 | **License + sponsor posture** — AutoGen: MIT-code + CC-BY-4.0-docs, Microsoft sponsor entering maintenance. Ascend: Apache 2.0, Huawei sponsor, active development. | `LICENSE-CODE` + `README.md:20-26` (maintenance) | `D:\chao_workspace\spring-ai-ascend\LICENSE` |
| 10 | **Governance / Code-as-Contract** — AutoGen: type-checked Python, OpenTelemetry tracing, pre-commit; no architectural enforcers, no recurring-defect ledger. Ascend: 144+ gate rules + ArchUnit + governance YAML kernel. | `python/pyproject.toml` (ruff + pyright) | `CLAUDE.md` (Rule kernel) + `gate/check_architecture_sync.sh` |

The architectural lineage AutoGen seeded — message-bus + user-proxy +
declarative-config — is alive in Microsoft Agent Framework and in
every multi-agent framework that came after. spring-ai-ascend's bus
discipline (Rule R-E three-track physical channel isolation) and
typed S2C callback envelope are the disciplined-runtime answers to
the same questions. Treating AutoGen as a learning corpus rather than
an integration target is the right posture given the maintenance banner.
