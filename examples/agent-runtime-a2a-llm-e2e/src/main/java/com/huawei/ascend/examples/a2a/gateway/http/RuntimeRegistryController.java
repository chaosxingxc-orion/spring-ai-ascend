package com.huawei.ascend.examples.a2a.gateway.http;

import com.huawei.ascend.examples.a2a.gateway.api.AgentDiscoveryApi;
import com.huawei.ascend.examples.a2a.gateway.api.RuntimeRegistrationApi;
import com.huawei.ascend.examples.a2a.gateway.model.AgentCardSummary;
import com.huawei.ascend.examples.a2a.gateway.model.AgentRouteNotFoundException;
import com.huawei.ascend.examples.a2a.gateway.model.GatewayErrorCode;
import com.huawei.ascend.examples.a2a.gateway.model.RoutingContext;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeAgentRegistration;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeCapacitySnapshot;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeDeregisterResult;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeInstanceId;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeLeaseRenewal;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeLeaseResult;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeRegistrationResult;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeRoute;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeState;
import com.huawei.ascend.examples.a2a.gateway.model.SlaSnapshot;
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

    private final RuntimeRegistrationApi registrationApi;
    private final AgentDiscoveryApi discoveryApi;

    public RuntimeRegistryController(RuntimeRegistrationApi registrationApi, AgentDiscoveryApi discoveryApi) {
        this.registrationApi = Objects.requireNonNull(registrationApi, "registrationApi");
        this.discoveryApi = Objects.requireNonNull(discoveryApi, "discoveryApi");
    }

    @PostMapping("/v1/runtime-registrations")
    public RuntimeRegistrationResult register(@RequestBody RuntimeRegistrationRequest request) {
        return registrationApi.register(new RuntimeAgentRegistration(
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
        return registrationApi.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of(runtimeInstanceId),
                request.state(),
                Duration.ofSeconds(request.ttlSeconds()),
                request.slaSnapshot(),
                request.capacitySnapshot(),
                request.metadata()));
    }

    @DeleteMapping("/v1/runtime-registrations/{runtimeInstanceId}")
    public RuntimeDeregisterResult deregister(@PathVariable String runtimeInstanceId) {
        return registrationApi.deregister(RuntimeInstanceId.of(runtimeInstanceId));
    }

    @GetMapping("/v1/agents")
    public List<AgentCardSummary> listAgents(@RequestParam String tenantId) {
        return discoveryApi.listAgents(tenantId);
    }

    @GetMapping("/v1/agents/{agentId}/card")
    public AgentCard getAgentCard(@PathVariable String agentId, @RequestParam String tenantId) {
        return discoveryApi.getAgentCard(agentId, tenantId);
    }

    @PostMapping("/v1/agents/{agentId}/routes/resolve")
    public RuntimeRoute resolveRoute(
            @PathVariable String agentId,
            @RequestParam String tenantId,
            @RequestBody(required = false) RoutingContext routingContext) {
        return discoveryApi.resolveRoute(
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
