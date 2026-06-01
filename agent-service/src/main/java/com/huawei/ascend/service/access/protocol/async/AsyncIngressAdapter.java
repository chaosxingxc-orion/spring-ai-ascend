package com.huawei.ascend.service.access.protocol.async;

import com.huawei.ascend.service.access.core.AccessGateway;
import com.huawei.ascend.service.access.model.AccessIntent;
import java.util.Objects;

public final class AsyncIngressAdapter implements AsyncIngressPort {

    private final AccessGateway accessGateway;

    public AsyncIngressAdapter(AccessGateway accessGateway) {
        this.accessGateway = Objects.requireNonNull(accessGateway, "accessGateway");
    }

    @Override
    public void enqueue(AsyncEnvelope envelope) {
        AccessIntent intent = accessGateway.acceptAsync(envelope);
        accessGateway.dispatch(intent);
    }
}
