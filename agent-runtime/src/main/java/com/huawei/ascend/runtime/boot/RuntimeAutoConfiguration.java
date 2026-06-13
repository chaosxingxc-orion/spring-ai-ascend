package com.huawei.ascend.runtime.boot;

import com.huawei.ascend.runtime.engine.a2a.A2aAgentExecutor;
import com.huawei.ascend.runtime.engine.a2a.AgentCardProperties;
import com.huawei.ascend.runtime.engine.a2a.AgentCardProvider;
import com.huawei.ascend.runtime.engine.a2a.RemoteAgentCardCache;
import com.huawei.ascend.runtime.engine.a2a.RemoteAgentInvocationService;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import com.huawei.ascend.runtime.engine.spi.TrajectorySinkFactory;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.a2aproject.sdk.server.config.DefaultValuesConfigProvider;
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
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RuntimeAccessProperties.class, TrajectoryProperties.class,
        AgentCardProperties.class})
@Import(TrajectoryOtelConfiguration.class)
public class RuntimeAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(RuntimeAutoConfiguration.class);

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
    public PushNotificationSender a2aPushSender(PushNotificationConfigStore store) {
        log.info("A2A push notification sender enabled with {}", store.getClass().getSimpleName());
        return new BasePushNotificationSender(store);
    }

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

    @Bean @ConditionalOnMissingBean
    public A2aServerExecutor a2aServerExecutor() { return new A2aServerExecutor(); }

    @Bean @ConditionalOnMissingBean
    public RuntimeReadiness runtimeReadiness() { return new RuntimeReadiness(); }

    @Bean @ConditionalOnMissingBean
    public AgentRuntimeLifecycle agentRuntimeLifecycle(ObjectProvider<AgentRuntimeHandler> handlers,
            RuntimeReadiness readiness) {
        return new AgentRuntimeLifecycle(handlers.orderedStream().toList(), readiness);
    }

    /**
     * Isolated in a nested class because actuator is an optional dependency: a bean
     * method on the outer class whose signature mentions {@link AgentRuntimeHealthIndicator}
     * (which implements HealthIndicator) makes reflective introspection of the whole
     * auto-configuration throw NoClassDefFoundError in hosts without actuator. The
     * condition is evaluated from class metadata, so the nested class is never loaded
     * unless HealthIndicator is present.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    static class HealthIndicatorConfiguration {

        @Bean @ConditionalOnMissingBean
        AgentRuntimeHealthIndicator agentRuntimeHealthIndicator(ObjectProvider<AgentRuntimeHandler> handlers,
                RuntimeReadiness readiness, ObjectProvider<RemoteAgentCardCache> remoteAgentCardCache) {
            return new AgentRuntimeHealthIndicator(handlers.orderedStream().toList(), readiness,
                    remoteAgentCardCache.getIfAvailable());
        }
    }

    @Bean @ConditionalOnMissingBean
    public AgentExecutor a2aAgentExecutor(ObjectProvider<AgentRuntimeHandler> handlers,
            ObjectProvider<RemoteAgentInvocationService> remoteInvocationService,
            RuntimeReadiness readiness, TrajectoryProperties trajectoryProperties,
            ObjectProvider<TrajectorySinkFactory> sinkFactories) {
        var registered = handlers.orderedStream().toList();
        RemoteAgentInvocationService invocationService = remoteInvocationService.getIfAvailable();
        if (registered.isEmpty()) {
            // Tolerated so the A2A surface can boot for card discovery; every
            // execution will be rejected until a handler bean is registered.
            log.warn("No AgentRuntimeHandler registered - A2A executions will be rejected");
            return new A2aAgentExecutor(null, invocationService, readiness::isReady);
        }
        if (registered.size() > 1) {
            throw new IllegalStateException(
                    "Multiple AgentRuntimeHandler beans registered but the runtime hosts exactly one agent."
                    + " Found: " + registered.stream().map(AgentRuntimeHandler::agentId).toList()
                    + ". Register exactly one AgentRuntimeHandler bean, or split agents into separate"
                    + " runtime instances.");
        }
        return new A2aAgentExecutor(registered.get(0), invocationService, readiness::isReady,
                toTrajectorySettings(trajectoryProperties), sinkFactories.orderedStream().toList());
    }

    static TrajectorySettings toTrajectorySettings(TrajectoryProperties properties) {
        if (!properties.isEnabled()) {
            return TrajectorySettings.off();
        }
        return new TrajectorySettings(true, compileMaskPattern(properties.getMask().getKeyPattern()),
                properties.getMask().getTruncateChars());
    }

    /**
     * Compiles the configured mask pattern, falling back to the default on a bad regex. A masking
     * typo must never crash boot, and must never degrade to a null pattern (which would silently
     * disable key redaction) — it fails safe toward the default pattern, with a WARN.
     */
    private static Pattern compileMaskPattern(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (RuntimeException e) {
            log.warn("invalid app.trajectory.mask.key-pattern '{}'; falling back to default ({})",
                    pattern, e.getMessage());
            return Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN);
        }
    }

    @Bean @ConditionalOnMissingBean
    public RequestHandler a2aRequestHandler(AgentExecutor agentExecutor, TaskStore store,
            QueueManager queueManager, PushNotificationConfigStore pushStore, MainEventBusProcessor eventBus,
            A2aServerExecutor exec) {
        return DefaultRequestHandler.create(agentExecutor, store, queueManager, pushStore, eventBus,
                exec.executor(), exec.executor());
    }

    /**
     * Default agent card: an explicit {@code agent-card.name} wins, then the
     * configured {@code default-agent-id} selects among registered handlers (with
     * a WARN when it matches none), then the first registered handler. The card
     * shape itself comes from {@link AgentCardProperties#createAgentCard(String)},
     * which delegates to the canonical {@code AgentCards} factory so YAML-driven
     * fields override rather than fork the card construction.
     */
    @Bean @ConditionalOnMissingBean
    public AgentCard a2aAgentCard(ObjectProvider<AgentCardProvider> cardProviders,
                                   ObjectProvider<AgentRuntimeHandler> handlers,
                                   RuntimeAccessProperties access,
                                   AgentCardProperties cardProperties) {
        var cp = cardProviders.getIfAvailable();
        if (cp != null) {
            return cp.agentCard();
        }
        String name;
        if (cardProperties.hasExplicitName()) {
            name = cardProperties.getName();
        } else {
            List<String> agentIds = handlers.orderedStream().map(AgentRuntimeHandler::agentId).toList();
            String configured = access.getDefaultAgentId();
            if (configured != null && !configured.isBlank() && agentIds.contains(configured.trim())) {
                name = configured.trim();
            } else {
                if (configured != null && !configured.isBlank()) {
                    log.warn("agent-runtime.access.a2a.default-agent-id '{}' matches no registered handler;"
                            + " available agent ids: {}", configured.trim(), agentIds);
                }
                name = agentIds.isEmpty() ? "agent" : agentIds.get(0);
            }
        }
        return cardProperties.createAgentCard(name);
    }

    /**
     * Registers the northbound HTTP surface for hosts that only depend on the jar:
     * without these bean methods a pure-dependency host boots with the full engine
     * wired but every northbound route silently 404s, because the controllers are
     * plain {@code @RestController} classes that only component scanning would find.
     * Hosts that do scan {@code runtime.boot} get the same beans by stereotype; the
     * {@code @ConditionalOnMissingBean} guards keep the two paths from colliding.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class NorthboundControllerConfiguration {

        @Bean @ConditionalOnMissingBean
        A2aJsonRpcController a2aJsonRpcController(RequestHandler handler, RuntimeAccessProperties access) {
            return new A2aJsonRpcController(handler, access);
        }

        @Bean @ConditionalOnMissingBean
        AgentCardController agentCardController(AgentCard agentCard, RuntimeAccessProperties access) {
            return new AgentCardController(agentCard, access);
        }
    }

    /**
     * Holder for the pool that runs A2A agent executions. Deliberately NOT exposed
     * as a {@code java.util.concurrent.Executor} bean directly: Spring Boot's
     * applicationTaskExecutor backs off when any Executor bean exists, so a broad
     * Executor bean here would silently disable the application's default task
     * executor (including the virtual-thread executor) or vice versa.
     *
     * <p>The drain runs in the {@link SmartLifecycle} stop phases, not at bean
     * destroy: Spring finishes every lifecycle {@code stop()} — including the
     * phase-0 {@link AgentRuntimeLifecycle} that calls {@code handler.stop()} —
     * before any destroy callback, so a destroy-time drain would let in-flight
     * executions run against handlers whose resources are already released.
     * Stop order by phase: web server stops accepting requests, this drain
     * waits out the in-flight executions, then the handlers release.
     */
    public static final class A2aServerExecutor implements SmartLifecycle, AutoCloseable {
        private static final AtomicInteger THREAD_SEQ = new AtomicInteger();
        private static final java.time.Duration DRAIN_GRACE = java.time.Duration.ofSeconds(10);
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "a2a-server-" + THREAD_SEQ.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        private volatile boolean running;

        public ExecutorService executor() { return executor; }

        @Override
        public void start() {
            // Participating in start is what makes the container call stop()
            // during the lifecycle stop phases.
            running = true;
        }

        @Override
        public void stop() {
            running = false;
            drain();
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            // Above AgentRuntimeLifecycle (phase 0) and below the web-server
            // phases, so on shutdown the drain runs after dispatch stopped and
            // before the handlers release their resources.
            return 1024;
        }

        @Override
        public void close() {
            // Fallback for non-lifecycle usage (direct construction, plain bean
            // destroy); after a lifecycle stop() the pool is already terminated
            // and this returns immediately.
            drain();
        }

        private void drain() {
            if (executor.isTerminated()) {
                return;
            }
            // Drain, don't interrupt: dispatch upstream has already stopped, so
            // in-flight executions get a grace window to finish before the
            // force-stop fallback.
            executor.shutdown();
            try {
                if (!executor.awaitTermination(DRAIN_GRACE.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
