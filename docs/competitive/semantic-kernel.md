---
analysis_id: COMPETITIVE-SEMANTIC-KERNEL
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 1
repo_clone_at: D:\ai-research\agent-platforms-survey\semantic-kernel\
---

# Competitive Analysis: microsoft/semantic-kernel

Source-grounded analysis at commit `32e904c` (2026-05-27, tip of `main`), .NET
package version `1.77.0` declared in the release-tagging commit
(`README.md:12` Nuget badge resolves to `Microsoft.SemanticKernel`).
Conducted per the C.2 ten-point template. Semantic Kernel (SK) is a Microsoft
Corporation open-source SDK (`LICENSE:1-3` MIT) implemented in three
language tracks: **Python** (`python/semantic_kernel/`), **.NET**
(`dotnet/src/`), and **Java** (now relocated to a separate repository).

**Important repo-shape note:** the Java SDK has been moved to a separate
Microsoft repo (`java/README.md:3-5` redirects to
`microsoft/semantic-kernel-java`). This analysis treats the **.NET
implementation as the canonical reference** (most mature track, and the one
the README MAF-succession banner at `README.md:3-7` tracks), with
cross-references to Python where relevant. The Java SDK is called out in
section 7 as a deployment-track gap.

## 1. Tagline & positioning

The README banner (`README.md:8`) is verbatim:

> "Build intelligent AI agents and multi-agent systems with this
> enterprise-ready orchestration framework"

The introduction (`README.md:15-17`):

> "Semantic Kernel is a model-agnostic SDK that empowers developers to build,
> orchestrate, and deploy AI agents and multi-agent systems. Whether you're
> building a simple chatbot or a complex multi-agent workflow, Semantic Kernel
> provides the tools you need with enterprise-grade reliability and
> flexibility."

A prominent IMPORTANT banner at the top (`README.md:3-7`) announces that SK is
"now Microsoft Agent Framework (MAF)" — the v1.0 enterprise-ready release that
supersedes SK; existing SK users are pointed at a migration guide. This is a
**legacy product in maintenance mode** while the company shifts mindshare to
Microsoft Agent Framework — a posture similar to Spring AI Alibaba's
relationship to Alibaba Cloud Bailian Admin (the SDK is the open layer below
the productised platform). Key features advertised (`README.md:26-36`): model
flexibility (OpenAI, Azure OpenAI, Hugging Face, NVIDIA NIM), Agent Framework,
Multi-Agent Systems, Plugin Ecosystem (native functions, prompt templates,
OpenAPI, MCP), 11 vector store integrations, multimodal (text/vision/audio),
local deployment (Ollama, LMStudio, ONNX), Process Framework, and "Enterprise
Ready: Built for observability, security, and stable APIs". The framing is
**enterprise SDK first**, **Azure-aligned but model-agnostic**, with
multi-agent orchestration as the headline differentiator. The Maven coordinate
analogue is **`Microsoft.SemanticKernel`** (NuGet) — Azure-cloud sponsorship is
the deepest gravity well in the integrations.

## 2. Architecture skeleton

The .NET solution declares 13 top-level source directories under
`dotnet/src/` (verified by `ls`):

```
SemanticKernel.Abstractions   # Kernel, AI services, filters, memory, plugin contracts
SemanticKernel.Core           # default Kernel + plugin invocation runtime
SemanticKernel.MetaPackage    # umbrella NuGet package `Microsoft.SemanticKernel`
VectorData                    # 12 vector store backends + Abstractions
Connectors                    # 9 model provider connectors
Functions                     # OpenAPI / Grpc / Prompty / Yaml / Markdown function loaders
Plugins                       # Plugins.Core / Memory / Document / MsGraph / Web / AI / StructuredData
Agents                        # Abstractions, Core, AzureAI, OpenAI, Bedrock, A2A, Copilot, Magentic, Orchestration, Runtime, Yaml
Experimental                  # Process.* (workflow framework), Orchestration.Flow, Agents
InternalUtilities             # shared helpers
```

The `Agents/` subtree itself declares the orchestration architecture
(`dotnet/src/Agents/`):

