package com.huawei.ascend.service.runtime.architecture;

import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryRunRegistry;
import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.service.runtime.runs.spi.RunRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class RunRepositoryAtomicContractTest {

    @Test
    void updateIfNotTerminalIsAnAbstractAtomicSpiRequirement() throws Exception {
        Method method = RunRepository.class.getMethod(
                "updateIfNotTerminal",
                String.class,
                UUID.class,
                UnaryOperator.class);

        assertThat(method.isDefault()).isFalse();
        assertThat(Modifier.isAbstract(method.getModifiers())).isTrue();
    }

    @Test
    void inMemoryRunRegistryOverridesTheAtomicUpdatePrimitive() throws Exception {
        Method method = InMemoryRunRegistry.class.getDeclaredMethod(
                "updateIfNotTerminal",
                String.class,
                UUID.class,
                UnaryOperator.class);

        assertThat(method.getReturnType()).isEqualTo(Optional.class);
        assertThat(method.getParameterTypes()).containsExactly(String.class, UUID.class, UnaryOperator.class);
        assertThat(method.getGenericReturnType().getTypeName()).contains(Run.class.getName());
    }

    @Test
    void crossTenantUpdateIfNotTerminalReturnsEmptyAndDoesNotMutate() {
        // The tenantId parameter codifies tenant-first persistence (Rule R-C.2.a +
        // R-J.a) in the SPI shape. A caller from tenant-B MUST NOT be able to drive
        // a Run state transition against tenant-A's row. Cross-tenant access
        // collapses to "not found" per Rule R-J.b W0 posture — Optional.empty().
        InMemoryRunRegistry runs = new InMemoryRunRegistry();
        UUID runId = UUID.randomUUID();
        Run tenantARun = new Run(runId, "tenant-A", "cap",
                com.huawei.ascend.service.runtime.runs.RunStatus.RUNNING,
                com.huawei.ascend.engine.orchestration.spi.RunMode.GRAPH,
                java.time.Instant.now(), java.time.Instant.now(),
                null, null, null, null, null);
        runs.save(tenantARun);

        Optional<Run> result = runs.updateIfNotTerminal("tenant-B", runId,
                r -> r.withStatus(com.huawei.ascend.service.runtime.runs.RunStatus.CANCELLED));

        assertThat(result).as("cross-tenant updateIfNotTerminal must collapse to empty").isEmpty();
        assertThat(runs.findById(runId).orElseThrow().status())
                .as("tenant-A row must remain unchanged after foreign-tenant attempt")
                .isEqualTo(com.huawei.ascend.service.runtime.runs.RunStatus.RUNNING);
    }
}
