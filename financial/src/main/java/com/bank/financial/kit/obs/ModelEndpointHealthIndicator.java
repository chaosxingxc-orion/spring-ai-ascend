package com.bank.financial.kit.obs;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Readiness signal: is the LLM endpoint reachable? Contributes to
 * {@code /actuator/health} (and the readiness group) so an orchestrator won't
 * route traffic to an instance that can't reach its model. A lightweight TCP
 * connect (no API call, no key, no cost), short timeout.
 */
@Component("modelEndpoint")
public class ModelEndpointHealthIndicator implements HealthIndicator {

    private final String apiBase;

    public ModelEndpointHealthIndicator(
            @Value("${financial.llm.api-base:${BANK_LLM_API_BASE:http://localhost:4000/v1}}") String apiBase) {
        this.apiBase = apiBase;
    }

    @Override
    public Health health() {
        try {
            URI u = URI.create(apiBase);
            String host = u.getHost();
            int port = u.getPort() > 0 ? u.getPort() : ("https".equalsIgnoreCase(u.getScheme()) ? 443 : 80);
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 1500);
            }
            return Health.up().withDetail("endpoint", host + ":" + port).build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("endpoint", apiBase)
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
