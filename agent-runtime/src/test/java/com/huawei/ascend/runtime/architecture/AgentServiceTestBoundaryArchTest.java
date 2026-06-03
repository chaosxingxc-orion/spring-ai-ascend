package com.huawei.ascend.service.runtime.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AgentServiceTestBoundaryArchTest {

    @Test
    void agentServiceTestTreeDoesNotOwnAgentBusDefinitionTests() {
        Path misplacedBusTests = Path.of(System.getProperty("user.dir"), "src/test/java/com/huawei/ascend/bus");

        assertThat(Files.exists(misplacedBusTests))
                .as("agent-bus definition tests belong in the agent-bus module, not agent-service")
                .isFalse();
    }
}
