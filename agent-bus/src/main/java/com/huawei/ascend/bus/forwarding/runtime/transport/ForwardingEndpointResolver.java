package com.huawei.ascend.bus.forwarding.runtime.transport;

import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;

import java.util.Optional;

/**
 * Resolves an opaque {@link ForwardingRouteHandle} to a physical transport
 * endpoint URL (Stage 15, A2A PoC).
 *
 * <p><b>HD4 preserved.</b> {@link ForwardingRouteHandle} stays opaque on the
 * outbox record and in the {@code ForwardingDeliveryPort} contract — the
 * dispatcher / delivery binding never unwraps it. This injected port is the
 * sanctioned seam where an opaque handle becomes a concrete URL: the binding
 * calls {@link #resolve} and acts on the result, it does not read
 * {@code routeHandle.value()} itself. Injected exactly like
 * {@code ForwardingRetryPolicy} / {@code DispatchLeasePolicy} / {@code EpochClock}.
 *
 * <p>An empty result means the handle has no resolvable endpoint → the delivery
 * binding returns {@code dlq(ROUTE_NOT_FOUND)} (non-retryable; a route that
 * cannot be resolved is a configuration / registry problem, not a transient
 * receiver failure).
 *
 * <p>Production implementation is the Stage 3 registry / discovery runtime
 * (deferred); the Stage 15 PoC ships {@link MapEndpointResolver}
 * (configuration map). This port lives outside {@code transport.a2a} so it
 * carries no A2A SDK coupling — a future non-A2A binding reuses it.
 *
 * <p>Plain JDK type — no Spring / JDBC / broker dependency (forwarding purity,
 * decision §6.1). The ArchUnit purity harness does not confine it.
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md}
 * (Stage 15 — A2A transport adapter permit, §6.1 item 4 lifted to scaffold level).
 */
public interface ForwardingEndpointResolver {

    /**
     * Resolve an opaque route handle to an endpoint URL, or empty if the handle
     * is not resolvable (the delivery binding maps this to ROUTE_NOT_FOUND).
     *
     * @param handle the opaque route handle (non-null)
     * @return the endpoint URL, or {@link Optional#empty()} if unresolvable
     */
    Optional<String> resolve(ForwardingRouteHandle handle);
}
