package com.huawei.ascend.examples.workmate.filehistory;

import com.huawei.ascend.examples.workmate.filehistory.dto.FileVersionResponse;
import com.huawei.ascend.examples.workmate.filehistory.dto.RevertFileRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/files")
public class FileHistoryController {

    private final FileHistoryService fileHistoryService;

    public FileHistoryController(FileHistoryService fileHistoryService) {
        this.fileHistoryService = fileHistoryService;
    }

    @GetMapping("/history")
    public List<FileVersionResponse> listHistory(
            @PathVariable UUID sessionId, @RequestParam String path) {
        return fileHistoryService.listVersions(sessionId, path);
    }

    @PostMapping("/revert")
    public FileVersionResponse revert(
            @PathVariable UUID sessionId, @Valid @RequestBody RevertFileRequest request) {
        return fileHistoryService.revert(sessionId, request.path(), request.versionId());
    }
}
