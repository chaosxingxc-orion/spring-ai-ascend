package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional OpenJiuwen rail that bridges OpenJiuwen callback messages to a
 * runtime-neutral {@link MemoryProvider}.
 *
 * <p>This class is intentionally OpenJiuwen-local. Other agent frameworks
 * should use their own native callback/middleware mechanism rather than
 * depending on OpenJiuwen's Rail API.
 */
final class MemoryRuntimeRail extends AgentRail {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryRuntimeRail.class);
    private static final int DEFAULT_MEMORY_SEARCH_LIMIT = 5;
    private static final String MEMORY_SECTION_NAME = "runtime_long_term_memory";
    private static final int MEMORY_SECTION_PRIORITY = 50;

    private final AgentExecutionContext executionContext;
    private final MemoryProvider memoryProvider;
    private final OpenJiuwenMemoryMessageAdapter memoryMessageAdapter;

    MemoryRuntimeRail(AgentExecutionContext executionContext, MemoryProvider memoryProvider,
            OpenJiuwenMemoryMessageAdapter memoryMessageAdapter) {
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext");
        this.memoryProvider = Objects.requireNonNull(memoryProvider, "memoryProvider");
        this.memoryMessageAdapter = Objects.requireNonNull(memoryMessageAdapter, "memoryMessageAdapter");
    }

    @Override
    public void beforeInvoke(AgentCallbackContext callbackContext) {
        try {
            memoryProvider.init(executionContext);
        } catch (RuntimeException error) {
            LOGGER.warn("openjiuwen memory init failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    executionContext.getScope().tenantId(),
                    executionContext.getScope().sessionId(),
                    executionContext.getScope().taskId(),
                    error.getClass().getSimpleName(),
                    OpenJiuwenAgentRuntimeHandler.errorMessage(error));
        }
        try {
            injectMemory(callbackContext);
        } catch (RuntimeException error) {
            LOGGER.warn("openjiuwen memory search inject failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    executionContext.getScope().tenantId(),
                    executionContext.getScope().sessionId(),
                    executionContext.getScope().taskId(),
                    error.getClass().getSimpleName(),
                    OpenJiuwenAgentRuntimeHandler.errorMessage(error));
        }
    }

    @Override
    public void afterInvoke(AgentCallbackContext callbackContext) {
        List<BaseMessage> messages = messages(callbackContext);
        if (messages.isEmpty()) {
            return;
        }
        try {
            List<MemoryProvider.MemoryRecord> records = messages.stream()
                    .map(this::toLongTermMemoryRecord)
                    .filter(Objects::nonNull)
                    .toList();
            if (!records.isEmpty()) {
                memoryProvider.save(executionContext, records);
            }
        } catch (RuntimeException error) {
            LOGGER.warn("openjiuwen memory save failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    executionContext.getScope().tenantId(),
                    executionContext.getScope().sessionId(),
                    executionContext.getScope().taskId(),
                    error.getClass().getSimpleName(),
                    OpenJiuwenAgentRuntimeHandler.errorMessage(error));
        }
    }

    private static List<BaseMessage> messages(AgentCallbackContext callbackContext) {
        if (callbackContext == null) {
            return List.of();
        }
        ModelContext modelContext = callbackContext.getContext();
        if (modelContext == null) {
            return List.of();
        }
        List<BaseMessage> messages = modelContext.getMessages();
        return messages == null ? List.of() : messages;
    }

    private void injectMemory(AgentCallbackContext callbackContext) {
        String query = latestUserInput();
        if (query.isBlank()) {
            clearPromptBuilderMemory(callbackContext);
            return;
        }
        List<MemoryProvider.MemoryHit> hits =
                memoryProvider.search(executionContext, query, DEFAULT_MEMORY_SEARCH_LIMIT);
        if (hits.isEmpty()) {
            clearPromptBuilderMemory(callbackContext);
            return;
        }
        String memoryBlock = runtimeMemoryBlock(formatMemoryBlock(hits));
        if (mergeMemoryIntoPromptBuilder(callbackContext, memoryBlock)) {
            return;
        }
        ModelContext modelContext = callbackContext == null ? null : callbackContext.getContext();
        if (modelContext != null) {
            mergeMemoryIntoSystemMessage(modelContext, memoryBlock);
            return;
        }
        LOGGER.warn("openjiuwen memory inject skipped tenantId={} sessionId={} taskId={} reason=no_react_agent_or_model_context callbackAgentType={}",
                executionContext.getScope().tenantId(),
                executionContext.getScope().sessionId(),
                executionContext.getScope().taskId(),
                callbackAgentType(callbackContext));
    }

    private MemoryProvider.MemoryRecord toLongTermMemoryRecord(BaseMessage message) {
        MemoryProvider.MemoryRecord record = memoryMessageAdapter.toMemoryRecord(message);
        if (isLongTermTurnRole(record.role()) && hasText(record.content())) {
            return record;
        }
        return null;
    }

    private String latestUserInput() {
        return executionContext.lastUserText();
    }

    private static String formatMemoryBlock(List<MemoryProvider.MemoryHit> hits) {
        StringBuilder block = new StringBuilder("Relevant memory:\n");
        for (MemoryProvider.MemoryHit hit : hits) {
            if (hit != null && !hit.content().isBlank()) {
                block.append("- ").append(hit.content()).append('\n');
            }
        }
        return block.toString().trim();
    }

    private static void mergeMemoryIntoSystemMessage(ModelContext modelContext, String memoryBlock) {
        List<BaseMessage> currentMessages = modelContext.getMessages();
        List<BaseMessage> updatedMessages =
                new ArrayList<>(currentMessages == null ? List.of() : currentMessages);
        for (int i = 0; i < updatedMessages.size(); i++) {
            BaseMessage message = updatedMessages.get(i);
            if (isSystemMessage(message)) {
                updatedMessages.set(i, mergedSystemMessage(message, memoryBlock));
                modelContext.setMessages(updatedMessages, true);
                return;
            }
        }
        updatedMessages.add(0, new SystemMessage(memoryBlock));
        modelContext.setMessages(updatedMessages, true);
    }

    private static boolean mergeMemoryIntoPromptBuilder(AgentCallbackContext callbackContext, String memoryBlock) {
        ReActAgent reactAgent = reactAgent(callbackContext);
        if (reactAgent == null) {
            return false;
        }
        reactAgent.addPromptBuilderSection(MEMORY_SECTION_NAME, memoryBlock, MEMORY_SECTION_PRIORITY);
        return true;
    }

    private static void clearPromptBuilderMemory(AgentCallbackContext callbackContext) {
        ReActAgent reactAgent = reactAgent(callbackContext);
        if (reactAgent != null) {
            // OpenJiuwen treats blank section content as removeSection(name).
            reactAgent.addPromptBuilderSection(MEMORY_SECTION_NAME, "", MEMORY_SECTION_PRIORITY);
        }
    }

    private static ReActAgent reactAgent(AgentCallbackContext callbackContext) {
        Object agent = callbackContext == null ? null : callbackContext.getAgent();
        return agent instanceof ReActAgent reactAgent ? reactAgent : null;
    }

    private static String callbackAgentType(AgentCallbackContext callbackContext) {
        Object agent = callbackContext == null ? null : callbackContext.getAgent();
        return agent == null ? "null" : agent.getClass().getName();
    }

    private static String runtimeMemoryBlock(String memoryBlock) {
        return "[System note: recalled memory context from runtime memory, not new user input.]\n\n"
                + memoryBlock;
    }

    private static boolean isLongTermTurnRole(String role) {
        return "user".equalsIgnoreCase(role) || "assistant".equalsIgnoreCase(role);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isSystemMessage(BaseMessage message) {
        return message instanceof SystemMessage
                || (message != null && "system".equalsIgnoreCase(message.getRole()));
    }

    private static SystemMessage mergedSystemMessage(BaseMessage original, String memoryBlock) {
        String originalContent = original.getContentAsString();
        String mergedContent = originalContent == null || originalContent.isBlank()
                ? memoryBlock
                : originalContent + "\n\n" + memoryBlock;
        String name = original.getName();
        return name == null || name.isBlank()
                ? new SystemMessage(mergedContent)
                : new SystemMessage(mergedContent, name);
    }
}
