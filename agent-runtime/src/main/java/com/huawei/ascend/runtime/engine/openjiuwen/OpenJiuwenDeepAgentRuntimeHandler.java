package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEmitter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for OpenJiuwen DeepAgent runtime handlers.
 *
 * <p>DeepAgent owns its own session lifecycle in {@code stream(...)}, so this
 * handler calls the harness entrypoint directly instead of wrapping it with
 * OpenJiuwen {@code Runner.runAgentStreaming(...)}.
 */
public abstract class OpenJiuwenDeepAgentRuntimeHandler extends AbstractOpenJiuwenRuntimeSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenJiuwenDeepAgentRuntimeHandler.class);

    private final ConcurrentMap<String, DeepAgent> runningAgents = new ConcurrentHashMap<>();
    private OpenJiuwenRemoteToolInstaller runtimeToolInstaller;

    protected OpenJiuwenDeepAgentRuntimeHandler(String agentId) {
        super(agentId);
    }

    protected OpenJiuwenDeepAgentRuntimeHandler(String agentId, OpenJiuwenMessageAdapter messageConverter) {
        super(agentId, messageConverter);
    }

    OpenJiuwenDeepAgentRuntimeHandler(String agentId, OpenJiuwenMessageAdapter messageConverter,
            OpenJiuwenStreamAdapter resultMapper) {
        super(agentId, messageConverter, resultMapper);
    }

    @Override
    protected Set<Kind> supportedKinds() {
        return EnumSet.of(
                Kind.RUN_START, Kind.RUN_END,
                Kind.MODEL_CALL_START, Kind.MODEL_CALL_END,
                Kind.TOOL_CALL_START, Kind.TOOL_CALL_END,
                Kind.ERROR);
    }

    @Override
    protected final Stream<?> doExecute(AgentExecutionContext context, TrajectoryEmitter trajectory) {
        String taskId = context.getScope().taskId();
        DeepAgent agent = null;
        boolean registered = false;
        try {
            LOGGER.info("openjiuwen deepagent execute start tenantId={} sessionId={} taskId={} agentId={}",
                    context.getScope().tenantId(),
                    context.getScope().sessionId(),
                    taskId,
                    context.getScope().agentId());
            agent = Objects.requireNonNull(createOpenJiuwenDeepAgent(context), "openJiuwen deepAgent");
            runningAgents.put(taskId, agent);
            registered = true;

            installRuntimeTools(agent, context);
            if (trajectory != TrajectoryEmitter.NOOP) {
                agent.getAgent().registerRail(new OpenJiuwenTrajectoryRail(trajectory));
            }

            Map<String, Object> input = requireMap(toOpenJiuwenInput(context));
            Iterator<Object> iterator = runOpenJiuwenDeepAgentStreaming(
                    agent, input, openJiuwenConversationId(context), openJiuwenStreamModes(context));
            DeepAgent registeredAgent = agent;
            return flattenIterator(cleaningIterator(iterator, () -> runningAgents.remove(taskId, registeredAgent)))
                    .onClose(() -> runningAgents.remove(taskId, registeredAgent));
        } catch (RuntimeException error) {
            if (registered) {
                runningAgents.remove(taskId, agent);
            }
            return failedResult(context, trajectory, error);
        }
    }

    protected abstract DeepAgent createOpenJiuwenDeepAgent(AgentExecutionContext context);

    protected void installRuntimeTools(DeepAgent agent, AgentExecutionContext context) {
        if (runtimeToolInstaller != null) {
            runtimeToolInstaller.install(agent, context);
        }
    }

    public final void setRuntimeToolInstaller(OpenJiuwenRemoteToolInstaller runtimeToolInstaller) {
        this.runtimeToolInstaller = runtimeToolInstaller;
    }

    protected Iterator<Object> runOpenJiuwenDeepAgentStreaming(
            DeepAgent agent,
            Map<String, Object> input,
            String conversationId,
            List<StreamMode> streamModes) {
        input.putIfAbsent("conversation_id", conversationId);
        return agent.stream(input, null, streamModes);
    }

    protected List<StreamMode> openJiuwenStreamModes(AgentExecutionContext context) {
        return List.of(StreamMode.OUTPUT);
    }

    @Override
    public void cancel(String taskId) {
        DeepAgent agent = runningAgents.get(taskId);
        if (agent != null) {
            agent.requestAbort();
        }
    }

    int runningAgentCount() {
        return runningAgents.size();
    }

    private static Map<String, Object> requireMap(Object rawInput) {
        if (rawInput instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        throw new IllegalArgumentException("OpenJiuwen DeepAgent input must be a Map<String,Object>");
    }

    private static Iterator<Object> cleaningIterator(Iterator<Object> delegate, Runnable cleanup) {
        Iterator<Object> safeDelegate = delegate != null ? delegate : List.of((Object) null).iterator();
        AtomicBoolean cleaned = new AtomicBoolean();
        Runnable cleanOnce = () -> {
            if (cleaned.compareAndSet(false, true)) {
                cleanup.run();
            }
        };
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                try {
                    boolean hasNext = safeDelegate.hasNext();
                    if (!hasNext) {
                        cleanOnce.run();
                    }
                    return hasNext;
                } catch (RuntimeException error) {
                    cleanOnce.run();
                    throw error;
                }
            }

            @Override
            public Object next() {
                try {
                    Object next = safeDelegate.next();
                    if (!safeDelegate.hasNext()) {
                        cleanOnce.run();
                    }
                    return next;
                } catch (RuntimeException error) {
                    cleanOnce.run();
                    throw error;
                }
            }
        };
    }
}
