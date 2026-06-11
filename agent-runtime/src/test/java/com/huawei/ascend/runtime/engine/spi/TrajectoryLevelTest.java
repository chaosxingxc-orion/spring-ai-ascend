package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TrajectoryLevelTest {

    @Test
    void nullOrBlankFallsBackToTheProvidedDefault() {
        assertThat(TrajectoryLevel.from(null, TrajectoryLevel.SUMMARY)).isEqualTo(TrajectoryLevel.SUMMARY);
        assertThat(TrajectoryLevel.from("   ", TrajectoryLevel.FULL)).isEqualTo(TrajectoryLevel.FULL);
    }

    @Test
    void parsesCaseInsensitivelyAndTrimmed() {
        assertThat(TrajectoryLevel.from("full", TrajectoryLevel.OFF)).isEqualTo(TrajectoryLevel.FULL);
        assertThat(TrajectoryLevel.from("  Summary ", TrajectoryLevel.OFF)).isEqualTo(TrajectoryLevel.SUMMARY);
        assertThat(TrajectoryLevel.from("OFF", TrajectoryLevel.FULL)).isEqualTo(TrajectoryLevel.OFF);
    }

    @Test
    void unrecognizedValueFallsBackToTheProvidedDefault() {
        assertThat(TrajectoryLevel.from("verbose", TrajectoryLevel.SUMMARY)).isEqualTo(TrajectoryLevel.SUMMARY);
    }
}
