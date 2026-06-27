package com.huawei.ascend.examples.workmate.memory.dto;

import com.huawei.ascend.examples.workmate.memory.MemoryService;

public record MemoryResponse(
        boolean enabled,
        boolean autoCapture,
        String ownerId,
        String content,
        String injectPreview,
        boolean hasContent,
        int charCount) {

    public static MemoryResponse from(MemoryService.MemoryStatus status) {
        return new MemoryResponse(
                status.enabled(),
                status.autoCapture(),
                status.ownerId(),
                status.content(),
                status.injectPreview(),
                status.hasContent(),
                status.charCount());
    }
}
