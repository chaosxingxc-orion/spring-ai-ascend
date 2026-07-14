package com.huawei.ascend.bus.forwarding.runtime.transport.broker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * RocketMQ concrete adapter for {@link BrokerForwardingConsumerPort} (FEAT-013/014, decision
 * packet agent-bus-broker-filtering-spi-completion §7 slice 2).
 *
 * <p>At full strength this adapter subscribes a {@code DefaultLitePullConsumer}
 * (group={@code consumerServiceId}, topic=resolver(route),
 * filter={@code MessageSelector.bySql(sql92Expression(filter))}) and polls param-less, committing
 * per-message (model B ack-after-consume). The broker-agnostic {@link DeliveryFilter} (D3) is
 * translated to a RocketMQ SQL92 expression HERE — the SPI layer never sees SQL (bySql confined to
 * the adapter, mirroring how {@link RocketMqBrokerForwardingRelay} owns the produce-side mapping).
 *
 * <p><b>Implementation status (slice 2).</b> Only the pure {@link #sql92Expression} mapping is
 * unit-tested here (no broker, no RocketMQ client needed). The real consumer lifecycle —
 * subscribe / poll / commit against a live {@code DefaultLitePullConsumer} — is supplemented
 * during 联调 + slice 6 (env-guarded real-broker IT, not a unit test), the same testability seam
 * the relay uses ({@link RocketMqBrokerForwardingRelay.MessageSender} for produce).
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/review-packets/
 * agent-bus-broker-filtering-spi-completion-decision.md} §7 / §3 (D3); L2 feat-013 §5.2.
 */
// scope: forwarding transport.broker — concrete RocketMQ consumer adapter (SPI-licensed, ArchUnit-confined)
public final class RocketMqBrokerForwardingConsumer {

    private RocketMqBrokerForwardingConsumer() {
        // slice 2 holds only the static bySql mapping; the instance + DefaultLitePullConsumer
        // lifecycle is wired during 联调 (slice 6).
    }

    /**
     * Pure mapping: a broker-agnostic {@link DeliveryFilter} → RocketMQ SQL92 expression string
     * (the {@code MessageSelector.bySql} argument). Each {@code requiredProperties} entry becomes a
     * {@code key = 'value'} clause; clauses are AND-joined and keys sorted for a deterministic
     * expression (RocketMQ does not constrain clause order). Single quotes in values are doubled
     * ({@code '} → {@code ''}) per SQL92 string-literal escaping, so a value containing a quote
     * stays well-formed and matches literally. Extracted static + package-private so unit tests
     * pin the mapping without a broker.
     */
    static String sql92Expression(DeliveryFilter filter) {
        Objects.requireNonNull(filter, "filter is required");
        // sort keys for a deterministic expression (RocketMQ does not constrain clause order);
        // single quotes in values are doubled per SQL92 string-literal escaping.
        List<String> keys = new ArrayList<>(filter.requiredProperties().keySet());
        Collections.sort(keys);
        List<String> clauses = new ArrayList<>(keys.size());
        for (String key : keys) {
            String value = filter.requiredProperties().get(key);
            clauses.add(key + " = '" + value.replace("'", "''") + "'");
        }
        return String.join(" AND ", clauses);
    }
}
