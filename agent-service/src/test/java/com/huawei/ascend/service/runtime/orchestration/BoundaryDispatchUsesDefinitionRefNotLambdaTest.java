package com.huawei.ascend.service.runtime.orchestration;

import com.huawei.ascend.bus.spi.engine.DefinitionRef;
import com.huawei.ascend.bus.spi.engine.EnginePort;
import com.huawei.ascend.bus.spi.engine.ExecuteRequest;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0158 wire-form invariant: the EnginePort boundary dispatches a serializable
 * {@link DefinitionRef} (capability name), never an inline {@link ExecutorDefinition} whose
 * node/reasoner functions are JVM lambdas that cannot cross a transport. The wire request
 * {@link ExecuteRequest} must be {@link Serializable}, carry a {@code DefinitionRef} component,
 * and carry NO {@code ExecutorDefinition} component; {@code EnginePort.execute}'s second
 * parameter must be the {@code ExecuteRequest} wire type.
 */
class BoundaryDispatchUsesDefinitionRefNotLambdaTest {

    @Test
    void execute_request_is_serializable() {
        assertThat(Serializable.class).isAssignableFrom(ExecuteRequest.class);
    }

    @Test
    void execute_request_carries_a_definition_ref_and_no_executor_definition() {
        RecordComponent[] components = ExecuteRequest.class.getRecordComponents();
        assertThat(components).as("ExecuteRequest must be a record").isNotNull();

        boolean hasDefinitionRef = Arrays.stream(components)
                .anyMatch(c -> c.getType() == DefinitionRef.class);
        assertThat(hasDefinitionRef)
                .as("ExecuteRequest must carry a DefinitionRef component")
                .isTrue();

        boolean hasExecutorDefinition = Arrays.stream(components)
                .anyMatch(c -> ExecutorDefinition.class.isAssignableFrom(c.getType()));
        assertThat(hasExecutorDefinition)
                .as("ExecuteRequest must NOT carry an inline ExecutorDefinition (lambdas cannot cross a wire)")
                .isFalse();
    }

    @Test
    void engine_port_execute_takes_the_wire_request() {
        Method execute = Arrays.stream(EnginePort.class.getMethods())
                .filter(m -> m.getName().equals("execute"))
                .findFirst()
                .orElseThrow();
        assertThat(execute.getParameterTypes()[1])
                .as("EnginePort.execute's 2nd parameter must be the ExecuteRequest wire type")
                .isEqualTo(ExecuteRequest.class);
    }
}
