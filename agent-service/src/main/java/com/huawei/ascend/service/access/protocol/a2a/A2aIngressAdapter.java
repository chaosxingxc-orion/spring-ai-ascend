package com.huawei.ascend.service.access.protocol.a2a;

import com.huawei.ascend.service.access.core.AccessGateway;
import com.huawei.ascend.service.access.model.AccessAcceptedResponse;
import com.huawei.ascend.service.access.model.AccessIntent;
import com.huawei.ascend.service.access.model.AccessOperation;

import java.util.Objects;

public final class A2aIngressAdapter implements A2aAccessService {

    private final AccessGateway accessGateway;

    public A2aIngressAdapter(AccessGateway accessGateway) {
        this.accessGateway = Objects.requireNonNull(accessGateway, "accessGateway");
    }

    @Override
    public A2aAcceptedResponse send(A2aEnvelope envelope) {
        return submit(accessGateway.acceptA2a(envelope));
    }

    @Override
    public A2aAcceptedResponse stream(A2aEnvelope envelope) {
        return submit(accessGateway.acceptA2a(envelope, true));
    }

    @Override
    public A2aAcceptedResponse cancel(A2aEnvelope envelope) {
        return submit(withOperation(accessGateway.acceptA2a(envelope), AccessOperation.CANCEL));
    }

    private A2aAcceptedResponse submit(AccessIntent intent) {
        AccessAcceptedResponse accepted = accessGateway.dispatch(intent).toCompletableFuture().join();
        if (accepted.accepted()) {
            accessGateway.bindEgress(intent, accepted);
        }
        return toA2aAcceptedResponse(accepted);
    }

    private static AccessIntent withOperation(AccessIntent intent, AccessOperation operation) {
        return new AccessIntent(
                operation,
                intent.tenantId(),
                intent.userId(),
                intent.agentId(),
                intent.sessionId(),
                intent.query(),
                intent.idempotencyKey(),
                intent.payload());
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


