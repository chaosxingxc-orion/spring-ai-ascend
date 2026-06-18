package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.a2a.AgentCards;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Covers the discovery endpoint's URL publication contract: a configured
 * public-base-url (which may carry a proxy path prefix) wins over the
 * request-derived base, and every published URL — card url, supported
 * interfaces, AND provider — is rewritten from the same base so the card
 * never leaks a hardcoded or internal address.
 */
class AgentCardControllerTest {

    private static AgentCardController controller(String publicBaseUrl) {
        RuntimeAccessProperties access = new RuntimeAccessProperties();
        access.setPublicBaseUrl(publicBaseUrl);
        return new AgentCardController(AgentCards.create("sample-agent", "agent-runtime"), access);
    }

    private static MockHttpServletRequest request(String scheme, String host, int port) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme(scheme);
        request.setServerName(host);
        request.setServerPort(port);
        return request;
    }

    @Test
    void publicBaseUrlWithPathPrefixWinsOverTheRequest() {
        AgentCardController controller = controller("https://agents.example.com/runtime-one");

        AgentCard card = controller.agentCard(request("http", "internal-pod", 8080));

        assertThat(card.url()).isNull();
        assertThat(card.provider().url()).isEqualTo("https://agents.example.com/runtime-one");
        assertThat(card.supportedInterfaces())
                .extracting(AgentInterface::url)
                .containsExactly("https://agents.example.com/runtime-one/a2a");
        assertThat(cardUrls(card)).noneMatch(url -> url.contains("localhost:8080"));
    }

    @Test
    void publicBaseUrlTrailingSlashDoesNotDoubleTheSeparator() {
        AgentCardController controller = controller("https://agents.example.com/runtime-one/");

        AgentCard card = controller.agentCard(request("http", "localhost", 8080));

        assertThat(card.supportedInterfaces())
                .extracting(AgentInterface::url)
                .containsExactly("https://agents.example.com/runtime-one/a2a");
        assertThat(card.provider().url()).isEqualTo("https://agents.example.com/runtime-one");
    }

    @Test
    void blankPublicBaseUrlFallsBackToTheRequestDerivedBase() {
        AgentCardController controller = controller(null);

        AgentCard card = controller.agentCard(request("http", "edge.internal", 9090));

        assertThat(card.url()).isNull();
        assertThat(card.provider().url()).isEqualTo("http://edge.internal:9090");
        assertThat(card.supportedInterfaces())
                .extracting(AgentInterface::url)
                .containsExactly("http://edge.internal:9090/a2a");
        assertThat(cardUrls(card)).noneMatch(url -> url.contains("localhost:8080"));
    }

    @Test
    void requestDerivedBaseHidesDefaultHttpsPort() {
        AgentCardController controller = controller("");

        AgentCard card = controller.agentCard(request("https", "agents.example.com", 443));

        assertThat(card.supportedInterfaces())
                .extracting(AgentInterface::url)
                .containsExactly("https://agents.example.com/a2a");
        assertThat(card.provider().url()).isEqualTo("https://agents.example.com");
    }

    @Test
    void legacyAgentCardLeavesAbsoluteTopLevelUrlsUntouched() {
        RuntimeAccessProperties access = new RuntimeAccessProperties();
        access.setPublicBaseUrl("https://agents.example.com/runtime-one");
        AgentCard absolute = AgentCard.builder(AgentCards.create("sample-agent", "agent-runtime"))
                .url("https://elsewhere.example.com/a2a")
                .build();
        AgentCardController controller = new AgentCardController(absolute, access);

        AgentCard card = controller.agentCardLegacy(request("http", "localhost", 8080));

        assertThat(card.url()).isEqualTo("https://elsewhere.example.com/a2a");
    }

    private static java.util.List<String> cardUrls(AgentCard card) {
        java.util.List<String> urls = new java.util.ArrayList<>();
        urls.add(card.provider().url());
        card.supportedInterfaces().forEach(i -> urls.add(i.url()));
        return urls;
    }
}
