package com.huawei.ascend.examples.a2a.gateway.http;

import com.huawei.ascend.examples.a2a.gateway.api.RouteGrantService;
import com.huawei.ascend.examples.a2a.gateway.model.GatewayErrorCode;
import com.huawei.ascend.examples.a2a.gateway.model.GrantValidationResult;
import com.huawei.ascend.examples.a2a.gateway.model.InboundA2aContext;
import com.huawei.ascend.examples.a2a.gateway.model.RouteGrant;
import com.huawei.ascend.examples.a2a.gateway.model.RouteGrantRequest;
import com.huawei.ascend.examples.a2a.gateway.model.RoutingContext;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class RouteGrantController {

    private final RouteGrantService routeGrantService;

    public RouteGrantController(RouteGrantService routeGrantService) {
        this.routeGrantService = Objects.requireNonNull(routeGrantService, "routeGrantService");
    }

    @PostMapping("/v1/route-grants/resolve")
    public RouteGrant resolveGrant(@RequestBody ResolveGrantRequest request) {
        return routeGrantService.resolveGrant(new RouteGrantRequest(
                request.tenantId(),
                request.sourceAgentId(),
                request.targetAgentId(),
                request.a2aMethod(),
                request.routingContext(),
                Duration.ofSeconds(request.ttlSeconds() <= 0 ? 60 : request.ttlSeconds())));
    }

    @PostMapping("/v1/route-grants/validate")
    public GrantValidationResult validateGrant(@RequestBody ValidateGrantRequest request) {
        return routeGrantService.validate(request.grant(), request.context());
    }

    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class})
    public ResponseEntity<RuntimeRegistryController.ErrorResponse> badRequest(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(new RuntimeRegistryController.ErrorResponse(GatewayErrorCode.BAD_REQUEST.name(), ex.getMessage()));
    }

    public record ResolveGrantRequest(
            String tenantId,
            String sourceAgentId,
            String targetAgentId,
            String a2aMethod,
            RoutingContext routingContext,
            long ttlSeconds,
            Map<String, Object> metadata) {
    }

    public record ValidateGrantRequest(RouteGrant grant, InboundA2aContext context) {
    }
}
