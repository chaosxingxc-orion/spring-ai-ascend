package com.huawei.ascend.runtime.engine.spi;

import java.util.List;
import java.util.Map;

/**
 * Port through which the runtime consumes the remote-agent topology: which remote A2A
 * runtimes exist, the tool surface they contribute, and the endpoint behind a remote
 * agent id. Discovery itself (agent-card fetching, caching, refresh, multi-instance
 * de-duplication) is a registration/discovery capability owned by the service plane;
 * the runtime only consumes the resolved view. Implementations must be thread-safe:
 * the refresh loop and the execute path read concurrently.
 */
public interface RemoteAgentCatalogPort {

    /** Tool specs for every remote agent that is currently resolved and callable. */
    List<RemoteAgentToolSpec> availableToolSpecs();

    /** The JSON-RPC endpoint behind a resolved remote agent id, or null when unknown. */
    String endpoint(String remoteAgentId);

    /** Re-attempts card resolution for entries that are not yet (or no longer) available. */
    void refreshPending();

    /** Configured URLs whose agent card has not resolved yet — for diagnostics. */
    List<String> pendingUrls();

    /** One remote agent projected as a runtime-managed tool. */
    record RemoteAgentToolSpec(
            String remoteAgentId,
            String toolName,
            String description,
            Map<String, Object> inputSchema) {
    }
}
