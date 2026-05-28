---
analysis_id: COMPETITIVE-LANGCHAIN4J
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 1
repo_clone_at: D:\ai-research\agent-platforms-survey\langchain4j\
---

# Competitive Analysis: langchain4j/langchain4j

Source-grounded analysis at commit `ed861e6` (2026-05-26, tip of `main`), Maven
aggregator version `1.16.0-beta26-SNAPSHOT` declared in `pom.xml:7`. Conducted
per the C.2 ten-point template. LangChain4j is an Apache-2.0 open-source Java
LLM library curated by an independent maintainer (Dmytro Liubarskyi,
`langchain4j-parent/pom.xml:22-29`), not a vendor-backed platform. The README
explicitly disclaims that "LangChain4j is **not a Java port of LangChain
(Python)** — it is built for Java, not ported to it" (`README.md:42`).

## 1. Tagline & positioning

The repository's elevator pitch is the README's H1, verbatim
(`README.md:1`):

> "LangChain4j: idiomatic, open-source Java library for building LLM-powered
> applications on the JVM"

Stated goals (`README.md:17-50`):

> "The goal of LangChain4j is to simplify integrating LLMs into Java
> applications. … 1. **Unified APIs:** LLM providers … and embedding (vector)
> stores … use proprietary APIs. LangChain4j offers a unified API to avoid the
> need for learning and implementing specific APIs for each of them. …
> LangChain4j currently supports **20+ popular LLM providers** and **30+
> embedding stores**. 2. **Comprehensive Toolbox:** … Our toolbox includes
> tools ranging from low-level prompt templating, chat memory management, and
> function calling to high-level patterns like Agents and RAG."

The framing is **library-first**, **integration-breadth-first**, and
**framework-agnostic** — the README lists Quarkus, Spring Boot, Helidon, and
Micronaut as equal first-class integration targets (`README.md:44`). The
positioning slogan "idiomatic Java" appears as the central claim — POJOs,
annotations, fluent builders, dependency injection. LangChain4j is the
**de-facto Java incumbent** in the agent-library space, with 89 reactor modules
covering the integration matrix (`pom.xml:11-244`: 20+ model providers, 22
vector stores, 7 document loaders, 6 document parsers, 3 web search engines, 3
code-execution engines, 11 in-process embeddings). Unlike Spring AI Alibaba's
Aliyun-aligned framing or Microsoft Semantic Kernel's Azure-aligned framing,
LangChain4j is **vendor-neutral**: there is no preferred cloud or model provider
in the docs, and the lead maintainer is an individual rather than a corporate
sponsor. Multi-tenancy, posture, audit-grade Run spine, and hardware sovereignty
are explicitly out of scope — the project is a building-block library, not a
platform.

## 2. Architecture skeleton

The reactor declares 89 modules (`pom.xml:11-244`). The core engine surface
lives in three modules:

```
langchain4j-core             # interfaces only — ChatMemory, EmbeddingStore, Tool, RAG
langchain4j                  # AiServices (high-level proxy-based agent API)
langchain4j-agentic          # multi-agent orchestration (sequence/parallel/loop/conditional/supervisor)
langchain4j-agentic-a2a      # A2A (agent-to-agent protocol) client
langchain4j-agentic-mcp      # MCP (Model Context Protocol) integration
langchain4j-agentic-patterns # GOAP planner, P2P, voting strategies
langchain4j-mcp              # raw MCP client (transport + protocol)
langchain4j-skills           # filesystem/classpath SKILL.md registry
langchain4j-guardrails       # input/output guardrail executors
langchain4j-easy-rag         # one-line RAG ingestion + retrieval
```

The aggregator surrounds these with 20+ `langchain4j-{provider}` model
connectors, 22 `langchain4j-{store}` vector backends, and per-format document
loader/parser starters. The `langchain4j-core` module is interface-only:
`ChatMemory.java` (108 lines), `EmbeddingStore`, `ContentRetriever`,
`ToolSpecification`, plus the new `Skill` SPI added in
`langchain4j-skills/.../Skill.java:17-59`.

