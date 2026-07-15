package com.huawei.ascend.bus.forwarding.runtime.transport;

import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;

import java.util.Objects;
import java.util.Optional;

/**
 * Convention-based {@link ForwardingEndpointResolver} for the FEAT-013/014 broker
 * topology (arch-driven G5-B).
 *
 * <p>Maps an opaque {@link ForwardingRouteHandle} to a broker topic by the L2 §5.2
 * naming convention: {@code ascend_bus_<routeHandle.value>_<suffix>}, where the
 * per-form / per-role {@code suffix} selects the hop ({@code req} gateway-produce /
 * {@code deliver} event-bus-forward-produce / {@code resp_in} event-bus-response-consume /
 * {@code resp_out} gateway-response-consume). This is the sanctioned HD4 place to read
 * {@code routeHandle.value()} — it keeps the per-form topic routing in the resolver
 * layer, not in the worker (the {@code EventBusRelayWorker} is routing-agnostic; it
 * re-publishes via whatever {@link BrokerForwardingRelayPort} it is wired with).
 *
 * <p>Replaces the test-inline {@link MapEndpointResolver} for production wiring — the
 * route→topic map is derived by convention, not a static config map (the route set is
 * not known at config time; it comes from envelopes at runtime). Recorded as the
 * route→topic mapping sub-decision in {@code docs/4plus1/delta/event-bus-relay/deviations.md}.
 *
 * <p>Plain JDK — no Spring / broker types (forwarding purity preserved).
 */
// scope: forwarding transport — pure-Java convention resolver (no Spring/broker types)
public final class BrokerTopicResolver implements ForwardingEndpointResolver {

    /** The L2 §5.2 topic-name prefix shared by every agent-bus broker topic. */
    public static final String TOPIC_PREFIX = "ascend_bus_";

    private final String suffix;

    public BrokerTopicResolver(String suffix) {
        this.suffix = Objects.requireNonNull(suffix, "suffix is required");
        if (suffix.isBlank()) {
            throw new IllegalArgumentException("suffix must not be blank");
        }
    }

    @Override
    public Optional<String> resolve(ForwardingRouteHandle handle) {
        Objects.requireNonNull(handle, "handle is required");
        return Optional.of(TOPIC_PREFIX + handle.value() + "_" + suffix);
    }
}
