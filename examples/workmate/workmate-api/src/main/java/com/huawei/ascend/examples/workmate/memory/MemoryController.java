package com.huawei.ascend.examples.workmate.memory;

import com.huawei.ascend.examples.workmate.memory.dto.MemoryCaptureResponse;
import com.huawei.ascend.examples.workmate.memory.dto.MemoryResponse;
import com.huawei.ascend.examples.workmate.memory.dto.MemorySettingsRequest;
import com.huawei.ascend.examples.workmate.memory.MemorySettingsStore.MemorySettings;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping("/api/v1/memory")
    public MemoryResponse getMemory() {
        return MemoryResponse.from(memoryService.status());
    }

    @PatchMapping("/api/v1/memory/settings")
    public MemoryResponse updateSettings(@RequestBody MemorySettingsRequest request) {
        MemorySettings settings = memoryService.updateSettings(request.toSettings());
        MemoryService.MemoryStatus status = memoryService.status();
        return new MemoryResponse(
                settings.enabled(),
                settings.autoCapture(),
                status.ownerId(),
                status.content(),
                status.injectPreview(),
                status.hasContent(),
                status.charCount());
    }

    @DeleteMapping("/api/v1/memory")
    public MemoryResponse clearMemory() {
        memoryService.clearMemory();
        return MemoryResponse.from(memoryService.status());
    }

    @PostMapping("/api/v1/sessions/{sessionId}/remember")
    public MemoryCaptureResponse remember(@PathVariable UUID sessionId) {
        return MemoryCaptureResponse.from(memoryService.captureFromSession(sessionId, true));
    }
}
