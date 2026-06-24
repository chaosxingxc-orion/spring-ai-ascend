package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
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
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenJiuwenLLMMemoryRailTest {

    @Test
    void beforeInvokeInitializesMemoryProvider() {
        FakeMemoryProvider provider = new FakeMemoryProvider();
        OpenJiuwenLLMMemoryRail rail = rail(provider);

        rail.beforeInvoke(callbackContext(new RecordingModelContext()));

        assertThat(provider.initialized).isTrue();
    }

    @Test
    void beforeInvokeToleratesInitFailure() {
        MemoryProvider provider = new ThrowingMemoryProvider();
        OpenJiuwenLLMMemoryRail rail = rail(provider);

        // Must not throw — graceful degradation is the contract
        rail.beforeInvoke(callbackContext(new RecordingModelContext()));
    }

    @Test
    void beforeModelCallSearchesAndInjectsMemoryViaTailAppend() {
        FakeMemoryProvider provider = new FakeMemoryProvider();
        OpenJiuwenLLMMemoryRail rail = rail(provider);
        RecordingModelContext modelContext = new RecordingModelContext();

        rail.beforeModelCall(callbackContextWithUserQuery(modelContext, "what is the capital of France?"));

        assertThat(provider.searchedQuery).isEqualTo("what is the capital of France?");
        assertThat(modelContext.messages).hasSize(1);
        assertThat(modelContext.messages.get(0).getContentAsString())
                .contains("Relevant memory")
                .contains("user prefers window seats");
    }

    @Test
    void beforeModelCallAppendsToTailWithoutModifyingExistingMessages() {
        FakeMemoryProvider provider = new FakeMemoryProvider();
        OpenJiuwenLLMMemoryRail rail = rail(provider);
        RecordingModelContext modelContext = new RecordingModelContext();
        // Pre-existing system message (simulating first model call's injection)
        modelContext.addMessages(List.of(new SystemMessage("first round memory")));

        rail.beforeModelCall(callbackContextWithUserQuery(modelContext, "follow-up question"));

        assertThat(modelContext.messages).hasSize(2);
        // First message unchanged (prefix stability for KV Cache)
        assertThat(modelContext.messages.get(0).getContentAsString()).isEqualTo("first round memory");
        // New memory appended at tail
        assertThat(modelContext.messages.get(1).getContentAsString())
                .contains("Relevant memory")
                .contains("user prefers window seats");
    }

    @Test
    void beforeModelCallSkipsWhenNoUserQueryInMessages() {
        FakeMemoryProvider provider = new FakeMemoryProvider();
        OpenJiuwenLLMMemoryRail rail = rail(provider);
        RecordingModelContext modelContext = new RecordingModelContext();

        rail.beforeModelCall(callbackContextWithUserQuery(modelContext, null));

        assertThat(provider.searchedQuery).isNull();
        assertThat(modelContext.messages).isEmpty();
    }

    @Test
    void beforeModelCallSkipsWhenUserQueryIsBlank() {
        FakeMemoryProvider provider = new FakeMemoryProvider();
        OpenJiuwenLLMMemoryRail rail = rail(provider);
        RecordingModelContext modelContext = new RecordingModelContext();

        rail.beforeModelCall(callbackContextWithUserQuery(modelContext, "   "));

        assertThat(provider.searchedQuery).isNull();
        assertThat(modelContext.messages).isEmpty();
    }

    @Test
    void beforeModelCallSkipsWhenSearchReturnsNoHits() {
        FakeMemoryProvider provider = new FakeMemoryProvider();
        provider.returnHits = false;
        OpenJiuwenLLMMemoryRail rail = rail(provider);
        RecordingModelContext modelContext = new RecordingModelContext();

        rail.beforeModelCall(callbackContextWithUserQuery(modelContext, "ping"));

        assertThat(modelContext.messages).isEmpty();
    }

    @Test
    void beforeModelCallToleratesSearchFailure() {
        MemoryProvider provider = new ThrowingMemoryProvider();
        OpenJiuwenLLMMemoryRail rail = rail(provider);
        RecordingModelContext modelContext = new RecordingModelContext();

        // Must not throw — graceful degradation
        rail.beforeModelCall(callbackContextWithUserQuery(modelContext, "ping"));

        assertThat(modelContext.messages).isEmpty();
    }

    @Test
    void beforeModelCallExtractsLatestUserMessageFromReActHistory() {
        FakeMemoryProvider provider = new FakeMemoryProvider();
        OpenJiuwenLLMMemoryRail rail = rail(provider);
        RecordingModelContext modelContext = new RecordingModelContext();

        // Simulate ReAct history: user → assistant → user (latest query is "second question")
        List<BaseMessage> history = List.of(
                new UserMessage("first question"),
                new AssistantMessage("first answer"),
                new UserMessage("second question"));
        rail.beforeModelCall(callbackContext(modelContext, history));

        assertThat(provider.searchedQuery).isEqualTo("second question");
    }

    @Test
    void afterInvokeSavesUserAndAssistantMessagesToMemory() {
        FakeMemoryProvider provider = new FakeMemoryProvider();
        OpenJiuwenLLMMemoryRail rail = rail(provider);
        RecordingModelContext modelContext = new RecordingModelContext();
        modelContext.addMessages(List.of(
                new SystemMessage("system prompt"),
                new UserMessage("hello"),
                new AssistantMessage("world")));

        rail.afterInvoke(callbackContext(modelContext));

        assertThat(provider.savedRecords)
                .extracting(MemoryProvider.MemoryRecord::role)
                .containsExactly("user", "assistant");
        assertThat(provider.savedRecords)
                .extracting(MemoryProvider.MemoryRecord::content)
                .containsExactly("hello", "world");
    }

    @Test
    void afterInvokeDoesNotSaveSystemMessagesToLongTermMemory() {
        FakeMemoryProvider provider = new FakeMemoryProvider();
        OpenJiuwenLLMMemoryRail rail = rail(provider);
        RecordingModelContext modelContext = new RecordingModelContext();
        modelContext.addMessages(List.of(new SystemMessage("business policy")));

        rail.afterInvoke(callbackContext(modelContext));

        assertThat(provider.savedRecords).isEmpty();
    }

    @Test
    void afterInvokeSkipsWhenNoMessages() {
        FakeMemoryProvider provider = new FakeMemoryProvider();
        OpenJiuwenLLMMemoryRail rail = rail(provider);

        rail.afterInvoke(callbackContext(new RecordingModelContext()));

        assertThat(provider.savedRecords).isEmpty();
    }

    @Test
    void afterInvokeToleratesSaveFailure() {
        MemoryProvider provider = new ThrowingMemoryProvider();
        OpenJiuwenLLMMemoryRail rail = rail(provider);
        RecordingModelContext modelContext = new RecordingModelContext();
        modelContext.addMessages(List.of(new UserMessage("hello"), new AssistantMessage("world")));

        // Must not throw — graceful degradation
        rail.afterInvoke(callbackContext(modelContext));
    }

    @Test
    void afterInvokeHandlesNullCallbackContext() {
        FakeMemoryProvider provider = new FakeMemoryProvider();
        OpenJiuwenLLMMemoryRail rail = rail(provider);

        rail.afterInvoke(null);

        assertThat(provider.savedRecords).isEmpty();
    }

    @Test
    void formatMemoryBlockOmitsBlankHits() {
        FakeMemoryProvider provider = new FakeMemoryProvider();
        provider.hits = List.of(
                new MemoryProvider.MemoryHit("m1", "valid memory", 0.9, Map.of()),
                new MemoryProvider.MemoryHit("m2", "  ", 0.8, Map.of()),
                new MemoryProvider.MemoryHit("m3", "another memory", 0.7, Map.of()));
        OpenJiuwenLLMMemoryRail rail = rail(provider);
        RecordingModelContext modelContext = new RecordingModelContext();

        rail.beforeModelCall(callbackContextWithUserQuery(modelContext, "query"));

        String content = modelContext.messages.get(0).getContentAsString();
        assertThat(content)
                .contains("valid memory")
                .contains("another memory")
                .doesNotContain("m2");
    }

    // ── helpers ──────────────────────────────────────────────────────

    private static OpenJiuwenLLMMemoryRail rail(MemoryProvider provider) {
        return new OpenJiuwenLLMMemoryRail(context(), provider, new OpenJiuwenMemoryMessageAdapter());
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task", "agent"),
                "USER_MESSAGE", List.of(RuntimeMessage.user("ping")), Map.of());
    }

    private static AgentCallbackContext callbackContext(ModelContext modelContext) {
        return AgentCallbackContext.builder().context(modelContext).build();
    }

    private static AgentCallbackContext callbackContextWithUserQuery(ModelContext modelContext, String query) {
        List<Object> messages = query == null ? List.of() : List.of(new UserMessage(query));
        return AgentCallbackContext.builder()
                .context(modelContext)
                .inputs(ModelCallInputs.builder().messages(messages).tools(List.of()).build())
                .build();
    }

    private static AgentCallbackContext callbackContext(ModelContext modelContext, List<BaseMessage> inputMessages) {
        List<Object> messages = new ArrayList<Object>(inputMessages);
        return AgentCallbackContext.builder()
                .context(modelContext)
                .inputs(ModelCallInputs.builder().messages(messages).tools(List.of()).build())
                .build();
    }

    private static final class FakeMemoryProvider implements MemoryProvider {
        private boolean initialized;
        private String searchedQuery;
        private List<MemoryRecord> savedRecords = List.of();
        private boolean returnHits = true;
        private List<MemoryHit> hits = List.of(new MemoryHit("m1", "user prefers window seats", 0.9, Map.of()));

        @Override
        public void init(AgentExecutionContext context) {
            initialized = true;
        }

        @Override
        public List<MemoryHit> search(AgentExecutionContext context, String query, int limit) {
            searchedQuery = query;
            return returnHits ? hits : List.of();
        }

        @Override
        public void save(AgentExecutionContext context, List<MemoryRecord> records) {
            savedRecords = records;
        }
    }

    private static final class ThrowingMemoryProvider implements MemoryProvider {
        @Override
        public void init(AgentExecutionContext context) {
            throw new RuntimeException("init failed");
        }

        @Override
        public List<MemoryHit> search(AgentExecutionContext context, String query, int limit) {
            throw new RuntimeException("search failed");
        }

        @Override
        public void save(AgentExecutionContext context, List<MemoryRecord> records) {
            throw new RuntimeException("save failed");
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
