package com.huawei.ascend.examples.workmate.filehistory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.audit.AuditLedgerService;
import com.huawei.ascend.examples.workmate.artifact.ArtifactService;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class FileHistoryServiceTest {

    @TempDir
    Path tempDir;

    private FileHistoryService fileHistoryService;
    private UUID sessionId;
    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        sessionId = UUID.randomUUID();
        workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);
        WorkmateSession session = new WorkmateSession(sessionId, "test", workspace.toString(), SessionStatus.CREATED);
        SessionService sessionService = Mockito.mock(SessionService.class);
        Mockito.when(sessionService.requireSession(sessionId)).thenReturn(session);
        fileHistoryService = new FileHistoryService(
                sessionService, Mockito.mock(AuditLedgerService.class), new ObjectMapper());
    }

    @Test
    void recordsModifiedVersionsAndReverts() throws Exception {
        Path file = workspace.resolve("a.md");
        Files.writeString(file, "v1");

        fileHistoryService.recordBeforeWrite(sessionId, workspace, "a.md", "run-1");
        Files.writeString(file, "v2");

        fileHistoryService.recordBeforeWrite(sessionId, workspace, "a.md", "run-2");
        Files.writeString(file, "v3");

        assertThat(fileHistoryService.listVersions(sessionId, "a.md")).hasSize(2);
        String versionId = fileHistoryService.listVersions(sessionId, "a.md").getFirst().versionId();

        fileHistoryService.revert(sessionId, "a.md", versionId);

        assertThat(Files.readString(file)).isEqualTo("v1");
    }

    @Test
    void historyHiddenFromArtifacts() throws Exception {
        Path file = workspace.resolve("hello.md");
        Files.writeString(file, "hello");
        fileHistoryService.recordBeforeWrite(sessionId, workspace, "hello.md", "run-1");
        Files.writeString(file, "hello2");

        SessionService sessionService = Mockito.mock(SessionService.class);
        WorkmateSession session = new WorkmateSession(sessionId, "test", workspace.toString(), SessionStatus.CREATED);
        Mockito.when(sessionService.requireSession(sessionId)).thenReturn(session);
        ArtifactService artifactService = new ArtifactService(sessionService);

        assertThat(artifactService.listArtifacts(sessionId))
                .extracting("path")
                .containsExactly("hello.md");
        assertThat(Files.exists(workspace.resolve(".file-history"))).isTrue();
    }

    @Test
    void listsSessionChangesAndDiff() throws Exception {
        Path file = workspace.resolve("a.md");
        Files.writeString(file, "v1");

        fileHistoryService.recordBeforeWrite(sessionId, workspace, "a.md", "run-1");
        Files.writeString(file, "v2");

        assertThat(fileHistoryService.listSessionChanges(sessionId)).hasSize(1);
        assertThat(fileHistoryService.listSessionChanges(sessionId).getFirst().op()).isEqualTo("modified");

        var diff = fileHistoryService.readDiff(sessionId, "a.md");
        assertThat(diff.original()).isEqualTo("v1");
        assertThat(diff.modified()).isEqualTo("v2");
    }
}
