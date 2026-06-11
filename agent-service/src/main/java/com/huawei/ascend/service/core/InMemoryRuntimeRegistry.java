package com.huawei.ascend.service.core;

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
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.a2aproject.sdk.spec.AgentCard;

/**
 * In-memory reference implementation of both registry and directory seams.
 * Lease state model: register puts the instance at READY; an expired lease is
 * observed as UNREACHABLE; AT_CAPACITY is derived from the capacity snapshot at
 * query time, never stored. Candidate filtering is tenant-first, then agent —
 * tenant isolation is structural, not a policy flag.
 *
 * <p>Route selection is sticky per session: when the {@code RoutingContext}
 * carries a non-blank sessionId, the first resolution pins
 * (tenant, agent, session) to the chosen instance and later resolutions return
 * that instance while it is still registered, lease-alive and READY; otherwise
 * the resolution re-picks among healthy candidates and re-pins. Pins are
 * dropped on deregister and lease expiry, and the pin map is capped at
 * {@link #MAX_SESSION_PINS} entries with oldest-pin eviction so abandoned
 * sessions can never grow memory unboundedly.
 */
public final class InMemoryRuntimeRegistry implements RuntimeRegistry, AgentDirectory {

    /**
     * Upper bound on concurrently tracked session pins. Evicting the oldest
     * pin merely costs that session one re-pick, so a modest cap is safe.
     */
    static final int MAX_SESSION_PINS = 10_000;

    private final Clock clock;
    private final ConcurrentHashMap<RuntimeInstanceId, RuntimeRecord> records = new ConcurrentHashMap<>();

