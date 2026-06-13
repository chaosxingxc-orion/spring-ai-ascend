package com.huawei.ascend.examples.runtime.middleware.memory.inmemory;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.ArrayList;
import java.util.Iterator;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemoryInMemoryExampleTest {

    @Test
    void inMemoryMemoryProviderWorksThroughOpenJiuwenHandlerExecution() {
        InMemoryMemoryProvider provider = new InMemoryMemoryProvider();
        AgentExecutionContext first = MiddlewareTestFixtures.context("memory-state-a");
        AgentExecutionContext second = MiddlewareTestFixtures.context("memory-state-b");

        provider.init(first);
        provider.init(second);
        provider.save(first, List.of(new MemoryProvider.MemoryRecord(null, "assistant",
                "the user prefers green tea", Map.of("source", "test"))));
        provider.save(second, List.of(new MemoryProvider.MemoryRecord(null, "assistant",
                "the user prefers black coffee", Map.of("source", "test"))));

        MemoryEnabledHandler handler = new MemoryEnabledHandler(provider);

        List<?> rawResults = handler.execute(first).toList();

        assertThat(rawResults).singleElement().isEqualTo(Map.of("result_type", "answer", "output", "pong"));
        assertThat(handler.agent.registeredRails).hasSize(1);
        assertThat(provider.search(first, "black coffee", 3)).isEmpty();
        assertThat(provider.records(first))
                .extracting(MemoryProvider.MemoryRecord::content)
                .contains("the user prefers green tea", "green tea", "pong");
        assertThat(provider.search(first, "green tea", 3))
                .first()
                .satisfies(hit -> assertThat(hit.content()).contains("green tea"));
    }

    private static final class MemoryEnabledHandler extends OpenJiuwenAgentRuntimeHandler {
        private final InMemoryMemoryProvider provider;
        private final RecordingAgent agent = new RecordingAgent();

        private MemoryEnabledHandler(InMemoryMemoryProvider provider) {
            super("openjiuwen-simple-agent");
            this.provider = provider;
        }

        @Override
        protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
            return List.of(memoryRuntimeRail(context, provider));
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            return agent;
        }

        @Override
        protected Object runOpenJiuwenAgent(BaseAgent agent, Object input, String conversationId) {
            RecordingModelContext modelContext = new RecordingModelContext();
            modelContext.setMessages(List.of(
                    new SystemMessage("business policy: keep original system prompt"),
                    new UserMessage("green tea"),
                    new AssistantMessage("pong")), true);
            AgentCallbackContext callbackContext = AgentCallbackContext.builder()
                    .context(modelContext)
                    .build();
            for (AgentRail rail : ((RecordingAgent) agent).registeredRails) {
                rail.beforeInvoke(callbackContext);
                rail.afterInvoke(callbackContext);
            }
            return Map.of("result_type", "answer", "output", "pong");
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
}
