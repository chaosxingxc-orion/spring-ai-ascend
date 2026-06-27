package com.huawei.ascend.examples.workmate.myfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.artifact.ArtifactService;
import com.huawei.ascend.examples.workmate.audit.AuditLedgerService;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.myfiles.dto.MyFileResponse;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.session.WorkmateSessionRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class MyFilesServiceTest {

    @TempDir
    Path tempDir;

    private MyFilesService myFilesService;
    private FavoriteStore favoriteStore;
    private UUID sessionA;
    private UUID sessionB;
    private Path workspaceA;
    private Path workspaceB;

    @BeforeEach
    void setUp() throws Exception {
        sessionA = UUID.randomUUID();
        sessionB = UUID.randomUUID();
        workspaceA = tempDir.resolve("a");
        workspaceB = tempDir.resolve("b");
        Files.createDirectories(workspaceA);
        Files.createDirectories(workspaceB);
        Files.writeString(workspaceA.resolve("alpha.md"), "alpha");
        Files.writeString(workspaceB.resolve("beta.txt"), "beta");

        WorkmateSession workmateA =
                new WorkmateSession(sessionA, "Task A", workspaceA.toString(), SessionStatus.CREATED);
        WorkmateSession workmateB =
                new WorkmateSession(sessionB, "Task B", workspaceB.toString(), SessionStatus.CREATED);

        WorkmateSessionRepository repository = Mockito.mock(WorkmateSessionRepository.class);
        Mockito.when(repository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(workmateA, workmateB));

        SessionService sessionService = Mockito.mock(SessionService.class);
        Mockito.when(sessionService.requireSession(sessionA)).thenReturn(workmateA);
        Mockito.when(sessionService.requireSession(sessionB)).thenReturn(workmateB);

        ArtifactService artifactService = new ArtifactService(sessionService);
        favoriteStore = new FavoriteStore(new WorkmateDataProperties(tempDir.resolve("data").toString()), new ObjectMapper());
        myFilesService = new MyFilesService(
                repository,
                sessionService,
                artifactService,
                favoriteStore,
                Mockito.mock(AuditLedgerService.class));
    }

    @Test
    void aggregatesFilesAcrossSessions() {
        List<MyFileResponse> files = myFilesService.list("", "name", "asc", false);
        assertThat(files).hasSize(2);
        assertThat(files).extracting(MyFileResponse::name).containsExactly("alpha.md", "beta.txt");
        assertThat(files).extracting(MyFileResponse::sessionTitle).containsExactly("Task A", "Task B");
    }

    @Test
    void renameMoveDeleteAndFavoritePersist() throws Exception {
        MyFileResponse renamed = myFilesService.rename(sessionA, "alpha.md", "renamed.md");
        assertThat(renamed.path()).isEqualTo("renamed.md");
        assertThat(Files.exists(workspaceA.resolve("renamed.md"))).isTrue();

        myFilesService.setFavorite(sessionA, "renamed.md", true);
        assertThat(favoriteStore.isFavorite(sessionA, "renamed.md")).isTrue();

        MyFileResponse moved = myFilesService.move(sessionA, "renamed.md", "docs/renamed.md");
        assertThat(moved.path()).isEqualTo("docs/renamed.md");
        assertThat(favoriteStore.isFavorite(sessionA, "docs/renamed.md")).isTrue();

        myFilesService.delete(sessionA, "docs/renamed.md");
        assertThat(Files.exists(workspaceA.resolve("docs/renamed.md"))).isFalse();
        assertThat(favoriteStore.isFavorite(sessionA, "docs/renamed.md")).isFalse();
    }

    @Test
    void rejectsPathEscapeOnMove() {
        assertThatThrownBy(() -> myFilesService.move(sessionB, "beta.txt", "../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filtersFavoritesOnly() {
        myFilesService.setFavorite(sessionA, "alpha.md", true);
        List<MyFileResponse> favorites = myFilesService.list("", "updatedAt", "desc", true);
        assertThat(favorites).hasSize(1);
        assertThat(favorites.getFirst().path()).isEqualTo("alpha.md");
    }
}
