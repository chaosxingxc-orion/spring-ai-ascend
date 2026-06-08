package com.huawei.ascend.examples.a2a.gateway.http;

import com.huawei.ascend.examples.a2a.gateway.api.AgentInteractionTelemetry;
import com.huawei.ascend.examples.a2a.gateway.model.AgentInteractionEvent;
import com.huawei.ascend.examples.a2a.gateway.model.GatewayErrorCode;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class TelemetryController {

    private final AgentInteractionTelemetry telemetry;

    public TelemetryController(AgentInteractionTelemetry telemetry) {
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
    }

    @PostMapping("/v1/a2a-interactions")
    public AgentInteractionEvent record(@RequestBody AgentInteractionEvent event) {
        return telemetry.record(event);
    }

    @GetMapping("/v1/a2a-interactions")
    public List<AgentInteractionEvent> query(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(defaultValue = "100") int limit) {
        return telemetry.query(tenantId, correlationId, Math.min(limit, 1_000));
    }

    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class})
    public ResponseEntity<RuntimeRegistryController.ErrorResponse> badRequest(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(new RuntimeRegistryController.ErrorResponse(GatewayErrorCode.BAD_REQUEST.name(), ex.getMessage()));
    }
}
