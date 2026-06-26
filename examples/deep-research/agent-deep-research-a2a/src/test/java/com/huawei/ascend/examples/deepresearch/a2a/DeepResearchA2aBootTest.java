/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.deepresearch.DeepResearchConstants;
import java.net.URI;
import java.time.Duration;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = DeepResearchApplication.class)
@ActiveProfiles("test")
class DeepResearchA2aBootTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @LocalServerPort
    private int port;

    @Test
    void agentCardExposesDeepResearchSkill() throws Exception {
        DeepResearchA2aClient client = client();

        AgentCard agentCard = client.agentCard();

        assertThat(agentCard.name()).isEqualTo(DeepResearchConstants.AGENT_ID);
        assertThat(agentCard.description()).contains("Deep-research root DeepAgent");
        assertThat(agentCard.capabilities().streaming()).isTrue();
        assertThat(agentCard.supportedInterfaces())
                .extracting(AgentInterface::protocolBinding)
                .contains(TransportProtocol.JSONRPC.asString());
        assertThat(agentCard.skills())
                .anySatisfy(skill -> assertThat(skill.id()).isEqualTo("deep-research"));
    }

    private DeepResearchA2aClient client() {
        return new DeepResearchA2aClient(URI.create("http://localhost:" + port), TIMEOUT);
    }
}
