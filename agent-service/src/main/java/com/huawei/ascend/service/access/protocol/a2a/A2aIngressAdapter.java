package com.huawei.ascend.service.access.protocol.a2a;

import com.huawei.ascend.service.access.core.AccessGateway;
import com.huawei.ascend.service.access.model.AccessAcceptedResponse;

import java.util.Objects;

public final class A2aIngressAdapter implements A2aAccessService {

    private final AccessGateway accessGateway;

    public A2aIngressAdapter(AccessGateway accessGateway) {
        this.accessGateway = Objects.requireNonNull(accessGateway, "accessGateway");
    }

    @Override
    public A2aAcceptedResponse send(A2aEnvelope envelope) {
        AccessAcceptedResponse accepted = accessGateway.submitA2a(envelope).toCompletableFuture().join();
        return toA2aAcceptedResponse(accepted);
    }

    @Override
    public A2aAcceptedResponse stream(A2aEnvelope envelope) {
        AccessAcceptedResponse accepted = accessGateway.submitA2a(envelope, true).toCompletableFuture().join();
        return toA2aAcceptedResponse(accepted);
    }

    @Override
    public A2aAcceptedResponse cancel(A2aEnvelope envelope) {
        AccessAcceptedResponse accepted = accessGateway.cancelA2a(envelope).toCompletableFuture().join();
        return toA2aAcceptedResponse(accepted);
    }

    private static A2aAcceptedResponse toA2aAcceptedResponse(AccessAcceptedResponse response) {
        return new A2aAcceptedResponse(
                response.tenantId(),
                response.userId(),
                response.agentId(),
                response.sessionId(),
                response.taskId(),
                response.accepted(),
                response.message());
    }
}

