package com.huawei.ascend.examples.workmate.share;

import com.huawei.ascend.examples.workmate.artifact.ArtifactService;
import com.huawei.ascend.examples.workmate.myfiles.MyFilesService;
import com.huawei.ascend.examples.workmate.share.dto.FileShareRequest;
import com.huawei.ascend.examples.workmate.share.dto.TempDownloadLinkResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class TempDownloadService {

    private final TempDownloadStore store;
    private final MyFilesService myFilesService;

    public TempDownloadService(TempDownloadStore store, MyFilesService myFilesService) {
        this.store = store;
        this.myFilesService = myFilesService;
    }

    public TempDownloadLinkResponse create(FileShareRequest request) {
        Instant expiresAt = Instant.now().plus(request.normalizedExpiresInHours(), ChronoUnit.HOURS);
        TempDownloadStore.TempLink link =
                store.create(request.sessionId(), request.path(), expiresAt);
        return new TempDownloadLinkResponse(link.token(), "/api/v1/share/files/" + link.token(), expiresAt);
    }

    public ResponseEntity<Resource> download(String token) {
        TempDownloadStore.TempLink link =
                store.findValid(token).orElseThrow(() -> new ShareNotFoundException(token));
        return myFilesService.download(link.sessionId(), link.path());
    }
}
