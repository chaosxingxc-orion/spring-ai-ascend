package com.huawei.ascend.examples.workmate.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.filehistory.FileHistoryService;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class WorkspaceToolFactoryModeTest {

    @TempDir
    Path tempDir;

    private static FileHistoryService fileHistoryNoop() {
        return Mockito.mock(FileHistoryService.class);
    }

    @Test
    void craftRegistersReadWriteBash() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Path workspace = tempDir.resolve(sessionId.toString());
        Files.createDirectories(workspace);

        WorkspaceToolFactory factory = new WorkspaceToolFactory(new WorkspaceShellExecutor(), fileHistoryNoop());
        ToolExecutionContext ctx = new ToolExecutionContext(sessionId, "task-1", null);
        WorkspaceToolFactory.WorkspaceToolSet toolSet =
                factory.create(workspace, "agent-test", ctx, sessionId, PermissionMode.CRAFT);

        assertThat(toolSet.tools()).hasSize(3);
        assertThat(toolSet.tools().stream().map(t -> t.getCard().getId()))
                .containsExactlyInAnyOrder(
                        WorkmateToolIds.read(sessionId),
                        WorkmateToolIds.write(sessionId),
                        WorkmateToolIds.bash(sessionId));
        factory.unregister(toolSet);
    }

    @Test
    void askRegistersReadOnly() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Path workspace = tempDir.resolve(sessionId.toString());
        Files.createDirectories(workspace);

        WorkspaceToolFactory factory = new WorkspaceToolFactory(new WorkspaceShellExecutor(), fileHistoryNoop());
        ToolExecutionContext ctx = new ToolExecutionContext(sessionId, "task-1", null);
        WorkspaceToolFactory.WorkspaceToolSet toolSet =
                factory.create(workspace, "agent-test", ctx, sessionId, PermissionMode.ASK);

        assertThat(toolSet.tools()).hasSize(1);
        assertThat(toolSet.tools().getFirst().getCard().getId()).isEqualTo(WorkmateToolIds.read(sessionId));
        factory.unregister(toolSet);
    }

    @Test
    void planRegistersReadOnly() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Path workspace = tempDir.resolve(sessionId.toString());
        Files.createDirectories(workspace);

        WorkspaceToolFactory factory = new WorkspaceToolFactory(new WorkspaceShellExecutor(), fileHistoryNoop());
        WorkspaceToolFactory.WorkspaceToolSet toolSet = factory.create(
                workspace,
                "agent-test",
                new ToolExecutionContext(sessionId, "task-1", null),
                sessionId,
                PermissionMode.PLAN);

        assertThat(toolSet.tools()).hasSize(1);
        factory.unregister(toolSet);
    }
}
