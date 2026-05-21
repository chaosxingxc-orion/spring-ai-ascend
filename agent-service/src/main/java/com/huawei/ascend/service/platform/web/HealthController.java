package com.huawei.ascend.service.platform.web;

import com.huawei.ascend.service.platform.persistence.HealthCheckRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * /v1/health -- returns 200 with status + sha + db round-trip evidence.
 *
 * <p>Per W0 acceptance gate: this endpoint must reach Postgres on every call,
 * proving the JDBC + Flyway-applied schema work end-to-end. The body shape is
 * stable for the OpenAPI snapshot test.</p>
 */
@RestController
@RequestMapping("/v1")
public class HealthController {

    private final HealthCheckRepository repo;
    private final String sha;

    public HealthController(HealthCheckRepository repo) {
        this.repo = repo;
        this.sha = System.getenv().getOrDefault("APP_SHA", "dev");
    }

    @GetMapping("/health")
    public HealthResponse health() {
        long ping = repo.pingDb();
        return new HealthResponse("UP", sha, ping, Instant.now().toString());
    }
}
