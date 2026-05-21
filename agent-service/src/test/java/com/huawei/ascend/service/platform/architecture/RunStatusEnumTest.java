package com.huawei.ascend.service.platform.architecture;

import com.huawei.ascend.service.runtime.runs.RunStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the {@link RunStatus} enum at the value set referenced by the HTTP
 * contract (plan §6.5, architecture-status.yaml line 892). In particular,
 * the controller emits {@code PENDING} as the initial state — there must be
 * no {@code CREATED} value that could drift into the contract.
 *
 * <p>Related enforcers in enforcers.yaml: E5 (RunHttpContractIT covers the
 * HTTP-edge surface that exposes these statuses). This test is documentation-
 * level scaffolding for the enum shape; it has no dedicated primary enforcer
 * row, so Rule 28k does not strict-check the `#` form here.
 */
class RunStatusEnumTest {

    @Test
    void run_status_does_not_contain_created() {
        assertThat(Arrays.stream(RunStatus.values()).map(Enum::name))
                .doesNotContain("CREATED");
    }

    @Test
    void run_status_contains_the_canonical_seven_values() {
        assertThat(Arrays.stream(RunStatus.values()).map(Enum::name))
                .containsExactlyInAnyOrder(
                        "PENDING", "RUNNING", "SUSPENDED",
                        "SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED");
    }

    @Test
    void pending_is_the_initial_status_emitted_by_RunController() {
        // The controller posts Run with status=PENDING (see RunController.create).
        // This test pins the assumption that PENDING exists.
        assertThat(RunStatus.valueOf("PENDING")).isNotNull();
    }
}
