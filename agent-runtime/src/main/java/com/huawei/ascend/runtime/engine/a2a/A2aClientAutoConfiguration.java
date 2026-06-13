package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenRemoteToolInstaller;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A2A remote-agent client wiring (outbound adapter, invocation service, card
 * cache), activated only when at least one remote agent URL is configured via
 * {@code agent-runtime.remote-agents[0].url}: the runtime perceives the remote
 * agents it can call as tools through its own deployment file, the same way any
 * service declares its outbound dependencies.
 *
 * <p>The nested {@link OpenJiuwenRemoteToolConfiguration} is isolated behind a
 * class-level condition so the enclosing config does not depend on OpenJiuwen types.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "agent-runtime.remote-agents.0", name = "url")
@EnableConfigurationProperties(RemoteAgentProperties.class)
public class A2aClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RemoteAgentCardCache remoteAgentCardCache(RemoteAgentProperties properties) {
        // The cache carries the per-remote configured stream timeout alongside
        // the endpoint, so downstream callers resolve both per remoteAgentId.
        return new RemoteAgentCardCache(properties.urls(), properties.streamTimeouts());
    }

    @Bean
    @ConditionalOnMissingBean
    public A2aRemoteAgentOutboundAdapter a2aRemoteAgentOutboundAdapter(RemoteAgentCardCache cardCache) {
        return new A2aRemoteAgentOutboundAdapter(cardCache);
    }

    @Bean
    @ConditionalOnMissingBean
    public RemoteAgentInvocationService remoteAgentInvocationService(
            A2aRemoteAgentOutboundAdapter outboundAdapter) {
        return new RemoteAgentInvocationService(outboundAdapter);
    }

    @Bean
    @ConditionalOnMissingBean
    public RemoteAgentCardCacheRefresher remoteAgentCardCacheRefresher(RemoteAgentCardCache cardCache) {
        return new RemoteAgentCardCacheRefresher(cardCache, cardCacheRefreshExecutor());
    }

    private static ExecutorService cardCacheRefreshExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "a2a-card-refresh");
            thread.setDaemon(true);
            return thread;
        });
    }

    // ── OpenJiuwen-specific tool wiring ──

    /**
     * Isolated in a nested class because the openJiuwen framework is an optional
     * dependency: a bean method whose signature mentions openJiuwen-typed classes
     * makes reflective introspection of the enclosing configuration throw
     * NoClassDefFoundError in hosts without the framework. The condition is
     * evaluated from class metadata, so this nested class is never loaded unless
     * openJiuwen is present. The remote-agents property guard is REPEATED here
     * because classpath scanning registers nested configuration classes
     * independently of the enclosing class — the outer @ConditionalOnProperty
     * does not cascade.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "agent-runtime.remote-agents.0", name = "url")
    @ConditionalOnClass(name = "com.openjiuwen.core.singleagent.BaseAgent")
    static class OpenJiuwenRemoteToolConfiguration {
        private static final Logger LOG = LoggerFactory.getLogger(OpenJiuwenRemoteToolConfiguration.class);

        @Bean
        @ConditionalOnMissingBean
        public OpenJiuwenRemoteToolInstaller openJiuwenRemoteToolInstaller(
                RemoteAgentCardCache cardCache,
                ObjectProvider<OpenJiuwenAgentRuntimeHandler> handlers) {
            OpenJiuwenRemoteToolInstaller installer =
                    new OpenJiuwenRemoteToolInstaller(cardCache::availableToolSpecs);
            int count = 0;
            for (OpenJiuwenAgentRuntimeHandler handler : handlers.orderedStream().toList()) {
                handler.setRuntimeToolInstaller(installer);
                count++;
                LOG.info("installed remote tool installer into openjiuwen handler agentId={}",
                        handler.agentId());
            }
            if (count == 0) {
                LOG.warn("remote tool installer created but no OpenJiuwenAgentRuntimeHandler beans found");
            }
            return installer;
        }
    }

    // ── Card cache refresher ──

    /**
     * Polls the card cache with an adaptive interval: every 10 s while any
     * remote is not yet reachable, backing off to 10 min once all configured
     * remotes have been resolved. This keeps log noise low in steady state
     * while retrying quickly when an agent boots later or is temporarily down.
     */
    public static final class RemoteAgentCardCacheRefresher implements SmartLifecycle {
        private static final Logger LOG = LoggerFactory.getLogger(RemoteAgentCardCacheRefresher.class);

        private static final long FAST_RETRY_MS = 10_000L;
        private static final long KEEPALIVE_MS = 600_000L;
        /** Jitter fraction applied to avoid thundering-herd on remote agents. */
        private static final double JITTER_FRACTION = 0.1;

        private final RemoteAgentCardCache cardCache;
        private final ExecutorService executor;
        private final AtomicBoolean running = new AtomicBoolean();
        private long backoffMs = FAST_RETRY_MS;

        public RemoteAgentCardCacheRefresher(RemoteAgentCardCache cardCache, ExecutorService executor) {
            this.cardCache = cardCache;
            this.executor = executor;
        }

        @Override
        public void start() {
            if (running.compareAndSet(false, true)) {
                LOG.info("remote agent card cache refresher started fastRetryMs={} keepaliveMs={}",
                        FAST_RETRY_MS, KEEPALIVE_MS);
                executor.execute(this::run);
            }
        }

        public void refreshOnce() {
            cardCache.refresh();
        }

        private void run() {
            while (running.get()) {
                boolean allSucceeded = cardCache.refresh();
                long delay;
                if (allSucceeded) {
                    backoffMs = FAST_RETRY_MS;
                    delay = KEEPALIVE_MS;
                } else {
                    delay = backoffMs;
                    backoffMs = Math.min(backoffMs * 2, KEEPALIVE_MS);
                }
                // Add ±JITTER_FRACTION/2 to avoid thundering herd
                delay = jitter(delay);
                LOG.debug("remote agent card refresh {} nextRefreshIn={}s",
                        allSucceeded ? "succeeded" : "failed", delay / 1000);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    running.set(false);
                }
            }
        }

        private static long jitter(long base) {
            double range = base * JITTER_FRACTION;
            return base + (long) (range * (Math.random() - 0.5) * 2);
        }

        @Override
        public void stop() {
            LOG.info("remote agent card cache refresher stopping");
            running.set(false);
            // The loop may be sleeping up to 10 min; interrupt it so shutdown
            // does not wait out the sleep.
            executor.shutdownNow();
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }
    }
}
