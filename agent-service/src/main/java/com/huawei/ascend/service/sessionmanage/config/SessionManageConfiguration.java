package com.huawei.ascend.service.sessionmanage.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.service.sessionmanage.api.SessionManager;
import com.huawei.ascend.service.sessionmanage.core.SessionManagerImpl;
import com.huawei.ascend.service.sessionmanage.store.SessionStore;
import com.huawei.ascend.service.sessionmanage.store.SessionStoreFactory;
import com.huawei.ascend.service.sessionmanage.store.redis.JacksonSessionCodec;
import com.huawei.ascend.service.sessionmanage.store.redis.RedisSessionCommands;
import com.huawei.ascend.service.sessionmanage.store.redis.RedisSessionStoreProperties;
import com.huawei.ascend.service.sessionmanage.store.redis.SessionCodec;

import java.time.Clock;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SessionManageProperties.class, RedisSessionStoreProperties.class})
public class SessionManageConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock sessionClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(SessionCodec.class)
    SessionCodec sessionCodec(ObjectMapper objectMapper) {
        return new JacksonSessionCodec(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    SessionStoreFactory sessionStoreFactory(
            SessionManageProperties properties,
            Optional<RedisSessionCommands> redisCommands,
            SessionCodec sessionCodec,
            RedisSessionStoreProperties redisProperties) {
        return new DefaultSessionStoreFactory(properties, redisCommands, sessionCodec, redisProperties);
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
