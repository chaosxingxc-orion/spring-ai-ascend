package com.huawei.ascend.service.session.config;

import com.huawei.ascend.service.session.store.SessionStore;
import com.huawei.ascend.service.session.store.SessionStoreFactory;
import com.huawei.ascend.service.session.store.memory.InMemorySessionStore;

public final class DefaultSessionStoreFactory implements SessionStoreFactory {

    private final SessionManageProperties properties;

    public DefaultSessionStoreFactory(SessionManageProperties properties) {
        this.properties = properties;
    }

    @Override
    public SessionStore create() {
        return switch (properties.store().type()) {
            case MEMORY -> new InMemorySessionStore();
        };
    }
}
