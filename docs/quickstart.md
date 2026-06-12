# Quickstart — First Agent on `spring-ai-ascend`

> Goal: reach your first authenticated agent invocation without modifying any
> platform source file. Required by `CLAUDE.md` Rule R-A (Business/Platform
> Decoupling + Developer Self-Service).

This document is referenced from the root [`README.md`](../README.md) and is
gated by gate-rule `quickstart_present`.

---

## 1. Prerequisites

- JDK 21 (any vendor; tested with Temurin and OpenJDK).
- Maven 3.9+ (or use the bundled wrapper `./mvnw`).
- Optional for `prod` / `research` posture: Vault, Postgres 16, an LLM provider.

`dev` posture (the default) needs nothing else; in-memory backends are wired
automatically. Set posture via the `APP_POSTURE` env var.

## 2. Build the reactor

```bash
./mvnw -T 1C -q clean install
```

The build runs unit + ArchUnit tests for every reactor module. `-T 1C` builds
independent modules in parallel; surefire runs JUnit classes concurrently within
each fork. Add `-DjunitParallel=false` to debug intermittent test failures.

For a fast inner loop, build just one module and its dependencies:
`./mvnw -pl agent-runtime -am test -q` (see §7 for why this module is
especially quick).

## 3. Boot `agent-service`

```bash
./mvnw -pl agent-service spring-boot:run
```

The HTTP edge starts on port 8080.

Smoke check:

```bash
curl -s http://localhost:8080/v1/health
# {"status":"UP","sha":"...","db_ping_ns":0,"ts":"..."}
```

## 4. Drive your first Run (in-process)

In `dev` posture, the orchestration stack is fully in-memory. Drop a
`@Configuration` class into your own application that wires a custom
`GraphExecutor` and submit a `Run`:

```java
@Configuration
public class MyFirstAgent {

  @Bean
  GraphExecutor myGraphExecutor() {
    return new SequentialGraphExecutor();   // reference impl shipped at W0
  }

  @Bean
  CommandLineRunner driver(Orchestrator orchestrator) {
    return args -> {
      // Orchestrator.run(runId, tenantId, executorDefinition, initialPayload)
      // is the canonical entry point (see
      // com.huawei.ascend.bus.spi.engine.Orchestrator#run).
      // It synchronously creates the Run if absent, marks it RUNNING, and
      // recursively drives the suspend/resume loop until SUCCEEDED / FAILED.
      UUID runId = UUID.randomUUID();
      var def = new ExecutorDefinition.GraphDefinition(
              Map.of("start", (ctx, payload) -> "hello-" + payload),
              Map.of(),
              "start");
      Object result = orchestrator.run(runId, "tenant-demo", def, "world");
      System.out.println("Result: " + result);
    };
  }
}
```

No platform-team intervention required. The patterns this exercises:

- Extension via **SPI** (`GraphExecutor`, `Orchestrator`, `RunRepository`) —
  not by patching `*.impl.*` or `com.huawei.ascend.service.platform.**`.
- Configuration via `@Bean` and `@ConfigurationProperties` — never by source
  patches into the platform module.

## 4.5 L0 Agentic Contract Surface preview (rc43+)

Wave rc43 of the L0 Agentic Contract Surface remediation (ADR-0120
through ADR-0128) lands the contract shapes for **Agent / ModelGateway
/ Skill / Memory / Vector / Retriever / EmbeddingModel / Planner**.
The shapes are `status: design_only` at L0 — Java SPI interfaces and
contract YAMLs exist; functional implementations of `Agent.invoke(...)`,
`ModelGateway.invoke(...)`, etc. land across W2 (LLM gateway, skill
registry), W3 (RAG vertical, SDK GA), and W4 (planner runtime). The
Spring AI reference adapters under `service.integration.springai` are
design-only shells today (throw `UnsupportedOperationException`); they
prove the boundary compiles and the
[`LlmGatewayHookChainOnlyTest`](../agent-service/src/test/java/com/huawei/ascend/service/runtime/architecture/LlmGatewayHookChainOnlyTest.java)
ArchUnit guard asserts the current `ChatModel` shell stays design-only
until the W2 hook-bound LLM implementation lands.

Customer-side registration pattern (the shape Audience B implements
against — runtime functional W2+):

