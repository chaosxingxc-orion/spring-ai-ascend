package com.huawei.ascend.bus.forwarding.runtime.transport.broker.rocketmq;

import com.huawei.ascend.bus.forwarding.spi.broker.DeliveryFilter;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the broker-agnostic {@link DeliveryFilter} → RocketMQ SQL92 mapping on
 * {@link RocketMqBrokerForwardingConsumer} (decision packet
 * agent-bus-broker-filtering-spi-completion §7 slice 2 / §3 D3). The SPI layer never sees SQL;
 * this is the adapter-confined translation the real {@code MessageSelector.bySql} will consume.
 * Pure mapping — no broker, no RocketMQ client needed.
 */
class RocketMqBrokerForwardingConsumerTest {

    @Test
    void sql92_for_runtime_filter_and_joins_required_properties_sorted_by_key() {
        // forRuntime stamps {tenantId, targetServiceId}; the SQL92 expression AND-joins each
        // key='value' clause, keys sorted so the string is deterministic (MessageSelector.bySql
        // does not constrain clause order; the unit pins a stable expression).
        DeliveryFilter filter = DeliveryFilter.forRuntime("tenant-a", "svc-a");

        String sql = RocketMqBrokerForwardingConsumer.sql92Expression(filter);

        assertThat(sql).isEqualTo("targetServiceId = 'svc-a' AND tenantId = 'tenant-a'");
    }

    @Test
    void sql92_for_single_required_property_emits_one_clause_no_and() {
        DeliveryFilter filter = new DeliveryFilter(Map.of("targetServiceId", "svc-a"));

        String sql = RocketMqBrokerForwardingConsumer.sql92Expression(filter);

        assertThat(sql).isEqualTo("targetServiceId = 'svc-a'");
    }

    @Test
    void sql92_doubles_single_quotes_in_values_so_the_expression_stays_well_formed() {
        // a value containing a single quote is SQL92-escaped by doubling (') -> ('') so the
        // expression stays well-formed and matches literally, not as a syntax error / injection.
        DeliveryFilter filter = new DeliveryFilter(Map.of("targetServiceId", "svc'a"));

        String sql = RocketMqBrokerForwardingConsumer.sql92Expression(filter);

        assertThat(sql).isEqualTo("targetServiceId = 'svc''a'");
    }
}
