package com.huawei.ascend.runtime.boot;

import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.a2aproject.sdk.server.config.DefaultValuesConfigProvider;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the A2A protocol infrastructure: config provider, task store, push
 * notification plumbing, and the event bus that connects them.
 */
@Configuration(proxyBeanMethods = false)
class A2aInfrastructureConfiguration {

    private static final Logger log = LoggerFactory.getLogger(A2aInfrastructureConfiguration.class);

    @Bean @ConditionalOnMissingBean
    public A2AConfigProvider a2aConfigProvider() {
        // Field-injected into DefaultRequestHandler (@Inject) and read by its
        // @PostConstruct initConfig(), which resolves the blocking-send timeouts
        // (a2a.blocking.agent.timeout.seconds) - override this bean to tune them.
        return new DefaultValuesConfigProvider();
    }

    @Bean @ConditionalOnMissingBean(TaskStore.class)
    public InMemoryTaskStore a2aTaskStore() { return new InMemoryTaskStore(); }

    @Bean @ConditionalOnMissingBean
    public PushNotificationConfigStore a2aPushConfigStore() { return new InMemoryPushNotificationConfigStore(); }

    @Bean @ConditionalOnMissingBean
    public MainEventBus a2aMainEventBus() {
        return new MainEventBus();
    }

    @Bean @ConditionalOnMissingBean
    public QueueManager a2aQueueManager(TaskStateProvider taskStateProvider, MainEventBus eventBus) {
        return new InMemoryQueueManager(taskStateProvider, eventBus);
    }

    @Bean @ConditionalOnMissingBean
    public MainEventBusProcessor a2aEventBus(TaskStore store,
                                              QueueManager qm, PushNotificationSender sender,
                                              MainEventBus eventBus) {
        var p = new MainEventBusProcessor(eventBus, store, sender, qm);
        // The SDK's own lifecycle runs the loop on a daemon thread, so a hosting
        // JVM can always exit; submitting the loop to a regular pool thread parks
        // it in MainEventBus.take() forever and blocks JVM shutdown.
        p.ensureStarted();
        return p;
    }
}
