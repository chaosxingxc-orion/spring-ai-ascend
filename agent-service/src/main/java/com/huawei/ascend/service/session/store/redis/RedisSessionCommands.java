package com.huawei.ascend.service.session.store.redis;

import java.time.Duration;
import java.util.Optional;

public interface RedisSessionCommands {
    Optional<String> get(String key);

    void set(String key, String value, Duration ttl);

    boolean compareAndSet(String key, long expectedVersion, String value, Duration ttl);

    void delete(String key);
}
