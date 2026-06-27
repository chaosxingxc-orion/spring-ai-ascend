package com.huawei.ascend.examples.workmate.share;

import com.huawei.ascend.examples.workmate.artifact.ArtifactService;
import com.huawei.ascend.examples.workmate.audit.RunEventPayloadRedactor;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.myfiles.MyFilesService;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.share.dto.ShareArtifactSummary;
import com.huawei.ascend.examples.workmate.share.dto.ShareCreateRequest;
import com.huawei.ascend.examples.workmate.share.dto.ShareLinkResponse;
import com.huawei.ascend.examples.workmate.share.dto.ShareReplayResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ShareService {

    private final ShareStore shareStore;
    private final SessionService sessionService;
    private final SessionPersistenceService sessionPersistenceService;
    private final ArtifactService artifactService;
    private final MyFilesService myFilesService;
    private final RunEventPayloadRedactor redactor;

    public ShareService(
            ShareStore shareStore,
            SessionService sessionService,
            SessionPersistenceService sessionPersistenceService,
            ArtifactService artifactService,
            MyFilesService myFilesService,
            RunEventPayloadRedactor redactor) {
        this.shareStore = shareStore;
        this.sessionService = sessionService;
        this.sessionPersistenceService = sessionPersistenceService;
        this.artifactService = artifactService;
        this.myFilesService = myFilesService;
        this.redactor = redactor;
    }

    public ShareLinkResponse createShare(UUID sessionId) {
        return createShare(sessionId, new ShareCreateRequest(null, null));
    }

    public ShareLinkResponse createShare(UUID sessionId, ShareCreateRequest request) {
        WorkmateSession session = sessionService.requireSession(sessionId);
        ShareCreateRequest normalized = request == null ? new ShareCreateRequest(null, null) : request;
        ShareStore.ShareLink link = shareStore.create(
                sessionId,
                session.getTitle(),
                normalized.normalizedScope(),
                normalized.normalizedExpiresInHours());
        return new ShareLinkResponse(
                link.token(),
                "/share/" + link.token(),
                link.scope(),
                link.expiresAt());
    }

    public ShareReplayResponse getReplay(String token) {
        ShareStore.ShareLink link =
                shareStore.findByToken(token).orElseThrow(() -> new ShareNotFoundException(token));
        UUID sessionId = link.sessionId();
        sessionService.requireSession(sessionId);
        WorkmateSession session = sessionService.requireSession(sessionId);

        boolean includeMessages = !"artifacts".equals(link.scope());
        boolean includeArtifacts = !"messages".equals(link.scope());

        List<Map<String, Object>> messages = includeMessages
                ? sessionPersistenceService.listMessages(sessionId).stream()
                        .map(this::redactMessage)
                        .toList()
                : List.of();
        List<Map<String, Object>> events = includeMessages
                ? sessionPersistenceService.listEventLog(sessionId).stream()
                        .map(this::redactEvent)
                        .toList()
                : List.of();
        List<ShareArtifactSummary> artifacts = includeArtifacts
                ? artifactService.listArtifacts(sessionId).stream()
                        .map(ShareArtifactSummary::from)
                        .toList()
                : List.of();

        return new ShareReplayResponse(
                link.token(),
                sessionId,
                session.getTitle(),
                session.getExpertId(),
                link.createdAt(),
                link.scope(),
                link.expiresAt(),
                messages,
                events,
                artifacts);
    }

    public ResponseEntity<Resource> downloadArtifact(String token, String path) {
        ShareStore.ShareLink link =
                shareStore.findByToken(token).orElseThrow(() -> new ShareNotFoundException(token));
        if ("messages".equals(link.scope())) {
            throw new IllegalArgumentException("This share link does not include artifact downloads");
        }
        return myFilesService.download(link.sessionId(), path);
    }

    private Map<String, Object> redactMessage(Map<String, Object> message) {
        Map<String, Object> copy = new LinkedHashMap<>(message);
        Object kind = copy.get("kind");
        String eventName = "user".equals(kind) ? "message.user" : "message.delta";
        Map<String, Object> redacted = redactor.redact(eventName, copy);
        Map<String, Object> out = new LinkedHashMap<>(redacted);
        for (Map.Entry<String, Object> entry : message.entrySet()) {
            if ("seq".equals(entry.getKey()) || "kind".equals(entry.getKey()) || "id".equals(entry.getKey())) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    private Map<String, Object> redactEvent(Map<String, Object> event) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seq", event.get("seq"));
        out.put("name", event.get("name"));
        Object data = event.get("data");
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> payload = new LinkedHashMap<>();
            map.forEach((key, value) -> payload.put(String.valueOf(key), value));
            out.put("data", redactor.redact(String.valueOf(event.get("name")), payload));
        } else {
            out.put("data", Map.of());
        }
        return out;
    }
}
