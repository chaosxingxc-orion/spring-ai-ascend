/**
 * C3 forwarding runtime substrate — broker-agnostic, tenant-scoped, durable
 * outbox / inbox domain model, ports and state machine.
 *
 * <p>{@code com.huawei.ascend.bus.forwarding.spi} holds the pure-Java contract
 * surface (envelope, ports, value types); {@code com.huawei.ascend.bus.forwarding.runtime}
 * holds the state-transition engine. The real persistent implementation (JDBC /
 * migration / polling / lease) is Stage 8.
 *
 * <p>Authority: {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md};
 * {@code ICD-Agent-Bus-Forwarding-Runtime}.
 */
package com.huawei.ascend.bus.forwarding;
