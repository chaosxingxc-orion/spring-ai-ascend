package com.huawei.ascend.examples.a2a.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = OpenJiuwenMemoryApplication.class)
class OpenJiuwenMemoryA2aE2eTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String AGENT_ID = OpenJiuwenMemoryConfiguration.AGENT_ID;

    @LocalServerPort
    private int port;

    @Test
    void agentCardIsExposedWithCorrectIdentity() throws Exception {
        SampleA2aClient client = new SampleA2aClient(URI.create("http://localhost:" + port), TIMEOUT);

        AgentCard agentCard = client.agentCard();
        assertThat(agentCard.name()).isEqualTo(AGENT_ID);
        assertThat(agentCard.description()).contains("memory engine");
        assertThat(agentCard.capabilities().streaming()).isTrue();
        assertThat(agentCard.supportedInterfaces())
                .extracting(AgentInterface::protocolBinding)
                .contains(TransportProtocol.JSONRPC.asString());
    }
}
