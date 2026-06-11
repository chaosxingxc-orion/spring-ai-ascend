package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.huawei.ascend.runtime.engine.spi.TrajectoryChannel;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import com.huawei.ascend.runtime.engine.spi.TrajectoryLevel;
import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
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
    void executeMapsOpenJiuwenFailuresToErrorResultMap() {
        FailingOpenJiuwenHandler handler = new FailingOpenJiuwenHandler();

        List<?> rawResults = handler.execute(context(Map.of())).toList();

        assertThat(rawResults).isEqualTo(List.of(Map.of("result_type", "error", "output", "boom")));
    }

    @Test
    @Timeout(10)
    void fullLevelEmitsOpenJiuwenModelCallTrajectory() throws Exception {
        List<TrajectoryEvent> events = runWithTrajectory(new ModelCallingHandler(), TrajectoryLevel.FULL);

        assertThat(events).extracting(TrajectoryEvent::kind)
                .contains(Kind.MODEL_CALL_START, Kind.MODEL_CALL_END);
    }

    @Test
    @Timeout(10)
    void summaryLevelDropsOpenJiuwenModelCalls() throws Exception {
        List<TrajectoryEvent> events = runWithTrajectory(new ModelCallingHandler(), TrajectoryLevel.SUMMARY);

        assertThat(events).extracting(TrajectoryEvent::kind)
                .contains(Kind.RUN_START, Kind.RUN_END)
                .doesNotContain(Kind.MODEL_CALL_START, Kind.MODEL_CALL_END);
    }

    @Test
    @Timeout(10)
    void runLevelFailureEmitsErrorTrajectoryEvent() throws Exception {
        // A run-level openJiuwen failure is mapped to an error result (not rethrown); it must still
        // surface a mandatory ERROR trajectory event rather than a silently truncated trajectory.
        List<TrajectoryEvent> events = runWithTrajectory(new FailingOpenJiuwenHandler(), TrajectoryLevel.SUMMARY);

        TrajectoryEvent error = events.stream()
                .filter(e -> e.kind() == Kind.ERROR).findFirst().orElseThrow();
        assertThat(error.error().code()).isEqualTo("OPENJIUWEN_RUN_ERROR");
        assertThat(error.error().message()).contains("boom");
    }

    /** Opens a trajectory channel at the given level, runs the handler, and returns the drained events. */
    private static List<TrajectoryEvent> runWithTrajectory(OpenJiuwenAgentRuntimeHandler handler,
            TrajectoryLevel level) throws InterruptedException {
        AgentExecutionContext context = context(Map.of());
        TrajectorySettings settings = new TrajectorySettings(
                level, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        TrajectoryChannel channel = handler.openTrajectory(context, settings);
        List<TrajectoryEvent> events = new CopyOnWriteArrayList<>();
        Thread drainer = new Thread(() -> channel.drain().forEach(events::add));
        drainer.start();
        try (Stream<?> raw = handler.execute(context)) {
            raw.forEach(x -> { });
        }
        drainer.join(3000);
        return new ArrayList<>(events);
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
