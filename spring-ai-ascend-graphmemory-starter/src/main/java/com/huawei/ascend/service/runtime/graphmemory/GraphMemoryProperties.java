package com.huawei.ascend.service.runtime.graphmemory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the GraphMemory sidecar adapter starter.
 *
 * <p>Posture matrix:
 * <ul>
 *   <li>{@code enabled=false} (default) — auto-configuration contributes no bean.</li>
 *   <li>{@code enabled=true} — auto-configuration still contributes no
 *       bean (the Graphiti REST adapter lands). The
 *       remaining fields below are RESERVED for W1 wiring and are not
 *       consumed; the orphan-config Rule 3 exemption is documented
 *       inline via {@link Deprecated} on each unused field.</li>
 *   <li>{@code enabled=true}+ — the adapter bean is contributed and the
 *       reserved fields become live.</li>
 * </ul>
 *
 * <p>Authority: ADR-0034 (memory-and-knowledge taxonomy); v2.0.0-rc3
 * cross-constraint audit α-8 / P1-7 (orphan-config honesty).
 */
@ConfigurationProperties("springai.ascend.graphmemory")
public class GraphMemoryProperties {

    private boolean enabled = false;

    /**
     * @deprecated reserved for the W1 Graphiti REST adapter (see ADR-0034).
     *     NOT consumed — the auto-config contributes no bean even when
     *     {@code enabled=true}. Setting this property has no effect.
     *     Documented as orphan-config exemption in v2.0.0-rc3 per
     *     cross-constraint audit α-8 / P1-7.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    private String baseUrl = "http://localhost:8001";

    /**
     * @deprecated reserved for the W1 Graphiti REST adapter (see ADR-0034).
     *     NOT consumed. Same orphan-config exemption note as
     *     {@link #baseUrl}.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    private String apiKey = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** @deprecated reserved for W1; ignored. */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public String getBaseUrl() { return baseUrl; }

    /** @deprecated reserved for W1; ignored. */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    /** @deprecated reserved for W1; ignored. */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public String getApiKey() { return apiKey; }

    /** @deprecated reserved for W1; ignored. */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}
