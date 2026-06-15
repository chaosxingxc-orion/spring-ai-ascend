package com.huawei.ascend.runtime.app;

/**
 * Handle to a runtime started via {@link RuntimeApp#run(RuntimeHost)}. {@link #port()} is the
 * bound HTTP port (useful when the host bound an ephemeral port); {@link #close()} stops it.
 * Implements {@link AutoCloseable} so callers can use try-with-resources.
 */
public interface RunningRuntime extends AutoCloseable {

    int port();

    @Override
    void close();
}
