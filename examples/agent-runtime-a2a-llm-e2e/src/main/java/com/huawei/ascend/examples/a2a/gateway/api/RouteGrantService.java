package com.huawei.ascend.examples.a2a.gateway.api;

import com.huawei.ascend.examples.a2a.gateway.model.GrantValidationResult;
import com.huawei.ascend.examples.a2a.gateway.model.InboundA2aContext;
import com.huawei.ascend.examples.a2a.gateway.model.RouteGrant;
import com.huawei.ascend.examples.a2a.gateway.model.RouteGrantRequest;

public interface RouteGrantService {

    RouteGrant resolveGrant(RouteGrantRequest request);

    GrantValidationResult validate(RouteGrant grant, InboundA2aContext context);
}