Agent abstractions live in **two parallel surfaces**: (1) the
`langchain4j/.../service/AiServices.java` proxy-builder (1254 lines) wraps a
user-declared Java interface — `@SystemMessage`, `@UserMessage`,
`@MemoryId`, `@Tool` annotations on interface methods compile into runtime JDK
dynamic proxies; (2) the `langchain4j-agentic/.../AgenticServices.java` static
factory (866 lines) is the multi-agent layer with `sequenceBuilder()`,
`parallelBuilder()`, `loopBuilder()`, `conditionalBuilder()`,
`supervisorBuilder()`, `plannerBuilder()`, and `a2aBuilder()` factories.

**Counterpart mapping to spring-ai-ascend** (modules listed in
`D:\chao_workspace\spring-ai-ascend\pom.xml`):

| spring-ai-ascend                        | langchain4j counterpart                                          | Notes |
|-----------------------------------------|------------------------------------------------------------------|-------|
| `agent-service`                         | (none — no Run/idempotency/tenant spine)                         | No Run aggregate, no state machine |
| `agent-bus`                             | (none — direct method/proxy invocation)                          | No bus channels; no out-of-band control plane |
| `agent-execution-engine`                | `langchain4j` + `langchain4j-agentic`                            | AiServices proxy + Agentic factory |
| `agent-middleware`                      | partial — `AgenticScopePersister` SPI + `EmbeddingStore` family  | No tenant middleware; checkpoint via JSON codec |
| `agent-client`                          | (none — Java embedded library only)                              | No HTTP edge boundary |
| `agent-evolve`                          | (none — no Python evolution plane)                               | Not in scope |
| `spring-ai-ascend-graphmemory-starter`  | `langchain4j-easy-rag` + 22 `langchain4j-{vector-store}` modules | Many backends, no graph-memory primitive |

## 3. Developer experience

The README points all examples at the sibling `langchain4j-examples` repo
(`README.md:64`), not this monorepo. The canonical onboarding shape — visible
in `langchain4j/src/main/java/dev/langchain4j/service/AiServices.java:67-83` —
is: a developer **declares a Java interface** (`interface Assistant { String
chat(String message); }`), then calls `AiServices.builder(Assistant.class)
.chatModel(model).build()` to obtain a runtime proxy. The proxy generation
machinery is JDK reflection, not Spring AOP — meaning AiServices runs unchanged
inside Quarkus, Spring Boot, Helidon, Micronaut, or plain `main()`.

The agentic surface is a thin layer on top: `AgenticServices.sequenceBuilder()
.subAgents(agentA, agentB).output(...).build()` (`AgenticServices.java:134-146`).
The agentic system can be expressed two ways — **declarative** (annotations
like `@SequenceAgent`, `@LoopAgent`, `@ConditionalAgent`, `@PlannerAgent`,
`@SupervisorAgent` on an interface, then `AgenticServices.createAgenticSystem
(MyAgent.class)` at `AgenticServices.java:298-347`) or **programmatic** (the
builder factories). Sub-agent topologies compose recursively because every
sub-agent is itself an `AgentExecutor`
(`langchain4j-agentic/.../internal/AgentExecutor.java`).

There is **no Spring Boot starter in this repo** — `find -name "spring*"`
under the langchain4j tree returns nothing. Spring integration is delegated to
external community repos (the README at `:67-69` redirects users to
`langchain4j-examples/spring-boot-example`, `quarkus-langchain4j`,
`micronaut-langchain4j`, `helidon-integrations-langchain4j`). API-key ingestion
is plain Java — `OpenAiChatModel.builder().apiKey(System.getenv("OPENAI_API_KEY"))
.build()`. No project scaffold, no `application.yml`, no posture concept. The
"from zero to first chat" path is shorter than any other library surveyed (about
10 lines of Java), at the cost of zero opinions about wiring, config posture,
or auditability.

