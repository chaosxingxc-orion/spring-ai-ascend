package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryDraft;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEmitter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.harness.rails.ExternalMemoryRail;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for openJiuwen {@link AgentRuntimeHandler} implementations. The
 * concrete handler owns how it builds its openJiuwen agent; this class owns the
 * runtime-facing execute flow, rail installation, input/result mapping, and
 * stable {@code conversation_id}. openJiuwen session persistence is delegated to
 * its native checkpointer mechanism.
 *
 * <p>{@code runOpenJiuwenAgent} is a synchronous call: the result is fully
 * computed before it is wrapped in a stream, so a cancel does not interrupt an
 * in-progress run — it only stops the host from consuming the finished result.
 */
public abstract class OpenJiuwenAgentRuntimeHandler extends AbstractAgentRuntimeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenJiuwenAgentRuntimeHandler.class);

    private final OpenJiuwenMessageAdapter messageConverter;
    private final OpenJiuwenStreamAdapter resultMapper;
    private OpenJiuwenRemoteToolInstaller runtimeToolInstaller;

    protected OpenJiuwenAgentRuntimeHandler(String agentId) {
        this(agentId, new OpenJiuwenMessageAdapter());
    }

    protected OpenJiuwenAgentRuntimeHandler(String agentId, OpenJiuwenMessageAdapter messageConverter) {
        this(agentId, messageConverter, new OpenJiuwenStreamAdapter());
    }

    OpenJiuwenAgentRuntimeHandler(String agentId, OpenJiuwenMessageAdapter messageConverter,
            OpenJiuwenStreamAdapter resultMapper) {
        super(agentId);
        this.messageConverter = Objects.requireNonNull(messageConverter, "messageConverter");
        this.resultMapper = Objects.requireNonNull(resultMapper, "resultMapper");
    }

    /**
     * openJiuwen taps native model-call callbacks (token usage, reasoning, finish reason) on top of
     * the cross-framework core, so it advertises the model-call kinds. Without this, the optional
     * tier would be dropped by the capability gate before the FULL-level gate is ever reached.
     */
    @Override
    protected Set<Kind> supportedKinds() {
        return EnumSet.of(
                Kind.RUN_START, Kind.RUN_END,
                Kind.MODEL_CALL_START, Kind.MODEL_CALL_END,
                Kind.TOOL_CALL_START, Kind.TOOL_CALL_END,
                Kind.ERROR);
    }

    @Override
    protected final java.util.stream.Stream<?> doExecute(AgentExecutionContext context,
            TrajectoryEmitter trajectory) {
        try {
            LOGGER.info("openjiuwen execute start tenantId={} sessionId={} taskId={} agentId={}",
                    context.getScope().tenantId(),
                    context.getScope().sessionId(),
                    context.getScope().taskId(),
                    context.getScope().agentId());
            BaseAgent agent = Objects.requireNonNull(createOpenJiuwenAgent(context), "openJiuwen agent");
            installRails(agent, context);
            installRuntimeTools(agent, context);
            if (trajectory != TrajectoryEmitter.NOOP) {
                agent.registerRail(new OpenJiuwenTrajectoryRail(trajectory));
            }
            Object input = toOpenJiuwenInput(context);
            Object result = runOpenJiuwenAgent(agent, input, openJiuwenConversationId(context));
            LOGGER.info("openjiuwen execute finished tenantId={} sessionId={} taskId={} resultType={}",
                    context.getScope().tenantId(),
                    context.getScope().sessionId(),
                    context.getScope().taskId(),
                    result == null ? "null" : result.getClass().getName());
            if (result instanceof java.util.stream.Stream<?> stream) {
                return stream;
            }
            return java.util.stream.Stream.of(result);
        } catch (RuntimeException error) {
            LOGGER.warn("openjiuwen execute failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    context.getScope().tenantId(),
                    context.getScope().sessionId(),
                    context.getScope().taskId(),
                    error.getClass().getSimpleName(),
                    errorMessage(error));
            // ERROR is a mandatory trajectory kind: surface the run-level failure northbound even though
            // the failure is mapped to a result (not rethrown), so the trajectory is not silently truncated.
            trajectory.emit(TrajectoryDraft.error(null, "OPENJIUWEN_RUN_ERROR", errorMessage(error), null, false));
            return java.util.stream.Stream.of(Map.of("result_type", "error", "output", errorMessage(error)));
        }
    }

    /** Build the concrete openJiuwen agent instance for this execution. */
    protected abstract BaseAgent createOpenJiuwenAgent(AgentExecutionContext context);

    /**
     * Adapter-owned rails installed on every openJiuwen agent before execution.
     *
     * <p>The default installs no rails. Subclasses can opt in to openJiuwen-local
     * decorations such as OpenJiuwen's external memory rail or the ReActAgent
     * compatibility {@link MemoryRuntimeRail} without changing A2A execution or
     * the framework-neutral runtime SPI.
     */
    protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
        return List.of();
    }

    /**
     * Install runtime-owned tools on the concrete openJiuwen agent instance.
     *
     * <p>The default is intentionally empty. Runtime integrations such as remote
     * A2A tool injection can use this hook without changing the concrete user's
     * agent implementation.
     */
    protected void installRuntimeTools(BaseAgent agent, AgentExecutionContext context) {
        if (runtimeToolInstaller != null) {
            runtimeToolInstaller.install(agent, context);
        }
    }

    public final void setRuntimeToolInstaller(OpenJiuwenRemoteToolInstaller runtimeToolInstaller) {
        this.runtimeToolInstaller = runtimeToolInstaller;
    }

    /**
     * Create the ReActAgent-compatible memory rail for subclasses that opt in.
     *
     * <p>Use {@link #openJiuwenExternalMemoryRail(AgentExecutionContext, MemoryProvider)}
     * first when the concrete OpenJiuwen agent supports the native harness
     * external-memory rail.
     */
    protected final MemoryRuntimeRail memoryRuntimeRail(AgentExecutionContext context, MemoryProvider memoryProvider) {
        return new MemoryRuntimeRail(context, memoryProvider, new OpenJiuwenMemoryMessageAdapter());
    }

    /**
     * Create an openJiuwen-native external memory rail backed by the runtime
     * neutral {@link MemoryProvider}.
     *
     * <p>Prefer this hook when the concrete openJiuwen agent supports the
     * harness external-memory rail. The OpenJiuwen memory API is intentionally
     * hidden behind an adapter in this package so the public runtime SPI remains
     * independent from OpenJiuwen memory package names.
     */
    protected final AgentRail openJiuwenExternalMemoryRail(AgentExecutionContext context, MemoryProvider memoryProvider) {
        return createExternalMemoryRail(
                new OpenJiuwenExternalMemoryProviderAdapter(context, memoryProvider),
                context.getScope().userId(),
                context.getAgentStateKey(),
                context.getScope().sessionId());
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
    private AgentExecutionResult mapRawResult(Object rawResult) {
        LOGGER.info("openjiuwen raw result received type={}",
                rawResult == null ? "null" : rawResult.getClass().getName());
        if (rawResult instanceof AgentExecutionResult result) {
            return result;
        }
        if (rawResult == null) {
            return resultMapper.map(Map.of(
                    "result_type", "error",
                    "output", "openjiuwen runner returned no result"));
        }
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

    private static AgentRail createExternalMemoryRail(
            com.openjiuwen.core.memory.external.MemoryProvider memoryProvider,
            String userId,
            String scopeId,
            String sessionId) {
        return ExternalMemoryRailHolder.create(memoryProvider, userId, scopeId, sessionId);
    }

    private static final class ExternalMemoryRailHolder {
        private static AgentRail create(
                com.openjiuwen.core.memory.external.MemoryProvider memoryProvider,
                String userId,
                String scopeId,
                String sessionId) {
            return new ExternalMemoryRail(memoryProvider, userId, scopeId, sessionId);
        }
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
                        errorMessage(error));
            }
            try {
                injectMemory(callbackContext);
            } catch (RuntimeException error) {
                LOGGER.warn("openjiuwen memory search inject failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
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
            }
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
}
