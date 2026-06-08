package com.huawei.ascend.runtime.app;

import com.huawei.ascend.runtime.access.api.NotificationPort;
import com.huawei.ascend.runtime.access.core.AccessSubmissionService;
import com.huawei.ascend.runtime.access.output.EngineOutputSink;
import com.huawei.ascend.runtime.engine.port.AccessLayerClient;
import com.huawei.ascend.runtime.session.api.SessionManager;
import com.huawei.ascend.runtime.control.api.TaskControlApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runtime wiring configuration: wires the cross-layer seams that connect the
 * independently-configured layers into one working runtime.
 *
 * <ul>
 *   <li>{@link AccessSubmissionService} - inbound: access layer to task-centric-control.</li>
 *   <li>{@link EngineOutputSink} - outbound: engine to access layer.</li>
 * </ul>
 *
 * <p>The access, session, queue, task-control and engine modules each ship their
 * own auto-configuration; this configuration only supplies the two adapters that
 * deliberately live outside any single module because they bridge two of them.
 */
@Configuration(proxyBeanMethods = false)
public class RuntimeWiringConfiguration {

    /**
     * Inbound seam. Access resolves the session before submitting to task
     * control, while task control owns task-id allocation and lifecycle state.
     */
    @Bean
    @ConditionalOnMissingBean(AccessSubmissionService.class)
    public AccessSubmissionService accessSubmissionService(
            TaskControlApi taskControlClient,
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
        return new EngineOutputSink(notificationPort);
    }
}