## 4. Multi-tenancy & governance

**There is no tenant model.** A repository-wide `Grep` for `tenantId`
across the `langchain4j-agentic` module returns zero matches. The 18
files in the full repo that mention "tenant" are exclusively (i) Azure-AD
`tenantId` properties on Azure OpenAI / Vertex AI / Cosmos / Vespa
connectors (auth credentials, not isolation), or (ii) test fixtures. The
`AgenticScope` interface (`agentic/scope/AgenticScope.java:20-245`)
exposes a `memoryId()` per scope — but `memoryId` is a session/thread
identifier, not a tenant identifier. The persistence SPI
`AgenticScopePersister` (`scope/AgenticScopePersister.java:5-31`) is a
`ServiceLoader`-discovered singleton with no tenant parameter on any
method. The `ChatMemory.id()` interface
(`langchain4j-core/.../memory/ChatMemory.java:22`) returns an
implementation-chosen `Object` — typically a user/conversation ID. There
is no audit MDC, no Posture concept, no `@RequiredConfig`, no Row-Level
Security helper, no idempotency primitive.

Governance surfaces (architectural enforcers, ADR ledger, recurring defect
families, gate rules) are absent. Static analysis is limited to
`mvn -Pspotless spotless:check` (Makefile lines 17-23). No ArchUnit tests, no
custom Spotbugs detector, no governance YAML files.

By contrast, spring-ai-ascend mandates `tenantId` on every Run record (Rule
R-C.2.a `Objects.requireNonNull(tenantId)`), enforces Postgres RLS in every
Flyway migration touching a `tenant_id` column (Rule R-J.a), re-validates
`request.tenantId == Run.tenantId` at the HTTP cancel edge (Rule R-J.b), and
runs 144+ gate rules across every commit. LangChain4j's design assumption is a
**single-tenant Java process** — adding tenancy is a downstream concern handled
by whichever Spring Boot / Quarkus harness embeds the library.

## 5. Engine pluggability

LangChain4j has the broadest **model-provider** pluggability of any library
surveyed: 20+ `langchain4j-{provider}` modules behind `dev.langchain4j.model.
chat.ChatModel`. But it has **no engine-discrimination concept** for execution
strategy.

The agentic surface has fixed orchestration shapes built into a
`WorkflowAgentsBuilder` SPI
(`langchain4j-agentic/.../workflow/WorkflowAgentsBuilder.java`) loaded by
`ServiceLoader` (`AgenticServices.java:80-87`) — `sequenceBuilder`,
`parallelBuilder`, `loopBuilder`, `conditionalBuilder`, `parallelMapperBuilder`.
A downstream can replace the entire builder, but the workflow vocabulary is
hard-coded. The four `Default*AgentInstance.java` files under
`workflow/impl/` are concrete execution. The planner surface
(`planner/Planner.java`) admits a custom `Planner` implementation, including
the `GoalOrientedPlanner` from `langchain4j-agentic-patterns`
(`goap/GoalOrientedPlanner.java`) and `P2PPlanner` (`p2p/P2PPlanner.java`) and
`VotingPlanner` (`voting/VotingPlanner.java`). These are alternative
**planners**, not alternative **engines** — they all sit on the same
`AgentExecutor` execution loop.

