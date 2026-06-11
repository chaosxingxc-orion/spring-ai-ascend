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

    public String getDefaultTenantId() { return defaultTenantId; }

    public void setDefaultTenantId(String defaultTenantId) { this.defaultTenantId = defaultTenantId; }
}
