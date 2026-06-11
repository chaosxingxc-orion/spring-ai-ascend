package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

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

    private static AgentExecutionContext context(Map<String, Object> variables) {
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("ping")))
                .build();
        return new AgentExecutionContext(new RuntimeIdentity("tenant", "user", "session", "task", "agent"),
                "USER_MESSAGE", List.of(message), variables);
    }

    private static class TestOpenJiuwenHandler extends OpenJiuwenAgentRuntimeHandler {
        private final RecordingAgent agent = new RecordingAgent();
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
