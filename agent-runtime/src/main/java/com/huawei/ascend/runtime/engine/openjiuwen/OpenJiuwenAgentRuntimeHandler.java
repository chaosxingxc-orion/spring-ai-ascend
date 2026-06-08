package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.openjiuwen.core.runner.Runner;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for openJiuwen {@link AgentRuntimeHandler} implementations. The concrete
 * handler owns how it builds and invokes its openJiuwen agent; this class only
 * provides the runtime-facing id, health default, and input/result mapping
 * helpers shared by openJiuwen handlers.
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

    OpenJiuwenAgentRuntimeHandler(String agentId, OpenJiuwenMessageAdapter messageConverter, OpenJiuwenStreamAdapter resultMapper) {
        this.agentId = agentId;
        this.messageConverter = messageConverter;
        this.resultMapper = resultMapper;
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    protected Object toOpenJiuwenInput(AgentExecutionContext context) {
        LOGGER.info("openjiuwen input convert tenantId={} sessionId={} taskId={} agentId={} inputType={} messages={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                context.getScope().agentId(),
                context.getInput().inputType(),
                context.getInput().messages().size());
        return messageConverter.toOpenJiuwenInput(context);
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

    protected void safeRelease(AgentExecutionContext context) {
        safeRelease(context.getScope());
    }

    protected void safeRelease(EngineExecutionScope scope) {
        try {
            Runner.release(scope.taskId());
        } catch (Exception ignored) {
            // best-effort cleanup; release failures must not mask the result
        }
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
}
