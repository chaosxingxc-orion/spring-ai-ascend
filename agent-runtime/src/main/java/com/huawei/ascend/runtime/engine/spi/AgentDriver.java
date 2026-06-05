package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.common.InvocationRequest;

/**
 * The single framework-neutral runtime SPI: a per-framework "driver" that runs one agent and
 * surfaces its output. A concrete driver (openjiuwen first; agentscope / spring-ai-alibaba /
 * langchain4j / langgraph4j / dify later) keeps ALL framework-specific concerns — tools,
 * memory, skills, MCP, middleware — internal to itself. The runtime stays framework-neutral
 * and is never customised for any one framework.
 *
 * <p>Naming: "driver" is the industry metaphor for adapting an external system (cf. JDBC);
 * lifecycle follows Spring {@code Lifecycle} ({@code start}/{@code stop}/{@code isRunning});
 * {@code invoke} + {@code stream} are the verbs attested across the target frameworks. These
 * are deliberately distinct from any single framework's exact type/method names.
 *
 * <p>Lifecycle: {@link #start()} → {@link #invoke(InvocationRequest)} (repeatable) →
 * {@link #stop()}. {@code invoke} returns the framework's native stream, which the matching
 * {@link #outputConverter()} turns into the neutral {@code RunEvent} stream.
 */
public interface AgentDriver {

    String name();

    String description();

    /** Framework discriminator, e.g. {@code "openjiuwen"}, {@code "agentscope"}, {@code "dify"}. */
    String frameworkId();

    void start();

    void stop();

    boolean isRunning();

    /**
     * Run the agent; returns the framework's <em>native</em> stream as an opaque object
     * (openjiuwen {@code Iterator}, reactive {@code Publisher}, langchain4j {@code TokenStream},
     * langgraph4j {@code AsyncGenerator}, Dify SSE handle, ...). Only this driver's matching
     * {@link #outputConverter()} understands it; the runtime never inspects the native type.
     */
    Object invoke(InvocationRequest request);

    /** Bridges this driver's native stream into the neutral {@code RunEvent} stream. */
    OutputConverter outputConverter();
}
