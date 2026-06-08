package com.huawei.ascend.examples.a2a.gateway.http;

import com.huawei.ascend.examples.a2a.gateway.api.AgentInteractionTelemetry;
import com.huawei.ascend.examples.a2a.gateway.api.RouteGrantService;
import com.huawei.ascend.examples.a2a.gateway.core.RuntimeA2aGateway;
import com.huawei.ascend.examples.a2a.gateway.model.A2aGatewayForwardException;
import com.huawei.ascend.examples.a2a.gateway.model.A2aGatewayStreamResponse;
import com.huawei.ascend.examples.a2a.gateway.model.AgentInteractionEvent;
import com.huawei.ascend.examples.a2a.gateway.model.AgentRouteNotFoundException;
import com.huawei.ascend.examples.a2a.gateway.model.GatewayErrorCode;
import com.huawei.ascend.examples.a2a.gateway.model.RouteGrant;
import com.huawei.ascend.examples.a2a.gateway.model.RouteGrantRequest;
import com.huawei.ascend.examples.a2a.gateway.model.RoutingContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public final class A2aGatewayController {

    private final RuntimeA2aGateway gateway;
    private final RouteGrantService routeGrantService;
    private final AgentInteractionTelemetry telemetry;

    public A2aGatewayController(
            RuntimeA2aGateway gateway,
            RouteGrantService routeGrantService,
            AgentInteractionTelemetry telemetry) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.routeGrantService = Objects.requireNonNull(routeGrantService, "routeGrantService");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
    }

    @PostMapping(
            value = "/v1/agents/{agentId}/a2a",
            consumes = MediaType.ALL_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<StreamingResponseBody> forwardA2a(
            @PathVariable String agentId,
            @RequestParam String tenantId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false, defaultValue = "gateway-facade") String sourceAgentId,
            @RequestParam(required = false, defaultValue = "message/stream") String a2aMethod,
            @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) byte[] body) {
        Instant requestStart = Instant.now();
        byte[] requestBody = body == null ? new byte[0] : body.clone();
        RoutingContext routingContext = new RoutingContext(sessionId, correlationId, Map.of());
        RouteGrant grant = routeGrantService.resolveGrant(new RouteGrantRequest(
                tenantId,
                sourceAgentId,
                agentId,
                a2aMethod,
                routingContext,
                Duration.ofSeconds(60)));
        Map<String, List<String>> forwardHeaders = copyHeaders(headers);
        addHeader(forwardHeaders, "X-Agent-Examples-Route-Grant-Id", grant.grantId());
        addHeader(forwardHeaders, "X-Agent-Examples-Route-Grant-Signature", grant.signature());
        addHeader(forwardHeaders, "X-Agent-Examples-Source-Agent", grant.sourceAgentId());
        addHeader(forwardHeaders, "X-Agent-Examples-Tenant", grant.tenantId());
        A2aGatewayStreamResponse response = gateway.forwardStreaming(
                agentId,
                tenantId,
                routingContext,
                requestBody,
                forwardHeaders);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.parseMediaType(response.contentType()));
        responseHeaders.set("X-Agent-Examples-Runtime-Instance", response.runtimeInstanceId());
        responseHeaders.set("X-Agent-Examples-Route-Grant-Id", grant.grantId());
        responseHeaders.set("X-Agent-Examples-Route-Resolve-Ms", Long.toString(response.routeResolveLatency().toMillis()));
        responseHeaders.set("X-Agent-Examples-First-Byte-Ms", Long.toString(response.firstByteLatency().toMillis()));
        responseHeaders.set("X-Agent-Examples-Forward-Start-Ms", Long.toString(response.firstByteLatency().toMillis()));
        StreamingResponseBody stream = output -> streamAndRecord(
                response,
                output,
                grant,
                a2aMethod,
                sessionId,
                correlationId,
                requestBody.length,
                requestStart);
        return new ResponseEntity<>(stream, responseHeaders, HttpStatus.valueOf(response.statusCode()));
    }

    @ExceptionHandler(AgentRouteNotFoundException.class)
    public ResponseEntity<RuntimeRegistryController.ErrorResponse> notFound(AgentRouteNotFoundException ex) {
        HttpStatus status = ex.code() == GatewayErrorCode.AGENT_NOT_FOUND ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .body(new RuntimeRegistryController.ErrorResponse(ex.code().name(), ex.getMessage()));
    }

    @ExceptionHandler(A2aGatewayForwardException.class)
    public ResponseEntity<RuntimeRegistryController.ErrorResponse> badGateway(A2aGatewayForwardException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new RuntimeRegistryController.ErrorResponse(ex.code().name(), ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class})
    public ResponseEntity<RuntimeRegistryController.ErrorResponse> badRequest(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(new RuntimeRegistryController.ErrorResponse(GatewayErrorCode.BAD_REQUEST.name(), ex.getMessage()));
    }

    private Map<String, List<String>> copyHeaders(HttpHeaders headers) {
        Map<String, List<String>> copied = new LinkedHashMap<>();
        headers.forEach((name, values) -> copied.put(name, List.copyOf(values)));
        return copied;
    }

    private void addHeader(Map<String, List<String>> headers, String name, String value) {
        headers.put(name, List.of(value));
    }

    private void streamAndRecord(
            A2aGatewayStreamResponse response,
            OutputStream output,
            RouteGrant grant,
            String a2aMethod,
            String sessionId,
            String correlationId,
            long requestBytes,
            Instant requestStart) throws IOException {
        long responseBytes = 0;
        String status = "OK";
        String errorCode = null;
        try (InputStream input = response.body()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                responseBytes += read;
            }
        } catch (IOException ex) {
            status = "FAILED";
            errorCode = GatewayErrorCode.RUNTIME_UNREACHABLE.name();
            throw ex;
        } finally {
            telemetry.record(new AgentInteractionEvent(
                    "event-" + UUID.randomUUID(),
                    "A2A_GATEWAY_FORWARD_COMPLETED",
                    Instant.now(),
                    grant.tenantId(),
                    "agent-examples-gateway",
                    grant.sourceAgentId(),
                    response.runtimeInstanceId(),
                    grant.targetAgentId(),
                    sessionId,
                    null,
                    correlationId,
                    null,
                    grant.grantId(),
                    a2aMethod,
                    status,
                    response.routeResolveLatency().toMillis(),
                    response.firstByteLatency().toMillis(),
                    Duration.between(requestStart, Instant.now()).toMillis(),
                    requestBytes,
                    responseBytes,
                    errorCode,
                    null,
                    null,
                    Map.of("gatewayForward", true)));
        }
    }
}
