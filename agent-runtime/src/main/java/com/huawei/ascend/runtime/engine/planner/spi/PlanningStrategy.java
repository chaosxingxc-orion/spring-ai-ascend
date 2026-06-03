package com.huawei.ascend.runtime.engine.planner.spi;

/**
 * Planner algorithm hint.
 *
 * <p>Authority: ADR-0126. CUSTOM allows planner-defined extensions
 * without an ADR amendment.
 */
public enum PlanningStrategy {
    SEQUENTIAL,
    DAG,
    REACT,
    TREE_OF_THOUGHT,
    CUSTOM
}
