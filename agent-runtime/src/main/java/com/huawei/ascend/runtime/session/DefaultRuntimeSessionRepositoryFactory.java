package com.huawei.ascend.runtime.session;


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