```java
@Configuration
public class MyFirstAgent {

  @Bean
  ModelGateway myModelGateway(ChatModel springAiChatModel) {
    // Spring AI is the canonical Model abstraction per ADR-0125.
    // Wave C1 SpringAiChatModelGateway is a design-only shell at L0;
    // W2 LLM gateway wires real Spring AI invocation behind the
    // platform's hook + capacity machinery.
    return new SpringAiChatModelGateway(springAiChatModel, "openai-gpt-4o-mini");
  }

  @Bean
  AgentDefinition myFirstAgent() {
    return new AgentDefinition(
        "support-agent",
        "tenant-demo",
        "Support Agent",
        "Answers customer support questions using RAG over docs/.",
        new ModelRef("openai-gpt-4o-mini"),
        Set.of(/* SkillRef("get_order_status"), SkillRef("escalate_to_human") */),
        Map.of(/* MemoryCategory.M5_KNOWLEDGE, new MemoryRef("docs-corpus", M5_KNOWLEDGE) */),
        Optional.empty(),                         // plannerBinding
        List.of(new AdvisorBinding(
            "pii-redaction",
            AdvisorBinding.Mode.BOTH,
            Optional.of(100),
            Map.of())),                           // advisorBindings
        "You are a helpful support agent.",
        SafetyPolicy.permissive(),
        Map.of());
  }
}
```

The same `Orchestrator.run(...)` entry point used in §4 above continues
to be the runtime contract for long-running agent work; the `AgentDefinition`
is the *declarative* shape and the W3 SDK GA wave wires
`Agent.invoke(...)` end-to-end via `AgentExecutorDefinitionFactory`.

See:
- [`docs/contracts/agent-definition.v1.yaml`](contracts/agent-definition.v1.yaml)
- [`docs/contracts/model-invocation.v1.yaml`](contracts/model-invocation.v1.yaml) (includes the rc51 `tool_call_loop:` section per ADR-0134)
- [`docs/contracts/skill-definition.v1.yaml`](contracts/skill-definition.v1.yaml)
- [`docs/contracts/memory-store.v1.yaml`](contracts/memory-store.v1.yaml) (includes the rc51 `conversation_memory:` section per ADR-0133)
- [`docs/contracts/vector-store.v1.yaml`](contracts/vector-store.v1.yaml)
- [`docs/contracts/planning-request.v1.yaml`](contracts/planning-request.v1.yaml)
- ADR-0120 through ADR-0128 under `docs/adr/`.

## 4.6 L0 Agentic-Completeness Surface preview (rc51+)

Wave rc51 of the L0 Agentic-Completeness program (ADR-0129 through
ADR-0135) adds the developer-ergonomics extension surface so Audience B
never needs to import Spring AI types directly: **streaming** on
`ModelGateway`, **`StructuredOutputConverter<T>`** for typed-bean
extraction, **`PromptTemplate`** for variable-substituted prompts,
**`ChatAdvisor`** for interceptor chains around model calls, and
**`ConversationMemory`** for windowed FIFO + token-budget pruning. Like
rc43, every shape is `status: design_only` at L0; functional
implementations land in W2 (LLM gateway, prompt rendering, advisor
binding, chat memory) and W3 (RAG cache strategy per ADR-0135).

Customer-side wiring (the shape Audience B implements — functional W2+):

