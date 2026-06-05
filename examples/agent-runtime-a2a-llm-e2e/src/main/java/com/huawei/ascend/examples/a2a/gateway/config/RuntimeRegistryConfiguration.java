package com.huawei.ascend.examples.a2a.gateway.config;

import com.huawei.ascend.examples.a2a.gateway.api.AgentDiscoveryApi;
import com.huawei.ascend.examples.a2a.gateway.api.RuntimeRegistrationApi;
import com.huawei.ascend.examples.a2a.gateway.core.InMemoryRuntimeRegistry;
import com.huawei.ascend.examples.a2a.gateway.core.RuntimeA2aGateway;
import com.huawei.ascend.examples.a2a.gateway.http.A2aGatewayController;
import com.huawei.ascend.examples.a2a.gateway.http.RuntimeRegistryController;
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
    RuntimeRegistryController runtimeRegistryController(
            RuntimeRegistrationApi registrationApi,
            AgentDiscoveryApi discoveryApi) {
        return new RuntimeRegistryController(registrationApi, discoveryApi);
    }

    @Bean
    @ConditionalOnMissingBean
    A2aGatewayController a2aGatewayController(RuntimeA2aGateway gateway) {
        return new A2aGatewayController(gateway);
    }
}
