package com.huawei.ascend.service.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the agent-service Spring edge. {@code enabled} switches the
 * whole auto-configuration off; {@code routeGrantSecret} signs HMAC route
 * grants and must be provisioned per deployment (the default only serves
 * local development); {@code publicBaseUrl}, when set, masks agent-card
 * endpoints behind the gateway-fronted route so served cards never leak
 * back-end runtime topology (empty keeps cards verbatim).
 */
@ConfigurationProperties("agent-service")
public class AgentServiceProperties {

    private boolean enabled = true;

    private String routeGrantSecret = "agent-service-local-route-grant-secret";

    private String publicBaseUrl = "";

    private final Access access = new Access();

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getRouteGrantSecret() { return routeGrantSecret; }

    public void setRouteGrantSecret(String routeGrantSecret) { this.routeGrantSecret = routeGrantSecret; }

    public String getPublicBaseUrl() { return publicBaseUrl; }

    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }

    public Access getAccess() { return access; }

    /** Access-layer settings for the service ingress. */
    public static class Access {

        private final Jwt jwt = new Jwt();

        public Jwt getJwt() { return jwt; }
    }

    /**
     * Tenant authentication at the service ingress (cross-check model,
     * ADR-0040; same shape as agent-runtime.access.a2a.jwt). Disabled by
     * default until a deployment provisions the shared secret; when enabled,
     * every registration/discovery/grant request must carry a verifying HS256
     * bearer token whose tenant_id claim matches any X-Tenant-Id header or
     * tenantId query parameter present.
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
