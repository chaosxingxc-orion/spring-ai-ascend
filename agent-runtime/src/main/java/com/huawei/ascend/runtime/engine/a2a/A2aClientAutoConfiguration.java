package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenRemoteToolInstaller;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.a2aproject.sdk.spec.AgentCard;
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
 * A2A auto-configuration — agent-card discovery plus remote-agent client wiring.
 *
 * <p>The outer class registers the {@link AgentCard} bean unconditionally.
 * Remote-agent support (outbound adapter, invocation service, card cache) is
 * activated only when at least one remote agent URL is configured via
 * {@code agent-runtime.remote-agents[0].url}.
 *
 * <p>The nested {@link OpenJiuwenRemoteToolConfiguration} is isolated behind a
 * class-level condition so the enclosing config does not depend on OpenJiuwen types.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AgentCardProperties.class, RemoteAgentProperties.class})
public class A2aClientAutoConfiguration {

    // ── AgentCard (always available) ──

    @Bean
    @ConditionalOnMissingBean
    public AgentCard a2aAgentCard(ObjectProvider<AgentCardProvider> cardProviders,
                                   ObjectProvider<AgentRuntimeHandler> handlers,
                                   AgentCardProperties cardProperties) {
        var cp = cardProviders.getIfAvailable();
        if (cp != null) {
            return cp.agentCard();
        }
        String name = handlers.orderedStream().map(AgentRuntimeHandler::agentId).findFirst().orElse("agent");
        if (cardProperties.hasExplicitName()) {
            name = cardProperties.getName();
        }
        return cardProperties.createAgentCard(name);
    }

    // ── Remote-agent support (conditional) ──

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "agent-runtime.remote-agents.0", name = "url")
    public static class RemoteConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public RemoteAgentCardCache remoteAgentCardCache(RemoteAgentProperties properties) {
            return new RemoteAgentCardCache(properties.urls());
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

        @Configuration(proxyBeanMethods = false)
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

        /** Polls the card cache so remote runtimes that boot later (or restart) become callable without a redeploy. */
        public static final class RemoteAgentCardCacheRefresher implements SmartLifecycle {
            private static final Logger LOG = LoggerFactory.getLogger(RemoteAgentCardCacheRefresher.class);

            private final RemoteAgentCardCache cardCache;
            private final ExecutorService executor;
            private final AtomicBoolean running = new AtomicBoolean();

            public RemoteAgentCardCacheRefresher(RemoteAgentCardCache cardCache, ExecutorService executor) {
                this.cardCache = cardCache;
                this.executor = executor;
            }

            @Override
            public void start() {
                if (running.compareAndSet(false, true)) {
                    LOG.info("remote agent card cache refresher started intervalMs=5000");
                    executor.execute(this::run);
                }
            }

            public void refreshOnce() {
                cardCache.refreshPending();
            }

            private void run() {
                while (running.get()) {
                    refreshOnce();
                    try {
                        Thread.sleep(5_000L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        running.set(false);
                    }
                }
            }

            @Override
            public void stop() {
                LOG.info("remote agent card cache refresher stopping");
                running.set(false);
            }

            @Override
            public boolean isRunning() {
                return running.get();
            }
        }
    }
}
