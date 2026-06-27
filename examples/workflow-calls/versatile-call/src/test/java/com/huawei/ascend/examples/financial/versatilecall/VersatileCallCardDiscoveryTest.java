package com.huawei.ascend.examples.financial.versatilecall;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Smoke test: the versatile-call runtime boots and serves a discoverable
 * A2A AgentCard. No LLM and no external versatile service are required —
 * the card endpoint and Spring context come up without contacting either.
 */
class VersatileCallCardDiscoveryTest {

    @Test
    void versatileCallCardIsDiscoverable() throws Exception {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(VersatileCallApplication.class)
                .run("--server.port=0")) {
            Integer port = ctx.getEnvironment().getProperty("local.server.port", Integer.class);
            assertThat(port).isNotNull();

            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create(
                            "http://localhost:" + port + "/.well-known/agent-card.json"))
                            .timeout(Duration.ofSeconds(10))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.body()).contains("\"versatile-call\"");
        }
    }
}
