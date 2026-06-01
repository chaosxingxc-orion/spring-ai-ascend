package com.huawei.ascend.service.access.protocol.a2a;

import com.huawei.ascend.service.access.model.EgressBinding;

import java.util.Objects;

public final class DefaultA2aOutputSink implements A2aOutputSink {

    private final A2aOutputRegistry outputRegistry;

    public DefaultA2aOutputSink(A2aOutputRegistry outputRegistry) {
        this.outputRegistry = Objects.requireNonNull(outputRegistry, "outputRegistry");
    }

    @Override
    public void send(EgressBinding binding, A2aOutput output) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(output, "output");
        A2aOutputHandle handle = new A2aOutputHandle(binding.tenantId(), binding.sessionId(), binding.taskId());
        outputRegistry.append(handle, output);
    }
}