```
Abstractions/    # Agent.cs, AgentChat.cs, AgentChannel.cs, AgentThread.cs
Core/            # default in-process agent execution
AzureAI/         # AzureAIAgent (Azure AI Foundry agents)
OpenAI/          # OpenAIAssistantAgent + OpenAIResponseAgent (OpenAI Assistants v2)
Bedrock/         # BedrockAgent (AWS Bedrock Agents)
A2A/             # A2AAgent + A2AHostAgent (Agent-to-Agent protocol)
Copilot/         # CopilotStudioAgent (Microsoft Copilot Studio)
Magentic/        # Magentic-One multi-agent orchestrator
Orchestration/   # Sequential, Concurrent, GroupChat, Handoff orchestrations
Runtime/         # Abstractions + Core + InProcess (actor-style runtime)
Yaml/            # YAML-defined agent definitions
```

The `Kernel` class (`dotnet/src/SemanticKernel.Abstractions/Kernel.cs:26-644`)
is the central type — an ambient context carrying plugins, function-invocation
filters, prompt-render filters, auto-function-invocation filters, and a service
provider. The `Agent` abstract class
(`dotnet/src/Agents/Abstractions/Agent.cs:24-433`) declares `Id`, `Name`,
`Description`, `Instructions`, `Kernel`, `Template` (prompt), `Arguments`, plus
streaming and non-streaming `InvokeAsync` methods.

The `IAgentRuntime` interface
(`dotnet/src/Agents/Runtime/Abstractions/IAgentRuntime.cs:15-123`) declares an
**actor-model runtime** with `SendMessageAsync(message, recipient, sender)`,
`PublishMessageAsync(message, topic)`, `GetAgentAsync(agentId/agentType)`,
`SaveAgentStateAsync`, `LoadAgentStateAsync`, `GetAgentMetadataAsync`, and
subscription management — closely modelled on Microsoft AutoGen Core. The
in-process implementation is `InProcessRuntime`
(`dotnet/src/Agents/Runtime/InProcess/InProcessRuntime.cs:18-…`) with a
`ConcurrentQueue<MessageDelivery>` message-delivery loop.

**Counterpart mapping to spring-ai-ascend** (modules listed in
`D:\chao_workspace\spring-ai-ascend\pom.xml`):

| spring-ai-ascend                        | semantic-kernel counterpart                                  | Notes |
|-----------------------------------------|--------------------------------------------------------------|-------|
| `agent-service`                         | partial — `Agents/Abstractions` + `Agents/Runtime`           | Actor runtime, no tenant Run spine |
| `agent-bus`                             | `Agents/Runtime/InProcess` message queue                     | In-process only; Dapr variant in Process.Runtime.Dapr |
| `agent-execution-engine`                | `Agents/Orchestration` + `Agents/Magentic`                   | 4 orchestrations + Magentic-One |
| `agent-middleware`                      | `Abstractions/Filters` (IFunctionInvocationFilter etc.)      | Filter pipeline analogous to RuntimeMiddleware |
| `agent-client`                          | (none — SDK only)                                            | No HTTP edge boundary |
| `agent-evolve`                          | (none — no evolution plane)                                  | Not in scope |
| `spring-ai-ascend-graphmemory-starter`  | `VectorData/{12 backends}` + `Plugins.Memory`                | Many backends, no graph-memory primitive |

## 3. Developer experience

The .NET "first agent" example (`README.md:100-132`) is 24 lines: build a
`Kernel` with `AddAzureOpenAIChatCompletion(...)`, instantiate
`ChatCompletionAgent { Name, Instructions, Kernel }`, iterate
`agent.InvokeAsync("...")` as an
`IAsyncEnumerable<AgentResponseItem<ChatMessageContent>>`. The Python
equivalent (`README.md:75-92`) is 14 lines.

