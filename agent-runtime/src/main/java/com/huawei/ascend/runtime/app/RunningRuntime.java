package com.huawei.ascend.runtime.app;

/**
 * Handle to a runtime started via {@link RuntimeApp#run(RuntimeHost)}. {@link #port()} is the
 * bound HTTP port (useful when the host bound an ephemeral port); {@link #close()} stops it.
 * Implements {@link AutoCloseable} so callers can use try-with-resources.
 */
public interface RunningRuntime extends AutoCloseable {

    int port();

    /**
     * The runtime component of the given type from the running host — the
     * probing seam for diagnostics and tests that assert internal state (e.g.
     * the {@code RunRepository} backing the run DFA). Implementations resolve
     * it from whatever container the host assembled.
     *
     * @throws IllegalStateException when the host serves no unique component
     *                               of that type
     */
    <T> T component(Class<T> type);

    @Override
    void close();
}
