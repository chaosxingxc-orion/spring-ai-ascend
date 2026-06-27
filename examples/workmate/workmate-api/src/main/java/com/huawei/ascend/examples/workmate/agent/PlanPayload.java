package com.huawei.ascend.examples.workmate.agent;

import java.util.List;

public record PlanPayload(String planId, String title, List<PlanStep> steps) {

    public record PlanStep(String id, String title, String status) {}
}
