package com.huawei.ascend.service.runtime.capability;

import com.huawei.ascend.engine.exec.SequentialGraphExecutor;
import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.bus.spi.engine.DefinitionRef;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.bus.spi.engine.Orchestrator;
import com.huawei.ascend.service.runtime.orchestration.TestEnginePorts;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryCheckpointer;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryRunRegistry;
import com.huawei.ascend.service.runtime.orchestration.inmemory.SyncOrchestrator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W4.1 spike — a {@link DefinitionRef} (the serializable capability name) is what
 * crosses a transport boundary; a remote engine resolves it to its own
 * {@link ExecutorDefinition}. In-process the same name resolves through
 * {@link CapabilityRegistry} to the fully-built, lambda-bearing definition and
 * dispatches identically. Proves dispatch-by-name plus reference serializability,
 * without forcing the unserializable definition itself onto the wire.
 */
class DefinitionRefDispatchByNameTest {

    private static ExecutorDefinition.GraphDefinition echoGraph() {
        return new ExecutorDefinition.GraphDefinition(
                Map.of("echo", (ctx, p) -> "ECHO"),
                Map.of(),
                "echo");
    }

    private static Capability echoCapability() {
        return new Capability() {
            @Override
            public String capabilityName() {
                return "echo";
            }

            @Override
            public ExecutorDefinition definition() {
                return echoGraph();
            }
        };
    }

    @Test
    void definition_ref_resolves_and_dispatches_in_process_by_name() {
        CapabilityRegistry registry = new CapabilityRegistry(List.of(echoCapability()));
        DefinitionRef ref = new DefinitionRef("echo");

        Capability resolved = registry.resolve(ref.capabilityName()).orElseThrow();
        Orchestrator orchestrator = TestEnginePorts.inProcessOrchestrator(
                new InMemoryRunRegistry(),
                new InMemoryCheckpointer(),
                new EngineRegistry().register(new SequentialGraphExecutor()));

        Object result = orchestrator.run(UUID.randomUUID(), "spike-tenant",
                resolved.definition(), resolved.initialPayload());

        assertThat(result).isEqualTo("ECHO");
    }

    @Test
    void definition_ref_is_byte_serializable_and_round_trips() throws Exception {
        DefinitionRef ref = new DefinitionRef("echo");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(ref);
        }
        Object back;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            back = in.readObject();
        }

        assertThat(back).isEqualTo(ref);
        assertThat(((DefinitionRef) back).capabilityName()).isEqualTo("echo");
    }

    @Test
    void definition_ref_rejects_blank_name() {
        assertThatThrownBy(() -> new DefinitionRef("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
