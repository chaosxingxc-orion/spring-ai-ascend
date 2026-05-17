package ascend.springai.service.runtime.resilience;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule 41.b — ResilienceContract runtime enforcement (Phase 9 / ADR-0070).
 *
 * <p>Asserts that {@link ResilienceContract#resolve(String, String)} consults
 * {@code skill-capacity.yaml} at runtime and that a second concurrent caller for a
 * 1-capacity skill is rejected via {@code SkillResolution.admitted = false} with
 * a {@link SuspendReason.RateLimited} reason — proving the contract maps capacity
 * exhaustion to "would-suspend", NOT to "would-fail".
 *
 * <p>Enforcer row: docs/governance/enforcers.yaml#E73.
 */
class SkillCapacityResolutionIT {

    private static final String BOTTLENECK_YAML = """
            skills:
              - id: bottleneck
                description: 1-capacity test fixture for Rule 41.b
                capacity_per_tenant: 1
                global_capacity: 1
                queue_strategy: suspend
                timeout_ms: 1000
            """;

    @Test
    void suspendsSecondCallerWhenCapacityIsOne(@TempDir Path tmp) throws IOException {
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
                .as("rejection carries a SuspendReason — Rule 41.b maps capacity to SUSPENDED, not FAILED")
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
