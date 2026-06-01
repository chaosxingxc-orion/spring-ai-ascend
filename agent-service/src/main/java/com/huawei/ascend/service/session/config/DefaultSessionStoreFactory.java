package com.huawei.ascend.service.session.config;

import com.huawei.ascend.service.session.store.SessionStore;
import com.huawei.ascend.service.session.store.SessionStoreFactory;
import com.huawei.ascend.service.session.store.memory.InMemorySessionStore;
import com.huawei.ascend.service.session.store.redis.RedisSessionCommands;
import com.huawei.ascend.service.session.store.redis.RedisSessionStore;
import com.huawei.ascend.service.session.store.redis.RedisSessionStoreProperties;
import com.huawei.ascend.service.session.store.redis.SessionCodec;

import java.util.Optional;

public final class DefaultSessionStoreFactory implements SessionStoreFactory {

    private final SessionManageProperties properties;
    private final Optional<RedisSessionCommands> redisCommands;
    private final SessionCodec sessionCodec;
    private final RedisSessionStoreProperties redisProperties;

    public DefaultSessionStoreFactory(
            SessionManageProperties properties,
            Optional<RedisSessionCommands> redisCommands,
            SessionCodec sessionCodec,
            RedisSessionStoreProperties redisProperties) {
        this.properties = properties;
        this.redisCommands = redisCommands;
        this.sessionCodec = sessionCodec;
        this.redisProperties = redisProperties;
    }

    @Override
    public SessionStore create() {
        return switch (properties.store().type()) {
            case MEMORY -> new InMemorySessionStore();
            case REDIS -> new RedisSessionStore(
                    redisCommands.orElseThrow(() -> new IllegalStateException(
                            "Redis session store requires a RedisSessionCommands bean")),
                    sessionCodec,
                    redisProperties);
        };
    }
}
