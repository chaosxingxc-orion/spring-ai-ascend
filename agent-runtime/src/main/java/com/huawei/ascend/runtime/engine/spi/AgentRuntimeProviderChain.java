package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes an {@link AgentRuntimeHandler} together with its optional providers.
 *
 * <p>The runtime bridge uses this helper so provider ordering and failure
 * isolation stay consistent across Agent framework adapters.
 */
public final class AgentRuntimeProviderChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentRuntimeProviderChain.class);

    private AgentRuntimeProviderChain() {
    }

    public static Stream<?> execute(AgentRuntimeHandler handler, AgentExecutionContext context) {
        List<AgentRuntimeProvider> providers = List.copyOf(handler.providers());
        int enteredProviders = 0;
        try {
            for (AgentRuntimeProvider provider : providers) {
                provider.beforeExecute(context);
                enteredProviders++;
            }
        } catch (RuntimeException ex) {
            closeEntered(providers, context, enteredProviders);
            throw ex;
        }
        AtomicBoolean closed = new AtomicBoolean(false);
        try {
            Stream<?> results = Objects.requireNonNull(handler.execute(context), "handler result stream");
            return results.onClose(() -> closeOnce(providers, context, providers.size(), closed));
        } catch (RuntimeException ex) {
            closeOnce(providers, context, providers.size(), closed);
            throw ex;
        }
    }

    private static void closeOnce(
            List<AgentRuntimeProvider> providers,
            AgentExecutionContext context,
            int enteredProviders,
            AtomicBoolean closed) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeEntered(providers, context, enteredProviders);
    }

    private static void closeEntered(
            List<AgentRuntimeProvider> providers, AgentExecutionContext context, int enteredProviders) {
        for (int index = enteredProviders - 1; index >= 0; index--) {
            AgentRuntimeProvider provider = providers.get(index);
            try {
                provider.afterExecute(context);
            } catch (RuntimeException ex) {
                LOGGER.warn("agent runtime provider afterExecute failed tenantId={} sessionId={} taskId={} agentId={} provider={} errorClass={} message={}",
                        context.getScope().tenantId(),
                        context.getScope().sessionId(),
                        context.getScope().taskId(),
                        context.getScope().agentId(),
                        provider.getClass().getName(),
                        ex.getClass().getSimpleName(),
                        ex.getMessage());
            }
        }
    }
}
