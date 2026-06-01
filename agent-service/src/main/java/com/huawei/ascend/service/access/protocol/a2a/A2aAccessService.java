package com.huawei.ascend.service.access.protocol.a2a;

public interface A2aAccessService {
    A2aAcceptedResponse send(A2aEnvelope envelope);

    A2aAcceptedResponse stream(A2aEnvelope envelope);

    A2aAcceptedResponse cancel(A2aEnvelope envelope);
}


