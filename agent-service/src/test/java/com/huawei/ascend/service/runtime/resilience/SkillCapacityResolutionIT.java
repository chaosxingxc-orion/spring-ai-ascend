package com.huawei.ascend.service.runtime.resilience;

import com.huawei.ascend.service.runtime.resilience.spi.ResilienceContract;
import com.huawei.ascend.service.runtime.resilience.spi.SkillCapacityRegistry;
import com.huawei.ascend.service.runtime.resilience.spi.SkillResolution;
import com.huawei.ascend.service.runtime.resilience.spi.SuspendReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule R-K.b — ResilienceContract runtime enforcement (Phase 9 / ADR-0070).
 *
 * <p>Asserts that {@link ResilienceContract#resolve(String, String)} consults
 * {@code skill-capacity.yaml} at runtime and that a second concurrent caller for a
 * 1-capacity skill receives a rejected decision envelope: {@code SkillResolution.admitted = false}
 * carrying a {@link SuspendReason.RateLimited} reason. The W1 shipped surface returns this
 * decision envelope; translating the decision into an actual {@code RunStatus.SUSPENDED}
 * transition is deferred to Rule R-K.c (W2 scheduler admission). The contract here proves
 * the W1 envelope shape (would-suspend intent carrier), NOT a Run state transition.
 *
 * <p>Enforcer row: docs/governance/enforcers.yaml#E73.
 */
class SkillCapacityResolutionIT {

    private static final String BOTTLENECK_YAML = """
            skills:
              - id: bottleneck
                description: 1-capacity test fixture for Rule R-K.b
                capacity_per_tenant: 1
                global_capacity: 1
                queue_strategy: suspend
                timeout_ms: 1000
            """;

    @Test
    void rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne(@TempDir Path tmp) throws IOException {
        Path yaml = tmp.resolve("skill-capacity-test.yaml");
        Files.writeString(yaml, BOTTLENECK_YAML);

        SkillCapacityRegistry registry = new YamlSkillCapacityRegistry(yaml.toString());
        ResilienceContract contract = new DefaultSkillResilienceContract(registry);

        SkillResolution first = contract.resolve("tenant-A", "bottleneck");
        SkillResolution second = contract.resolve("tenant-A", "bottleneck");

        assertThat(first.admitted()).as("first caller admitted").isTrue();
        assertThat(first.reasonIfRejected()).isNull();

        assertThat(second.admitted())
                .as("second concurrent caller must be rejected, not admitted")
                .isFalse();
        assertThat(second.reasonIfRejected())
                .as("rejection carries a SuspendReason — Rule R-K.b returns a decision envelope at W1; actual RunStatus.SUSPENDED transition deferred to Rule R-K.c (W2 scheduler admission)")
                .isInstanceOf(SuspendReason.RateLimited.class);

        SuspendReason.RateLimited reason = (SuspendReason.RateLimited) second.reasonIfRejected();
        assertThat(reason.skill()).isEqualTo("bottleneck");
        assertThat(reason.code()).isEqualTo(SuspendReason.RateLimited.SKILL_CAPACITY_EXCEEDED);
    }

    @Test
    void releaseRestoresCapacity(@TempDir Path tmp) throws IOException {
        Path yaml = tmp.resolve("skill-capacity-test.yaml");
        Files.writeString(yaml, BOTTLENECK_YAML);

        SkillCapacityRegistry registry = new YamlSkillCapacityRegistry(yaml.toString());
        ResilienceContract contract = new DefaultSkillResilienceContract(registry);

        assertThat(contract.resolve("tenant-A", "bottleneck").admitted()).isTrue();
        assertThat(contract.resolve("tenant-A", "bottleneck").admitted()).isFalse();

        registry.release("tenant-A", "bottleneck");
        assertThat(contract.resolve("tenant-A", "bottleneck").admitted())
                .as("after release, capacity slot is reusable")
                .isTrue();
    }

    @Test
    void doubleReleaseFloorsAtZeroAndDoesNotInflateCapacity(@TempDir Path tmp) throws IOException {
        // Atomic check-and-decrement guard: prior code did
        //   if (slot.get() > 0) slot.decrementAndGet();
        // which TOCTOU-races against a parallel release(), letting the counter
        // drift negative and silently inflate capacity for subsequent
        // tryAcquire calls. The fixed implementation must floor at zero so a
        // double-release of a 1-capacity slot does not admit two parallel
        // callers afterwards.
        Path yaml = tmp.resolve("skill-capacity-test.yaml");
        Files.writeString(yaml, BOTTLENECK_YAML);

        YamlSkillCapacityRegistry registry = new YamlSkillCapacityRegistry(yaml.toString());
        ResilienceContract contract = new DefaultSkillResilienceContract(registry);

        assertThat(contract.resolve("tenant-A", "bottleneck").admitted()).isTrue();
        registry.release("tenant-A", "bottleneck");
        registry.release("tenant-A", "bottleneck"); // double-release simulates the TOCTOU loser

        assertThat(contract.resolve("tenant-A", "bottleneck").admitted())
                .as("after release the slot is reusable exactly once")
                .isTrue();
        assertThat(contract.resolve("tenant-A", "bottleneck").admitted())
                .as("double-release MUST NOT inflate capacity beyond the configured limit of 1")
                .isFalse();
    }

    @Test
    void unknownSkillIsRejected(@TempDir Path tmp) throws IOException {
        Path yaml = tmp.resolve("skill-capacity-test.yaml");
        Files.writeString(yaml, BOTTLENECK_YAML);

        SkillCapacityRegistry registry = new YamlSkillCapacityRegistry(yaml.toString());
        ResilienceContract contract = new DefaultSkillResilienceContract(registry);

        SkillResolution result = contract.resolve("tenant-A", "unknown-skill");
        assertThat(result.admitted()).isFalse();
        assertThat(result.reasonIfRejected()).isInstanceOf(SuspendReason.RateLimited.class);
    }
}
