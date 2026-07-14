package com.huawei.ascend.bus.forwarding.runtime.transport.broker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract for {@link DeliveryFilter} — the broker-agnostic named-property criteria a
 * receiver subscribes with (decision packet agent-bus-broker-filtering-spi-completion
 * §3). {@code forRuntime} stamps the tenant + target-serviceId the runtime wants to
 * receive; the RocketMQ adapter translates this to {@code MessageSelector.bySql(...)},
 * other brokers to their equivalent — the SPI layer never sees SQL (D3).
 */
class DeliveryFilterTest {

    @Test
    void for_runtime_stamps_tenant_id_and_target_service_id_as_required_properties() {
        DeliveryFilter filter = DeliveryFilter.forRuntime("tenant-a", "svc-a");

        assertThat(filter.requiredProperties())
                .containsEntry("tenantId", "tenant-a")
                .containsEntry("targetServiceId", "svc-a")
                .hasSize(2);
    }

    @Test
    void for_runtime_rejects_blank_or_null_criteria() {
        // a blank criterion would silently match nothing (or the wrong traffic) — reject at
        // construction, matching the substrate record convention (ForwardingRouteHandle and
        // BrokerMessageHeaders both validate non-blank).
        assertThatThrownBy(() -> DeliveryFilter.forRuntime(" ", "svc-a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliveryFilter.forRuntime("tenant-a", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliveryFilter.forRuntime(null, "svc-a"))
                .isInstanceOf(NullPointerException.class);
    }
}
