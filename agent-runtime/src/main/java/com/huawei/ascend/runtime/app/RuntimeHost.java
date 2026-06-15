package com.huawei.ascend.runtime.app;

/**
 * Framework-neutral SPI for a runtime host: given the assembled {@link RuntimeComponents},
 * stand the runtime up and return a handle to it. Implementations choose the transport/HTTP
 * stack (e.g. {@link LocalA2aRuntimeHost} boots Spring Boot + A2A); {@code RuntimeApp} and this
 * interface stay Spring-free so the runtime core remains embeddable.
 */
public interface RuntimeHost {

    RunningRuntime start(RuntimeComponents components);
}
