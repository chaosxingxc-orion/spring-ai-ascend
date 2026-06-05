package com.huawei.ascend.examples.a2a.gateway.model;

public class AgentRouteNotFoundException extends RuntimeException {

    private final GatewayErrorCode code;

    public AgentRouteNotFoundException(String message) {
        this(GatewayErrorCode.AGENT_NOT_FOUND, message);
    }

    public AgentRouteNotFoundException(GatewayErrorCode code, String message) {
        super(message);
        this.code = code == null ? GatewayErrorCode.AGENT_NOT_FOUND : code;
    }

    public GatewayErrorCode code() {
        return code;
    }
}
