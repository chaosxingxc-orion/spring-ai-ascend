package com.huawei.ascend.service.platform.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforcer for plan §11 row E18: every Micrometer
 * {@code Counter/Timer/Gauge.builder("...")} call in the platform-side main
 * sources (post-Phase-C: {@code agent-service/src/main/...platform}; pre-Phase-C
 * was {@code agent-platform} per ADR-0078) must name a metric beginning with
 * {@code springai_ascend_}.
 *
 * <p>Source-level static scan; complements {@link
 * com.huawei.ascend.service.platform.observability.TenantTagMeterFilter} which strips
 * high-cardinality tags from registered meters at runtime (E19).
 *
 * <p>Enforcer row: docs/governance/enforcers.yaml#E18.
 */
class MetricNamingTest {

    private static final Pattern METRIC_BUILDER = Pattern.compile(
            "\\b(?:Counter|Timer|Gauge|DistributionSummary)\\.builder\\(\\s*\"([^\"]+)\"");

    private static final String EXPECTED_PREFIX = "springai_ascend_";

    @Test
    void all_metric_builder_calls_in_main_sources_use_the_namespace_prefix() throws IOException {
        Path main = Path.of("src/main/java");
        if (!Files.isDirectory(main)) {
            return; // running outside the platform module (pre-Phase-C: agent-platform)
        }
        List<String> violations = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(main)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String body = Files.readString(p, StandardCharsets.UTF_8);
                            Matcher m = METRIC_BUILDER.matcher(body);
                            while (m.find()) {
                                String name = m.group(1);
                                if (!name.startsWith(EXPECTED_PREFIX)) {
                                    violations.add(p + " :: \"" + name + "\"");
                                }
                            }
                        } catch (IOException e) {
                            violations.add(p + " :: read failed: " + e.getMessage());
                        }
                    });
        }
        assertThat(violations)
                .as("Every metric registered under springai_ascend_*; enforcer row E18.")
                .isEmpty();
    }
}
