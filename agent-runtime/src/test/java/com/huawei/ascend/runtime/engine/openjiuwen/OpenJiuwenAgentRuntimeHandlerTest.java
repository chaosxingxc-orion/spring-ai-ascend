package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.huawei.ascend.runtime.engine.spi.McpProvider;
import com.huawei.ascend.runtime.engine.spi.McpToolResult;
import com.huawei.ascend.runtime.engine.spi.McpToolSpec;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import com.huawei.ascend.runtime.engine.spi.TrajectorySink;
import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.interrupt.ToolCallInterruptRequest;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.ExternalMemoryRail;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class OpenJiuwenAgentRuntimeHandlerTest {
    private static final String MEMORY_MODEL_PROVIDER = "RuntimeMemoryRailPromptBuilderTestProvider";
    private static final AtomicBoolean MEMORY_MODEL_FACTORY_REGISTERED = new AtomicBoolean(false);

    @Test
    void executeUsesStableAgentStateConversationIdWithoutDefaultRail() {
        TestOpenJiuwenHandler handler = new TestOpenJiuwenHandler();
        AgentExecutionContext context = context(Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "order-42"));

        List<Object> rawResults = handler.execute(context).map(Object.class::cast).toList();

        assertThat(rawResults).containsExactly(
                new OutputSchema("llm_output", 0, Map.of("result_type", "answer", "content", "pong")),
                new OutputSchema("answer", 0, Map.of("result_type", "answer", "output", "done")));
        assertThat(handler.agent.registeredRails).isEmpty();
        assertThat(handler.capturedConversationId).isEqualTo("order-42");
        assertThat(handler.capturedInput)
                .containsEntry("query", "ping")
                .containsEntry("conversation_id", "order-42");
        assertThat(handler.agent.streamModes).containsExactly(StreamMode.OUTPUT);
    }

    @Test
    void executeInstallsOpenJiuwenRailsWhenSubclassOptsIn() {
        AgentRail rail = new AgentRail() {
        };
        TestOpenJiuwenHandler handler = new RailOpenJiuwenHandler(rail);

        handler.execute(context(Map.of())).toList();

        assertThat(handler.agent.registeredRails).containsExactly(rail);
    }

    @Test
    void executeInstallsRuntimeToolsAfterOpenJiuwenRailsBeforeRunningAgent() {
        AgentRail frameworkRail = new AgentRail() {
        };
        AgentRail runtimeRail = new AgentRail() {
        };
        RuntimeToolOpenJiuwenHandler handler = new RuntimeToolOpenJiuwenHandler(frameworkRail, runtimeRail);

        handler.execute(context(Map.of())).toList();

        assertThat(handler.agent.registeredRails).containsExactly(frameworkRail, runtimeRail);
        assertThat(handler.runtimeToolInstalled).isTrue();
        assertThat(handler.installedBeforeRun).isTrue();
    }

    @Test
    void executeInstallsConfiguredMcpToolInstaller() {
        TestOpenJiuwenHandler handler = new TestOpenJiuwenHandler();
        FakeMcpProvider provider = new FakeMcpProvider();
        handler.setMcpToolInstaller(new OpenJiuwenMcpToolInstaller(provider));

        handler.execute(context(Map.of())).toList();

        assertThat(provider.listed).isTrue();
        assertThat(handler.agent.getAbilityManager().get("get_current_time")).isNotNull();
    }

    @Test
    void memoryMessageAdapterConvertsOpenJiuwenMessagesBothWays() {
        OpenJiuwenMemoryMessageAdapter adapter = new OpenJiuwenMemoryMessageAdapter();

        List<MemoryProvider.MemoryRecord> records = adapter.toMemoryRecords(List.of(
                new SystemMessage("system prompt", "system-name"),
                new UserMessage("hello"),
                new AssistantMessage("hi"),
                new ToolMessage("tool result", "tool-call-1", "tool-name")));

        assertThat(records)
                .extracting(MemoryProvider.MemoryRecord::role)
                .containsExactly("system", "user", "assistant", "tool");
        assertThat(records.get(0).metadata()).containsEntry(OpenJiuwenMemoryMessageAdapter.METADATA_NAME, "system-name");
        assertThat(records.get(3).metadata())
                .containsEntry(OpenJiuwenMemoryMessageAdapter.METADATA_TOOL_CALL_ID, "tool-call-1")
                .containsEntry(OpenJiuwenMemoryMessageAdapter.METADATA_NAME, "tool-name");

        BaseMessage restored = adapter.toOpenJiuwenMessage(records.get(0));

        assertThat(restored).isInstanceOf(SystemMessage.class);
        assertThat(restored.getContentAsString()).isEqualTo("system prompt");
        assertThat(restored.getName()).isEqualTo("system-name");
    }

    @Test
    void memoryRuntimeRailInjectsSearchResultsIntoOpenJiuwenContext() {
        AgentExecutionContext context = context(Map.of());
        FakeMemoryProvider memoryProvider = new FakeMemoryProvider();
        OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail rail =
                new OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail(
                        context, memoryProvider, new OpenJiuwenMemoryMessageAdapter());
        RecordingModelContext modelContext = new RecordingModelContext();

        rail.beforeInvoke(AgentCallbackContext.builder().context(modelContext).build());

        assertThat(memoryProvider.initialized).isTrue();
        assertThat(memoryProvider.searchedQuery).isEqualTo("ping");
        assertThat(modelContext.messages)
                .singleElement()
                .satisfies(message -> assertThat(message.getContentAsString())
                        .contains("remembered ping")
                        .doesNotContain("runtime-memory-context"));
    }

    @Test
    void memoryRuntimeRailMergesSearchResultsIntoExistingSystemMessage() {
        AgentExecutionContext context = context(Map.of());
        FakeMemoryProvider memoryProvider = new FakeMemoryProvider();
        OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail rail =
                new OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail(
                        context, memoryProvider, new OpenJiuwenMemoryMessageAdapter());
        RecordingModelContext modelContext = new RecordingModelContext();
        modelContext.setMessages(List.of(new SystemMessage("existing system prompt")), true);

        rail.beforeInvoke(AgentCallbackContext.builder().context(modelContext).build());

        assertThat(modelContext.messages)
                .singleElement()
                .satisfies(message -> assertThat(message.getContentAsString())
                        .contains("existing system prompt")
                        .contains("remembered ping"));
    }

    @Test
    void memoryRuntimeRailInjectsSearchResultsIntoRealReActPromptBuilder() {
        AgentExecutionContext context = context(Map.of());
        FakeMemoryProvider memoryProvider = new FakeMemoryProvider();
        OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail rail =
                new OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail(
                        context, memoryProvider, new OpenJiuwenMemoryMessageAdapter());
        ReActAgent reactAgent = new ReActAgent(
                AgentCard.builder().id("agent").name("agent").description("test").build());
        RecordingModelContext modelContext = new RecordingModelContext();

        rail.beforeInvoke(AgentCallbackContext.builder()
                .agent(reactAgent)
                .context(modelContext)
                .build());

        assertThat(reactAgent.getPromptBuilder().build())
                .contains("remembered ping")
                .contains("recalled memory context from runtime memory");
        assertThat(modelContext.messages).isEmpty();
    }

    @Test
    void memoryRuntimeRailReachesRealReActAgentModelInput() {
        ensureMemoryModelFactoryRegistered();
        CapturingModelClient.capturedMessages.clear();
        CapturingModelClient.capturedRawMessages = null;
        AgentExecutionContext context = context(Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "order-42"));
        FakeMemoryProvider memoryProvider = new FakeMemoryProvider();
        OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail rail =
                new OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail(
                        context, memoryProvider, new OpenJiuwenMemoryMessageAdapter());
        ReActAgent reactAgent = new ReActAgent(
                AgentCard.builder().id("agent").name("agent").description("test").build());
        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", "base system prompt")))
                .maxIterations(1)
                .build()
                .configureModelClient(MEMORY_MODEL_PROVIDER, "key", "http://localhost", "fake-model", false);
        reactAgent.configure(config);
        reactAgent.registerRail(rail);

        Iterator<Object> output = reactAgent.stream(
                Map.of("query", "ping", "conversation_id", "order-42"),
                AgentSessionApi.create("order-42", null, reactAgent.getCard()),
                List.of(StreamMode.OUTPUT));
        output.forEachRemaining(ignored -> { });

        assertThat(CapturingModelClient.capturedRawMessages).isNotNull();
        assertThat(CapturingModelClient.capturedMessages).isNotEmpty();
        assertThat(CapturingModelClient.capturedMessages)
                .anySatisfy(message -> assertThat(message.getContentAsString())
                        .contains("base system prompt")
                        .contains("remembered ping")
                        .contains("recalled memory context from runtime memory")
                        .doesNotContain("runtime_long_term_memory"));
    }

    @Test
    void memoryRuntimeRailReachesModelInputThroughRunnerRunAgent() {
        ensureMemoryModelFactoryRegistered();
        CapturingModelClient.capturedMessages.clear();
        CapturingModelClient.capturedRawMessages = null;
        String conversationId = "runner-memory-" + System.nanoTime();
        RunnerMemoryOpenJiuwenHandler handler = new RunnerMemoryOpenJiuwenHandler(new FakeMemoryProvider());

        List<?> rawResults = handler
                .execute(context(Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, conversationId)))
                .toList();

        assertThat(rawResults).isNotEmpty();
        assertThat(CapturingModelClient.capturedRawMessages).isNotNull();
        assertThat(CapturingModelClient.capturedMessages).isNotEmpty();
        assertThat(CapturingModelClient.capturedMessages)
                .anySatisfy(message -> assertThat(message.getContentAsString())
                        .contains("HOTEL_BUSINESS_RULES_MARKER")
                        .contains("remembered ping")
                        .contains("recalled memory context from runtime memory")
                        .doesNotContain("runtime_long_term_memory"));
    }

    @Test
    void memoryRuntimeRailClearsRealReActPromptBuilderWhenNoMemoryHits() {
        AgentExecutionContext context = context(Map.of());
        FakeMemoryProvider memoryProvider = new FakeMemoryProvider();
        OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail rail =
                new OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail(
                        context, memoryProvider, new OpenJiuwenMemoryMessageAdapter());
        ReActAgent reactAgent = new ReActAgent(
                AgentCard.builder().id("agent").name("agent").description("test").build());
        AgentCallbackContext callbackContext = AgentCallbackContext.builder().agent(reactAgent).build();

        rail.beforeInvoke(callbackContext);
        memoryProvider.returnHits = false;
        rail.beforeInvoke(callbackContext);

        assertThat(reactAgent.getPromptBuilder().build()).doesNotContain("remembered ping");
    }

    @Test
    void memoryRuntimeRailAcceptsHitsWithoutScore() {
        AgentExecutionContext context = context(Map.of());
        FakeMemoryProvider memoryProvider = new FakeMemoryProvider();
        memoryProvider.score = null;
        OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail rail =
                new OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail(
                        context, memoryProvider, new OpenJiuwenMemoryMessageAdapter());
        RecordingModelContext modelContext = new RecordingModelContext();

        rail.beforeInvoke(AgentCallbackContext.builder().context(modelContext).build());

        assertThat(modelContext.messages)
                .singleElement()
                .satisfies(message -> assertThat(message.getContentAsString()).contains("remembered ping"));
    }

    @Test
    void memoryRuntimeRailDoesNotSaveInjectedRuntimeMemoryBackToMemory() {
        AgentExecutionContext context = context(Map.of());
        FakeMemoryProvider memoryProvider = new FakeMemoryProvider();
        OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail rail =
                new OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail(
                        context, memoryProvider, new OpenJiuwenMemoryMessageAdapter());
        RecordingModelContext modelContext = new RecordingModelContext();
        rail.beforeInvoke(AgentCallbackContext.builder().context(modelContext).build());
        modelContext.messages.add(new UserMessage("hello"));
        modelContext.messages.add(new AssistantMessage("world"));

        rail.afterInvoke(AgentCallbackContext.builder().context(modelContext).build());

        assertThat(memoryProvider.savedRecords)
                .extracting(MemoryProvider.MemoryRecord::role)
                .containsExactly("user", "assistant");
        assertThat(memoryProvider.savedRecords)
                .extracting(MemoryProvider.MemoryRecord::content)
                .containsExactly("hello", "world");
    }

    @Test
    void memoryRuntimeRailDoesNotSaveSystemPromptToLongTermMemory() {
        AgentExecutionContext context = context(Map.of());
        FakeMemoryProvider memoryProvider = new FakeMemoryProvider();
        OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail rail =
                new OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail(
                        context, memoryProvider, new OpenJiuwenMemoryMessageAdapter());
        RecordingModelContext modelContext = new RecordingModelContext();
        modelContext.setMessages(List.of(new SystemMessage("business policy: keep order status")), true);

        rail.beforeInvoke(AgentCallbackContext.builder().context(modelContext).build());
        rail.afterInvoke(AgentCallbackContext.builder().context(modelContext).build());

        assertThat(memoryProvider.savedRecords).isEmpty();
    }

    @Test
    void openJiuwenExternalMemoryProviderAdapterDelegatesToRuntimeMemoryProvider() throws Exception {
        AgentExecutionContext context = context(Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "order-42"));
        FakeMemoryProvider memoryProvider = new FakeMemoryProvider();
        OpenJiuwenExternalMemoryProviderAdapter adapter =
                new OpenJiuwenExternalMemoryProviderAdapter(context, memoryProvider);

        adapter.initialize(Map.of("scope_id", "order-42"));
        String prefetch = adapter.prefetch("ping", Map.of("scope_id", "order-42"));
        adapter.syncTurn("hello", "world", Map.of("scope_id", "order-42"));

        assertThat(adapter.getName()).isEqualTo("runtime_memory");
        assertThat(adapter.isInitialized()).isTrue();
        assertThat(memoryProvider.initialized).isTrue();
        assertThat(memoryProvider.searchedQuery).isEqualTo("ping");
        assertThat(prefetch).contains("remembered ping");
        assertThat(memoryProvider.savedRecords)
                .extracting(MemoryProvider.MemoryRecord::role)
                .containsExactly("user", "assistant");
        assertThat(memoryProvider.savedRecords)
                .extracting(MemoryProvider.MemoryRecord::content)
                .containsExactly("hello", "world");
    }

    @Test
    void openJiuwenLLMMemoryRailCreatesKVCacheFriendlyRail() {
        TestOpenJiuwenHandler handler = new TestOpenJiuwenHandler();

        AgentRail rail = handler.llmMemoryRail(context(Map.of()), new FakeMemoryProvider());

        assertThat(rail).isInstanceOf(OpenJiuwenLLMMemoryRail.class);
    }

    @Test
    void openJiuwenExternalMemoryRailUsesNativeOpenJiuwenRailType() {
        TestOpenJiuwenHandler handler = new TestOpenJiuwenHandler();

        AgentRail rail = handler.nativeMemoryRail(context(Map.of()), new FakeMemoryProvider());

        assertThat(rail).isInstanceOf(ExternalMemoryRail.class);
    }

    @Test
    void executeMapsOpenJiuwenFailuresToFailedResult() {
        FailingOpenJiuwenHandler handler = new FailingOpenJiuwenHandler();

        List<?> rawResults = handler.execute(context(Map.of())).toList();

        assertThat(rawResults).singleElement()
                .isInstanceOfSatisfying(AgentExecutionResult.class, result -> {
                    assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.FAILED);
                    assertThat(result.errorCode()).isEqualTo("OPENJIUWEN_RUN_ERROR");
                    assertThat(result.errorMessage()).isEqualTo("boom");
                });
    }

    @Test
    void enabledEmitsOpenJiuwenModelCallTrajectory() {
        List<TrajectoryEvent> events = runWithTrajectory(new ModelCallingHandler());

        assertThat(events).extracting(TrajectoryEvent::kind)
                .contains(Kind.MODEL_CALL_START, Kind.MODEL_CALL_END);
    }

    @Test
    void runLevelFailureEmitsErrorTrajectoryEvent() {
        // A run-level openJiuwen failure is mapped to an error result (not rethrown); it must still
        // surface a mandatory ERROR trajectory event rather than a silently truncated trajectory.
        List<TrajectoryEvent> events = runWithTrajectory(new FailingOpenJiuwenHandler());

        TrajectoryEvent error = events.stream()
                .filter(e -> e.kind() == Kind.ERROR).findFirst().orElseThrow();
        assertThat(error.error().code()).isEqualTo("OPENJIUWEN_RUN_ERROR");
        assertThat(error.error().message()).contains("boom");
    }

    /** Opens the trajectory with a synchronous capturing sink, runs the handler, returns the events. */
    private static List<TrajectoryEvent> runWithTrajectory(OpenJiuwenAgentRuntimeHandler handler) {
        AgentExecutionContext context = context(Map.of());
        TrajectorySettings settings = new TrajectorySettings(
                true, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        List<TrajectoryEvent> events = new ArrayList<>();
        handler.openTrajectory(context, settings, (TrajectorySink) events::add);
        try (Stream<?> raw = handler.execute(context)) {
            raw.forEach(x -> { });
        }
        return events;
    }

    @Test
    void executeFlattensStreamReturnedByOpenJiuwenRunner() {
        StreamingOpenJiuwenHandler handler = new StreamingOpenJiuwenHandler();

        List<?> rawResults = handler.execute(context(Map.of())).toList();

        assertThat(rawResults).isEqualTo(List.of("first", "second"));
    }

    @Test
    void executeMarksIteratorBackedStreamsAsOrdered() {
        StreamingOpenJiuwenHandler handler = new StreamingOpenJiuwenHandler();

        try (Stream<?> rawResults = handler.execute(context(Map.of()))) {
            assertThat(rawResults.spliterator().hasCharacteristics(Spliterator.ORDERED)).isTrue();
        }
    }

    @Test
    void resultAdapterMapsOpenJiuwenStreamingOutputChunks() {
        TestOpenJiuwenHandler handler = new TestOpenJiuwenHandler();

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(Stream.of(
                new OutputSchema("llm_output", 0, Map.of("result_type", "answer", "content", "first ")),
                new OutputSchema("llm_output", 1, Map.of("result_type", "answer", "content", "second")),
                new OutputSchema("answer", 0, Map.of("result_type", "answer", "output", "final")))).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(
                        AgentExecutionResult.Type.OUTPUT,
                        AgentExecutionResult.Type.OUTPUT,
                        AgentExecutionResult.Type.COMPLETED);
        assertThat(results).extracting(AgentExecutionResult::outputContent)
                .containsExactly("first ", "second", "final");
    }

    @Test
    void resultAdapterMapsScalarAnswerChunkAsCompletion() {
        TestOpenJiuwenHandler handler = new TestOpenJiuwenHandler();

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(Stream.of(
                new OutputSchema("answer", 0, "done"))).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.COMPLETED);
        assertThat(results).extracting(AgentExecutionResult::outputContent)
                .containsExactly("done");
    }

    @Test
    void resultAdapterMapsOpenJiuwenStreamingErrorsAndInteractions() {
        TestOpenJiuwenHandler handler = new TestOpenJiuwenHandler();

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(Stream.of(
                new OutputSchema("llm_usage", 0, Map.of("tokens", 1)),
                new OutputSchema("error", 0, Map.of("result_type", "error", "output", "boom")),
                new OutputSchema("__interaction__", 0, new InteractionOutput("question", "need input")))).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.FAILED, AgentExecutionResult.Type.INTERRUPTED);
        assertThat(results.get(0).errorMessage()).isEqualTo("boom");
        assertThat(results.get(1).prompt()).isEqualTo("need input");
    }

    @Test
    void resultAdapterMapsOpenJiuwenInteractionMarkerToRemoteInvocation() {
        TestOpenJiuwenHandler handler = new TestOpenJiuwenHandler();
        ToolCallInterruptRequest request = remoteInterruptRequest();

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(Stream.of(
                new OutputSchema("__interaction__", 0,
                        new InteractionOutput("tool-call-1", request)))).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.INTERRUPTED);
        assertThat(results.get(0).remoteInvocation().remoteAgentId()).isEqualTo("remote-agent");
        assertThat(results.get(0).remoteInvocation().arguments()).containsEntry("remoteInput", "hello remote");
    }

    @Test
    void resultAdapterMapsPlainInterruptRequestToPromptMessage() {
        TestOpenJiuwenHandler handler = new TestOpenJiuwenHandler();
        ToolCallInterruptRequest request = new ToolCallInterruptRequest();
        request.setMessage("need approval");

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(Stream.of(
                new OutputSchema("__interaction__", 0,
                        new InteractionOutput("tool-call-1", request)))).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.INTERRUPTED);
        assertThat(results.get(0).prompt()).isEqualTo("need approval");
    }

    @Test
    void resultAdapterFailsUnsupportedInteractionPayloads() {
        TestOpenJiuwenHandler handler = new TestOpenJiuwenHandler();

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(Stream.of(
                new OutputSchema("__interaction__", 0,
                        new InteractionOutput("tool-call-1", Map.of("unexpected", true))))).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.FAILED);
        assertThat(results.get(0).errorMessage()).contains("Unsupported openjiuwen interaction payload");
    }

    @Test
    void resultAdapterPassesThroughAgentExecutionResult() {
        TestOpenJiuwenHandler handler = new TestOpenJiuwenHandler();

        List<AgentExecutionResult> results = handler.resultAdapter()
                .adapt(Stream.of(AgentExecutionResult.output("part"), AgentExecutionResult.completed("done")))
                .toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.OUTPUT, AgentExecutionResult.Type.COMPLETED);
        assertThat(results).extracting(AgentExecutionResult::outputContent)
                .containsExactly("part", "done");
    }

    private static AgentExecutionContext context(Map<String, Object> variables) {
        return new AgentExecutionContext(new RuntimeIdentity("tenant", "user", "session", "task", "agent"),
                "USER_MESSAGE", List.of(RuntimeMessage.user("ping")), variables);
    }

    private static ToolCallInterruptRequest remoteInterruptRequest() {
        ToolCallInterruptRequest request = new ToolCallInterruptRequest();
        request.setInterruptId("tool-call-1");
        request.setToolCallId("tool-call-1");
        request.setToolName("remote-agent");
        request.setContext(Map.of(
                "runtime.remote.kind", "REMOTE_AGENT_INVOCATION",
                "runtime.remote.agentId", "remote-agent",
                "runtime.remote.toolName", "remote-agent",
                "runtime.remote.toolCallId", "tool-call-1",
                "runtime.remote.parentTaskId", "task-1",
                "runtime.remote.parentContextId", "ctx-1",
                "runtime.remote.localConversationId", "conversation-1",
                "runtime.remote.arguments", Map.of("remoteInput", "hello remote")));
        return request;
    }

    private static void ensureMemoryModelFactoryRegistered() {
        if (MEMORY_MODEL_FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new Model.ModelClientFactory() {
                @Override
                public String providerName() {
                    return MEMORY_MODEL_PROVIDER;
                }

                @Override
                public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                    return new CapturingModelClient(modelConfig, clientConfig);
                }
            });
        }
    }

    private static class TestOpenJiuwenHandler extends OpenJiuwenAgentRuntimeHandler {
        protected final RecordingAgent agent = new RecordingAgent();
        private Map<String, Object> capturedInput;
        private String capturedConversationId;

        private TestOpenJiuwenHandler() {
            super("agent");
        }

        private AgentRail nativeMemoryRail(AgentExecutionContext context, MemoryProvider memoryProvider) {
            return openJiuwenExternalMemoryRail(context, memoryProvider);
        }

        private OpenJiuwenLLMMemoryRail llmMemoryRail(AgentExecutionContext context, MemoryProvider memoryProvider) {
            return openJiuwenLLMMemoryRail(context, memoryProvider);
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            return agent;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Iterator<Object> runOpenJiuwenAgentStreaming(BaseAgent agent, Object input, String conversationId,
                List<StreamMode> streamModes) {
            capturedInput = (Map<String, Object>) input;
            capturedConversationId = conversationId;
            return agent.stream(input, new AgentSessionApi(conversationId), streamModes);
        }
    }

    private static final class FailingOpenJiuwenHandler extends OpenJiuwenAgentRuntimeHandler {
        private FailingOpenJiuwenHandler() {
            super("agent");
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            return new RecordingAgent();
        }

        @Override
        protected Iterator<Object> runOpenJiuwenAgentStreaming(BaseAgent agent, Object input, String conversationId,
                List<StreamMode> streamModes) {
            throw new IllegalStateException("boom");
        }
    }

    private static final class StreamingOpenJiuwenHandler extends OpenJiuwenAgentRuntimeHandler {
        private StreamingOpenJiuwenHandler() {
            super("agent");
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            return new RecordingAgent();
        }

        @Override
        protected Iterator<Object> runOpenJiuwenAgentStreaming(BaseAgent agent, Object input, String conversationId,
                List<StreamMode> streamModes) {
            return List.<Object>of("first", "second").iterator();
        }
    }

    private static final class RailOpenJiuwenHandler extends TestOpenJiuwenHandler {
        private final AgentRail rail;

        private RailOpenJiuwenHandler(AgentRail rail) {
            this.rail = rail;
        }

        @Override
        protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
            return List.of(rail);
        }
    }

    /** Fires openJiuwen's native model-call callbacks mid-run through the registered trajectory rail. */
    private static final class ModelCallingHandler extends TestOpenJiuwenHandler {
        @Override
        protected Iterator<Object> runOpenJiuwenAgentStreaming(BaseAgent agent, Object input, String conversationId,
                List<StreamMode> streamModes) {
            emitModelCallTrajectory(agent);
            return super.runOpenJiuwenAgentStreaming(agent, input, conversationId, streamModes);
        }

        private void emitModelCallTrajectory(BaseAgent agent) {
            for (AgentRail rail : ((RecordingAgent) agent).registeredRails) {
                if (rail instanceof OpenJiuwenTrajectoryRail trajectoryRail) {
                    trajectoryRail.beforeModelCall(AgentCallbackContext.builder()
                            .inputs(ModelCallInputs.builder().messages(List.of("a")).tools(List.of()).build())
                            .build());
                    trajectoryRail.afterModelCall(AgentCallbackContext.builder()
                            .inputs(ModelCallInputs.builder().messages(List.of("a")).response("done").build())
                            .build());
                }
            }
        }
    }

    private static final class RuntimeToolOpenJiuwenHandler extends TestOpenJiuwenHandler {
        private final AgentRail frameworkRail;
        private final AgentRail runtimeRail;
        private boolean runtimeToolInstalled;
        private boolean installedBeforeRun;

        private RuntimeToolOpenJiuwenHandler(AgentRail frameworkRail, AgentRail runtimeRail) {
            this.frameworkRail = frameworkRail;
            this.runtimeRail = runtimeRail;
        }

        @Override
        protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
            return List.of(frameworkRail);
        }

        @Override
        protected void installRuntimeTools(BaseAgent agent, AgentExecutionContext context) {
            runtimeToolInstalled = true;
            agent.registerRail(runtimeRail);
        }

        @Override
        protected Iterator<Object> runOpenJiuwenAgentStreaming(BaseAgent agent, Object input, String conversationId,
                List<StreamMode> streamModes) {
            installedBeforeRun = runtimeToolInstalled;
            return super.runOpenJiuwenAgentStreaming(agent, input, conversationId, streamModes);
        }
    }

    private static final class RunnerMemoryOpenJiuwenHandler extends OpenJiuwenAgentRuntimeHandler {
        private final MemoryProvider memoryProvider;

        private RunnerMemoryOpenJiuwenHandler(MemoryProvider memoryProvider) {
            super("agent");
            this.memoryProvider = memoryProvider;
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            ReActAgent reactAgent = new ReActAgent(
                    AgentCard.builder().id("agent").name("agent").description("test").build());
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .maxIterations(2)
                    .build()
                    .configureModelClient(MEMORY_MODEL_PROVIDER, "key", "http://localhost", "fake-model", false);
            reactAgent.configure(config);
            reactAgent.addPromptBuilderSection("hotel_business_rules", "HOTEL_BUSINESS_RULES_MARKER", 20);
            return reactAgent;
        }

        @Override
        protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
            return List.of(memoryRuntimeRail(context, memoryProvider));
        }
    }

    private static final class FakeMemoryProvider implements MemoryProvider {
        private boolean initialized;
        private String searchedQuery;
        private List<MemoryRecord> savedRecords = List.of();
        private Double score = 0.9;
        private boolean returnHits = true;

        @Override
        public void init(AgentExecutionContext context) {
            initialized = true;
        }

        @Override
        public List<MemoryHit> search(AgentExecutionContext context, String query, int limit) {
            searchedQuery = query;
            if (!returnHits) {
                return List.of();
            }
            return List.of(new MemoryHit("m1", "remembered " + query, score, Map.of()));
        }

        @Override
        public void save(AgentExecutionContext context, List<MemoryRecord> records) {
            savedRecords = records;
        }
    }

    private static final class FakeMcpProvider implements McpProvider {
        private boolean listed;

        @Override
        public List<McpToolSpec> listTools(AgentExecutionContext context) {
            listed = true;
            return List.of(new McpToolSpec(
                    "local-time",
                    "get_current_time",
                    "Current time",
                    "Return current time",
                    Map.of("type", "object", "properties", Map.of()),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of()));
        }

        @Override
        public McpToolResult callTool(AgentExecutionContext context, String serverId, String name,
                Map<String, Object> arguments) {
            return McpToolResult.success(
                    List.of(Map.of("type", "text", "text", "12:00:00")),
                    Map.of("time", "12:00:00"),
                    Map.of(),
                    Map.of("serverId", serverId, "toolName", name));
        }
    }

    private static final class RecordingModelContext extends ModelContext {
        private final List<BaseMessage> messages = new ArrayList<>();

        @Override
        public int size() {
            return messages.size();
        }

        @Override
        public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
            return List.copyOf(messages);
        }

        @Override
        public void setMessages(List<BaseMessage> messages, boolean withHistory) {
            this.messages.clear();
            this.messages.addAll(messages);
        }

        @Override
        public List<BaseMessage> popMessages(int size, boolean withHistory) {
            return List.of();
        }

        @Override
        public void clearMessages(boolean withHistory) {
            messages.clear();
        }

        @Override
        public List<BaseMessage> addMessages(List<BaseMessage> messages) {
            this.messages.addAll(messages);
            return List.copyOf(this.messages);
        }

        @Override
        public ContextWindow getContextWindow(
                List<BaseMessage> systemMessages,
                List<ToolInfo> tools,
                Integer windowSize,
                Integer dialogueRound,
                Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public ContextStats statistic() {
            return null;
        }

        @Override
        public String sessionId() {
            return "test-session";
        }

        @Override
        public String contextId() {
            return "test-context";
        }

        @Override
        public TokenCounter tokenCounter() {
            return null;
        }

        @Override
        public Tool reloaderTool() {
            return null;
        }
    }

    private static final class CapturingModelClient extends BaseModelClient {
        private static final List<BaseMessage> capturedMessages = new CopyOnWriteArrayList<>();
        private static Object capturedRawMessages;

        private CapturingModelClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        public AssistantMessage invoke(
                Object messages,
                Object tools,
                Float temperature,
                Float topP,
                String model,
                Integer maxTokens,
                String stop,
                BaseOutputParser outputParser,
                Float timeout,
                Map<String, Object> kwargs) {
            capturedMessages.clear();
            capturedRawMessages = messages;
            if (messages instanceof List<?> list) {
                list.stream()
                        .filter(BaseMessage.class::isInstance)
                        .map(BaseMessage.class::cast)
                        .forEach(capturedMessages::add);
            }
            return new AssistantMessage("pong");
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(
                Object messages,
                Object tools,
                Float temperature,
                Float topP,
                String model,
                Integer maxTokens,
                String stop,
                BaseOutputParser outputParser,
                Float timeout,
                Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(
                List<UserMessage> messages,
                String model,
                String size,
                String negativePrompt,
                int n,
                boolean promptExtend,
                boolean watermark,
                int seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AudioGenerationResponse generateSpeech(
                List<UserMessage> messages,
                String model,
                String voice,
                String languageType,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VideoGenerationResponse generateVideo(
                List<UserMessage> messages,
                String imgUrl,
                String audioUrl,
                String model,
                String size,
                String resolution,
                int duration,
                boolean promptExtend,
                boolean watermark,
                String negativePrompt,
                Integer seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingAgent extends BaseAgent {
        private final List<AgentRail> registeredRails = new ArrayList<>();

        private RecordingAgent() {
            super(AgentCard.builder().id("agent").name("agent").description("test").build());
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public BaseAgent registerRail(AgentRail rail) {
            registeredRails.add(rail);
            return this;
        }

        @Override
        public Object invoke(Object input, Session session) {
            return null;
        }

        @Override
        public Iterator<Object> stream(Object input, Session session, List<StreamMode> streamModes) {
            this.streamModes = List.copyOf(streamModes);
            return List.<Object>of(
                    new OutputSchema("llm_output", 0, Map.of("result_type", "answer", "content", "pong")),
                    new OutputSchema("answer", 0, Map.of("result_type", "answer", "output", "done")))
                    .iterator();
        }

        private List<StreamMode> streamModes = List.of();
    }
}
