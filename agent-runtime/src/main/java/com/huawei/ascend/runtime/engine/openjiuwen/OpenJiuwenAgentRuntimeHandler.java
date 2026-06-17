package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEmitter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.harness.rails.ExternalMemoryRail;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
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
 * <p>The runtime always executes through openJiuwen's streaming runner and
 * maps standard {@link OutputSchema} chunks into the framework-neutral result
 * stream.
 */
public abstract class OpenJiuwenAgentRuntimeHandler extends AbstractOpenJiuwenRuntimeSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenJiuwenAgentRuntimeHandler.class);

    private OpenJiuwenRemoteToolInstaller runtimeToolInstaller;

    protected OpenJiuwenAgentRuntimeHandler(String agentId) {
        this(agentId, new OpenJiuwenMessageAdapter());
    }

    protected OpenJiuwenAgentRuntimeHandler(String agentId, OpenJiuwenMessageAdapter messageConverter) {
        this(agentId, messageConverter, new OpenJiuwenStreamAdapter());
    }

    OpenJiuwenAgentRuntimeHandler(String agentId, OpenJiuwenMessageAdapter messageConverter,
            OpenJiuwenStreamAdapter resultMapper) {
        super(agentId, messageConverter, resultMapper);
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
            Iterator<Object> result = runOpenJiuwenAgentStreaming(
                    agent, input, openJiuwenConversationId(context), openJiuwenStreamModes(context));
            LOGGER.info("openjiuwen execute finished tenantId={} sessionId={} taskId={} resultType={}",
                    context.getScope().tenantId(),
                    context.getScope().sessionId(),
                    context.getScope().taskId(),
                    result == null ? "null" : result.getClass().getName());
            return flattenIterator(result);
        } catch (RuntimeException error) {
            return failedResult(context, trajectory, error);
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

    protected Iterator<Object> runOpenJiuwenAgentStreaming(BaseAgent agent, Object input, String conversationId,
            List<StreamMode> streamModes) {
        return Runner.runAgentStreaming(agent, input, conversationId, null, streamModes);
    }

    protected List<StreamMode> openJiuwenStreamModes(AgentExecutionContext context) {
        return List.of(StreamMode.OUTPUT);
    }

    private void installRails(BaseAgent agent, AgentExecutionContext context) {
        for (AgentRail rail : openJiuwenRails(context)) {
            if (rail != null) {
                agent.registerRail(rail);
            }
        }
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
}
