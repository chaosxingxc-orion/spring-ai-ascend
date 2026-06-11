package com.huawei.ascend.client.telemetry;

import com.huawei.ascend.client.SendSpec;

/**
 * The unconfigured default: every call gets the same inert span, and a null
 * {@link #traceparent()} tells the client to mint its own header.
 */
enum NoopClientTelemetry implements ClientTelemetry, ClientCallSpan {

    INSTANCE;

    @Override
    public ClientCallSpan startCall(String operation, SendSpec spec, String tenantId,
            String serverAddress) {
        return this;
    }

    @Override
    public String traceparent() {
        return null;
    }

    @Override
    public void traceresponse(String traceresponse) {
    }

    @Override
    public void succeed(boolean terminal, String responseText) {
    }

    @Override
    public void fail(Throwable error) {
    }
}
