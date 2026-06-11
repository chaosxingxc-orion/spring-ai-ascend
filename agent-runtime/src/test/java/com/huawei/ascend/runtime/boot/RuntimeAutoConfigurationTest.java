package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.TrajectoryLevel;
import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import org.junit.jupiter.api.Test;

/** Covers the config→settings mapping that decides whether (and how) trajectory is enabled in prod. */
class RuntimeAutoConfigurationTest {

    @Test
    void disabledYieldsOff() {
        TrajectoryProperties properties = new TrajectoryProperties();
        properties.setEnabled(false);
        assertThat(RuntimeAutoConfiguration.toTrajectorySettings(properties).level()).isEqualTo(TrajectoryLevel.OFF);
    }

    @Test
    void fullLevelIsParsedWithMaskAndTruncate() {
        TrajectoryProperties properties = new TrajectoryProperties();
        properties.setDefaultLevel("full");
        TrajectorySettings settings = RuntimeAutoConfiguration.toTrajectorySettings(properties);
        assertThat(settings.level()).isEqualTo(TrajectoryLevel.FULL);
        assertThat(settings.truncateChars()).isEqualTo(256);
        assertThat(settings.maskKeyPattern()).isNotNull();
    }

    @Test
    void unrecognizedLevelFallsBackToSummary() {
        TrajectoryProperties properties = new TrajectoryProperties();
        properties.setDefaultLevel("nonsense");
        assertThat(RuntimeAutoConfiguration.toTrajectorySettings(properties).level()).isEqualTo(TrajectoryLevel.SUMMARY);
    }

    @Test
    void offLevelYieldsOff() {
        TrajectoryProperties properties = new TrajectoryProperties();
        properties.setDefaultLevel("off");
        assertThat(RuntimeAutoConfiguration.toTrajectorySettings(properties).level()).isEqualTo(TrajectoryLevel.OFF);
    }

    @Test
    void invalidMaskPatternFailsSafeToTheDefaultNotABootCrash() {
        TrajectoryProperties properties = new TrajectoryProperties();
        properties.getMask().setKeyPattern("(unbalanced");
        TrajectorySettings settings = RuntimeAutoConfiguration.toTrajectorySettings(properties);
        // Never crashes, never degrades to a null pattern (which would silently disable redaction).
        assertThat(settings.maskKeyPattern().pattern()).isEqualTo(TrajectoryMasking.DEFAULT_KEY_PATTERN);
    }
}
