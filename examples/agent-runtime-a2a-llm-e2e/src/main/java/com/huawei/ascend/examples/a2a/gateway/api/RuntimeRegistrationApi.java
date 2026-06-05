package com.huawei.ascend.examples.a2a.gateway.api;

import com.huawei.ascend.examples.a2a.gateway.model.RuntimeAgentRegistration;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeDeregisterResult;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeInstanceId;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeLeaseRenewal;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeLeaseResult;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeRegistrationResult;

public interface RuntimeRegistrationApi {

    RuntimeRegistrationResult register(RuntimeAgentRegistration registration);

    RuntimeLeaseResult renew(RuntimeLeaseRenewal renewal);

    RuntimeDeregisterResult deregister(RuntimeInstanceId runtimeInstanceId);
}
