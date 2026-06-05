package com.huawei.ascend.examples.a2a.gateway.http;

import com.huawei.ascend.examples.a2a.gateway.core.RuntimeA2aGateway;
import com.huawei.ascend.examples.a2a.gateway.model.A2aGatewayForwardException;
import com.huawei.ascend.examples.a2a.gateway.model.A2aGatewayResponse;
import com.huawei.ascend.examples.a2a.gateway.model.AgentRouteNotFoundException;
import com.huawei.ascend.examples.a2a.gateway.model.GatewayErrorCode;
import com.huawei.ascend.examples.a2a.gateway.model.RoutingContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

@RestController
public final class A2aGatewayController {

    private final RuntimeA2aGateway gateway;

    public A2aGatewayController(RuntimeA2aGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    @PostMapping(
            value = "/v1/agents/{agentId}/a2a",
            consumes = MediaType.ALL_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<byte[]> forwardA2a(
            @PathVariable String agentId,
            @RequestParam String tenantId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String correlationId,
            @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) byte[] body) {
        A2aGatewayResponse response = gateway.forward(
                agentId,
                tenantId,
                new RoutingContext(sessionId, correlationId, Map.of()),
                body,
                copyHeaders(headers));
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.parseMediaType(response.contentType()));
        responseHeaders.set("X-Agent-Examples-Runtime-Instance", response.runtimeInstanceId());
        responseHeaders.set("X-Agent-Examples-Route-Resolve-Ms", Long.toString(response.routeResolveLatency().toMillis()));
        responseHeaders.set("X-Agent-Examples-First-Byte-Ms", Long.toString(response.firstByteLatency().toMillis()));
        responseHeaders.set("X-Agent-Examples-Forward-Ms", Long.toString(response.forwardLatency().toMillis()));
        return new ResponseEntity<>(response.body(), responseHeaders, HttpStatus.valueOf(response.statusCode()));
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
}