The abstraction level is **per-agent (record-with-init-properties)** — a
developer declares an `Agent` instance with init-only properties
(`Agent.cs:24-100`), bound to a `Kernel` built via fluent
`Kernel.CreateBuilder().AddXxx(...).Build()`. Plugin / Function attachment is
via attribute-based `[KernelFunction]` declarations on a plain C# class
(SK's "Plugin" concept) — the equivalent of LangChain4j's `@Tool` or Spring
AI's `@FunctionalSpec` annotations. Python developer experience is parallel
via `kernel_function` decorators (`README.md:147-160`); Java SDK DX cannot be
assessed in this clone.

Multi-agent orchestration is built fluently on top:
`new SequentialOrchestration(agentA, agentB)`,
`new ConcurrentOrchestration(...)`, `new GroupChatOrchestration(...)`,
`new HandoffOrchestration(...)`, and the most sophisticated form
`MagenticOrchestration` (`dotnet/src/Agents/Magentic/MagenticOrchestration.cs`
+ `MagenticManager.cs`) that runs Microsoft's Magentic-One open-ended team
manager.

There is no Spring-style application-properties posture (dev/research/prod) —
configuration is left to `IConfiguration` (Microsoft.Extensions.Configuration)
or environment variables. API-key ingestion is environment-variable based
(`AZURE_OPENAI_API_KEY`, `OPENAI_API_KEY` per `README.md:44-49`). Compared to
spring-ai-ascend's `docs/quickstart.md`, SK's onboarding is similar in length
to LangChain4j's but more opinionated about the Kernel-as-ambient-state shape.

## 4. Multi-tenancy & governance

**There is no runtime tenant model in the agent layer.** A repository-wide
`Grep` for `tenantId|tenant_id|TenantId` across `dotnet/src/Agents/` returns
three files (`CopilotStudioConnectionSettings.cs`,
`CopilotStudioConnectionSettingsTests.cs`, `CopilotStudioTokenHandler.cs`),
all of which use `TenantId` exclusively for **Microsoft Entra (Azure AD)
tenant authentication credentials** (verified by reading
`Copilot/CopilotStudioConnectionSettings.cs:27-44` — `TenantId` is a
constructor argument for OAuth token acquisition, not a runtime isolation
boundary). The `AgentThread` abstraction
(`dotnet/src/Agents/Abstractions/AgentThread.cs`) carries a thread/session
identifier — analogous to LangChain4j's `memoryId` or SAA's `thread_id` — not
a tenant.

The `IAgentRuntime` interface
(`dotnet/src/Agents/Runtime/Abstractions/IAgentRuntime.cs:15-123`) admits an
`AgentId` (the agent instance handle) and a `MessageContext` per delivery
(`Runtime/Abstractions/MessageContext.cs`), but neither carries a
tenant boundary. The default `InProcessRuntime` is **explicitly single-process,
in-memory** (`Runtime/InProcess/InProcessRuntime.cs:16-31`) — multi-tenant
isolation would have to be layered on at the hosting application level (e.g.,
one runtime per tenant in an ASP.NET Core host).

Governance surfaces are partial: SK ships **filter pipelines** for
function-invocation interception (`Abstractions/Filters/Function/
IFunctionInvocationFilter.cs:13-21` with `OnFunctionInvocationAsync(context,
next)` — middleware-pattern), prompt-rendering, and auto-function-invocation.
There is structured **OpenTelemetry observability**
(`Abstractions/AI/` integrations with `Microsoft.Extensions.AI` and the
`ActivitySource` declared at `Runtime/Core/BaseAgent.cs:23`). There is no
ADR ledger, no recurring-defect-families YAML, no architectural enforcer
suite, no posture-aware fail-closed config. The `[Experimental("SKEXP0130")]`
attribute (`Agent.cs:78`) is the closest construct to a posture marker — it
guards Pre-GA APIs at compile time.

By contrast, spring-ai-ascend enforces tenant isolation at the **storage
engine** (Rule R-J), at the **HTTP edge** (cancel re-auth), and at the **Run
record level** (Rule R-C.2.a). SK's filter pipeline is the closest analogue to
our `RuntimeMiddleware`, but it is scoped to a single Kernel function call,
not engine-agnostic, not tenant-aware, and not cross-process.

## 5. Engine pluggability

SK has **strong agent-host pluggability** but **no Engine Contract envelope**.
The `Agent` base class (`Abstractions/Agent.cs:24-433`) has concrete subclasses
for each agent host:

- `ChatCompletionAgent` (`dotnet/src/Agents/Core/`) — local agent running a
  chat completion loop over any registered `IChatCompletionService`.
- `OpenAIAssistantAgent` + `OpenAIResponseAgent`
  (`dotnet/src/Agents/OpenAI/`) — OpenAI Assistants v2 + OpenAI Responses API.
- `AzureAIAgent` (`dotnet/src/Agents/AzureAI/`) — Azure AI Foundry hosted
  agent.
- `BedrockAgent` (`dotnet/src/Agents/Bedrock/`) — AWS Bedrock Agent.
- `A2AAgent` + `A2AHostAgent` (`dotnet/src/Agents/A2A/`) — Agent-to-Agent
  protocol client and host.
- `CopilotStudioAgent` (`dotnet/src/Agents/Copilot/`) — Microsoft Copilot
  Studio adapter.

Dispatch is by **C# polymorphism** — each agent host implements `Agent`'s
`InvokeAsync` / `InvokeStreamingAsync`. There is no engine-type discriminator,
no typed mismatch exception, no `EngineRegistry.resolve(envelope)` call. The
orchestration layer (`Agents/Orchestration/AgentOrchestration.cs:39-255`)
treats every member as a generic `Agent` and composes via actor messages.

The Magentic orchestration (`Agents/Magentic/MagenticOrchestration.cs`) is
worth singling out as a deeper integration: it ships a
`StandardMagenticManager` plus a `MagenticProgressLedger` and
`MagenticPrompts` — a faithful reimplementation of Microsoft's Magentic-One
research paper's open-ended team manager. The Magentic team's `TaskLedger` is
the closest analogue in the surveyed corpus to a long-horizon Run plan ledger,
but it is in-memory and per-orchestration.

The **filter pipeline** (`IFunctionInvocationFilter`, `IPromptRenderFilter`,
`IAutoFunctionInvocationFilter` — `Abstractions/Filters/Function/`,
`Filters/Prompt/`, `Filters/AutoFunctionInvocation/`) is the strongest
cross-cutting policy primitive — equivalent to our `RuntimeMiddleware` on
`HookPoint` events, but scoped per function call rather than per Run.

The architectural shape is therefore **one Kernel ambient context, one
actor-model in-process runtime, many concrete Agent host implementations**.
spring-ai-ascend's Engine Contract envelope (Rule R-M.a/.b — typed
`EngineEnvelope` with `engine_type` discriminator and `EngineMatchingException`)
is structurally absent — SK's polymorphism handles "different agent hosts"
elegantly, but cannot represent "the same Run dispatched against an
alternative engine implementation".

