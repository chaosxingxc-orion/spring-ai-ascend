package com.huawei.ascend.runtime.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Access-layer settings for the A2A ingress.
 *
 * <ul>
 *   <li>{@code defaultTenantId} — the tenant attributed to requests that carry no
 *       {@code X-Tenant-Id} header; single-tenant deployments set it once instead of
 *       sending the header.</li>
 *   <li>{@code defaultAgentId} — pins the agent id the discovery endpoint serves
 *       and warns when it matches no registered handler (the runtime hosts exactly
 *       one agent); blank falls back to the hosted handler's id.</li>
 *   <li>{@code publicBaseUrl} — externally reachable base URL (scheme + host +
 *       optional path prefix) used when publishing absolute URLs in the agent card;
 *       blank derives the base from the current HTTP request instead.</li>
 * </ul>
 */
@ConfigurationProperties("agent-runtime.access.a2a")
public class RuntimeAccessProperties {

    private String defaultTenantId = "default";

    private String defaultAgentId;

    private String publicBaseUrl;

    public String getDefaultTenantId() { return defaultTenantId; }

    public void setDefaultTenantId(String defaultTenantId) { this.defaultTenantId = defaultTenantId; }

    public String getDefaultAgentId() { return defaultAgentId; }

    public void setDefaultAgentId(String defaultAgentId) { this.defaultAgentId = defaultAgentId; }

    public String getPublicBaseUrl() { return publicBaseUrl; }

    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
}
