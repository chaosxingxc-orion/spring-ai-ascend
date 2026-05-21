package com.huawei.ascend.service.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link TenantTagMeterFilter} (plan §10, enforcer E19) strips
 * forbidden high-cardinality tag keys from {@code springai_ascend_*} metrics
 * at registration time, and leaves non-namespace metrics untouched.
 *
 * <p>Related enforcers in enforcers.yaml: E18 (MetricNamingTest enforces
 * lowercase namespace), E19 (gate-script high_cardinality_tag_guard). This
 * unit-test is documentation-level coverage; no `#` form so Rule 28k stays
 * scoped to primary-citation checks.
 */
class TenantTagMeterFilterTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new TenantTagMeterFilter().highCardinalityTagScrubber());
    }

    @Test
    void run_id_tag_is_stripped_from_namespace_metric() {
        Counter c = Counter.builder("springai_ascend_test_total")
                .tag("run_id", "11111111-1111-1111-1111-111111111111")
                .tag("tenant", "t1")
                .register(registry);
        assertThat(c.getId().getTags()).extracting("key")
                .doesNotContain("run_id")
                .contains("tenant");
    }

    @Test
    void idempotency_key_tag_is_stripped() {
        Counter c = Counter.builder("springai_ascend_test2_total")
                .tag("idempotency_key", "22222222-2222-2222-2222-222222222222")
                .register(registry);
        assertThat(c.getId().getTags()).extracting("key").doesNotContain("idempotency_key");
    }

    @Test
    void jwt_sub_tag_is_stripped() {
        Counter c = Counter.builder("springai_ascend_test3_total")
                .tag("jwt_sub", "user-123")
                .register(registry);
        assertThat(c.getId().getTags()).extracting("key").doesNotContain("jwt_sub");
    }

    @Test
    void body_tag_is_stripped() {
        Counter c = Counter.builder("springai_ascend_test4_total")
                .tag("body", "{ \"x\": 1 }")
                .register(registry);
        assertThat(c.getId().getTags()).extracting("key").doesNotContain("body");
    }

    @Test
    void non_namespace_metric_is_left_alone() {
        // jvm.* and other framework metrics keep all tags — only the
        // springai_ascend_* namespace is policed.
        Counter c = Counter.builder("jvm_threads_live")
                .tag("run_id", "leave-this-alone")
                .register(registry);
        assertThat(c.getId().getTags()).extracting("key").contains("run_id");
    }

    @Test
    void namespace_metric_keeps_low_cardinality_tags() {
        Counter c = Counter.builder("springai_ascend_keep_test_total")
                .tag("posture", "dev")
                .tag("reason", "missing")
                .register(registry);
        assertThat(c.getId().getTags()).extracting("key")
                .containsExactlyInAnyOrder("posture", "reason");
    }
}
