package com.huawei.ascend.examples.workmate.team;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConvergenceSpecTest {

    @Test
    void parsesNoNewFindingsThreshold() {
        assertThat(ConvergenceSpec.noNewFindingsThreshold(null)).isZero();
        assertThat(ConvergenceSpec.noNewFindingsThreshold("")).isZero();
        assertThat(ConvergenceSpec.noNewFindingsThreshold("noNewFindingsForN(2)")).isEqualTo(2);
        assertThat(ConvergenceSpec.noNewFindingsThreshold("prefix noNewFindingsForN(3) suffix")).isEqualTo(3);
        assertThat(ConvergenceSpec.noNewFindingsThreshold("invalid")).isZero();
    }
}
