package com.huawei.ascend.examples.a2a.versatileparent;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Auto-starts the Versatile mock WireMock server for the {@code versatile}
 * profile (the child agent). The mock listens on port 18083 — the same port
 * configured as the default {@code versatile.url} in
 * {@code application-versatile.yaml}.
 *
 * <p>With this configuration the child agent can run completely standalone
 * without an external Versatile REST API.
 */
@Configuration(proxyBeanMethods = false)
@Profile("versatile")
@ConditionalOnProperty(name = "versatile.mock.embedded", havingValue = "true", matchIfMissing = true)
public class VersatileMockConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(VersatileMockConfiguration.class);
    static final int PORT = 18083;
    private static final String SCENARIO = "hotel-booking";
    private static final String STATE_FIRST_DONE = "first-done";

    @Bean
    WireMockServer versatileMockServer() {
        WireMockServer server = new WireMockServer(PORT);
        server.start();
        WireMock.configureFor(PORT);

        // First request → hotels_info WITHOUT End → child emits INTERRUPTED
        WireMock.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/.*/agents/.*/conversations/.*"))
                .inScenario(SCENARIO).whenScenarioStateIs(Scenario.STARTED)
                .withQueryParam("type", WireMock.equalTo("controller"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/event-stream; charset=utf-8")
                        .withBody(VersatileMockSse.hotelsInfoNoEnd()))
                .willSetStateTo(STATE_FIRST_DONE));

        WireMock.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/.*/agents/.*/conversations/.*"))
                .inScenario(SCENARIO).whenScenarioStateIs(STATE_FIRST_DONE)
                .withQueryParam("type", WireMock.equalTo("controller"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/event-stream; charset=utf-8")
                        .withBody(VersatileMockSse.hotelBookSuccess())));

        LOG.info("Versatile mock server started on port {} (versatile.mock.enabled=true)", PORT);
        return server;
    }

    @Bean
    DisposableBean versatileMockShutdown(WireMockServer server) {
        return () -> {
            server.stop();
            LOG.info("Versatile mock server stopped.");
        };
    }
}
