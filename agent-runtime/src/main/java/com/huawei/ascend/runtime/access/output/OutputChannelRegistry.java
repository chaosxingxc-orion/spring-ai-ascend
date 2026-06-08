package com.huawei.ascend.runtime.access.output;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class OutputChannelRegistry {

    private final ConcurrentMap<RuntimeOutputHandle, OutputChannel> channels = new ConcurrentHashMap<>();

    public OutputChannel getOrCreate(RuntimeOutputHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return channels.computeIfAbsent(handle, ignored -> new OutputChannel());
    }

    public List<RuntimeOutput> list(RuntimeOutputHandle handle) {
        Objects.requireNonNull(handle, "handle");
        OutputChannel channel = channels.get(handle);
        return channel == null ? List.of() : channel.history();
    }
}
