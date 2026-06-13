package com.huawei.ascend.runtime.engine.spi;

import java.util.Map;

/**
 * Protocol-neutral description of one callable remote agent, exposed to
 * framework adapters as a tool: stable {@code remoteAgentId}, the derived
 * {@code toolName}, a human-readable description, and a JSON-schema-shaped
 * input contract. Produced by the A2A card cache from resolved agent cards;
 * lives in the neutral SPI so adapters can consume remote tools without
 * depending on the protocol bridge.
 */
public record RemoteAgentToolSpec(
        String remoteAgentId,
        String toolName,
        String description,
        Map<String, Object> inputSchema) {
}