## 6. Evolution substrate

There is no fine-tuning or trajectory plane, but SK has the most mature
**process / workflow** framework of the libraries surveyed:

- **Process Framework**
  (`dotnet/src/Experimental/Process.Core/` + `.Abstractions/` +
  `.LocalRuntime/` + `.Runtime.Dapr/`) — a stateful workflow framework with
  `ProcessBuilder`, `ProcessStepBuilder`, `KernelProcess`,
  `KernelProcessEdge`, `KernelProcessMap`. The Dapr runtime
  (`Experimental/Process.Runtime.Dapr/`) lets a process span multiple
  workers backed by Dapr actor state. This is the closest analogue to
  spring-ai-ascend's graph-engine inside the Engine Contract.
- **AgentThread** (`Agents/Abstractions/AgentThread.cs`) — per-conversation
  state container, plus host-specific subclasses
  (`AzureAIAgentThread`, `OpenAIAssistantAgentThread`,
  `A2AAgentThread`, `OpenAIResponseAgentThread`,
  `Magentic*Thread`). The thread is the closest equivalent to LangChain4j's
  `AgenticScope.memoryId` or SAA's `thread_id`.
- **Memory** has a multi-layer story. Legacy
  `Abstractions/Memory/IMemoryStore.cs` + `ISemanticTextMemory.cs` is the
  pre-1.0 simple text-memory API; **the modern path is `AIContextProvider`**
  (`Abstractions/Memory/AIContextProvider.cs:20-…`, marked
  `[Experimental("SKEXP0130")]`) — a base class with
  `ConversationCreatedAsync`, `MessageAddingAsync`,
  `ConversationDeletingAsync`, and `OnModelInvokingAsync` hooks for
  long-term memory enrichment. The default implementation
  `AggregateAIContextProvider` chains multiple providers.
- **Vector Data** (`dotnet/src/VectorData/`) — 12 backend integrations
  (AzureAISearch, Chroma, CosmosMongoDB, CosmosNoSql, InMemory, Milvus,
  MongoDB, PgVector, Pinecone, Qdrant, Redis, SqliteVec, SqlServer,
  Weaviate). Common abstraction in `VectorData.Abstractions/`. The runtime
  vector primitive is `VectorStoreCollection<TKey, TRecord>` with typed
  records.
- **Plugin Ecosystem** — `Plugins/Plugins.Core` (file, time, math, text
  utilities), `Plugins.Memory` (memory-as-plugin), `Plugins.Document`,
  `Plugins.MsGraph`, `Plugins.Web`, `Plugins.AI`,
  `Plugins.StructuredData.EntityFramework`. OpenAPI plugins via
  `Functions/Functions.OpenApi`. MCP integration via the
  `Microsoft.SemanticKernel.Connectors.MCP` adapter (extension package).
