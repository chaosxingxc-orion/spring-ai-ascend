package com.huawei.ascend.examples.workmate.office.dto;

public record StudioCoordinationWriteRequest(
        String pattern,
        StudioTerminationWriteRequest termination,
        String acceptanceCriteria) {

    public record StudioTerminationWriteRequest(
            Integer maxIterations, Long timeBudgetMs, String convergence, String decider) {}
}
