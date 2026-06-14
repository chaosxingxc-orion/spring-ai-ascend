/**
 * Pure-Java contract surface for the C3 forwarding runtime: envelope, receipt,
 * value types (message id, route handle), status / failure-code enums, and the
 * outbox / inbox / dispatcher ports.
 *
 * <p>This package MUST stay pure Java — no Spring, no JDBC, no HTTP, no broker
 * client. SPI purity for this package is enforced by
 * {@code AgentBusForwardingSpiPurityTest}.
 *
 * <p>Authority: {@code ICD-Agent-Bus-Forwarding-Runtime};
 * {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §8}.
 */
package com.huawei.ascend.bus.forwarding.spi;
