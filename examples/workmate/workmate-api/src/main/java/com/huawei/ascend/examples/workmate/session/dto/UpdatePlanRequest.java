package com.huawei.ascend.examples.workmate.session.dto;

import java.util.List;
import java.util.Map;

public record UpdatePlanRequest(String title, List<Map<String, Object>> steps) {}
