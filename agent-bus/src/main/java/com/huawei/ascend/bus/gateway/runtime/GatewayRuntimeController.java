package com.huawei.ascend.bus.gateway.runtime;

import com.huawei.ascend.bus.spi.ingress.IngressEnvelope;
import com.huawei.ascend.bus.spi.ingress.IngressGateway;
import com.huawei.ascend.bus.spi.ingress.IngressResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring HTTP entry for FEAT-013 client-invocation forwarding (arch-driven G5-C).
 *
 * <p>This is the S4-deferred {@code POST /a2a} controller — {@code GatewayRuntimeService}'s
 * class javadoc explicitly deferred the Spring HTTP controller (alongside the async dispatch
 * worker + A2A SSE bridge) to S4. It binds the ingress request, delegates to the
 * {@link IngressGateway} (the {@code GatewayRuntimeService} bean, wired by the
 * {@code @Profile(gateway)} config in G5-B), and maps the bus acknowledgement to an HTTP
 * status: ACCEPTED → 202 (Task cursor in body), REJECTED → 422, DEFERRED → 503 (backpressure
 * — the client retries with the same idempotencyKey).
 *
 * <p><b>Scope (minimal landing).</b> Request binding delegates validation to the
 * {@link IngressEnvelope} compact constructor (tenant non-blank, W3C 32-hex traceId,
 * required attributes); a malformed body therefore surfaces as the record-constructor's
 * {@link IllegalArgumentException} (HTTP 500 by default) — a {@code @ExceptionHandler}
 * mapping that to 400 is a deliberate refinement, not in this slice. The full A2A SSE
 * streaming bridge (token streaming) remains deferred.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md §4.2 / §4.6};
 * {@code docs/4plus1/delta/event-bus-relay/decision-tree.md} (G5-C).
 */
// scope: gateway runtime — Spring HTTP controller; web confined to gateway.runtime (ADR-0160)
@RestController
@RequestMapping("/a2a")
@Profile("gateway")
public final class GatewayRuntimeController {

    private final IngressGateway gateway;

    public GatewayRuntimeController(IngressGateway gateway) {
        this.gateway = java.util.Objects.requireNonNull(gateway, "gateway is required");
    }

    @PostMapping
    public ResponseEntity<IngressResponse> route(@RequestBody IngressEnvelope envelope) {
        IngressResponse response = gateway.routeClientRequest(envelope);
        return switch (response.status()) {
            case ACCEPTED -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response);        // 202
            case DEFERRED -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response); // 503
            case REJECTED -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response); // 422
        };
    }
}
