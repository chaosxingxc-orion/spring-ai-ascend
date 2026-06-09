package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes an {@link AgentRuntimeHandler} together with its optional extensions.
 *
 * <p>The dispatcher uses this helper so extension ordering and failure
 * isolation stay consistent across Agent framework adapters.
 */
public final class AgentRuntimeExtensions {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentRuntimeExtensions.class);

    private AgentRuntimeExtensions() {
    }

    public static Stream<?> execute(AgentRuntimeHandler handler, AgentExecutionContext context) {
        List<AgentRuntimeExtension> extensions = List.copyOf(handler.extensions());
        extensions.forEach(extension -> extension.beforeExecute(context));
        AtomicBoolean closed = new AtomicBoolean(false);
        try {
            Stream<?> results = Objects.requireNonNull(handler.execute(context), "handler result stream");
            return results.onClose(() -> closeOnce(extensions, context, closed));
        } catch (RuntimeException ex) {
            closeOnce(extensions, context, closed);
            throw ex;
        }
    }

    private static void closeOnce(
            List<AgentRuntimeExtension> extensions, AgentExecutionContext context, AtomicBoolean closed) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (int index = extensions.size() - 1; index >= 0; index--) {
            AgentRuntimeExtension extension = extensions.get(index);
            try {
                extension.afterExecute(context);
            } catch (RuntimeException ex) {
                LOGGER.warn("agent runtime extension afterExecute failed tenantId={} sessionId={} taskId={} agentId={} extension={} errorClass={} message={}",
                        context.getScope().tenantId(),
                        context.getScope().sessionId(),
                        context.getScope().taskId(),
                        context.getScope().agentId(),
                        extension.getClass().getName(),
                        ex.getClass().getSimpleName(),
                        ex.getMessage());
            }
        }
    }
}