A2A is treated as a remote-agent transport adapter
(`langchain4j-agentic-a2a/.../A2AClientAgentInvoker.java:1`,
`DefaultA2AClientBuilder.java`) — an A2A endpoint becomes an `AgentExecutor`
just like a local agent, sharing one execution loop. There is **no
`EngineRegistry`, no `EngineEnvelope`, no `engine_type` discriminator, no typed
mismatch exception**. The closest structural analogue to spring-ai-ascend's
`RuntimeMiddleware` is `AgentListener` (`observability/AgentListener.java`)
plus the `Guardrail`/`InputGuardrail`/`OutputGuardrail` interfaces
(`langchain4j-core/.../guardrail/*.java` — 22 files including
`InputGuardrailExecutor`, `OutputGuardrailExecutor`, and the
`JsonExtractorOutputGuardrail` and `MessageModeratorInputGuardrail`
implementations in `langchain4j-guardrails/`). Guardrails are scoped to a
single function invocation — they cannot reroute across engine adapters.

The architectural shape is therefore **one execution loop per Java JVM, many
model providers, many orchestration shapes** — not the dual-mode (graph +
agent-loop) coexistence with `EngineMatchingException` typing that
spring-ai-ascend mandates via Rule R-M.b.

## 6. Evolution substrate

There is no evolution plane, no trajectory store, and no fine-tuning
capture. A repository-wide grep for `Trajectory|FineTune` in the
`langchain4j-agentic` module returns zero hits. The memory / RAG / skill
surface is the deepest of any library surveyed but stays at "stateless
embedding store + RAG retrieval + per-conversation chat memory":

- **Chat memory** is interface-only at the core (`ChatMemory.java:16-107`):
  `id() / add(message) / messages() / clear()` plus a `set(...)` semantic
  added in 1.11.0 for memory compaction. Concrete implementations are
  `MessageWindowChatMemory` and `TokenWindowChatMemory` (sliding-window) plus
  per-store persistent variants in each `langchain4j-{store}` module.
- **AgenticScope** (`agentic/scope/AgenticScope.java:20-245`) is the
  multi-agent shared-state primitive — a typed key-value bag (`writeState`,
  `readState`, `state()`, `executionContext` for non-serializable
  state, `agentInvocations()` history). Serialization goes through
  `AgenticScopeJsonCodec` + `JacksonAgenticScopeJsonCodec`
  (`scope/JacksonAgenticScopeJsonCodec.java`). Persistence is
  `AgenticScopePersister` (singleton `ServiceLoader` lookup) — there is no
  bundled JDBC / Postgres / Redis implementation in this repo; downstream
  ships its own.
- **Vector / embedding stores**: 22 backend modules
  (`langchain4j-pgvector`, `langchain4j-milvus`, `langchain4j-qdrant`,
  `langchain4j-chroma`, `langchain4j-pinecone`, `langchain4j-weaviate`,
  `langchain4j-elasticsearch`, `langchain4j-mongodb-atlas`,
  `langchain4j-cassandra`, `langchain4j-oracle`, etc.). Each implements
  `EmbeddingStore<TextSegment>`. Plus 11 in-process embedding models under
  `embeddings/langchain4j-embeddings-*` (BGE small en/zh, MiniLM, E5).
- **RAG**: `langchain4j-easy-rag` ships a one-line `EmbeddingStoreIngestor`
  for any backend. The `langchain4j-core/.../rag/` package declares
  `RetrievalAugmentor`, `ContentRetriever`, `EmbeddingStoreContentRetriever`,
  and `DefaultRetrievalAugmentor`.
