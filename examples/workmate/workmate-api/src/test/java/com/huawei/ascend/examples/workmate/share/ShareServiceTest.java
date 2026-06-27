package com.huawei.ascend.examples.workmate.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.artifact.ArtifactService;
import com.huawei.ascend.examples.workmate.audit.RunEventPayloadRedactor;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.config.WorkmateAuditProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.myfiles.MyFilesService;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.share.dto.ShareCreateRequest;
import com.huawei.ascend.examples.workmate.share.dto.ShareLinkResponse;
import com.huawei.ascend.examples.workmate.share.dto.ShareReplayResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShareServiceTest {

    @TempDir
    Path dataDir;

    private ShareService shareService;
    private ShareStore shareStore;
    private SessionService sessionService;
    private SessionPersistenceService sessionPersistenceService;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        shareStore = new ShareStore(new WorkmateDataProperties(dataDir.toString()), new ObjectMapper());
        sessionService = mock(SessionService.class);
        sessionPersistenceService = mock(SessionPersistenceService.class);
        ArtifactService artifactService = mock(ArtifactService.class);
        RunEventPayloadRedactor redactor =
                new RunEventPayloadRedactor(new ObjectMapper(), new WorkmateAuditProperties(200, 16384, true));

        shareService = new ShareService(
                shareStore,
                sessionService,
                sessionPersistenceService,
                artifactService,
                mock(MyFilesService.class),
                redactor);

        WorkmateSession session = new WorkmateSession(
                sessionId, "Demo task", "/tmp/ws", SessionStatus.COMPLETED, "prd-writer");
        when(sessionService.requireSession(sessionId)).thenReturn(session);
        when(sessionPersistenceService.listMessages(sessionId)).thenReturn(List.of(
                Map.of("kind", "user", "seq", 1, "id", "u1", "text", "hello@secret.com")));
        when(sessionPersistenceService.listEventLog(sessionId)).thenReturn(List.of(
                Map.of("seq", 1, "name", "message.delta", "data", Map.of("text", "Hi there"))));
        when(artifactService.listArtifacts(sessionId)).thenReturn(List.of());
    }

    @Test
    void createShareReturnsTokenAndPath() {
        ShareLinkResponse response = shareService.createShare(sessionId);

        assertThat(response.token()).isNotBlank();
        assertThat(response.sharePath()).isEqualTo("/share/" + response.token());
        assertThat(response.scope()).isEqualTo("full");
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    void createShareHonorsScopeAndExpiry() {
        ShareLinkResponse response = shareService.createShare(
                sessionId, new ShareCreateRequest("messages", 24));

        assertThat(response.scope()).isEqualTo("messages");
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    void getReplayRedactsPiiAndReturnsMessages() {
        ShareLinkResponse link = shareService.createShare(sessionId);

        ShareReplayResponse replay = shareService.getReplay(link.token());

        assertThat(replay.title()).isEqualTo("Demo task");
        assertThat(replay.expertId()).isEqualTo("prd-writer");
        assertThat(replay.scope()).isEqualTo("full");
        assertThat(replay.messages()).hasSize(1);
        assertThat(String.valueOf(replay.messages().get(0).get("text"))).contains("[REDACTED_EMAIL]");
        assertThat(replay.events()).hasSize(1);
    }

    @Test
    void getReplayThrowsWhenTokenMissing() {
        assertThatThrownBy(() -> shareService.getReplay("missing-token"))
                .isInstanceOf(ShareNotFoundException.class);
    }
}
