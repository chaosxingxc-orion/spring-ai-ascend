package com.huawei.ascend.examples.a2a.versatileparent;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

/**
 * Test-only WireMock versatile API for JUnit E2E tests.
 * Uses a random port so multiple test runs never collide.
 *
 * <p>SSE bodies are shared with {@link VersatileMockServer} via
 * {@link VersatileMockSse}.
 */
public final class VersatileMockService {

    private static final String SCENARIO = "hotel-booking";
    private static final String STATE_FIRST_DONE = "first-done";

    private final WireMockServer server;
    private boolean started;

    public VersatileMockService() {
        this.server = new WireMockServer(0);
    }

    public void start() {
        if (started) return;
        server.start();
        WireMock.configureFor(server.port());
        started = true;
    }

    public int port() { return server.port(); }

    public String baseUrl() { return "http://localhost:" + port(); }

    public void stop() {
        if (!started) return;
        server.stop();
        started = false;
    }

    // ── Flows ──

    public void stubBookingFlow() {
        WireMock.reset();
        firstResponse(VersatileMockSse.hotelsInfo());
        secondResponse(VersatileMockSse.hotelBookSuccess());
    }

    public void stubInterruptFlow() {
        WireMock.reset();
        firstResponse(VersatileMockSse.hotelsInfoNoEnd());
        secondResponse(VersatileMockSse.hotelBookSuccess());
    }

    private static void firstResponse(String body) {
        WireMock.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/.*/agents/.*/conversations/.*"))
                .inScenario(SCENARIO).whenScenarioStateIs(Scenario.STARTED)
                .withQueryParam("type", WireMock.equalTo("controller"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/event-stream; charset=utf-8")
                        .withBody(body))
                .willSetStateTo(STATE_FIRST_DONE));
    }

    private static void secondResponse(String body) {
        WireMock.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/.*/agents/.*/conversations/.*"))
                .inScenario(SCENARIO).whenScenarioStateIs(STATE_FIRST_DONE)
                .withQueryParam("type", WireMock.equalTo("controller"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/event-stream; charset=utf-8")
                        .withBody(body)));
    }
}
