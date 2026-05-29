package com.huawei.ascend.service.runtime.orchestration.transport;

import java.io.Serializable;

/**
 * Mock A2A federation envelope: wraps the serialized {@link com.huawei.ascend.bus.spi.engine.ExecuteRequest}
 * bytes with fake federation framing (from / to / agentCard) to mirror an A2A message shape
 * without a real federation broker.
 */
public record A2aEnvelopeMock(String from, String to, String agentCard, byte[] payload) implements Serializable {
}
