package com.huawei.ascend.runtime.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Access-layer settings for the A2A ingress. {@code defaultTenantId} is the
 * tenant attributed to requests that carry no {@code X-Tenant-Id} header —
 * single-tenant deployments set it once instead of sending the header.
 */
@ConfigurationProperties("agent-runtime.access.a2a")
public class RuntimeAccessProperties {

    private String defaultTenantId = "default";

    private final Jwt jwt = new Jwt();

    public String getDefaultTenantId() { return defaultTenantId; }

    public void setDefaultTenantId(String defaultTenantId) { this.defaultTenantId = defaultTenantId; }

    public Jwt getJwt() { return jwt; }

    /**
     * Tenant authentication (cross-check model, ADR-0040). Disabled by
     * default until a deployment provisions the shared secret; when enabled,
     * every /a2a request must carry a verifying HS256 bearer token whose
     * tenant_id claim matches any X-Tenant-Id header present.
     */
    public static class Jwt {

        private boolean enabled;
        private String hmacSecret;
        private long clockSkewSeconds = 30;

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getHmacSecret() { return hmacSecret; }

        public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }

        public long getClockSkewSeconds() { return clockSkewSeconds; }

        public void setClockSkewSeconds(long clockSkewSeconds) { this.clockSkewSeconds = clockSkewSeconds; }
    }
}
