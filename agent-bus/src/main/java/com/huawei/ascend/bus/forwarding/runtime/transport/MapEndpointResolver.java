package com.huawei.ascend.bus.forwarding.runtime.transport;

import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuration-map {@link ForwardingEndpointResolver} — Stage 15 PoC default.
 *
 * <p>Maps {@link ForwardingRouteHandle#value()} → endpoint URL. In the PoC the
 * map is populated with the MockWebServer URL; production swaps this for the
 * Stage 3 registry resolver. The handle's {@code value} is treated as an opaque
 * lookup key — the resolver still makes no assumption about its internal
 * encoding; it is the registry's job (real or mocked) to map a handle to a URL.
 *
 * <p>Immutable; thread-safe via an unmodifiable copy of the supplied map.
 *
 * <p>Authority: Stage 15 PoC — A2A transport adapter
 * ({@code docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage14-review-and-stage15-plan.md}).
 */
public final class MapEndpointResolver implements ForwardingEndpointResolver {

    private final Map<String, String> endpoints;

    /**
     * @param endpoints map of {@code routeHandle.value()} → endpoint URL; copied
     *                  unmodifiable. Values may be null (mapped to
     *                  {@link Optional#empty()}).
     */
    public MapEndpointResolver(Map<String, String> endpoints) {
        Objects.requireNonNull(endpoints, "endpoints is required");
        this.endpoints = Map.copyOf(endpoints);
    }

    @Override
    public Optional<String> resolve(ForwardingRouteHandle handle) {
        Objects.requireNonNull(handle, "handle is required");
        return Optional.ofNullable(endpoints.get(handle.value()));
    }
}
