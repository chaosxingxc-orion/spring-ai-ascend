package com.huawei.ascend.service.starter;

import com.huawei.ascend.service.core.HmacRouteGrantService;
import com.huawei.ascend.service.core.InMemoryRuntimeRegistry;
import com.huawei.ascend.service.core.RuntimeA2aGateway;
import com.huawei.ascend.service.spi.discovery.AgentDirectory;
import com.huawei.ascend.service.spi.registry.RuntimeRegistry;
import com.huawei.ascend.service.spi.routing.RouteGrantService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configures the agent-service HTTP edge: in-memory reference
 * implementations of the registration/discovery/routing SPI behind
 * {@code @ConditionalOnMissingBean} seams, the controllers serving them, and —
 * when {@code agent-service.access.jwt.enabled=true} — the JWT tenant
 * cross-check filter on the service routes. Disable the whole edge with
 * {@code agent-service.enabled=false}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "agent-service.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AgentServiceProperties.class)
public class AgentServiceAutoConfiguration {

    /**
     * One in-memory store serves both the registration and discovery
     * interfaces. A deployment replaces it by contributing its own
     * {@link RuntimeRegistry} and {@link AgentDirectory} beans — interface
     * alias beans are deliberately absent so by-type injection never sees two
     * candidates for the same instance.
     */
    @Bean
    @ConditionalOnMissingBean({RuntimeRegistry.class, AgentDirectory.class})
    InMemoryRuntimeRegistry inMemoryRuntimeRegistry() {
        return new InMemoryRuntimeRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    RuntimeA2aGateway runtimeA2aGateway(AgentDirectory directory) {
        return new RuntimeA2aGateway(directory);
    }

    @Bean
    @ConditionalOnMissingBean
    RouteGrantService routeGrantService(AgentDirectory directory, AgentServiceProperties properties) {
        return new HmacRouteGrantService(directory, properties.getRouteGrantSecret());
    }

    @Bean
    @ConditionalOnMissingBean
    A2aForwardObserver a2aForwardObserver() {
        return A2aForwardObserver.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    RuntimeRegistryController runtimeRegistryController(
            RuntimeRegistry runtimeRegistry,
            AgentDirectory directory) {
        return new RuntimeRegistryController(runtimeRegistry, directory);
    }

    @Bean
    @ConditionalOnMissingBean
    A2aGatewayController a2aGatewayController(
            RuntimeA2aGateway gateway,
            RouteGrantService routeGrantService,
            A2aForwardObserver forwardObserver) {
        return new A2aGatewayController(gateway, routeGrantService, forwardObserver);
    }

    @Bean
    @ConditionalOnMissingBean
    RouteGrantController routeGrantController(RouteGrantService routeGrantService) {
        return new RouteGrantController(routeGrantService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "agent-service.access.jwt", name = "enabled", havingValue = "true")
    FilterRegistrationBean<ServiceTenantAuthFilter> serviceTenantAuthFilter(AgentServiceProperties properties) {
        FilterRegistrationBean<ServiceTenantAuthFilter> registration =
                new FilterRegistrationBean<>(new ServiceTenantAuthFilter(properties));
        registration.addUrlPatterns(
                "/v1/runtime-registrations", "/v1/runtime-registrations/*",
                "/v1/agents", "/v1/agents/*",
                "/v1/route-grants", "/v1/route-grants/*");
        registration.setOrder(10);
        return registration;
    }
}
