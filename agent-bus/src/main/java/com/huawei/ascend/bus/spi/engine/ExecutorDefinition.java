package com.huawei.ascend.bus.spi.engine;

import java.util.Map;

/**
 * Sealed type hierarchy describing what an executor should run.
 * Pure Java — no Spring, no Micrometer imports per architecture §4.7.
 */
public sealed interface ExecutorDefinition
        permits ExecutorDefinition.GraphDefinition, ExecutorDefinition.AgentLoopDefinition {

    /**
     * A deterministic graph: named nodes connected by named edges.
     * nodes: nodeKey -> function that transforms payload to next payload
     * edges: sourceKey -> targetKey (absent key in map = terminal node)
     * startNode: the key of the first node to execute
     */
    record GraphDefinition(
            Map<String, NodeFunction> nodes,
            Map<String, String> edges,
            String startNode
    ) implements ExecutorDefinition {
        public GraphDefinition {
            if (nodes == null || nodes.isEmpty()) {
                throw new IllegalArgumentException("nodes must not be empty");
            }
            if (edges == null) {
                throw new IllegalArgumentException("edges must not be null");
            }
            if (startNode == null || startNode.isBlank()) {
                throw new IllegalArgumentException("startNode is required");
            }
            nodes = Map.copyOf(nodes);
            edges = Map.copyOf(edges);
            // Referential integrity at construction — a dangling key would otherwise
            // surface mid-run as a missing node on a live graph.
            if (!nodes.containsKey(startNode)) {
                throw new IllegalArgumentException("startNode is not a node key: " + startNode);
            }
            for (Map.Entry<String, String> edge : edges.entrySet()) {
                if (!nodes.containsKey(edge.getKey())) {
                    throw new IllegalArgumentException("edge source is not a node key: " + edge.getKey());
                }
                if (!nodes.containsKey(edge.getValue())) {
                    throw new IllegalArgumentException("edge target is not a node key: " + edge.getValue());
                }
            }
        }
    }

    /**
     * A ReAct-style agent loop: a Reasoner drives tool-action iterations until a terminal action.
     * maxIterations guards against runaway loops.
     * initialContext: arbitrary key-value metadata injected into the first reasoning step.
     */
    record AgentLoopDefinition(
            Reasoner reasoner,
            int maxIterations,
            Map<String, Object> initialContext
    ) implements ExecutorDefinition {
        public AgentLoopDefinition {
            if (reasoner == null) {
                throw new IllegalArgumentException("reasoner is required");
            }
            if (maxIterations <= 0) {
                throw new IllegalArgumentException("maxIterations must be > 0");
            }
            if (initialContext == null) {
                throw new IllegalArgumentException("initialContext must not be null");
            }
            initialContext = Map.copyOf(initialContext);
        }
    }

    /** Single-method abstraction for a graph node's compute function. */
    @FunctionalInterface
    interface NodeFunction {
        Object apply(RunContext ctx, Object payload) throws SuspendSignal;
    }

    /** Single-method abstraction for the ReAct reasoning step. */
    @FunctionalInterface
    interface Reasoner {
        ReasoningResult reason(RunContext ctx, Object payload, int iteration) throws SuspendSignal;
    }

    /** Outcome of one reasoning step: either a next-step action or a terminal result. */
    record ReasoningResult(boolean terminal, Object payload) {
        public static ReasoningResult next(Object payload) { return new ReasoningResult(false, payload); }
        public static ReasoningResult done(Object payload) { return new ReasoningResult(true, payload); }
    }
}
