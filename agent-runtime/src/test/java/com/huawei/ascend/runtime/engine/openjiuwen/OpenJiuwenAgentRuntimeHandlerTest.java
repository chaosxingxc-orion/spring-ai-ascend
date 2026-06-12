package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import com.huawei.ascend.runtime.engine.spi.TrajectorySink;
import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.ExternalMemoryRail;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class OpenJiuwenAgentRuntimeHandlerTest {

    @Test
    void executeUsesStableAgentStateConversationIdWithoutDefaultRail() {
        TestOpenJiuwenHandler handler = new TestOpenJiuwenHandler();
        AgentExecutionContext context = context(Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "order-42"));

        List<?> rawResults = handler.execute(context).toList();

        assertThat(rawResults).isEqualTo(List.of(Map.of("result_type", "answer", "output", "pong")));
        assertThat(handler.agent.registeredRails).isEmpty();
        assertThat(handler.capturedConversationId).isEqualTo("order-42");
        assertThat(handler.capturedInput)
                .containsEntry("query", "ping")
                .containsEntry("conversation_id", "order-42");
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
                .satisfies(message -> assertThat(message.getContentAsString()).contains("remembered ping"));
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
    void memoryRuntimeRailDoesNotSaveSystemPromptBackToMemory() {
        AgentExecutionContext context = context(Map.of());
        FakeMemoryProvider memoryProvider = new FakeMemoryProvider();
        OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail rail =
                new OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail(
                        context, memoryProvider, new OpenJiuwenMemoryMessageAdapter());
        RecordingModelContext modelContext = new RecordingModelContext();
        modelContext.setMessages(List.of(new SystemMessage("runtime memory"), new UserMessage("hello")), true);

        rail.afterInvoke(AgentCallbackContext.builder().context(modelContext).build());

        assertThat(memoryProvider.savedRecords)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.role()).isEqualTo("user");
                    assertThat(record.content()).isEqualTo("hello");
                });
    }

    @Test
    void openJiuwenExternalMemoryProviderAdapterDelegatesToRuntimeMemoryProvider() {
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
    void openJiuwenExternalMemoryRailUsesNativeOpenJiuwenRailType() {
        TestOpenJiuwenHandler handler = new TestOpenJiuwenHandler();

        AgentRail rail = handler.nativeMemoryRail(context(Map.of()), new FakeMemoryProvider());

        assertThat(rail).isInstanceOf(ExternalMemoryRail.class);
    }

    @Test
    void executeMapsOpenJiuwenFailuresToErrorResultMap() {
        FailingOpenJiuwenHandler handler = new FailingOpenJiuwenHandler();

        List<?> rawResults = handler.execute(context(Map.of())).toList();

        assertThat(rawResults).isEqualTo(List.of(Map.of("result_type", "error", "output", "boom")));
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
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("ping")))
                .build();
        return new AgentExecutionContext(new RuntimeIdentity("tenant", "user", "session", "task", "agent"),
                "USER_MESSAGE", List.of(message), variables);
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

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            return agent;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Object runOpenJiuwenAgent(BaseAgent agent, Object input, String conversationId) {
            capturedInput = (Map<String, Object>) input;
            capturedConversationId = conversationId;
            return Map.of("result_type", "answer", "output", "pong");
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
        protected Object runOpenJiuwenAgent(BaseAgent agent, Object input, String conversationId) {
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
        protected Object runOpenJiuwenAgent(BaseAgent agent, Object input, String conversationId) {
            return Stream.of("first", "second");
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
        protected Object runOpenJiuwenAgent(BaseAgent agent, Object input, String conversationId) {
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
            return Map.of("result_type", "answer", "output", "pong");
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
        protected Object runOpenJiuwenAgent(BaseAgent agent, Object input, String conversationId) {
            installedBeforeRun = runtimeToolInstalled;
            return super.runOpenJiuwenAgent(agent, input, conversationId);
        }
    }

    private static final class FakeMemoryProvider implements MemoryProvider {
        private boolean initialized;
        private String searchedQuery;
        private List<MemoryRecord> savedRecords = List.of();
        private Double score = 0.9;

        @Override
        public void init(AgentExecutionContext context) {
            initialized = true;
        }

        @Override
        public List<MemoryHit> search(AgentExecutionContext context, String query, int limit) {
            searchedQuery = query;
            return List.of(new MemoryHit("m1", "remembered " + query, score, Map.of()));
        }

        @Override
        public void save(AgentExecutionContext context, List<MemoryRecord> records) {
            savedRecords = records;
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
            return List.of().iterator();
        }
    }
}