    /**
     * Insertion-ordered so the eldest pin is the eviction victim; all view
     * iteration synchronizes on the map per {@link Collections#synchronizedMap}.
     */
    private final Map<SessionKey, RuntimeInstanceId> sessionPins =
            Collections.synchronizedMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<SessionKey, RuntimeInstanceId> eldest) {
                    return size() > MAX_SESSION_PINS;
                }
            });

    public InMemoryRuntimeRegistry() {
        this(Clock.systemUTC());
    }

    public InMemoryRuntimeRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Tenant-agnostic operational counts for deployment-specific health edges. */
    public record HealthStats(
            int registeredRuntimeCount,
            int readyRuntimeCount,
            int unreachableRuntimeCount) {
    }

    @Override
    public RuntimeRegistrationResult register(RuntimeAgentRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        refreshExpiredLeases();
        Instant now = clock.instant();
        RuntimeRecord record = RuntimeRecord.from(registration, now);
        records.put(registration.runtimeInstanceId(), record);
        return new RuntimeRegistrationResult(
                record.runtimeInstanceId(),
                record.tenantId(),
                record.agentId(),
                record.state(),
                record.expiresAt());
    }

    @Override
    public RuntimeLeaseResult renew(RuntimeLeaseRenewal renewal) {
        Objects.requireNonNull(renewal, "renewal");
        refreshExpiredLeases();
        Instant now = clock.instant();
        while (true) {
            RuntimeRecord existing = records.get(renewal.runtimeInstanceId());
            if (existing == null || existing.state() == RuntimeState.DEREGISTERED) {
                throw new AgentRouteNotFoundException(
                        GatewayErrorCode.RUNTIME_UNREACHABLE,
                        "Runtime is not registered: " + renewal.runtimeInstanceId());
            }
            RuntimeRecord renewed = existing.renew(renewal, now);
            if (records.replace(renewal.runtimeInstanceId(), existing, renewed)) {
                return new RuntimeLeaseResult(renewed.runtimeInstanceId(), renewed.state(), renewed.expiresAt());
            }
        }
    }

    @Override
    public RuntimeDeregisterResult deregister(RuntimeInstanceId runtimeInstanceId) {
        Objects.requireNonNull(runtimeInstanceId, "runtimeInstanceId");
        refreshExpiredLeases();
        RuntimeRecord removed = records.remove(runtimeInstanceId);
        dropPinsFor(runtimeInstanceId);
        return new RuntimeDeregisterResult(runtimeInstanceId, RuntimeState.DEREGISTERED, removed != null);
    }

    public HealthStats healthStats() {
        refreshExpiredLeases();
        int ready = 0;
        int unreachable = 0;
        for (RuntimeRecord record : records.values()) {
            RuntimeState state = effectiveRouteState(record);
            if (state == RuntimeState.READY) {
                ready++;
            }
            if (state == RuntimeState.UNREACHABLE) {
                unreachable++;
            }
        }
        return new HealthStats(records.size(), ready, unreachable);
    }

    @Override
    public AgentCard getAgentCard(String agentId, String tenantId) {
        refreshExpiredLeases();
        return eligibleRoutes(agentId, tenantId).stream()
                .findFirst()
                .map(RuntimeRecord::agentCard)
                .orElseThrow(() -> new AgentRouteNotFoundException(
                        "No READY runtime for tenantId=" + tenantId + ", agentId=" + agentId));
    }

    @Override
    public List<AgentCardSummary> listAgents(String tenantId) {
        refreshExpiredLeases();
        String normalizedTenantId = required(tenantId, "tenantId");
        return records.values().stream()
                .filter(record -> normalizedTenantId.equals(record.tenantId()))
                .filter(this::isRouteVisible)
                .sorted(Comparator
                        .comparing(RuntimeRecord::agentId)
                        .thenComparing(record -> record.runtimeInstanceId().value()))
                .map(record -> new AgentCardSummary(
                        record.tenantId(),
                        record.agentId(),
                        record.agentCard().name(),
                        record.version(),
                        record.a2aEndpoint(),
                        effectiveRouteState(record)))
                .toList();
    }

    @Override
    public RuntimeRoute resolveRoute(String agentId, String tenantId, RoutingContext routingContext) {
        refreshExpiredLeases();
        RuntimeRecord record = selectForSession(eligibleRoutes(agentId, tenantId), routingContext);
        return new RuntimeRoute(
                record.agentId(),
                record.runtimeInstanceId(),
                record.a2aEndpoint(),
                effectiveRouteState(record),
                record.lastHeartbeatAt(),
                record.slaSnapshot(),
                record.capacitySnapshot());
    }

    /**
     * Honors an existing session pin only while the pinned instance is still
     * among the READY candidates; a dead pin is silently replaced by the
     * score-best pick so a session survives instance loss with one re-pin.
     */
    private RuntimeRecord selectForSession(List<RuntimeRecord> ready, RoutingContext routingContext) {
        RuntimeRecord best = ready.get(0);
        String sessionId = routingContext == null ? null : routingContext.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return best;
        }
        SessionKey key = new SessionKey(best.tenantId(), best.agentId(), sessionId.trim());
        RuntimeInstanceId pinnedId = sessionPins.get(key);
        if (pinnedId != null) {
            for (RuntimeRecord candidate : ready) {
                if (candidate.runtimeInstanceId().equals(pinnedId)) {
                    return candidate;
                }
            }
        }
        sessionPins.put(key, best.runtimeInstanceId());
        return best;
    }

    private List<RuntimeRecord> eligibleRoutes(String agentId, String tenantId) {
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedAgentId = required(agentId, "agentId");
        List<RuntimeRecord> candidates = records.values().stream()
                .filter(record -> normalizedTenantId.equals(record.tenantId()))
                .filter(record -> normalizedAgentId.equals(record.agentId()))
                .sorted(Comparator
                        .comparing(RuntimeRecord::lastHeartbeatAt, Comparator.reverseOrder())
                        .thenComparing(record -> record.runtimeInstanceId().value()))
                .toList();
        if (candidates.isEmpty()) {
            throw new AgentRouteNotFoundException(
                    GatewayErrorCode.AGENT_NOT_FOUND,
                    "No registered runtime for tenantId=" + tenantId + ", agentId=" + agentId);
        }
        List<RuntimeRecord> ready = candidates.stream()
                .filter(record -> effectiveRouteState(record) == RuntimeState.READY)
                .sorted(Comparator.<RuntimeRecord>comparingDouble(record -> record.capacitySnapshot().routeScore())
                        .thenComparingLong(record -> record.capacitySnapshot().p95FirstTokenMs())
                        .thenComparing(RuntimeRecord::lastHeartbeatAt, Comparator.reverseOrder())
                        .thenComparing(record -> record.runtimeInstanceId().value()))
                .toList();
        if (!ready.isEmpty()) {
            return ready;
        }
        RuntimeState dominantState = candidates.stream()
                .map(this::effectiveRouteState)
                .filter(RuntimeState.AT_CAPACITY::equals)
                .findFirst()
                .orElse(effectiveRouteState(candidates.get(0)));
        throw new AgentRouteNotFoundException(errorCodeForState(dominantState),
                "No READY runtime for tenantId=" + tenantId + ", agentId=" + agentId
                        + ", dominantState=" + dominantState);
    }

    private boolean isRouteVisible(RuntimeRecord record) {
        RuntimeState state = effectiveState(record);
        return state != RuntimeState.DEREGISTERED && state != RuntimeState.UNREACHABLE;
    }

    private RuntimeState effectiveState(RuntimeRecord record) {
        if (record.state() == RuntimeState.DEREGISTERED) {
            return RuntimeState.DEREGISTERED;
        }
        return record.expiresAt().isAfter(clock.instant()) ? record.state() : RuntimeState.UNREACHABLE;
    }

    private RuntimeState effectiveRouteState(RuntimeRecord record) {
        RuntimeState state = effectiveState(record);
        if (state == RuntimeState.READY && record.capacitySnapshot().atCapacity()) {
            return RuntimeState.AT_CAPACITY;
        }
        return state;
    }

    private void refreshExpiredLeases() {
        Instant now = clock.instant();
        List<RuntimeInstanceId> expired = new ArrayList<>();
        records.replaceAll((id, record) -> {
            RuntimeState effective = effectiveState(record);
            if (effective == RuntimeState.UNREACHABLE && record.state() != RuntimeState.UNREACHABLE) {
                expired.add(id);
                return record.withState(RuntimeState.UNREACHABLE, now);
            }
            return record;
        });
        expired.forEach(this::dropPinsFor);
    }

    private void dropPinsFor(RuntimeInstanceId runtimeInstanceId) {
        synchronized (sessionPins) {
            sessionPins.values().removeIf(runtimeInstanceId::equals);
        }
    }

    private GatewayErrorCode errorCodeForState(RuntimeState state) {
        return switch (state) {
            case COLD, REGISTERING -> GatewayErrorCode.RUNTIME_COLD;
            case AT_CAPACITY -> GatewayErrorCode.RUNTIME_AT_CAPACITY;
            case DRAINING -> GatewayErrorCode.RUNTIME_DRAINING;
            case UNREACHABLE, DEREGISTERED -> GatewayErrorCode.RUNTIME_UNREACHABLE;
            case READY -> GatewayErrorCode.AGENT_NOT_FOUND;
        };
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private record SessionKey(String tenantId, String agentId, String sessionId) {
    }

    private record RuntimeRecord(
            RuntimeInstanceId runtimeInstanceId,
            String tenantId,
            String agentId,
            AgentCard agentCard,
            java.net.URI a2aEndpoint,
            java.net.URI healthEndpoint,
            String version,
            RuntimeState state,
            Instant registeredAt,
            Instant lastHeartbeatAt,
            Instant expiresAt,
            SlaSnapshot slaSnapshot,
            RuntimeCapacitySnapshot capacitySnapshot,
            Map<String, Object> metadata) {

        private static RuntimeRecord from(RuntimeAgentRegistration registration, Instant now) {
            return new RuntimeRecord(
                    registration.runtimeInstanceId(),
                    registration.tenantId(),
                    registration.agentId(),
                    registration.agentCard(),
                    registration.a2aEndpoint(),
                    registration.healthEndpoint(),
                    registration.version(),
                    RuntimeState.READY,
                    now,
                    now,
                    now.plus(registration.ttl()),
                    SlaSnapshot.empty(),
                    registration.capacitySnapshot(),
                    registration.metadata());
        }

        private RuntimeRecord renew(RuntimeLeaseRenewal renewal, Instant now) {
            return new RuntimeRecord(
                    runtimeInstanceId,
                    tenantId,
                    agentId,
                    agentCard,
                    a2aEndpoint,
                    healthEndpoint,
                    version,
                    renewal.state(),
                    registeredAt,
                    now,
                    now.plus(renewal.ttl()),
                    renewal.slaSnapshot(),
                    renewal.capacitySnapshot(),
                    renewal.metadata());
        }

        private RuntimeRecord withState(RuntimeState nextState, Instant now) {
            return new RuntimeRecord(
                    runtimeInstanceId,
                    tenantId,
                    agentId,
                    agentCard,
                    a2aEndpoint,
                    healthEndpoint,
                    version,
                    nextState,
                    registeredAt,
                    lastHeartbeatAt,
                    now,
                    slaSnapshot,
                    capacitySnapshot,
                    metadata);
        }
    }
}
