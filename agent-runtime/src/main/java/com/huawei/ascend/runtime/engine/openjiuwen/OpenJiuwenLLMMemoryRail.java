package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Memory rail that searches and injects memory before every model call
 * ({@code beforeModelCall})
 * The injection strategy is tail-append only: each round's memory stays in
 * the message buffer as part of that round's context. New memory is always
 * appended to the tail without modifying or replacing earlier messages. This
 * keeps the LLM input prefix stable across calls, maximizing KV Cache reuse.
 */
public final class OpenJiuwenLLMMemoryRail extends AgentRail {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenJiuwenLLMMemoryRail.class);

    private static final int DEFAULT_MEMORY_SEARCH_LIMIT = 5;

    private final AgentExecutionContext executionContext;
    private final MemoryProvider memoryProvider;
    private final OpenJiuwenMemoryMessageAdapter memoryMessageAdapter;

    OpenJiuwenLLMMemoryRail(AgentExecutionContext executionContext,
                            MemoryProvider memoryProvider,
                            OpenJiuwenMemoryMessageAdapter memoryMessageAdapter) {
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext");
        this.memoryProvider = Objects.requireNonNull(memoryProvider, "memoryProvider");
        this.memoryMessageAdapter = Objects.requireNonNull(memoryMessageAdapter, "memoryMessageAdapter");
    }

    // ── beforeInvoke: init only (consistent with MemoryRuntimeRail) ───

    @Override
    public void beforeInvoke(AgentCallbackContext callbackContext) {
        String tenantId = executionContext.getScope().tenantId();
        String sessionId = executionContext.getScope().sessionId();
        String taskId = executionContext.getScope().taskId();
        LOGGER.info("[LLMMemoryRail] beforeInvoke tenantId={} sessionId={} taskId={} provider={}",
                tenantId, sessionId, taskId, memoryProvider.getClass().getSimpleName());
        try {
            memoryProvider.init(executionContext);
            LOGGER.info("[LLMMemoryRail] init ok tenantId={} sessionId={} taskId={}", tenantId, sessionId, taskId);
        } catch (RuntimeException error) {
            LOGGER.warn("[LLMMemoryRail] init failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    tenantId, sessionId, taskId,
                    error.getClass().getSimpleName(), errorMessage(error));
        }
    }

    // ── beforeModelCall: search + tail-append ─────────────────────────

    @Override
    public void beforeModelCall(AgentCallbackContext callbackContext) {
        String tenantId = executionContext.getScope().tenantId();
        String sessionId = executionContext.getScope().sessionId();
        String taskId = executionContext.getScope().taskId();
        try {
            String query = extractQuery(callbackContext);
            if (query == null || query.isBlank()) {
                LOGGER.info("[LLMMemoryRail] beforeModelCall skip: no user query tenantId={} sessionId={} taskId={}",
                        tenantId, sessionId, taskId);
                return;
            }
            LOGGER.info("[LLMMemoryRail] beforeModelCall search tenantId={} sessionId={} taskId={} query=\"{}\"",
                    tenantId, sessionId, taskId, truncate(query, 120));
            List<MemoryProvider.MemoryHit> hits =
                    memoryProvider.search(executionContext, query, DEFAULT_MEMORY_SEARCH_LIMIT);
            LOGGER.info("[LLMMemoryRail] search returned {} hits tenantId={} sessionId={} taskId={}",
                    hits.size(), tenantId, sessionId, taskId);
            if (hits.isEmpty()) {
                return;
            }
            String memoryBlock = formatMemoryBlock(hits);
            appendToContext(callbackContext, memoryBlock);
            LOGGER.info("[LLMMemoryRail] memory injected tenantId={} sessionId={} taskId={} hitCount={} blockLength={}",
                    tenantId, sessionId, taskId, hits.size(), memoryBlock.length());
        } catch (RuntimeException error) {
            LOGGER.warn("[LLMMemoryRail] inject failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    tenantId, sessionId, taskId,
                    error.getClass().getSimpleName(), errorMessage(error));
        }
    }

    // ── afterInvoke: save conversation (consistent with MemoryRuntimeRail) ──

    @Override
    public void afterInvoke(AgentCallbackContext callbackContext) {
        String tenantId = executionContext.getScope().tenantId();
        String sessionId = executionContext.getScope().sessionId();
        String taskId = executionContext.getScope().taskId();
        List<BaseMessage> messages = messages(callbackContext);
        LOGGER.info("[LLMMemoryRail] afterInvoke tenantId={} sessionId={} taskId={} totalMessages={}",
                tenantId, sessionId, taskId, messages.size());
        if (messages.isEmpty()) {
            return;
        }
        try {
            List<MemoryProvider.MemoryRecord> records = messages.stream()
                    .map(this::toLongTermMemoryRecord)
                    .filter(Objects::nonNull)
                    .toList();
            LOGGER.info("[LLMMemoryRail] saving {} records (filtered from {} messages) tenantId={} sessionId={} taskId={}",
                    records.size(), messages.size(), tenantId, sessionId, taskId);
            if (!records.isEmpty()) {
                memoryProvider.save(executionContext, records);
                LOGGER.info("[LLMMemoryRail] save ok tenantId={} sessionId={} taskId={} recordCount={}",
                        tenantId, sessionId, taskId, records.size());
            }
        } catch (RuntimeException error) {
            LOGGER.warn("[LLMMemoryRail] save failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    tenantId, sessionId, taskId,
                    error.getClass().getSimpleName(), errorMessage(error));
        }
    }

    // ── internals ─────────────────────────────────────────────────────

    /**
     * Extract the latest user query. Prefers the messages in the callback
     * context (which reflect the current ReAct iteration state); falls back
     * to {@code executionContext.lastUserText()}.
     */
    private static String extractQuery(AgentCallbackContext callbackContext) {
        if (callbackContext != null && callbackContext.getInputs() instanceof ModelCallInputs modelInputs) {
            List<?> inputMessages = modelInputs.getMessages();
            if (inputMessages != null) {
                for (int i = inputMessages.size() - 1; i >= 0; i--) {
                    Object item = inputMessages.get(i);
                    if (item instanceof BaseMessage msg && "user".equalsIgnoreCase(msg.getRole())) {
                        String text = msg.getContentAsString();
                        if (text != null && !text.isBlank()) {
                            return text;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Append memory as a SystemMessage to the model context tail.
     * Never modifies or replaces existing messages.
     */
    private static void appendToContext(AgentCallbackContext callbackContext, String memoryBlock) {
        ModelContext modelContext = callbackContext == null ? null : callbackContext.getContext();
        if (modelContext == null) {
            return;
        }
        modelContext.addMessages(new SystemMessage(memoryBlock));
    }

    private MemoryProvider.MemoryRecord toLongTermMemoryRecord(BaseMessage message) {
        MemoryProvider.MemoryRecord record = memoryMessageAdapter.toMemoryRecord(message);
        if (isLongTermTurnRole(record.role()) && hasText(record.content())) {
            return record;
        }
        return null;
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

    private static String formatMemoryBlock(List<MemoryProvider.MemoryHit> hits) {
        StringBuilder block = new StringBuilder("[System note: recalled memory context from runtime memory, not new user input.]\n\n");
        block.append("Relevant memory:\n");
        for (MemoryProvider.MemoryHit hit : hits) {
            if (hit != null && !hit.content().isBlank()) {
                block.append("- ").append(hit.content()).append('\n');
            }
        }
        return block.toString().trim();
    }

    private static boolean isLongTermTurnRole(String role) {
        return "user".equalsIgnoreCase(role) || "assistant".equalsIgnoreCase(role);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').replaceAll("\\s+", " ");
        return oneLine.length() <= maxLength ? oneLine : oneLine.substring(0, maxLength) + "...";
    }

    private static String errorMessage(Throwable error) {
        StringBuilder message = new StringBuilder();
        Throwable cursor = error;
        while (cursor != null) {
            String part = cursor.getMessage();
            if (part != null && !part.isBlank()) {
                if (!message.isEmpty()) {
                    message.append(": ");
                }
                message.append(part);
            }
            cursor = cursor.getCause();
        }
        return message.isEmpty() ? error.getClass().getName() : message.toString();
    }
}
