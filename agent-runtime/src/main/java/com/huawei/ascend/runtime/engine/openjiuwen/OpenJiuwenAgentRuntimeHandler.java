package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for openJiuwen {@link AgentRuntimeHandler} implementations. The
 * concrete handler owns how it builds its openJiuwen agent; this class owns the
 * runtime-facing execute flow, rail installation, input/result mapping, and
 * stable {@code conversation_id}. openJiuwen session persistence is delegated to
 * its native checkpointer mechanism.
 */
public abstract class OpenJiuwenAgentRuntimeHandler implements AgentRuntimeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenJiuwenAgentRuntimeHandler.class);

    private final String agentId;
    private final OpenJiuwenMessageAdapter messageConverter;
    private final OpenJiuwenStreamAdapter resultMapper;

    protected OpenJiuwenAgentRuntimeHandler(String agentId) {
        this(agentId, new OpenJiuwenMessageAdapter());
    }

    protected OpenJiuwenAgentRuntimeHandler(String agentId, OpenJiuwenMessageAdapter messageConverter) {
        this(agentId, messageConverter, new OpenJiuwenStreamAdapter());
    }

    OpenJiuwenAgentRuntimeHandler(String agentId, OpenJiuwenMessageAdapter messageConverter,
            OpenJiuwenStreamAdapter resultMapper) {
        org.springframework.util.Assert.hasText(agentId, "agentId must not be blank");
        this.agentId = agentId;
        this.messageConverter = Objects.requireNonNull(messageConverter, "messageConverter");
        this.resultMapper = Objects.requireNonNull(resultMapper, "resultMapper");
    }

    @Override
    public final String agentId() {
        return agentId;
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public final java.util.stream.Stream<?> execute(AgentExecutionContext context) {
        try {
            LOGGER.info("openjiuwen execute start tenantId={} sessionId={} taskId={} agentId={}",
                    context.getScope().tenantId(),
                    context.getScope().sessionId(),
                    context.getScope().taskId(),
                    context.getScope().agentId());
            BaseAgent agent = Objects.requireNonNull(createOpenJiuwenAgent(context), "openJiuwen agent");
            installRails(agent, context);
            Object input = toOpenJiuwenInput(context);
            Object result = runOpenJiuwenAgent(agent, input, openJiuwenConversationId(context));
            LOGGER.info("openjiuwen execute finished tenantId={} sessionId={} taskId={} resultType={}",
                    context.getScope().tenantId(),
                    context.getScope().sessionId(),
                    context.getScope().taskId(),
                    result == null ? "null" : result.getClass().getName());
            return java.util.stream.Stream.of(result);
        } catch (RuntimeException error) {
            LOGGER.warn("openjiuwen execute failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    context.getScope().tenantId(),
                    context.getScope().sessionId(),
                    context.getScope().taskId(),
                    error.getClass().getSimpleName(),
                    errorMessage(error));
            return java.util.stream.Stream.of(Map.of("result_type", "error", "output", errorMessage(error)));
        }
    }

    /** Build the concrete openJiuwen agent instance for this execution. */
    protected abstract BaseAgent createOpenJiuwenAgent(AgentExecutionContext context);

    /**
     * Adapter-owned rails installed on every openJiuwen agent before execution.
     *
     * <p>The default installs no rails. Subclasses can opt in to openJiuwen-local
     * decorations such as {@link MemoryRuntimeRail} without changing A2A
     * execution or the framework-neutral runtime SPI.
     */
    protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
        return List.of();
    }

    /** Create the default openJiuwen memory rail for subclasses that opt in. */
    protected final MemoryRuntimeRail memoryRuntimeRail(AgentExecutionContext context, MemoryProvider memoryProvider) {
        return new MemoryRuntimeRail(context, memoryProvider, new OpenJiuwenMemoryMessageAdapter());
    }

    protected Object runOpenJiuwenAgent(BaseAgent agent, Object input, String conversationId) {
        return Runner.runAgent(agent, input, conversationId, null);
    }

    /**
     * Returns the stable conversation id openJiuwen should use for native
     * checkpointer restore/save. Subclasses pass this value as the Runner
     * session id, or rely on {@link #toOpenJiuwenInput(AgentExecutionContext)}
     * to place it in {@code conversation_id}.
     */
    protected String openJiuwenConversationId(AgentExecutionContext context) {
        String conversationId = context.getAgentStateKey();
        LOGGER.info("openjiuwen conversation resolve tenantId={} sessionId={} taskId={} agentId={} conversationId={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                context.getScope().agentId(),
                conversationId);
        return conversationId;
    }

    protected Object toOpenJiuwenInput(AgentExecutionContext context) {
        LOGGER.info("openjiuwen input convert tenantId={} sessionId={} taskId={} agentId={} inputType={} messages={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                context.getScope().agentId(),
                context.getInputType(),
                context.getMessages().size());
        return messageConverter.toOpenJiuwenInput(context);
    }

    private void installRails(BaseAgent agent, AgentExecutionContext context) {
        for (AgentRail rail : openJiuwenRails(context)) {
            if (rail != null) {
                agent.registerRail(rail);
            }
        }
    }

    @Override
    public StreamAdapter resultAdapter() {
        return rawResults -> rawResults.map(this::mapRawResult);
    }

    @SuppressWarnings("unchecked")
    private com.huawei.ascend.runtime.engine.spi.AgentExecutionResult mapRawResult(Object rawResult) {
        LOGGER.info("openjiuwen raw result received type={}",
                rawResult == null ? "null" : rawResult.getClass().getName());
        if (rawResult instanceof Map<?, ?> map) {
            return resultMapper.map((Map<String, Object>) map);
        }
        return resultMapper.map(Map.of("result_type", "answer", "output", String.valueOf(rawResult)));
    }

    protected static String errorMessage(Throwable error) {
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

    /**
     * Optional openJiuwen rail that bridges openJiuwen callback messages to a
     * runtime-neutral {@link MemoryProvider}.
     *
     * <p>This class is intentionally openJiuwen-local. Other agent frameworks
     * should use their own native callback/middleware mechanism rather than
     * depending on openJiuwen's Rail API.
     */
    public static final class MemoryRuntimeRail extends AgentRail {
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
                        errorMessage(error));
            }
        }

        @Override
        public void afterInvoke(AgentCallbackContext callbackContext) {
            List<BaseMessage> messages = messages(callbackContext);
            if (messages.isEmpty()) {
                return;
            }
            try {
                memoryProvider.save(executionContext, memoryMessageAdapter.toMemoryRecords(messages));
            } catch (RuntimeException error) {
                LOGGER.warn("openjiuwen memory save failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                        executionContext.getScope().tenantId(),
                        executionContext.getScope().sessionId(),
                        executionContext.getScope().taskId(),
                        error.getClass().getSimpleName(),
                        errorMessage(error));
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
    }
}
