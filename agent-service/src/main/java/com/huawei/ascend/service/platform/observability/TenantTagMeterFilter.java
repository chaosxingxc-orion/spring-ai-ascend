package com.huawei.ascend.service.platform.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Strips high-cardinality tags from every {@code springai_ascend_*} metric
 * registration (plan §10 forbidden-tag list, ADR-0056 / ADR-0057 metric surface).
 *
 * <p>Forbidden tag keys are: {@code run_id}, {@code idempotency_key},
 * {@code jwt_sub}, {@code body}. These leak request-correlation identifiers into
 * the metric registry and explode cardinality (UUIDs and opaque keys are not
 * dimensions, they are correlation IDs).
 *
 * <p>Implementation note: Micrometer's {@link MeterFilter#map(Meter.Id)} runs
 * at meter registration time. The filter is registered as a Spring bean and
 * applied to every {@code MeterRegistry} via Spring Boot's
 * {@code MeterRegistryConfigurer} autoconfiguration.
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E18 (metric prefix),
 * #E19 (forbidden tag scrubber).
 */
@Configuration(proxyBeanMethods = false)
public class TenantTagMeterFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TenantTagMeterFilter.class);

    static final Set<String> FORBIDDEN_TAG_KEYS = Set.of(
            "run_id",
            "idempotency_key",
            "jwt_sub",
            "body");

    static final String METRIC_NAMESPACE_PREFIX = "springai_ascend_";

    @Bean
    public MeterFilter highCardinalityTagScrubber() {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if (!id.getName().startsWith(METRIC_NAMESPACE_PREFIX)) {
                    return id;
                }
                Tags kept = Tags.empty();
                boolean stripped = false;
                for (Tag tag : id.getTags()) {
                    if (FORBIDDEN_TAG_KEYS.contains(tag.getKey())) {
                        stripped = true;
                    } else {
                        kept = kept.and(tag);
                    }
                }
                if (stripped) {
                    LOG.warn("Stripped forbidden high-cardinality tag from metric {}: keys must not include {}",
                            id.getName(), FORBIDDEN_TAG_KEYS);
                    return id.replaceTags(kept);
                }
                return id;
            }

            @Override
            public MeterFilterReply accept(Meter.Id id) {
                return MeterFilterReply.NEUTRAL;
            }
        };
    }
}
