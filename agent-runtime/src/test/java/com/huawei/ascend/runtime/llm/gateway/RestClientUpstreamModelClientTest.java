package com.huawei.ascend.runtime.llm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.huawei.ascend.runtime.llm.gateway.UpstreamModelClient.UpstreamRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestClientUpstreamModelClientTest {

    private static WireMockServer upstream;

    private final RestClientUpstreamModelClient client =
            new RestClientUpstreamModelClient(Duration.ofSeconds(2), Duration.ofSeconds(10));

    @BeforeAll
    static void startUpstream() {
        upstream = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        upstream.start();
    }

    @AfterAll
    static void stopUpstream() {
        upstream.stop();
    }

    @BeforeEach
    void resetUpstream() {
        upstream.resetAll();
        upstream.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));
    }

    /** The configured bounds reach both underlying clients — a hung provider can never pin threads forever. */
    @Test
    void constructorPlumbsConnectAndRequestTimeouts() {
        assertThat(client.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(client.requestTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void bufferedExchangeSendsBearerCredential() {
        client.exchange(request("sk-real"));

        assertThat(lastRequest().getHeader("Authorization")).isEqualTo("Bearer sk-real");
    }

    /** Empty api-key = documented no-auth upstream: no Authorization header at all, never "Bearer null". */
    @Test
    void bufferedExchangeOmitsAuthorizationHeaderForNoAuthUpstream() {
        client.exchange(request(""));

        assertThat(lastRequest().containsHeader("Authorization")).isFalse();
    }

    @Test
    void streamingOpenSendsBearerCredential() throws Exception {
        try (var response = client.openStream(request("sk-real"))) {
            assertThat(response.status()).isEqualTo(200);
        }

        assertThat(lastRequest().getHeader("Authorization")).isEqualTo("Bearer sk-real");
    }

    @Test
    void streamingOpenOmitsAuthorizationHeaderForNoAuthUpstream() throws Exception {
        try (var response = client.openStream(request(""))) {
            assertThat(response.status()).isEqualTo(200);
        }

        assertThat(lastRequest().containsHeader("Authorization")).isFalse();
    }

    private static UpstreamRequest request(String apiKey) {
        return new UpstreamRequest(
                "http://localhost:" + upstream.port() + "/v1/chat/completions",
                apiKey,
                "{\"model\":\"m\"}".getBytes(StandardCharsets.UTF_8));
    }

    private static LoggedRequest lastRequest() {
        var requests = upstream.findAll(postRequestedFor(urlEqualTo("/v1/chat/completions")));
        assertThat(requests).hasSize(1);
        return requests.get(0);
    }
}
