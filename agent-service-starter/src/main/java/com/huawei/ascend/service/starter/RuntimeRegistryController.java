package com.huawei.ascend.service.starter;

import com.huawei.ascend.service.spi.AgentRouteNotFoundException;
import com.huawei.ascend.service.spi.GatewayErrorCode;
import com.huawei.ascend.service.spi.discovery.AgentCardSummary;
import com.huawei.ascend.service.spi.discovery.AgentDirectory;
import com.huawei.ascend.service.spi.discovery.RoutingContext;
import com.huawei.ascend.service.spi.discovery.RuntimeRoute;
import com.huawei.ascend.service.spi.registry.RuntimeAgentRegistration;
import com.huawei.ascend.service.spi.registry.RuntimeCapacitySnapshot;
import com.huawei.ascend.service.spi.registry.RuntimeDeregisterResult;
import com.huawei.ascend.service.spi.registry.RuntimeInstanceId;
import com.huawei.ascend.service.spi.registry.RuntimeLeaseRenewal;
import com.huawei.ascend.service.spi.registry.RuntimeLeaseResult;
import com.huawei.ascend.service.spi.registry.RuntimeRegistrationResult;
import com.huawei.ascend.service.spi.registry.RuntimeRegistry;
import com.huawei.ascend.service.spi.registry.RuntimeState;
import com.huawei.ascend.service.spi.registry.SlaSnapshot;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.a2aproject.sdk.spec.AgentCard;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class RuntimeRegistryController {

    private final RuntimeRegistry runtimeRegistry;
    private final AgentDirectory directory;

    public RuntimeRegistryController(RuntimeRegistry runtimeRegistry, AgentDirectory directory) {
        this.runtimeRegistry = Objects.requireNonNull(runtimeRegistry, "runtimeRegistry");
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    @PostMapping("/v1/runtime-registrations")
    public RuntimeRegistrationResult register(@RequestBody RuntimeRegistrationRequest request) {
        return runtimeRegistry.register(new RuntimeAgentRegistration(
                RuntimeInstanceId.of(request.runtimeInstanceId()),
                request.tenantId(),
                request.agentId(),
                request.agentCard(),
                request.a2aEndpoint(),
                request.healthEndpoint(),
                request.version(),
                Duration.ofSeconds(request.ttlSeconds()),
                request.capacitySnapshot(),
                request.metadata()));
    }

    @PutMapping("/v1/runtime-registrations/{runtimeInstanceId}/lease")
    public RuntimeLeaseResult renew(@PathVariable String runtimeInstanceId, @RequestBody RuntimeLeaseRenewalRequest request) {
        return runtimeRegistry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of(runtimeInstanceId),
                request.state(),
                Duration.ofSeconds(request.ttlSeconds()),
                request.slaSnapshot(),
                request.capacitySnapshot(),
                request.metadata()));
    }

    @DeleteMapping("/v1/runtime-registrations/{runtimeInstanceId}")
    public RuntimeDeregisterResult deregister(@PathVariable String runtimeInstanceId) {
        return runtimeRegistry.deregister(RuntimeInstanceId.of(runtimeInstanceId));
    }

    @GetMapping("/v1/agents")
    public List<AgentCardSummary> listAgents(@RequestParam String tenantId) {
        return directory.listAgents(tenantId);
    }

    @GetMapping("/v1/agents/{agentId}/card")
    public AgentCard getAgentCard(@PathVariable String agentId, @RequestParam String tenantId) {
        return directory.getAgentCard(agentId, tenantId);
    }

    @PostMapping("/v1/agents/{agentId}/routes/resolve")
    public RuntimeRoute resolveRoute(
            @PathVariable String agentId,
            @RequestParam String tenantId,
            @RequestBody(required = false) RoutingContext routingContext) {
        return directory.resolveRoute(
                agentId,
                tenantId,
                routingContext == null ? RoutingContext.empty() : routingContext);
    }

    @ExceptionHandler(AgentRouteNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(AgentRouteNotFoundException ex) {
        HttpStatus status = ex.code() == GatewayErrorCode.AGENT_NOT_FOUND ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(new ErrorResponse(ex.code().name(), ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class})
    public ResponseEntity<ErrorResponse> badRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(GatewayErrorCode.BAD_REQUEST.name(), ex.getMessage()));
    }

    public record RuntimeRegistrationRequest(
            String runtimeInstanceId,
            String tenantId,
            String agentId,
            AgentCard agentCard,
            URI a2aEndpoint,
            URI healthEndpoint,
            String version,
            long ttlSeconds,
            RuntimeCapacitySnapshot capacitySnapshot,
            Map<String, Object> metadata) {

        public RuntimeRegistrationRequest(
                String runtimeInstanceId,
                String tenantId,
                String agentId,
                AgentCard agentCard,
                URI a2aEndpoint,
                URI healthEndpoint,
                String version,
                long ttlSeconds,
                Map<String, Object> metadata) {
            this(runtimeInstanceId, tenantId, agentId, agentCard, a2aEndpoint, healthEndpoint, version, ttlSeconds,
                    RuntimeCapacitySnapshot.empty(), metadata);
        }
    }

    public record RuntimeLeaseRenewalRequest(
            RuntimeState state,
            long ttlSeconds,
            SlaSnapshot slaSnapshot,
            RuntimeCapacitySnapshot capacitySnapshot,
            Map<String, Object> metadata) {

        public RuntimeLeaseRenewalRequest(
                RuntimeState state,
                long ttlSeconds,
                SlaSnapshot slaSnapshot,
                Map<String, Object> metadata) {
            this(state, ttlSeconds, slaSnapshot, RuntimeCapacitySnapshot.empty(), metadata);
        }
    }

    public record ErrorResponse(String code, String message) {
    }
}
