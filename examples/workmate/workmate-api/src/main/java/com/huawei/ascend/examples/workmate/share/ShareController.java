package com.huawei.ascend.examples.workmate.share;

import com.huawei.ascend.examples.workmate.share.dto.FileShareRequest;
import com.huawei.ascend.examples.workmate.share.dto.ShareCreateRequest;
import com.huawei.ascend.examples.workmate.share.dto.ShareLinkResponse;
import com.huawei.ascend.examples.workmate.share.dto.ShareReplayResponse;
import com.huawei.ascend.examples.workmate.share.dto.TempDownloadLinkResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ShareController {

    private final ShareService shareService;
    private final TempDownloadService tempDownloadService;

    public ShareController(ShareService shareService, TempDownloadService tempDownloadService) {
        this.shareService = shareService;
        this.tempDownloadService = tempDownloadService;
    }

    @PostMapping("/sessions/{id}/share")
    public ShareLinkResponse createShare(
            @PathVariable UUID id,
            @RequestBody(required = false) ShareCreateRequest request) {
        return shareService.createShare(id, request);
    }

    @GetMapping("/share/{token}")
    public ShareReplayResponse getShare(@PathVariable String token) {
        return shareService.getReplay(token);
    }

    @GetMapping("/share/{token}/download")
    public ResponseEntity<Resource> downloadShareArtifact(
            @PathVariable String token, @RequestParam String path) {
        return shareService.downloadArtifact(token, path);
    }

    @PostMapping("/files/share")
    public TempDownloadLinkResponse createFileShare(@Valid @RequestBody FileShareRequest request) {
        return tempDownloadService.create(request);
    }

    @GetMapping("/share/files/{token}")
    public ResponseEntity<Resource> downloadFileShare(@PathVariable String token) {
        return tempDownloadService.download(token);
    }
}
