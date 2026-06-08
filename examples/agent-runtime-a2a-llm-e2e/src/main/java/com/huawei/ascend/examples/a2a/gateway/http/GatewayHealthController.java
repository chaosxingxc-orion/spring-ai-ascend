package com.huawei.ascend.examples.a2a.gateway.http;

import com.huawei.ascend.examples.a2a.gateway.api.AgentInteractionTelemetry;
import com.huawei.ascend.examples.a2a.gateway.core.InMemoryRuntimeRegistry;
import com.huawei.ascend.examples.a2a.gateway.model.GatewayHealthSnapshot;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class GatewayHealthController {

    private final InMemoryRuntimeRegistry registry;
    private final AgentInteractionTelemetry telemetry;

    public GatewayHealthController(InMemoryRuntimeRegistry registry, AgentInteractionTelemetry telemetry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
    }

    @GetMapping("/v1/gateway-health")
    public GatewayHealthSnapshot health() {
        return registry.healthSnapshot(telemetry.count());
    }
}
