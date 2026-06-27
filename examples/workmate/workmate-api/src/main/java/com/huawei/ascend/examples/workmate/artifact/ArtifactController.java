package com.huawei.ascend.examples.workmate.artifact;

import com.huawei.ascend.examples.workmate.artifact.dto.ArtifactResponse;
import com.huawei.ascend.examples.workmate.artifact.dto.FileContentResponse;
import com.huawei.ascend.examples.workmate.filehistory.FileHistoryService;
import com.huawei.ascend.examples.workmate.filehistory.dto.FileChangeResponse;
import com.huawei.ascend.examples.workmate.filehistory.dto.FileDiffResponse;
import com.huawei.ascend.examples.workmate.artifact.dto.UserAttachmentResponse;
import com.huawei.ascend.examples.workmate.artifact.dto.WorkspaceEntryResponse;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
public class ArtifactController {

    private final ArtifactService artifactService;
    private final FileHistoryService fileHistoryService;

    public ArtifactController(ArtifactService artifactService, FileHistoryService fileHistoryService) {
        this.artifactService = artifactService;
        this.fileHistoryService = fileHistoryService;
    }

    @GetMapping("/artifacts")
    public List<ArtifactResponse> listArtifacts(@PathVariable UUID sessionId) {
        return artifactService.listArtifacts(sessionId);
    }

    @PostMapping("/attachments")
    public UserAttachmentResponse uploadAttachment(
            @PathVariable UUID sessionId,
            @RequestParam("file") MultipartFile file) {
        return artifactService.uploadUserAttachment(sessionId, file);
    }

    @GetMapping("/files")
    public FileContentResponse readFile(
            @PathVariable UUID sessionId,
            @RequestParam @NotBlank String path) {
        return artifactService.readFile(sessionId, path);
    }

    /** Serves workspace web assets for iframe preview (HTML/CSS/JS/images). */
    @GetMapping("/preview/{*relativePath}")
    public ResponseEntity<Resource> previewFile(
            @PathVariable UUID sessionId,
            @PathVariable String relativePath) {
        String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        return artifactService.servePreviewFile(sessionId, path);
    }

    @GetMapping("/workspace/entries")
    public List<WorkspaceEntryResponse> listWorkspaceEntries(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "") String path) {
        return artifactService.listDirectory(sessionId, path);
    }

    @GetMapping("/workspace/search")
    public List<ArtifactResponse> searchWorkspaceFiles(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "20") int limit) {
        return artifactService.searchFiles(sessionId, q, limit);
    }

    @GetMapping("/changes")
    public List<FileChangeResponse> listChanges(@PathVariable UUID sessionId) {
        return fileHistoryService.listSessionChanges(sessionId);
    }

    @GetMapping("/changes/diff")
    public FileDiffResponse readChangeDiff(
            @PathVariable UUID sessionId,
            @RequestParam @NotBlank String path) {
        return fileHistoryService.readDiff(sessionId, path);
    }
}
