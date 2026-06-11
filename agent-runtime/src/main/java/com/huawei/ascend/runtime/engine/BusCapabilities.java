package com.huawei.ascend.runtime.engine;

import com.huawei.ascend.bus.knowledge.KnowledgeRegistry;
import com.huawei.ascend.bus.memory.BusinessFactPublisher;
import com.huawei.ascend.bus.memory.SessionMemoryStore;
import com.huawei.ascend.bus.messaging.AgentMessageBus;

/**
 * Carrier for the agent-bus capability surfaces the dispatcher hands to
 * handlers through {@link AgentExecutionContext} (Authority: ADR-0163).
 *
 * <p>One record instead of one constructor parameter per capability keeps the
 * context's construction surface flat: a future capability grows this record,
 * not every {@code AgentExecutionContext} construction site. Every component
 * is nullable — a hosting application that does not wire a capability simply
 * leaves it absent, and the matching context accessor yields an empty
 * {@link java.util.Optional}.
 */
public record BusCapabilities(
        SessionMemoryStore sessionMemory,
        KnowledgeRegistry knowledge,
        AgentMessageBus messageBus,
        BusinessFactPublisher businessFacts) {
}