- **Skill registry**: no dedicated skill registry like LangChain4j's
  `langchain4j-skills` or SAA's `SkillRegistry`. The plugin system itself is
  effectively the skill registry — a plugin is a labelled bundle of
  `[KernelFunction]` methods.

Maturity assessment: process framework + AgentThread + AIContextProvider are
production-mature, well-integrated with the actor runtime; legacy
`SemanticTextMemory` is being deprecated in favour of `AIContextProvider`;
**trajectory capture, fine-tuning ingest, and any notion of an evolution plane
are absent**. There is no equivalent to spring-ai-ascend's `agent-evolve`
deployment plane (Rule R-I) or `EvolutionExport` scope discriminator on
emitted events (Rule R-M.e). The Magentic `MagenticProgressLedger` is the
closest in-memory analogue to a Run trajectory ledger but is orchestration-
scoped, not durable across runs.

## 7. Deployment model

SK is **an SDK library shipped via NuGet (.NET) and PyPI (Python)**, with
the Java track in a sibling repository. There is no Dockerfile, no Helm chart,
no docker-compose in this clone (verified by `find -name "Dockerfile"` and
`find -name "Chart.yaml"` returning zero hits). The deployment shape is
"embed into your own .NET / Python / Java host application".

The most production-relevant deployment path is the **Dapr Process runtime**
(`dotnet/src/Experimental/Process.Runtime.Dapr/`) — a SK process can be
distributed across multiple workers backed by Dapr actor state. The
in-process runtime (`Agents/Runtime/InProcess/InProcessRuntime.cs:18-…`) is
the default single-process actor host. There is no Kubernetes-native runtime
or sidecar shape declared in-repo.

**No Chinese-silicon support.** A repository-wide `Grep` for
`Ascend|Kunpeng|昇腾|鲲鹏` returns 57 matches across 20 files, but **all 57 are
"Ascending" sort-order enums** in vector-store query builders
(`VectorData/Weaviate/WeaviateQueryBuilder.cs:1`,
`VectorData/Qdrant/QdrantCollection.cs:1`, etc.) — verified by spot-reading
several. The platform target is .NET 10.0 (`README.md:22`), Python 3.10+, and
Java 17+ — all generic. The Connectors set includes Ollama, ONNX, LMStudio,
Hugging Face, NVIDIA NIM, and Bedrock for indirect access to alternative
silicon, but no first-class Huawei Ascend / Kunpeng adapter.

**Java SDK deployment-track gap**: the Java code has moved out of this repo
(`java/README.md:3-5`). Maven users targeting Microsoft's Java SDK must pull
from `microsoft/semantic-kernel-java` separately. From the Java-shop
spring-ai-ascend perspective, this means SK's Java track is structurally
second-class — the .NET and Python tracks ship together with the orchestration
abstractions, while Java follows asynchronously in a separate repo.

A downstream user adopting SK makes most deployment decisions themselves —
which host application, which orchestration runtime (InProcess or Dapr or
custom), which storage backend, which model provider, which tenancy story. SK
opinionates one level above LangChain4j (the actor-model runtime is given) but
several levels below spring-ai-ascend (no Run aggregate, no tenant boundary,
no posture, no governance).

## 8. License + corporate sponsor

License: **MIT** (`LICENSE:1-3` — `MIT License / Copyright (c) Microsoft
Corporation`). No copyleft, no field-of-use restrictions, fully permissive
for embedding into proprietary products. Every C# source file carries the
boilerplate `// Copyright (c) Microsoft. All rights reserved.` header
(verified across `Agents/Abstractions/Agent.cs:1`, `Kernel.cs:1`, et al).

Corporate sponsor: **Microsoft Corporation** — declared in every source file
header and in `LICENSE:3`. The NuGet group is `Microsoft.SemanticKernel`.
Distribution channels:

- `.NET`: NuGet package `Microsoft.SemanticKernel` and
  `Microsoft.SemanticKernel.Agents.Core` (`README.md:60-63`).
- `Python`: PyPI package `semantic-kernel` (`README.md:55`).
- `Java`: separate repo `microsoft/semantic-kernel-java`.

