package com.huawei.ascend.agentsdk.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OptionValuesTest {

    @Test
    void numbersAndNumericStringsCoerce() {
        assertThat(OptionValues.intOption(Map.of("maxIterations", 10), "maxIterations", 5)).isEqualTo(10);
        // Quoted YAML scalars and ${ENV} substitution both arrive as String.
        assertThat(OptionValues.intOption(Map.of("maxIterations", "10"), "maxIterations", 5)).isEqualTo(10);
        assertThat(OptionValues.intOption(Map.of(), "maxIterations", 5)).isEqualTo(5);
    }

    @Test
    void garbageFailsWithTheOptionName() {
        assertThatThrownBy(() -> OptionValues.intOption(Map.of("maxIterations", "ten"), "maxIterations", 5))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maxIterations");
    }
}
