package com.huawei.ascend.runtime.boot;

import com.huawei.ascend.runtime.engine.spi.LocalFsPayloadRefStore;
import com.huawei.ascend.runtime.engine.spi.PayloadRefStore;
import com.huawei.ascend.runtime.engine.spi.Redactor;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.a2aproject.sdk.server.tasks.BasePushNotificationSender;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RuntimeAccessProperties.class, TrajectoryProperties.class,
        AgentCardProperties.class})
@Import({A2aInfrastructureConfiguration.class, RuntimeLifecycleConfiguration.class,
        A2aExecutionConfiguration.class, TrajectoryOtelConfiguration.class})
public class RuntimeAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RuntimeAutoConfiguration.class);

    /**
     * Kept on the outer class so existing unit tests that instantiate
     * {@code RuntimeAutoConfiguration} directly can call this as a factory
     * method without going through the Spring context.
     */
    @Bean @ConditionalOnMissingBean
    public PushNotificationSender a2aPushSender(PushNotificationConfigStore store) {
        log.info("A2A push notification sender enabled with {}", store.getClass().getSimpleName());
        return new BasePushNotificationSender(store);
    }

    static TrajectorySettings toTrajectorySettings(TrajectoryProperties properties) {
        return toTrajectorySettings(properties, null);
    }

    static TrajectorySettings toTrajectorySettings(TrajectoryProperties properties, Redactor redactor) {
        TrajectoryProperties.PayloadRef refCfg = properties.getPayloadRef();
        PayloadRefStore store = null;
        int refThreshold = 0;
        Set<String> refFields = Set.of();
        if (refCfg.isEnabled() && refCfg.getBaseDir() != null && !refCfg.getBaseDir().isBlank()
                && !refCfg.getFields().isEmpty()) {
            store = new LocalFsPayloadRefStore(Path.of(refCfg.getBaseDir()));
            refThreshold = refCfg.getThreshold();
            refFields = new HashSet<>(refCfg.getFields());
        }
        return TrajectorySettings.from(properties.isEnabled(), properties.getMask().getKeyPattern(),
                properties.getMask().getTruncateChars(), properties.getSampleRate(), redactor,
                store, refThreshold, refFields);
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
