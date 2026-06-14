package com.huawei.ascend.bus.forwarding.test;

import com.huawei.ascend.bus.forwarding.spi.ForwardingDispatcher;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingReceipt;

import java.util.Objects;

/**
 * In-memory test double for {@link ForwardingDispatcher} — NON-PRODUCTION.
 *
 * <p>Stage 7 only confirms durable enqueue via the outbox port; real dispatch /
 * delivery orchestration (dispatcher → receiver transport) is Stage 8. This
 * double therefore delegates straight to {@link ForwardingOutboxPort#enqueue}.
 *
 * <p>Authority: {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §3}.
 */
// non-production — test fixture only; real delivery binding is Stage 8
public final class InMemoryForwardingDispatcher implements ForwardingDispatcher {

    private final ForwardingOutboxPort outbox;

    public InMemoryForwardingDispatcher(ForwardingOutboxPort outbox) {
        this.outbox = Objects.requireNonNull(outbox, "outbox is required");
    }

    @Override
    public ForwardingReceipt dispatch(ForwardingEnvelope envelope, long nowMillisEpoch) {
        return outbox.enqueue(envelope, nowMillisEpoch);
    }
}
