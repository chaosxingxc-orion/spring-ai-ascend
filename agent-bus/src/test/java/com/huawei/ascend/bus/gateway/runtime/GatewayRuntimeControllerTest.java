package com.huawei.ascend.bus.gateway.runtime;

import com.huawei.ascend.bus.spi.ingress.IngressEnvelope;
import com.huawei.ascend.bus.spi.ingress.IngressGateway;
import com.huawei.ascend.bus.spi.ingress.IngressResponse;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link GatewayRuntimeController} — HTTP-status mapping of the bus
 * acknowledgement (ACCEPTED/REJECTED/DEFERRED). Direct method calls (no MockMvc / no
 * {@code @SpringBootTest} — agent-bus convention); the {@link IngressGateway} is a
 * stub that returns a canned {@link IngressResponse}.
 */
class GatewayRuntimeControllerTest {

    private static final UUID REQ_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TRACE = "0123456789abcdef0123456789abcdef"; // W3C 32-char lowercase hex

    private static IngressEnvelope envelope() {
        return new IngressEnvelope(
                REQ_ID, "tenant-a", UUID.fromString("00000000-0000-0000-0000-000000000002"),
                IngressEnvelope.IngressRequestType.RUN_CREATE,
                Map.of("method", "run.create"),   // opaque payload
                TRACE, null,
                Map.of("routeHandle", "route-1", "targetServiceId", "runtime-1", "capability", "a2a"));
    }

    private static IngressGateway stubReturning(IngressResponse response) {
        return env -> Objects.requireNonNull(response);
    }

    @Test
    void acceptedMapsTo202() {
        GatewayRuntimeController c = new GatewayRuntimeController(
                stubReturning(IngressResponse.accepted(REQ_ID, "task-1")));
        assertEquals(202, c.route(envelope()).getStatusCode().value());
    }

    @Test
    void rejectedMapsTo422() {
        GatewayRuntimeController c = new GatewayRuntimeController(
                stubReturning(IngressResponse.rejected(REQ_ID, "invocation_failed")));
        assertEquals(422, c.route(envelope()).getStatusCode().value());
    }

    @Test
    void deferredMapsTo503() {
        GatewayRuntimeController c = new GatewayRuntimeController(
                stubReturning(IngressResponse.deferred(REQ_ID)));
        assertEquals(503, c.route(envelope()).getStatusCode().value());
    }
}
