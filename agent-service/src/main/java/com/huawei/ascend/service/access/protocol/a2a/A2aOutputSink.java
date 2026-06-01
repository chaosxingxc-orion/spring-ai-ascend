package com.huawei.ascend.service.access.protocol.a2a;

import com.huawei.ascend.service.access.model.EgressBinding;

public interface A2aOutputSink {
    void send(EgressBinding binding, A2aOutput output);
}