Latest commit on `main`: `32e904c017c33eb35f7abb5a9e6e61e2e7aea81c` dated
**2026-05-27** (".Net: Bump .NET package version to 1.77.0 (#14040)"). The
README banner at `README.md:3-7` announces the **succession** of Semantic
Kernel by **Microsoft Agent Framework (MAF) 1.0** — meaning Microsoft's
strategic focus is shifting to MAF, and SK is positioned as the open-source
SDK foundation on top of which MAF builds. From a Spring-Java-ecosystem
perspective, MAF's GA was a .NET-first event; the Java SDK is therefore
strategically downstream of two Microsoft Java priorities. The Discord
community at `aka.ms/SKDiscord` is the primary support channel; enterprise
procurement support is via Microsoft Azure customer agreements rather than a
direct SK contact.

## 9. What we LEARN

Patterns worth absorbing into spring-ai-ascend, with concrete file paths:

1. **Actor-model agent runtime as an interface** —
   `dotnet/src/Agents/Runtime/Abstractions/IAgentRuntime.cs:15-123` declares
   the runtime as an interface with `SendMessageAsync(message, recipient,
   sender)`, `PublishMessageAsync(message, topic)`, and pluggable agent
   factories. `InProcessRuntime` (`Runtime/InProcess/InProcessRuntime.cs:18`)
   is one impl; Dapr is implied. This is a stronger and more explicit
   abstraction than LangChain4j's `AgentExecutor`. Our `agent-bus` SPI could
   absorb the `IAgentRuntime` shape — promote message routing to a
   first-class interface rather than embedding it inside the engine.

2. **Filter pipeline as middleware** —
   `Abstractions/Filters/Function/IFunctionInvocationFilter.cs:13-21` declares
   `OnFunctionInvocationAsync(FunctionInvocationContext context, Func<…,
   Task> next)` — the classic middleware "around" pattern. Mirrored by
   `IPromptRenderFilter` (prompt template rendering) and
   `IAutoFunctionInvocationFilter` (auto-tool-call). Our `RuntimeMiddleware`
   could adopt the same per-hook-point signature for clearer downstream
   composition.

3. **Multi-agent orchestration vocabulary** —
   `dotnet/src/Agents/Orchestration/` ships four distinct orchestration
   shapes: `SequentialOrchestration`, `ConcurrentOrchestration`,
   `GroupChatOrchestration` (with `RoundRobinGroupChatManager`), and
   `HandoffOrchestration` (`Orchestration/Handoff/HandoffOrchestration.cs` +
   `HandoffActor.cs` + `Handoffs.cs`). Plus `MagenticOrchestration` for
   open-ended team-of-agents. This vocabulary lines up with SAA's
   `SequentialAgent/ParallelAgent/RoutingAgent/LoopAgent` but extends with
   `Handoff` (explicit agent-to-agent control transfer) and `Magentic`
   (research-grade team manager) — worth absorbing as named built-in shapes
   on our Engine Contract.

4. **AgentThread per host** —
   `Abstractions/AgentThread.cs` plus host-specific subclasses
   (`AzureAIAgentThread`, `OpenAIAssistantAgentThread`, `A2AAgentThread`,
   `OpenAIResponseAgentThread`). The thread is the persistent surface where
   each agent host's native state lives — Azure AI's thread,
   OpenAI's thread, etc. — abstracted under a common interface. Our `Run`
   aggregate could expose an `EngineRunHandle` per engine implementation,
   absorbing the "agent thread" concept as engine-specific state.

5. **AIContextProvider as long-term memory hook chain** —
   `Abstractions/Memory/AIContextProvider.cs` declares
   `ConversationCreatedAsync`, `MessageAddingAsync`,
   `ConversationDeletingAsync`, and `OnModelInvokingAsync` hooks. Multiple
   providers chain via `AggregateAIContextProvider`. This is more refined
   than LangChain4j's `ChatMemory` because it has a lifecycle ladder.
   Our `graphmemory-starter` could expose the same lifecycle ladder.

6. **Process Framework with Dapr runtime** —
   `Experimental/Process.Core/ProcessBuilder.cs` + `Process.Runtime.Dapr/`
   show how a workflow can be portable between a `LocalRuntime` (single
   process, in-memory state) and a `DaprRuntime` (multi-worker, actor-
   persisted state) without changing the workflow definition. The shape is
   "ProcessBuilder → KernelProcess → KernelProcessStep → KernelProcessEdge",
   each step backed by a `KernelFunction`. Worth absorbing as a portability
   pattern for our graph-engine execution between dev (in-memory) and prod
   (distributed) postures.

