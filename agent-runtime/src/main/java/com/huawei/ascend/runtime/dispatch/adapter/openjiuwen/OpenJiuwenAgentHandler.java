package com.huawei.ascend.service.engine.adapter.openjiuwen;

import com.huawei.ascend.service.engine.handler.AgentExecutionContext;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.spi.AgentResultAdapter;
import com.huawei.ascend.service.engine.spi.AgentHandler;
import com.openjiuwen.core.runner.Runner;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for openJiuwen {@link AgentHandler} implementations. The concrete
 * handler owns how it builds and invokes its openJiuwen agent; this class only
 * provides the runtime-facing id, health default, and input/result mapping
 * helpers shared by openJiuwen handlers.
 */
public abstract class OpenJiuwenAgentHandler implements AgentHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenJiuwenAgentHandler.class);

    private final String agentId;
    private final OpenJiuwenMessageConverter messageConverter;
    private final OpenJiuwenResultMapper resultMapper;

    protected OpenJiuwenAgentHandler(String agentId) {
        this(agentId, new OpenJiuwenMessageConverter());
    }

    protected OpenJiuwenAgentHandler(String agentId, OpenJiuwenMessageConverter messageConverter) {
        this(agentId, messageConverter, new OpenJiuwenResultMapper());
    }

    OpenJiuwenAgentHandler(String agentId, OpenJiuwenMessageConverter messageConverter, OpenJiuwenResultMapper resultMapper) {
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
    public AgentResultAdapter resultAdapter() {
        return rawResults -> rawResults.map(this::mapRawResult);
    }

    @SuppressWarnings("unchecked")
    private com.huawei.ascend.service.engine.spi.AgentExecutionResult mapRawResult(Object rawResult) {
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
