package com.huawei.ascend.bus.eventbus.runtime;

import com.huawei.ascend.bus.common.AgentBusBrokerProperties;
import com.huawei.ascend.bus.forwarding.runtime.relay.RelayTick;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Schedules the two-hop relay ticks on a dedicated {@link ThreadPoolTaskScheduler}
 * (relay-scheduler slice; spec {@code docs/superpowers/specs/2026-07-15-relay-scheduler-design.md}).
 *
 * <p><b>Dedicated pool, not the global @Scheduled pool:</b> {@code RegistrySchedulingConfig}
 * binds the global {@code @Scheduled} {@code TaskScheduler} to the registry probes via
 * {@code setTaskScheduler}; a {@code @Scheduled} relay method would land there (shared with
 * probes). The relay is instead driven <b>programmatically</b> ({@code scheduleWithFixedDelay})
 * on its own pool — a hung relay tick blocks only its own thread.
 */
@Configuration
@Profile("eventbus")
public class EventBusRelaySchedulingConfig {

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler relayTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("relay-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Bean
    public RelayScheduler relayScheduler(
            @Qualifier("forwardRelayTick") RelayTick forward,
            @Qualifier("responseRelayTick") RelayTick response,
            AgentBusBrokerProperties props,
            ThreadPoolTaskScheduler relayTaskScheduler) {
        return new RelayScheduler(forward, response, props.tenant(),
                props.relayTickLimit(), props.relayFixedDelayMs(), relayTaskScheduler);
    }
}
