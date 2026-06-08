package com.huawei.ascend.examples.a2a.gateway.config;

import com.huawei.ascend.examples.a2a.gateway.api.AgentDiscoveryApi;
import com.huawei.ascend.examples.a2a.gateway.api.AgentInteractionTelemetry;
import com.huawei.ascend.examples.a2a.gateway.api.RouteGrantService;
import com.huawei.ascend.examples.a2a.gateway.api.RuntimeRegistrationApi;
import com.huawei.ascend.examples.a2a.gateway.core.HmacRouteGrantService;
import com.huawei.ascend.examples.a2a.gateway.core.InMemoryAgentInteractionTelemetry;
import com.huawei.ascend.examples.a2a.gateway.core.InMemoryRuntimeRegistry;
import com.huawei.ascend.examples.a2a.gateway.core.RuntimeA2aGateway;
import com.huawei.ascend.examples.a2a.gateway.http.A2aGatewayController;
import com.huawei.ascend.examples.a2a.gateway.http.GatewayHealthController;
import com.huawei.ascend.examples.a2a.gateway.http.RouteGrantController;
import com.huawei.ascend.examples.a2a.gateway.http.RuntimeRegistryController;
import com.huawei.ascend.examples.a2a.gateway.http.TelemetryController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RuntimeRegistryConfiguration {

    @Bean
    @ConditionalOnMissingBean
    InMemoryRuntimeRegistry inMemoryRuntimeRegistry() {
        return new InMemoryRuntimeRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(RuntimeRegistrationApi.class)
    RuntimeRegistrationApi runtimeRegistrationApi(InMemoryRuntimeRegistry registry) {
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean(AgentDiscoveryApi.class)
    AgentDiscoveryApi agentDiscoveryApi(InMemoryRuntimeRegistry registry) {
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    RuntimeA2aGateway runtimeA2aGateway(AgentDiscoveryApi discoveryApi) {
        return new RuntimeA2aGateway(discoveryApi);
    }

    @Bean
    @ConditionalOnMissingBean
    RouteGrantService routeGrantService(
            AgentDiscoveryApi discoveryApi,
            @Value("${sample.gateway.route-grant-secret:${SAA_SAMPLE_GATEWAY_ROUTE_GRANT_SECRET:agent-examples-local-route-grant-secret}}") String secret) {
        return new HmacRouteGrantService(discoveryApi, secret);
    }

    @Bean
    @ConditionalOnMissingBean
    AgentInteractionTelemetry agentInteractionTelemetry() {
        return new InMemoryAgentInteractionTelemetry();
    }

    @Bean
    @ConditionalOnMissingBean
    RuntimeRegistryController runtimeRegistryController(
            RuntimeRegistrationApi registrationApi,
            AgentDiscoveryApi discoveryApi) {
        return new RuntimeRegistryController(registrationApi, discoveryApi);
    }

    @Bean
    @ConditionalOnMissingBean
    A2aGatewayController a2aGatewayController(
            RuntimeA2aGateway gateway,
            RouteGrantService routeGrantService,
            AgentInteractionTelemetry telemetry) {
        return new A2aGatewayController(gateway, routeGrantService, telemetry);
    }

    @Bean
    @ConditionalOnMissingBean
    RouteGrantController routeGrantController(RouteGrantService routeGrantService) {
        return new RouteGrantController(routeGrantService);
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
