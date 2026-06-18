package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryDraft;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEmitter;
import com.openjiuwen.core.session.stream.OutputSchema;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractOpenJiuwenRuntimeSupport extends AbstractAgentRuntimeHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractOpenJiuwenRuntimeSupport.class);

    private final OpenJiuwenMessageAdapter messageConverter;
    private final OpenJiuwenStreamAdapter resultMapper;

    AbstractOpenJiuwenRuntimeSupport(String agentId) {
        this(agentId, new OpenJiuwenMessageAdapter());
    }

    AbstractOpenJiuwenRuntimeSupport(String agentId, OpenJiuwenMessageAdapter messageConverter) {
        this(agentId, messageConverter, new OpenJiuwenStreamAdapter());
    }

    AbstractOpenJiuwenRuntimeSupport(String agentId, OpenJiuwenMessageAdapter messageConverter,
            OpenJiuwenStreamAdapter resultMapper) {
        super(agentId);
        this.messageConverter = Objects.requireNonNull(messageConverter, "messageConverter");
        this.resultMapper = Objects.requireNonNull(resultMapper, "resultMapper");
    }

    protected final Object toOpenJiuwenInput(AgentExecutionContext context) {
        LOGGER.info("openjiuwen input convert tenantId={} sessionId={} taskId={} agentId={} inputType={} messages={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                context.getScope().agentId(),
                context.getInputType(),
                context.getMessages().size());
        return messageConverter.toOpenJiuwenInput(context);
    }

    protected final String openJiuwenConversationId(AgentExecutionContext context) {
        String conversationId = context.getAgentStateKey();
        LOGGER.info("openjiuwen conversation resolve tenantId={} sessionId={} taskId={} agentId={} conversationId={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                context.getScope().agentId(),
                conversationId);
        return conversationId;
    }

    protected final Stream<?> flattenIterator(Iterator<Object> iterator) {
        if (iterator == null) {
            return Stream.of((Object) null);
        }
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }

    protected final Stream<?> failedResult(
            AgentExecutionContext context,
            TrajectoryEmitter trajectory,
            Throwable error) {
        LOGGER.warn("openjiuwen execute failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                error.getClass().getSimpleName(),
                errorMessage(error));
        trajectory.emit(TrajectoryDraft.error(null, "OPENJIUWEN_RUN_ERROR", errorMessage(error), null, false));
        return Stream.of(AgentExecutionResult.failed("OPENJIUWEN_RUN_ERROR", errorMessage(error)));
    }

    @Override
    public StreamAdapter resultAdapter() {
        return rawResults -> rawResults.map(this::mapRawResult).filter(Objects::nonNull);
    }

    protected AgentExecutionResult mapRawResult(Object rawResult) {
        LOGGER.info("openjiuwen raw result received type={}",
                rawResult == null ? "null" : rawResult.getClass().getName());
        if (rawResult instanceof AgentExecutionResult result) {
            return result;
        }
        if (rawResult == null) {
            return AgentExecutionResult.failed(
                    OpenJiuwenStreamAdapter.ERROR_CODE, "openjiuwen runner returned no result");
        }
        if (rawResult instanceof OutputSchema chunk) {
            return resultMapper.map(chunk);
        }
        return AgentExecutionResult.output(String.valueOf(rawResult));
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