7. **YAML-defined agent definitions** —
   `dotnet/src/Agents/Yaml/` ships a YAML schema for declaring agents
   (name, instructions, plugins, model settings) outside of code. This is a
   data-driven companion to LangChain4j's declarative-interface approach and
   would dovetail with our `module-metadata.yaml` style.

8. **`[Experimental("SKEXP****")]` attribute as compile-time posture marker**
   — `Agent.cs:78` (`UseImmutableKernel`) and `AIContextProvider.cs:19`
   illustrate a compile-time discipline for Pre-GA APIs: every experimental
   surface has a unique `SKEXPxxxx` identifier, and downstream callers must
   add the identifier to their `<NoWarn>` list to opt in. Cleaner than
   markdown-tagged "experimental" status. Our `@Experimental` annotation
   could be extended with the same per-feature stable identifier.

9. **MCP-as-connector adapter** — SK's MCP integration ships as
   `Microsoft.SemanticKernel.Connectors.MCP` (extension package), surfacing MCP
   servers as kernel plugins. Pattern parallel to LangChain4j's
   `langchain4j-mcp` + `langchain4j-agentic-mcp` — right shape for our
   engine-adapter library.

## 10. Where we DIFFER

Each row cites one evidence file in **each** repo.

| # | Dimension | Semantic Kernel evidence | spring-ai-ascend evidence |
|---|-----------|--------------------------|---------------------------|
| 1 | **Multi-tenancy depth** — SK: no runtime tenant model; `TenantId` references in source refer exclusively to Azure-AD auth credentials. Ascend: tenantId mandatory on Run, RLS at Postgres, re-validation at HTTP edge. | `dotnet/src/Agents/Copilot/CopilotStudioConnectionSettings.cs:27-44` (TenantId is Azure-AD auth only) + grep returns zero runtime-tenancy hits in `Agents/Abstractions/` | `D:\chao_workspace\spring-ai-ascend\docs\governance\rules\rule-R-C.2.md` + `agent-service/src/main/java/com/huawei/ascend/service/runtime/runs/Run.java` (tenantId NOT NULL) |
| 2 | **Engine Contract envelope vs Agent polymorphism** — SK dispatches by `Agent` subclass polymorphism (`ChatCompletionAgent`, `OpenAIAssistantAgent`, `AzureAIAgent`, `BedrockAgent`, `A2AAgent`, `CopilotStudioAgent`). Ascend dispatches by `EngineEnvelope` + `EngineRegistry.resolve()` with typed mismatch. | `dotnet/src/Agents/Abstractions/Agent.cs:24-100` + 6 concrete `*Agent.cs` subclasses | `D:\chao_workspace\spring-ai-ascend\docs\contracts\engine-envelope.v1.yaml` + Rule R-M.a |
| 3 | **Evolution substrate scope** — SK: process workflow ledger (Magentic `MagenticProgressLedger`) but no trajectory capture or fine-tuning ingest. Ascend: dedicated `agent-evolve` module on deployment plane `evolution`. | `dotnet/src/Agents/Magentic/MagenticProgressLedger.cs` (in-memory ledger, orchestration-scoped) | `D:\chao_workspace\spring-ai-ascend\agent-evolve\module-metadata.yaml` |
| 4 | **Ascend+Kunpeng sovereignty** — SK: .NET 10 / Python 3.10+ / Java 17+ generic; Bedrock + Ollama + ONNX for indirect alt-silicon; no first-class Huawei silicon adapter. Ascend: ARM64 + NPU as design target, five-plane topology declared per-module. | `README.md:21-23` (.NET 10.0+ / Python 3.10+ / Java 17+) + zero hits for `Ascend\|Kunpeng` as silicon names | `D:\chao_workspace\spring-ai-ascend\README.md:3-10` + Rule R-I five-plane manifest |
| 5 | **Spring-native ergonomics vs .NET-first SDK** — SK is .NET-first (1.77.0); Python tracks closely; Java SDK was extracted to a separate repo for "easier development", de facto a second-class track. Ascend is Spring-Boot-first Java with `agent-service-spring-boot-starter` autoconfiguration as the only target. | `java/README.md:3-5` (Java SDK moved to `microsoft/semantic-kernel-java`) | `D:\chao_workspace\spring-ai-ascend\pom.xml` + Rule R-A (developer self-service quickstart) |
| 6 | **Audit-grade Run spine** — SK: `AgentThread` per conversation, no Run aggregate, no state machine, no idempotency. Ascend: Run aggregate with `RunStateMachine.validate(from, to)` + durable idempotency. | `dotnet/src/Agents/Abstractions/AgentThread.cs` (thread, no transitions) | Rule R-C.2.b + `agent-service/.../service/runtime/runs/RunStateMachine.java` |
| 7 | **Three-track bus channel isolation** — SK: in-process `ConcurrentQueue<MessageDelivery>` single channel; Dapr variant for the Process framework only; no out-of-band control plane. Ascend: control/data/rhythm channels physically isolated. | `dotnet/src/Agents/Runtime/InProcess/InProcessRuntime.cs:22` (single ConcurrentQueue) | Rule R-E + `D:\chao_workspace\spring-ai-ascend\docs\governance\bus-channels.yaml` |
| 8 | **中台 + 能力复用 dual deployment topology** — SK: SDK-as-NuGet/PyPI delivery shape; Microsoft Agent Framework (MAF) is the productised platform above SK, separately licensed. Ascend: kernel-first SPI library + BoM + dual-deployment topology (中台 + 能力复用) shipped in one repo. | `README.md:3-7` (MAF banner — SK is the SDK below the platform) | `D:\chao_workspace\spring-ai-ascend\pom.xml` (kernel-first reactor) + Rule R-I |
| 9 | **License + competitive-supplier posture** — SK: MIT under Microsoft Corp; absorbing SK ties roadmap and security-disclosure cycle to a US hyperscaler. Ascend: vendor-backed by Huawei, supplied as enterprise platform under partner agreements. | `LICENSE:1-3` + every `.cs` header `// Copyright (c) Microsoft.` | `D:\chao_workspace\spring-ai-ascend\README.md` (Huawei-sponsored) |
| 10 | **Governance / Code-as-Contract** — SK: filter pipelines + OpenTelemetry; no architectural enforcer suite, no ADR ledger, no recurring-defect-families YAML. Ascend: 144+ gate rules, ArchUnit tests, governance YAML kernel. | `dotnet/src/SemanticKernel.Abstractions/Filters/Function/IFunctionInvocationFilter.cs` is the only built-in governance primitive | `D:\chao_workspace\spring-ai-ascend\CLAUDE.md` (Rule kernel index) + `gate/check_architecture_sync.sh` |
| 11 | **Financial-vertical-first vs vertical-agnostic** — SK ships zero domain-vertical examples or compliance artefacts; v1 use cases are generic chatbot + multi-agent demo + Copilot Studio. Ascend v1.0 targets financial-vertical (audit-grade, posture-aware, residency-respecting) with sovereignty + idempotency + cancel-re-auth shipped on Day 1. | `dotnet/samples/Concepts/Agents/*` (generic concept samples, no vertical guidance) | `D:\chao_workspace\spring-ai-ascend\README.md` + Rule R-J cancel re-authorisation |
| 12 | **Successor-product posture** — SK is explicitly being superseded by Microsoft Agent Framework (per the README's IMPORTANT banner). Adopting SK in 2026 means committing to migrate later. Ascend is on its initial v1.0 trajectory with no pending successor product. | `README.md:3-7` (MAF migration banner) | `D:\chao_workspace\spring-ai-ascend\CLAUDE.md` (active development, no successor planned) |

These dimensions are mutually reinforcing: SK optimises for **enterprise SDK
across Microsoft's three language tracks** — a .NET / Python developer reaches
first-multi-agent-orchestration quickly inside the Microsoft ecosystem, with a
high-quality actor-model runtime and filter middleware; the Java track is
second-class. Spring-ai-ascend optimises for **governed deployment on
sovereign hardware** — an enterprise runs governed agents on Ascend NPUs with
audit-grade evidence and opinionated Spring-Boot ergonomics. SK's biggest risk
for a Java enterprise adopter is **succession risk** (README's MAF banner
promises migration ahead) compounded with **Java-track second-class status**.
The positioning is therefore complementary, not overlapping — SK fits .NET /
Azure shops; spring-ai-ascend fits Java-Spring / Ascend shops with multi-
tenant governance as a Day 1 requirement.
