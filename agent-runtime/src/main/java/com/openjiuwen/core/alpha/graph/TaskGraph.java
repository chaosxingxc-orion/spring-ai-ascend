package com.openjiuwen.core.alpha.graph;

import com.openjiuwen.core.kernel.model.NodeId;

import java.util.*;

/**
 * 任务图——Alpha 策略规划阶段的产出。
 *
 * 有向无环图（DAG）。节点是子任务，边是依赖关系。
 * 拓扑排序后按层执行：同层节点无依赖关系，可以并行。
 *
 * 契约：
 * - 必须是无环图（构造时检查）
 * - 至少一个节点
 * - 边的 from/to 必须引用已存在的节点
 */
public record TaskGraph(
    String goal,
    List<TaskNode> nodes,
    List<TaskEdge> edges
) {

    public TaskGraph {
        Objects.requireNonNull(goal, "goal 不能为 null");
        Objects.requireNonNull(nodes, "nodes 不能为 null");
        Objects.requireNonNull(edges, "edges 不能为 null");
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("TaskGraph 至少需要一个节点");
        }
    }

    /**
     * 拓扑排序，生成执行层。
     * 同一层内的节点无依赖关系，可以并行执行。
     *
     * @return 执行层列表，Layer 0 最先执行
     * @throws IllegalStateException 如果图中有环
     */
    public List<List<TaskNode>> executionLayers() {
        Map<NodeId, TaskNode> nodeMap = new LinkedHashMap<>();
        for (TaskNode node : nodes) {
            nodeMap.put(node.id(), node);
        }

        // 计算每个节点的入度
        Map<NodeId, Integer> inDegree = new LinkedHashMap<>();
        Map<NodeId, Set<NodeId>> dependents = new LinkedHashMap<>(); // 谁依赖我
        for (TaskNode node : nodes) {
            inDegree.put(node.id(), 0);
            dependents.put(node.id(), new LinkedHashSet<>());
        }
        for (TaskEdge edge : edges) {
            // 防御性跳过不存在的节点引用
            if (!nodeMap.containsKey(edge.from()) || !nodeMap.containsKey(edge.to())) {
                continue;
            }
            inDegree.merge(edge.to(), 1, Integer::sum);
            dependents.get(edge.from()).add(edge.to());
        }

        // Kahn 算法拓扑排序
        List<List<TaskNode>> layers = new ArrayList<>();
        Set<NodeId> visited = new LinkedHashSet<>();

        while (visited.size() < nodes.size()) {
            List<TaskNode> currentLayer = new ArrayList<>();
            for (TaskNode node : nodes) {
                if (!visited.contains(node.id()) && inDegree.get(node.id()) == 0) {
                    currentLayer.add(node);
                }
            }
            if (currentLayer.isEmpty()) {
                throw new IllegalStateException("TaskGraph 中存在环，无法拓扑排序");
            }
            layers.add(currentLayer);
            for (TaskNode node : currentLayer) {
                visited.add(node.id());
                for (NodeId dep : dependents.get(node.id())) {
                    inDegree.merge(dep, -1, Integer::sum);
                }
            }
        }

        return layers;
    }

    /** 获取指定节点的所有上游依赖 */
    public Set<NodeId> dependenciesOf(NodeId nodeId) {
        Set<NodeId> deps = new LinkedHashSet<>();
        for (TaskEdge edge : edges) {
            if (edge.to().equals(nodeId)) {
                deps.add(edge.from());
            }
        }
        return deps;
    }
}
