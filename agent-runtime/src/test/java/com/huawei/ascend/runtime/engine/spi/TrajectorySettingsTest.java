package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Verifies the factory methods and default values on {@link TrajectorySettings}. */
class TrajectorySettingsTest {

    @Test
    void offHasSampleRateOneAndDisabled() {
        TrajectorySettings settings = TrajectorySettings.off();
        assertThat(settings.enabled()).isFalse();
        assertThat(settings.sampleRate()).isEqualTo(1.0);
    }

    @Test
    void basicHasSampleRateOne() {
        TrajectorySettings settings = TrajectorySettings.basic(
                true, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        assertThat(settings.enabled()).isTrue();
        assertThat(settings.sampleRate()).isEqualTo(1.0);
        assertThat(settings.truncateChars()).isEqualTo(256);
        assertThat(settings.maskKeyPattern()).isNotNull();
    }

    @Test
    void fourArgConstructorCarriesSampleRate() {
        TrajectorySettings settings = new TrajectorySettings(
                true, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256, 0.5);
        assertThat(settings.sampleRate()).isEqualTo(0.5);
    }
}
