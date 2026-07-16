package com.huawei.ascend.bus.forwarding.runtime.transport.broker;

import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;

import java.util.Objects;

/**
 * Shared control-descriptor codec for the FEAT-013/014 broker {@code payloadRef}.
 *
 * <p>{@link com.huawei.ascend.bus.forwarding.spi.broker.BrokerInboundMessage} carries routing metadata (tenantId / messageId /
 * sourceServiceId / targetServiceId / consumerServiceId / payloadRef) plus the
 * native {@code correlationId} / {@code eventType} (mirrored from headers at poll).
 * It does NOT carry the request envelope's {@code traceId} /
 * {@code idempotencyKey} / {@code routeHandle} / {@code capability} / {@code deadline}
 * — those control fields would otherwise be lost crossing the broker hop. They are
 * therefore encoded into the request's {@code payloadRef} as a compact
 * {@code k=v;k=v;...} descriptor token: it is the only field besides the native
 * correlation/event headers that survives the broker's poll, so it is the clean
 * vehicle for the request envelope's control-plane fields (L2 feat-013 §4.2).
 *
 * <p>This utility is the SHARED main-side codec used by BOTH the production
 * {@code GatewayRuntimeService} (which builds the request payloadRef) and the
 * in-repo {@code TestAgentRuntime} test double (which decodes it to pick its
 * response sequence). Promoting it out of the test double lets the gateway
 * produce a request the runtime decodes without the production class depending on
 * a test fixture.
 *
 * <p><b>Format</b>: {@code eventType=<ET>;traceId=<T>;correlationId=<C>;
 * idempotencyKey=<K>;routeHandle=<R>;capability=<CAP>;deadline=<MILLIS>} — order
 * is fixed so round-trip assertions are deterministic. {@link #softEventType} and
 * {@link #token} tolerate response payloadRefs that carry a different shape
 * ({@code taskId=...;status=...;streamRef=...}) without throwing.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md §4.2 / §4.3};
 * {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-014-a2a-call-event-forwarding.md §4.4}.
 */
// scope: forwarding transport.broker — payloadRef-carried control descriptor codec; pure Java
public final class BrokerControlDescriptor {

    private BrokerControlDescriptor() {
        throw new AssertionError("BrokerControlDescriptor is a static codec namespace");
    }

    /** Decoded request control descriptor carried in the {@code payloadRef}. */
    public record Descriptor(
            AgentBusEventType eventType,
            String traceId,
            String correlationId,
            String idempotencyKey,
            String routeHandle,
            String capability,
            long deadlineMillisEpoch
    ) {
        public Descriptor {
            Objects.requireNonNull(eventType, "eventType is required");
            requireNonBlank(traceId, "traceId");
            requireNonBlank(correlationId, "correlationId");
            requireNonBlank(idempotencyKey, "idempotencyKey");
            requireNonBlank(routeHandle, "routeHandle");
            requireNonBlank(capability, "capability");
        }
    }

    /**
     * Encode the request control fields into a {@code payloadRef} descriptor token.
     *
     * @param eventType          the request event type (FEAT-013/014 family)
     * @param traceId           W3C 32-char hex trace id
     * @param correlationId     cross-hop correlation key (gateway = requestId)
     * @param idempotencyKey    client retry idempotency key
     * @param routeHandle       opaque route handle value (resolved via ForwardingEndpointResolver)
     * @param capability        capability identifier
     * @param deadlineMillisEpoch absolute deadline, or {@code Long.MAX_VALUE} for none
     * @return the compact {@code k=v;k=v;...} descriptor
     */
    public static String encode(AgentBusEventType eventType, String traceId, String correlationId,
                                String idempotencyKey, String routeHandle, String capability,
                                long deadlineMillisEpoch) {
        Objects.requireNonNull(eventType, "eventType is required");
        requireNonBlank(traceId, "traceId");
        requireNonBlank(correlationId, "correlationId");
        requireNonBlank(idempotencyKey, "idempotencyKey");
        requireNonBlank(routeHandle, "routeHandle");
        requireNonBlank(capability, "capability");
        return "eventType=" + eventType.name()
                + ";traceId=" + traceId
                + ";correlationId=" + correlationId
                + ";idempotencyKey=" + idempotencyKey
                + ";routeHandle=" + routeHandle
                + ";capability=" + capability
                + ";deadline=" + deadlineMillisEpoch;
    }

