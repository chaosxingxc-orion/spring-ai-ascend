package com.huawei.ascend.service.runtime.orchestration;

import com.huawei.ascend.bus.spi.engine.AgentEvent;
import com.huawei.ascend.bus.spi.engine.EngineDescriptor;
import com.huawei.ascend.bus.spi.engine.EnginePort;
import com.huawei.ascend.bus.spi.engine.ExecuteRequest;
import com.huawei.ascend.bus.spi.engine.ExecutionContext;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.bus.spi.engine.RunContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0158 signature regression guard: the EnginePort boundary MUST NOT retain the old
 * value-returning {@code Object execute(RunContext, ExecutorDefinition, Object)} shape (which
 * threw {@code SuspendSignal} across the boundary), and MUST expose the new streaming shape:
 * {@code Flow.Publisher<AgentEvent> execute(ExecutionContext, ExecuteRequest)} plus a
 * {@code describe()} returning {@link EngineDescriptor}.
 */
class EnginePortSignatureNoRegressionTest {

    @Test
    void old_value_returning_execute_is_gone() {
        boolean hasLegacy = Arrays.stream(EnginePort.class.getMethods())
                .anyMatch(m -> m.getName().equals("execute")
                        && m.getReturnType() == Object.class
                        && m.getParameterCount() == 3
                        && m.getParameterTypes()[0] == RunContext.class
                        && m.getParameterTypes()[1] == ExecutorDefinition.class
                        && m.getParameterTypes()[2] == Object.class);
        assertThat(hasLegacy)
                .as("EnginePort must not retain Object execute(RunContext, ExecutorDefinition, Object)")
                .isFalse();
    }

    @Test
    void streaming_execute_signature_is_present() {
        Method execute = Arrays.stream(EnginePort.class.getMethods())
                .filter(m -> m.getName().equals("execute"))
                .findFirst()
                .orElseThrow();

        assertThat(execute.getReturnType())
                .as("execute must return a Flow.Publisher stream")
                .isEqualTo(Flow.Publisher.class);
        assertThat(execute.getParameterCount()).isEqualTo(2);
        assertThat(execute.getParameterTypes()[0]).isEqualTo(ExecutionContext.class);
        assertThat(execute.getParameterTypes()[1]).isEqualTo(ExecuteRequest.class);

        // The element type of the Flow.Publisher is AgentEvent.
        assertThat(execute.getGenericReturnType().getTypeName())
                .contains(AgentEvent.class.getName());
    }

    @Test
    void describe_returns_engine_descriptor() {
        Method describe = Arrays.stream(EnginePort.class.getMethods())
                .filter(m -> m.getName().equals("describe"))
                .findFirst()
                .orElseThrow();
        assertThat(describe.getReturnType()).isEqualTo(EngineDescriptor.class);
        assertThat(describe.getParameterCount()).isZero();
    }
}