- **Skill registry** exists as a fresh experimental SPI
  (`langchain4j-skills/.../Skill.java:17-59`) — `name() / description() /
  content() / resources() / toolProviders()`. Loaders are
  `ClassPathSkillLoader.java` and `FileSystemSkillLoader.java` (parallel to
  Spring AI Alibaba's filesystem skill registry, with a `SKILL.md`-style
  metadata convention pointing at https://agentskills.io). The skill surface
  is prompt-injected tools, not capacity-arbitrated resources.
- **MCP** (Model Context Protocol): the dedicated `langchain4j-mcp` module
  ships `DefaultMcpClient`, an stdio transport, and resource-as-tools
  conversion (`resourcesastools/`). The agentic module then bridges MCP into
  a sub-agent via `langchain4j-agentic-mcp` and the `@McpClientAgent`
  declarative annotation (`agentic/declarative/McpClientAgent.java`).
- **Guardrails** as a quasi-evolution substrate: input and output guardrails
  (`langchain4j-core/.../guardrail/InputGuardrail.java`,
  `OutputGuardrail.java`) allow rejection-and-retry loops on model output —
  the closest LangChain4j gets to feedback-based correction.

Maturity assessment: chat memory + RAG + embedding store ecosystem are
production-mature (this is the strength of the library); skill registry and
MCP integration are recent/experimental; **trajectory capture, fine-tuning
ingest, and any notion of an evolution plane are absent**. The
`langchain4j-experimental-*` modules cover Hibernate, SQL, and shell-skills
experiments — none of them touch model evolution. There is no equivalent to
spring-ai-ascend's `agent-evolve` deployment plane (Rule R-I) or
`EvolutionExport` scope discriminator on emitted events (Rule R-M.e).

## 7. Deployment model

LangChain4j is **a library, not a runtime**. There is no Dockerfile, no Helm
chart, no docker-compose, no admin console, no managed service shape — the
delivery vehicle is Maven Central JARs only (`pom.xml:5-8` declares
`<groupId>dev.langchain4j</groupId> <artifactId>langchain4j-aggregator</
artifactId> <version>1.16.0-beta26-SNAPSHOT</version>`). The library is
embedded into whatever JVM application the downstream chooses — typically a
Spring Boot, Quarkus, Helidon, Micronaut, or plain-Java service hosted
wherever the downstream wants. Java 17+ is the floor
(`langchain4j-parent/pom.xml:39`).

**No Chinese-silicon support.** A repository-wide `Grep` for
`Ascend|Kunpeng|昇腾|鲲鹏` returns zero source-code hits. The JVM target is
generic Java 17 with no ARM64-specific build profile, no NPU adapter, and no
deployment-plane discriminator. The Bedrock connector
(`langchain4j-bedrock/`) gives indirect access to AWS Trainium / Inferentia;
the Vertex AI connector gives indirect access to Google TPU; but no Huawei
Ascend / Kunpeng path exists. This is the second-deepest dimension of
differentiation — spring-ai-ascend explicitly positions for **Ascend NPU +
Kunpeng ARM64** sovereignty (`README.md:3-10`) with a per-module
`deployment_plane:` declaration.

A downstream user adopting LangChain4j makes **all** deployment decisions
themselves — what plane the agents run on, how multi-tenancy is enforced,
which model provider, which vector store, which orchestration runtime. The
library does not opine. This is its strength for greenfield experimentation
and its weakness for enterprise governance.

## 8. License + corporate sponsor

License: **Apache 2.0** (`LICENSE:1-3`; `langchain4j-parent/pom.xml:16` declares
`The Apache Software License, Version 2.0`). No copyleft, no field-of-use
restrictions, fully permissive for embedding into proprietary products.

Corporate sponsor: **none formal.** The project is maintained by an
individual — Dmytro Liubarskyi (`langchain4j-parent/pom.xml:22-29` lists him as
sole declared developer; email `info@langchain4j.dev`; GitHub
`@dliubarskyi`). The aggregator's Maven group is `dev.langchain4j`. The
project has community partnerships visible in the README's "Get Help"
section (Discord, GitHub Discussions, BlueSky, X), but no
`<organization>` element in either the aggregator or parent POM. The
latest commit on `main` is `ed861e6c2d534a1c9e4e529d395b4af91c787e27`
dated **2026-05-26** ("MCP: surface tool outputSchema on
ToolSpecification (#5293)"). The version `1.16.0-beta26-SNAPSHOT`
indicates active pre-stable iteration on the beta line. Maven Central
artifacts live under group `dev.langchain4j` (`README.md:10` cites the
Maven Central badge for `dev.langchain4j:langchain4j`).

The absence of a corporate sponsor is a double-edged characteristic:
license-wise it removes any vendor-lock concern (unlike SAA's Alibaba Cloud
sponsorship or Microsoft's behind-SK relationship); roadmap-wise it means no
guaranteed long-term support, no enterprise SLA, and no procurement-ready
contact channel.

## 9. What we LEARN

Patterns worth absorbing into spring-ai-ascend, with concrete file paths:

1. **Annotation-driven declarative agent definition** —
   `langchain4j-agentic/.../declarative/SequenceAgent.java`,
   `LoopAgent.java`, `ConditionalAgent.java`, `PlannerAgent.java`,
   `SupervisorAgent.java`, `A2AClientAgent.java`, `McpClientAgent.java` form a
   complete annotation vocabulary. A user declares an interface with these
   annotations on its methods, calls `AgenticServices.createAgenticSystem(
   MyAgent.class)` (`AgenticServices.java:298`), and gets a runtime proxy with
   full multi-agent orchestration. This is the cleanest mapping of agent
   topology to Java type system in the surveyed corpus and would be a strong
   developer-experience win as a `@SpringBootApplication`-style starter on top
   of our Engine Contract.

2. **AgenticScope as a typed shared-state primitive** —
   `langchain4j-agentic/.../scope/AgenticScope.java:20-245` declares typed
   keys (`TypedKey<T>`), an execution-context sub-store for
   non-serializable runtime objects (`writeExecutionContext` /
   `executionContext` / `executionContextAs`, lines 178-244), JSON
   serialization via pluggable `AgenticScopeJsonCodec`, and
   `agentInvocations()` history. Our `RunContext` could absorb the
   typed-key + execution-context split — particularly the explicit
   separation of "persistable conversation state" from "transient
   execution objects".

3. **Skill SPI with `SKILL.md` + tool-provider mapping** —
   `langchain4j-skills/.../Skill.java:17-59` defines `name() / description()
   / content() / resources() / toolProviders()`. The `toolProviders()` method
   returns a `List<ToolProvider>` so activating a skill activates the tools
   it bundles. This is more elegant than SAA's skill registry because it
   bundles tools with the skill. Our `skill-capacity.yaml` could be extended
   to point at a `SKILL.md` per skill with a tool-provider list.

4. **Per-method `@Tool` declaration with `@Agent` cross-invocation** — a
   single Java interface can mix `@Tool`-annotated methods (callable by the
   LLM) with `@Agent`-annotated methods (composable in a multi-agent topology
   — see `langchain4j-agentic/.../Agent.java:14-72`). Tool ↔ Agent
   composition becomes a single Java-typed namespace.

5. **Guardrail executor pattern** —
   `langchain4j-core/.../guardrail/InputGuardrailExecutor.java`,
   `OutputGuardrailExecutor.java`, `GuardrailRequest.java`,
   `GuardrailResult.java` form a clean retry-with-reprompt loop for output
   validation, mirroring our middleware notion of cross-cutting policy. The
   `JsonExtractorOutputGuardrail.java` in `langchain4j-guardrails` is a
   reference implementation that retries on JSON-parse failure with a corrective
   prompt — a pattern our `RuntimeMiddleware` could canonicalise.

6. **22-backend vector store integration matrix** — every major vector store
   has a dedicated `langchain4j-{store}` module (Pgvector, Milvus, Qdrant,
   Chroma, Pinecone, Weaviate, Elasticsearch, MongoDB Atlas, Cassandra, Oracle,
   AzureAISearch, Cosmos, Couchbase, Coherence, Infinispan, MariaDB, OpenSearch,
   Tablestore, Vespa, Hibernate, Redis-via-langchain4j-core,
   AzureCosmosMongoVCore). Our `graphmemory-starter` could publish a sibling
   set of starters with the same naming convention, but adding a `tenant_id` +
   RLS column where the underlying store supports it.

7. **`@MemoryId` parameter convention** —
   `langchain4j/.../service/MemoryId.java` lets a service method declare
   `String chat(@MemoryId String userId, @UserMessage String msg)` so chat
   memory is per-user automatically. The annotation convention pre-figures
   our tenant-scoped middleware lookup but stops short of being a
   strong-typed `TenantId` since the value can be any `String`.

8. **MCP-as-sub-agent adapter pattern** —
   `langchain4j-agentic-mcp/.../McpService.java` plus
   `agentic/declarative/McpClientAgent.java` show how an external MCP server
   becomes a first-class sub-agent in the multi-agent topology. This is the
   same shape we want for our future "engine adapters under the Engine
   Contract envelope" — a third-party framework appearing as a `BaseAgent`
   subclass that emits an `AgentExecutor`-compatible result.

## 10. Where we DIFFER

Each row cites one evidence file in **each** repo.

| # | Dimension | LangChain4j evidence | spring-ai-ascend evidence |
|---|-----------|----------------------|---------------------------|
| 1 | **Multi-tenancy depth** — LangChain4j: no tenant model; `@MemoryId` is a per-conversation key, not a tenant boundary. Ascend: tenantId mandatory on Run, RLS at Postgres, re-validation at HTTP edge. | `langchain4j/src/main/java/dev/langchain4j/service/MemoryId.java` (conversation-scoped only) | `D:\chao_workspace\spring-ai-ascend\docs\governance\rules\rule-R-C.2.md` + `agent-service/src/main/java/com/huawei/ascend/service/runtime/runs/Run.java` (tenantId NOT NULL) |
| 2 | **Engine Contract envelope vs library composition** — LangChain4j: one `AgentExecutor` execution loop per JVM, many planners + many model providers. Ascend: typed `EngineEnvelope` dispatched by `EngineRegistry.resolve()` with `EngineMatchingException` on mismatch, supporting dual-mode graph + agent-loop coexistence. | `langchain4j-agentic/.../internal/AgentExecutor.java` + `AgenticServices.java:80-87` (ServiceLoader of `WorkflowAgentsBuilder`) | `D:\chao_workspace\spring-ai-ascend\docs\contracts\engine-envelope.v1.yaml` + Rule R-M.a |
| 3 | **Evolution substrate scope** — LangChain4j: no trajectory or evolution plane; guardrail-retry is the only feedback shape. Ascend: dedicated `agent-evolve` module on deployment plane `evolution` with `EvolutionExport` scope discriminator. | repository-wide grep `Trajectory\|FineTune` returns zero matches in `langchain4j-agentic` | `D:\chao_workspace\spring-ai-ascend\agent-evolve\module-metadata.yaml` |
| 4 | **Ascend+Kunpeng sovereignty** — LangChain4j: vendor-neutral but x86_64 Java 17 only; AWS Bedrock + GCP Vertex give indirect access to non-NVIDIA silicon, no first-class Huawei silicon adapter. Ascend: ARM64 + NPU as design target, five-plane topology declared per-module. | `langchain4j-parent/pom.xml:39` (`<java.version>17</java.version>`) + zero hits for `Ascend|Kunpeng` | `D:\chao_workspace\spring-ai-ascend\README.md:3-10` + Rule R-I five-plane manifest |
| 5 | **Spring-native ergonomics vs framework-agnostic SPI** — LangChain4j is deliberately framework-agnostic; the Spring Boot starter lives in a separate community repo (`README.md:67`). Ascend opinionatedly Spring-Boot-first, with `agent-service-spring-boot-starter` autoconfiguration. | `D:\ai-research\agent-platforms-survey\langchain4j\` has zero modules named `spring*` (verified by `find -maxdepth 4 -name "spring*" -type d`) | `D:\chao_workspace\spring-ai-ascend\pom.xml` + Rule R-A (developer self-service quickstart) |
| 6 | **Audit-grade Run spine** — LangChain4j: `AgenticScope` is per-conversation state, no Run aggregate, no state machine, no idempotency. Ascend: Run aggregate with `RunStateMachine.validate(from, to)` + durable idempotency. | `langchain4j-agentic/.../scope/AgenticScope.java:20-245` (memoryId, no Run, no transitions) | Rule R-C.2.b + `agent-service/.../service/runtime/runs/RunStateMachine.java` |
| 7 | **Three-track bus channel isolation** — LangChain4j: synchronous proxy invocation + per-step streaming Token; no out-of-band control plane. Ascend: control/data/rhythm channels physically isolated. | `langchain4j/.../service/AiServiceTokenStream.java` (single streaming surface) | Rule R-E + `D:\chao_workspace\spring-ai-ascend\docs\governance\bus-channels.yaml` |
| 8 | **能力复用 (capability-reuse) vs library-of-libraries** — LangChain4j: 89 reactor modules optimised for breadth of integration; deployment shape is "pull the jars you need into your own service". Ascend: kernel-first SPI library + BoM + dual-deployment-topology (中台 + 能力复用); the platform IS the BoM + SPI surface plus deployment-plane discipline. | `D:\ai-research\agent-platforms-survey\langchain4j\pom.xml:11-244` (89 modules, no deployment opinion) | `D:\chao_workspace\spring-ai-ascend\pom.xml` (kernel-first reactor) + Rule R-I |
| 9 | **License + corporate sponsor posture** — LangChain4j: Apache 2.0, single-maintainer community project; no enterprise SLA, no procurement contact. Ascend: vendor-backed (Huawei), enterprise procurement contact, governance ledger. | `langchain4j-parent/pom.xml:22-29` (sole `<developer>` element, no `<organization>`) | `D:\chao_workspace\spring-ai-ascend\README.md` (Huawei-sponsored) |
| 10 | **Governance / Code-as-Contract** — LangChain4j: Spotless code-style only; no architectural enforcers, no ADRs, no recurring-defect-families ledger. Ascend: 144+ gate rules, ArchUnit tests, governance YAML kernel, ADR-numbered decisions. | `D:\ai-research\agent-platforms-survey\langchain4j\Makefile:17-23` (spotless is the only enforcement) | `D:\chao_workspace\spring-ai-ascend\CLAUDE.md` (Rule kernel index) + `gate/check_architecture_sync.sh` |
| 11 | **Financial-vertical-first vs vertical-agnostic** — LangChain4j ships zero domain-vertical examples or compliance artefacts; the v1 use cases are generic chat/RAG. Ascend v1.0 targets financial-vertical (audit-grade, posture-aware, residency-respecting) with sovereignty + idempotency + cancel-re-auth shipped on Day 1. | `langchain4j-examples` (separate repo) — generic chat/RAG examples only | `D:\chao_workspace\spring-ai-ascend\README.md` + Rule R-J cancel re-authorisation |

These dimensions are mutually reinforcing: LangChain4j optimises for **library
breadth** — a Java developer reaches first-LLM-call quickly inside any JVM
framework, pulling exactly the integration jars they need, with zero opinions
imposed. Spring-ai-ascend optimises for **governed deployment** — an enterprise
runs governed agents on sovereign hardware with audit-grade evidence, with
opinions baked into Maven coordinates, configuration YAML, and 144+ enforced
rules. Both target the JVM, both publish to Maven Central, and both implement
"agent" and "RAG" abstractions — but the architectural intent is non-overlapping
enough that spring-ai-ascend's positioning is defensible without arguing against
LangChain4j's feature list. The honest competitive frame is "LangChain4j is the
right choice if your developer wants library composition freedom on a single-
tenant JVM; spring-ai-ascend is the right choice if your platform team needs a
governed multi-tenant runtime on Ascend hardware".
