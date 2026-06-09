package com.huawei.ascend.runtime.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.AgentRuntimeHandlerRegistry;
import com.huawei.ascend.runtime.engine.DefaultAgentRuntimeHandlerRegistry;
import com.huawei.ascend.runtime.engine.a2a.A2aAgentExecutor;
import com.huawei.ascend.runtime.engine.spi.AgentCardProvider;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.a2aproject.sdk.server.config.DefaultValuesConfigProvider;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.BasePushNotificationSender;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RuntimeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public A2AConfigProvider a2aConfigProvider() {
        return new DefaultValuesConfigProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public InMemoryTaskStore a2aTaskStore() {
        return new InMemoryTaskStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public PushNotificationConfigStore a2aPushConfigStore() {
        return new InMemoryPushNotificationConfigStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public PushNotificationSender a2aPushNotificationSender(PushNotificationConfigStore store) {
        return new BasePushNotificationSender(store);
    }

    @Bean
    @ConditionalOnMissingBean
    public QueueManager a2aQueueManager(InMemoryTaskStore store) {
        return new InMemoryQueueManager(store, new MainEventBus());
    }

    @Bean
    @ConditionalOnMissingBean
    public MainEventBusProcessor a2aMainEventBusProcessor(
            InMemoryTaskStore store, QueueManager queueManager,
            PushNotificationSender pushSender) {
        var p = new MainEventBusProcessor(new MainEventBus(), store, pushSender, queueManager);
        p.ensureStarted();
        return p;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public Executor runtimeExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentRuntimeHandlerRegistry agentRuntimeHandlerRegistry(
            ObjectProvider<AgentRuntimeHandler> handlers) {
        DefaultAgentRuntimeHandlerRegistry registry = new DefaultAgentRuntimeHandlerRegistry();
        handlers.orderedStream().forEach(h -> registry.register(h.agentId(), h));
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentExecutor a2aAgentExecutor(ObjectProvider<AgentRuntimeHandler> handlers) {
        AgentRuntimeHandler handler = handlers.orderedStream().findFirst().orElse(null);
        if (handler == null) {
            return new NoopAgentExecutor();
        }
        return new A2aAgentExecutor(handler);
    }

    /** No-op when no handler registered (gateway-only deployments). */
    private static class NoopAgentExecutor implements AgentExecutor {
        @Override public void execute(org.a2aproject.sdk.server.agentexecution.RequestContext ctx,
                                       org.a2aproject.sdk.server.tasks.AgentEmitter emitter) {
            emitter.fail();
        }
        @Override public void cancel(org.a2aproject.sdk.server.agentexecution.RequestContext ctx,
                                      org.a2aproject.sdk.server.tasks.AgentEmitter emitter) {
            emitter.cancel();
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestHandler a2aRequestHandler(
            AgentExecutor agentExecutor, InMemoryTaskStore store,
            QueueManager queueManager, PushNotificationConfigStore pushStore,
            MainEventBusProcessor busProcessor, Executor runtimeExecutor) {
        return DefaultRequestHandler.create(
                agentExecutor, store, queueManager,
                pushStore, busProcessor, runtimeExecutor, runtimeExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentCard agentCard(
            ObjectProvider<AgentCardProvider> cardProviders,
            ObjectProvider<AgentRuntimeHandler> handlers) {
        AgentCardProvider cardProvider = cardProviders.getIfAvailable();
        if (cardProvider != null) {
            return cardProvider.agentCard();
        }
        String name = handlers.orderedStream()
                .map(AgentRuntimeHandler::agentId)
                .findFirst().orElse("agent-runtime");
        return AgentCard.builder()
                .name(name)
                .description("Agent hosted by spring-ai-ascend runtime")
                .url("/a2a").version("0.1.0")
                .provider(new AgentProvider("spring-ai-ascend", "http://localhost:8080"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true).pushNotifications(true).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
                .build();
    }
}
