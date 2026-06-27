package com.huawei.ascend.examples.workmate.team;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.office.CoordinationSpec;
import org.junit.jupiter.api.Test;

class TeamTerminationGuardTest {

    @Test
    void noBudgetNeverExpires() {
        TeamTerminationGuard guard = new TeamTerminationGuard(null);
        assertThat(guard.expired()).isFalse();
        assertThat(guard.expiredMessage()).isEmpty();
    }

    @Test
    void expiresAfterBudget() throws Exception {
        TeamTerminationGuard guard =
                new TeamTerminationGuard(new CoordinationSpec.Termination(null, 50L, null, null));
        Thread.sleep(80);
        assertThat(guard.expired()).isTrue();
        assertThat(guard.expiredMessage().orElse("")).contains("time budget");
    }
}
