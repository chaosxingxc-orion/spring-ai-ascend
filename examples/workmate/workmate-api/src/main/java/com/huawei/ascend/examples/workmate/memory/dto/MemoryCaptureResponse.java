package com.huawei.ascend.examples.workmate.memory.dto;

import com.huawei.ascend.examples.workmate.memory.MemoryService;
import java.util.List;

public record MemoryCaptureResponse(String status, List<String> entries, String reason) {
    public static MemoryCaptureResponse from(MemoryService.CaptureResult result) {
        return new MemoryCaptureResponse(result.status(), result.entries(), result.reason());
    }
}
