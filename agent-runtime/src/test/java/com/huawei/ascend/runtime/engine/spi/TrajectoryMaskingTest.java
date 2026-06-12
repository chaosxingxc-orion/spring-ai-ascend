package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TrajectoryMaskingTest {

    private static final Pattern KEYS = Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN);

    @Test
    void redactsSensitiveMapKeysRecursively() {
        Object masked = TrajectoryMasking.mask(
                Map.of("apiKey", "abc", "nested", Map.of("password", "p", "name", "ok")),
                KEYS, 256);
        assertThat(masked).isInstanceOf(Map.class);
        Map<?, ?> out = (Map<?, ?>) masked;
        assertThat(out.get("apiKey")).isEqualTo("***");
        assertThat(((Map<?, ?>) out.get("nested")).get("password")).isEqualTo("***");
        assertThat(((Map<?, ?>) out.get("nested")).get("name")).isEqualTo("ok");
    }

    @Test
    void truncatesLongStringsAtTheConfiguredBound() {
        String longText = "x".repeat(300);
        Object truncated = TrajectoryMasking.mask(longText, KEYS, 256);
        assertThat((String) truncated).startsWith("x".repeat(256)).contains("(300)");

        Object unbounded = TrajectoryMasking.mask(longText, KEYS, 0);
        assertThat(unbounded).isEqualTo(longText);
    }

    @Test
    void walksListsAndPassesScalars() {
        Object masked = TrajectoryMasking.mask(List.of("a", Map.of("token", "t")), KEYS, 256);
        assertThat(masked).isInstanceOf(List.class);
        List<?> out = (List<?>) masked;
        assertThat(out.get(0)).isEqualTo("a");
        assertThat(((Map<?, ?>) out.get(1)).get("token")).isEqualTo("***");
        assertThat(TrajectoryMasking.mask(42, KEYS, 256)).isEqualTo(42);
        assertThat(TrajectoryMasking.mask(null, KEYS, 256)).isNull();
    }
}
