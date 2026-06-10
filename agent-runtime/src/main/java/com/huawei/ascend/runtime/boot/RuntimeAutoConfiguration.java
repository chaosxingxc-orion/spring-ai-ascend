package com.huawei.ascend.runtime.boot;

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
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RuntimeAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(RuntimeAutoConfiguration.class);

    @Bean @ConditionalOnMissingBean
    public A2AConfigProvider a2aConfigProvider() { return new DefaultValuesConfigProvider(); }

    @Bean @ConditionalOnMissingBean
    public InMemoryTaskStore a2aTaskStore() { return new InMemoryTaskStore(); }

    @Bean @ConditionalOnMissingBean
    public PushNotificationConfigStore a2aPushConfigStore() { return new InMemoryPushNotificationConfigStore(); }

    @Bean @ConditionalOnMissingBean
    public PushNotificationSender a2aPushSender(PushNotificationConfigStore store) {
        log.info("A2A push notification sender enabled with {}", store.getClass().getSimpleName());
        return new BasePushNotificationSender(store);
    }

    @Bean @ConditionalOnMissingBean
    public MainEventBus a2aMainEventBus() {
        return new MainEventBus();
    }

    @Bean @ConditionalOnMissingBean
    public QueueManager a2aQueueManager(InMemoryTaskStore store, MainEventBus eventBus) {
        return new InMemoryQueueManager(store, eventBus);
    }

    @Bean @ConditionalOnMissingBean
    public MainEventBusProcessor a2aEventBus(InMemoryTaskStore store,
                                              QueueManager qm, PushNotificationSender sender,
                                              MainEventBus eventBus, Executor exec) {
        var p = new MainEventBusProcessor(eventBus, store, sender, qm);
        exec.execute(p); // run on background thread
        return p;
    }

    @Bean(destroyMethod = "shutdown") @ConditionalOnMissingBean
    public Executor a2aExecutor() { return Executors.newCachedThreadPool(); }

    @Bean @ConditionalOnMissingBean
    public AgentExecutor a2aAgentExecutor(ObjectProvider<AgentRuntimeHandler> handlers) {
        return new A2aAgentExecutor(handlers.orderedStream().findFirst().orElse(null));
    }

    @Bean @ConditionalOnMissingBean
    public RequestHandler a2aRequestHandler(AgentExecutor agentExecutor, InMemoryTaskStore store,
            QueueManager queueManager, PushNotificationConfigStore pushStore, MainEventBusProcessor eventBus,
            Executor exec) {
        return DefaultRequestHandler.create(agentExecutor, store, queueManager, pushStore, eventBus, exec, exec);
    }

    @Bean @ConditionalOnMissingBean
    public AgentCard a2aAgentCard(ObjectProvider<AgentCardProvider> cardProviders,
                                   ObjectProvider<AgentRuntimeHandler> handlers) {
        var cp = cardProviders.getIfAvailable();
        if (cp != null) return cp.agentCard();
        String name = handlers.orderedStream().map(AgentRuntimeHandler::agentId).findFirst().orElse("agent");
        return AgentCard.builder().name(name).description("agent-runtime").url("/a2a").version("0.1.0")
                .provider(new AgentProvider("spring-ai-ascend", "http://localhost:8080"))
                .capabilities(AgentCapabilities.builder().streaming(true).pushNotifications(true).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text")).skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a"))).build();
    }
}
