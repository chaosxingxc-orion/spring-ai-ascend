package com.huawei.ascend.examples.a2a.gateway.config;

import com.huawei.ascend.examples.a2a.gateway.api.AgentInteractionTelemetry;
import com.huawei.ascend.examples.a2a.gateway.core.InMemoryAgentInteractionTelemetry;
import com.huawei.ascend.examples.a2a.gateway.http.GatewayHealthController;
import com.huawei.ascend.examples.a2a.gateway.http.TelemetryController;
import com.huawei.ascend.examples.a2a.gateway.model.AgentInteractionEvent;
import com.huawei.ascend.service.core.InMemoryRuntimeRegistry;
import com.huawei.ascend.service.starter.A2aForwardObserver;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sample-side telemetry over the agent-service-starter edge: the starter ships
 * the registration/discovery/route-grant controllers and an
 * {@link A2aForwardObserver} seam with a no-op default; this configuration
 * plugs the sample's in-memory interaction store into that seam and exposes it
 * over the sample's query/health endpoints.
 */
@Configuration(proxyBeanMethods = false)
public class GatewayTelemetryConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AgentInteractionTelemetry agentInteractionTelemetry() {
        return new InMemoryAgentInteractionTelemetry();
    }

    @Bean
    A2aForwardObserver telemetryForwardObserver(AgentInteractionTelemetry telemetry) {
        return completion -> telemetry.record(new AgentInteractionEvent(
                "event-" + UUID.randomUUID(),
                "A2A_GATEWAY_FORWARD_COMPLETED",
                Instant.now(),
                completion.tenantId(),
                "agent-examples-gateway",
                completion.sourceAgentId(),
                completion.runtimeInstanceId(),
                completion.targetAgentId(),
                completion.sessionId(),
                null,
                completion.correlationId(),
                null,
                completion.grantId(),
                completion.a2aMethod(),
                completion.status(),
                completion.routeResolveLatency().toMillis(),
                completion.firstByteLatency().toMillis(),
                completion.totalLatency().toMillis(),
                completion.requestBytes(),
                completion.responseBytes(),
                completion.errorCode(),
                null,
                null,
                Map.of("gatewayForward", true)));
    }

    @Bean
    @ConditionalOnMissingBean
    TelemetryController telemetryController(AgentInteractionTelemetry telemetry) {
        return new TelemetryController(telemetry);
    }

    @Bean
    @ConditionalOnMissingBean
    GatewayHealthController gatewayHealthController(
            InMemoryRuntimeRegistry registry,
            AgentInteractionTelemetry telemetry) {
        return new GatewayHealthController(registry, telemetry);
    }
}
