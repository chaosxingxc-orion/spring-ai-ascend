package com.huawei.ascend.service.session.config;

import com.huawei.ascend.service.session.api.SessionManager;
import com.huawei.ascend.service.session.core.SessionManagerImpl;
import com.huawei.ascend.service.session.store.SessionStore;
import com.huawei.ascend.service.session.store.SessionStoreFactory;

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
    SessionStoreFactory sessionStoreFactory(SessionManageProperties properties) {
        return new DefaultSessionStoreFactory(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    SessionStore sessionStore(SessionStoreFactory factory) {
        return factory.create();
    }

    @Bean
    @ConditionalOnMissingBean
    SessionManager sessionManager(
            SessionStore sessionStore,
            Clock sessionClock,
            SessionManageProperties properties) {
        return new SessionManagerImpl(sessionStore, sessionClock, properties.ttl());
    }
}