```java
@Configuration
public class MyAgentExtensions {

  /** rc51 — typed-bean extraction wraps Spring AI BeanOutputConverter. */
  @Bean
  <T> StructuredOutputConverter<T> orderResponseConverter(
      ObjectMapper jackson, Class<T> targetType) {
    // SpringAiBeanOutputConverterAdapter is design-only shell at L0;
    // W2 LLM gateway wires it through ModelGateway.invoke decoration.
    return new SpringAiBeanOutputConverterAdapter<>(
        /* org.springframework.ai.converter.BeanOutputConverter */ jackson, targetType);
  }

  /** rc51 — variable-substituted prompts wrap Spring AI PromptTemplate. */
  @Bean
  PromptTemplate supportSystemPromptTemplate() {
    return new SpringAiPromptTemplateAdapter(
        /* org.springframework.ai.chat.prompt.PromptTemplate */ new Object(),
        "support-agent.system-prompt.v1",
        new PromptTemplateSource.InlineString(
            "You help {tenant} with orders placed via {channel}.",
            PromptTemplateSource.PlaceholderSyntax.MUSTACHE_SINGLE_BRACE));
  }

  /** rc51 — interceptor chain around ModelGateway.invoke; binds via
   *  HookDispatcher internally at W2 (Telemetry Vertical co-arrival). */
  @Bean
  ChatAdvisor piiRedactionAdvisor() {
    return new ChatAdvisor() {
      @Override public String advisorName() { return "pii-redaction"; }
      @Override public int order() { return 100; }
      @Override public AdvisedResponse aroundCall(AdvisedRequest req, AdvisorChain chain) {
        // Inbound: inspect or replace req.modelRequest().messages() before chain.next.
        AdvisedResponse response = chain.next(req);
        // Outbound: inspect or replace response.modelResponse().content() before return.
        return response; // L0 contract shape; W2 wires real binding.
      }
    };
  }

  /** rc51 — windowed FIFO + token-budget pruning over M2_EPISODIC. */
  @Bean
  ConversationMemory chatMemory() {
    // First production ConversationMemory impl lands in W2 chat-memory
    // wave; for now customers compose against the SPI shape.
    return null; // placeholder — Audience B impl lands here in W2.
  }
}
```

The same `AgentDefinition` shape from §4.5 binds these extensions via
`toolBindings`, `memoryBindings`, and `advisorBindings`. Advisors are
bound by `AdvisorBinding.advisorName()` so the agent contract stays pure
and never imports `ChatAdvisor` directly.

See:
- [`docs/contracts/model-streaming.v1.yaml`](contracts/model-streaming.v1.yaml) (ADR-0129)
- [`docs/contracts/structured-output.v1.yaml`](contracts/structured-output.v1.yaml) (ADR-0130)
- [`docs/contracts/prompt-template.v1.yaml`](contracts/prompt-template.v1.yaml) (ADR-0131)
- [`docs/contracts/chat-advisor.v1.yaml`](contracts/chat-advisor.v1.yaml) (ADR-0132)
- ADR-0129 through ADR-0135 under `docs/adr/`.

## 5. Switch posture

Set `APP_POSTURE=research` or `prod` and re-run. Now:

- `IdempotencyStore` must be a durable bean (otherwise startup throws).
- `IdempotencyHeaderFilter` rejects missing `Idempotency-Key` headers on
  POST/PUT/PATCH.
- The in-memory `SyncOrchestrator` refuses to construct (use a durable
  alternative wired by your own `@Configuration`).

See [`docs/governance/posture-coverage.md`](governance/posture-coverage.md)
for the full matrix.

## 6. Where to go next

- Architecture and SPI surface: [`ARCHITECTURE.md`](../architecture/docs/L0/ARCHITECTURE.md).
- HTTP contract surface: [`docs/contracts/`](contracts/).
- Engineering rules you must honour: [`CLAUDE.md`](../CLAUDE.md).
- DFX coverage per module: [`docs/dfx/`](dfx/).
- Module metadata (kind / version / semver): each module's
  `module-metadata.yaml`.

## 7. When a test fails

Before you start reasoning about the failure, run the six-step **Evidence-First
Debug Sequence** in [`docs/harness/debug-first-evidence.md`](harness/debug-first-evidence.md).
Authority: CLAUDE.md Rule D-3 (Evidence-First Debug). The runbook tells you what to capture (failing
FQN → trace ID → MDC slice → raw error → transition history) BEFORE you open
`ARCHITECTURE.md`. Spec reading is allowed in step 6, after evidence is recorded.

For library-mode pure-JUnit tests (`./mvnw -pl agent-runtime test`),
the engine SPI module runs in under 2 seconds. Use this loop when you
want sub-second feedback on the SPI value-type algebra. The engine SPI
lives in `agent-runtime`; the run + idempotency entities are an `agent-service`
design target (not yet implemented); the server-to-client transport SPI lives in
`agent-bus`.

If anything in this quickstart requires modifying platform source to make it
work — file an issue tagged `decoupling-defect`. Rule R-A says: developers
build agents against the platform, not into the platform.
