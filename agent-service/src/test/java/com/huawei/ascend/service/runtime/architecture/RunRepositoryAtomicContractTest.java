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
                UUID.class,
                UnaryOperator.class);

        assertThat(method.isDefault()).isFalse();
        assertThat(Modifier.isAbstract(method.getModifiers())).isTrue();
    }

    @Test
    void inMemoryRunRegistryOverridesTheAtomicUpdatePrimitive() throws Exception {
        Method method = InMemoryRunRegistry.class.getDeclaredMethod(
                "updateIfNotTerminal",
                UUID.class,
                UnaryOperator.class);

        assertThat(method.getReturnType()).isEqualTo(Optional.class);
        assertThat(method.getParameterTypes()).containsExactly(UUID.class, UnaryOperator.class);
        assertThat(method.getGenericReturnType().getTypeName()).contains(Run.class.getName());
    }
}
