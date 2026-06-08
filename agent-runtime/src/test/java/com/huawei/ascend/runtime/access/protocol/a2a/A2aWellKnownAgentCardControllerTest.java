package com.huawei.ascend.runtime.access.protocol.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class A2aWellKnownAgentCardControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishesAbsoluteA2aUrlsFromRequestOriginWhenPublicBaseUrlIsNotConfigured() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new A2aWellKnownAgentCardController(relativeAgentCard(), new A2aAccessProperties()))
                .build();

        MvcResult result = mockMvc.perform(get("http://runtime.example:18080/.well-known/agent-card.json"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode card = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(card.path("url").asText()).isEqualTo("http://runtime.example:18080/a2a");
        assertThat(card.path("supportedInterfaces").get(0).path("url").asText())
                .isEqualTo("http://runtime.example:18080/a2a");
    }

    @Test
    void configuredPublicBaseUrlOverridesRequestOriginForPublishedA2aUrls() throws Exception {
        A2aAccessProperties properties = new A2aAccessProperties();
        properties.setPublicBaseUrl("https://agents.example.com/runtime-one");
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new A2aWellKnownAgentCardController(relativeAgentCard(), properties))
                .build();

        MvcResult result = mockMvc.perform(get("http://internal:8080/.well-known/agent-card.json"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode card = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(card.path("url").asText()).isEqualTo("https://agents.example.com/runtime-one/a2a");
        assertThat(card.path("supportedInterfaces").get(0).path("url").asText())
                .isEqualTo("https://agents.example.com/runtime-one/a2a");
    }

    private static AgentCard relativeAgentCard() {
        return AgentCard.builder()
                .name("sample")
                .description("sample")
                .url("/a2a")
                .version("1")
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();
    }
}
