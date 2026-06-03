package com.huawei.ascend.service.bootstrap;

import com.huawei.ascend.service.access.api.NotificationPort;
import com.huawei.ascend.service.access.core.AccessSubmissionService;
import com.huawei.ascend.service.engine.port.AccessLayerClient;
import com.huawei.ascend.service.session.api.SessionManager;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bootstrap glue configuration: wires the cross-module seams that connect the
 * five independently-configured layers into one working runtime.
 *
 * <ul>
 *   <li>{@link AccessSubmissionService} - inbound: access layer to task-centric-control.</li>
 *   <li>{@link AccessNotificationClient} - outbound: engine to access layer.</li>
 * </ul>
 *
 * <p>The access, session, queue, task-control and engine modules each ship their
 * own auto-configuration; this configuration only supplies the two adapters that
 * deliberately live outside any single module because they bridge two of them.
 */
@Configuration(proxyBeanMethods = false)
public class AgentServiceBootstrapConfiguration {

    /**
     * Inbound seam. Access resolves the session before submitting to task
     * control, while task control owns task-id allocation and lifecycle state.
     */
    @Bean
    @ConditionalOnMissingBean(AccessSubmissionService.class)
    public AccessSubmissionService accessSubmissionService(
            TaskControlClient taskControlClient,
            SessionManager sessionManager) {
        return new AccessSubmissionService(taskControlClient, sessionManager);
    }

    /**
     * Outbound seam. Providing this bean satisfies the engine's
     * {@code @ConditionalOnBean(AccessLayerClient.class)} guard and lets engine
     * output flow back to the caller.
     */
    @Bean
    @ConditionalOnMissingBean(AccessLayerClient.class)
    public AccessLayerClient accessNotificationClient(NotificationPort notificationPort) {
        return new AccessNotificationClient(notificationPort);
    }
}