    /**
     * Fully decode a request descriptor {@code payloadRef}, validating every required
     * field. Throws on a malformed pair or a missing {@code eventType}. Unknown keys
     * are ignored (forward-compat).
     *
     * @param payloadRef the request descriptor (non-null, non-blank)
     * @return the decoded control descriptor
     * @throws IllegalArgumentException if the descriptor is malformed or missing fields
     */
    public static Descriptor decode(String payloadRef) {
        Objects.requireNonNull(payloadRef, "payloadRef (request descriptor) is required");
        if (payloadRef.isBlank()) {
            throw new IllegalArgumentException("payloadRef (request descriptor) must not be blank");
        }
        AgentBusEventType eventType = null;
        String traceId = null, correlationId = null, idempotencyKey = null;
        String routeHandle = null, capability = null;
        long deadline = 0L;
        for (String pair : payloadRef.split(";")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("malformed descriptor pair: " + pair);
            }
            String k = pair.substring(0, eq);
            String v = pair.substring(eq + 1);
            switch (k) {
                case "eventType" -> eventType = AgentBusEventType.valueOf(v);
                case "traceId" -> traceId = v;
                case "correlationId" -> correlationId = v;
                case "idempotencyKey" -> idempotencyKey = v;
                case "routeHandle" -> routeHandle = v;
                case "capability" -> capability = v;
                case "deadline" -> deadline = Long.parseLong(v);
                default -> { /* ignore unknown keys (forward-compat) */ }
            }
        }
        if (eventType == null) {
            throw new IllegalArgumentException("descriptor missing eventType");
        }
        return new Descriptor(eventType, requireNonBlank(traceId, "traceId"),
                requireNonBlank(correlationId, "correlationId"),
                requireNonBlank(idempotencyKey, "idempotencyKey"),
                requireNonBlank(routeHandle, "routeHandle"),
                requireNonBlank(capability, "capability"), deadline);
    }

    /**
     * Soft-extract the {@code eventType} token from a {@code payloadRef} without
     * throwing — returns {@code null} if the descriptor has no {@code eventType=}
     * token or the value is not a known {@link AgentBusEventType}. Used to skip
     * self-produced responses (which carry {@code taskId=...;status=...}) before the
     * full, validating decode.
     *
     * @param payloadRef the descriptor (nullable; null/blank → null)
     * @return the event type, or {@code null} if absent / unknown
     */
    public static AgentBusEventType softEventType(String payloadRef) {
        if (payloadRef == null || payloadRef.isBlank()) {
            return null;
        }
        for (String pair : payloadRef.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals("eventType")) {
                try {
                    return AgentBusEventType.valueOf(pair.substring(eq + 1));
                } catch (IllegalArgumentException ex) {
                    return null; // unknown event type → treat as non-request
                }
            }
        }
        return null;
    }

    /**
     * Extract a single {@code key=value} token value from a {@code payloadRef}
     * descriptor. Tolerates response-shaped descriptors ({@code taskId=...;
     * status=...; streamRef=...}) without throwing. Returns {@code null} if the
     * payloadRef is null/blank or the key is absent.
     *
     * <p>Used by the gateway to pull the {@code taskId} / {@code status} /
     * {@code streamRef} / {@code reason} tokens from response payloadRefs.
     *
     * @param payloadRef the descriptor (nullable)
     * @param key       the token key to match
     * @return the token value, or {@code null} if absent
     */
    public static String token(String payloadRef, String key) {
        if (payloadRef == null || payloadRef.isBlank()) {
            return null;
        }
        for (String pair : payloadRef.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
