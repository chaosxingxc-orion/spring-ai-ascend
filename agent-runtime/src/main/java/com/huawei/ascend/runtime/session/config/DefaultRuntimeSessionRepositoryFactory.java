package com.huawei.ascend.runtime.session.config;

import com.huawei.ascend.runtime.session.RuntimeSessionRepository;
import com.huawei.ascend.runtime.session.RuntimeSessionRepositoryFactory;
import com.huawei.ascend.runtime.session.InMemoryRuntimeSessionRepository;

public final class DefaultRuntimeSessionRepositoryFactory implements RuntimeSessionRepositoryFactory {

    private final SessionManageProperties properties;

    public DefaultRuntimeSessionRepositoryFactory(SessionManageProperties properties) {
        this.properties = properties;
    }

    @Override
    public RuntimeSessionRepository create() {
        return switch (properties.store().type()) {
            case MEMORY -> new InMemoryRuntimeSessionRepository();
        };
    }
}
