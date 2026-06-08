package com.huawei.ascend.runtime.session;

import com.huawei.ascend.runtime.session.api.SessionManager;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SessionManageProperties.class)
public class SessionManageConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock sessionClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    RuntimeSessionRepositoryFactory sessionStoreFactory(SessionManageProperties properties) {
        return new DefaultRuntimeSessionRepositoryFactory(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    RuntimeSessionRepository sessionStore(RuntimeSessionRepositoryFactory factory) {
        return factory.create();
    }

    @Bean
    @ConditionalOnMissingBean
    SessionManager sessionManager(
            RuntimeSessionRepository sessionStore,
            Clock sessionClock,
            SessionManageProperties properties) {
        return new SessionManagerImpl(sessionStore, sessionClock, properties.ttl());
    }
}
