package com.huawei.ascend.service.session.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent-service.session")
public record SessionManageProperties(
        Duration ttl,
        Store store) {

    public enum StoreType {
        MEMORY
    }

    public record Store(StoreType type) {
        public Store {
            type = type == null ? StoreType.MEMORY : type;
        }
    }

    public SessionManageProperties {
        ttl = ttl == null ? Duration.ofHours(24) : ttl;
        store = store == null ? new Store(StoreType.MEMORY) : store;
    }
}
