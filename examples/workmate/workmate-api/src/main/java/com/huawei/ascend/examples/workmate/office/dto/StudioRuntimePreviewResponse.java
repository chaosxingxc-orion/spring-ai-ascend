package com.huawei.ascend.examples.workmate.office.dto;

public record StudioRuntimePreviewResponse(
        String requestedRuntime,
        String resolvedRuntime,
        String coordinationPattern,
        boolean migratablePattern,
        boolean hasLead,
        String hint) {}
