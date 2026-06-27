package com.huawei.ascend.examples.workmate.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.filehistory.FileHistoryService;
import com.openjiuwen.core.foundation.tool.Tool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class WorkspaceToolFactoryParallelTest {

    @TempDir
    Path workspaceA;

    @TempDir
    Path workspaceB;

    @Test
    void parallelWritesStayInSeparateWorkspaces() throws Exception {
        UUID sessionA = UUID.randomUUID();
        UUID sessionB = UUID.randomUUID();
        WorkspaceToolFactory factory = new WorkspaceToolFactory(
                new WorkspaceShellExecutor(), Mockito.mock(FileHistoryService.class));
        ToolExecutionContext ctxA = new ToolExecutionContext(sessionA, "task-a", null);
        ToolExecutionContext ctxB = new ToolExecutionContext(sessionB, "task-b", null);

        WorkspaceToolFactory.WorkspaceToolSet setA =
                factory.create(workspaceA, "agent-" + sessionA, ctxA, sessionA);
        WorkspaceToolFactory.WorkspaceToolSet setB =
                factory.create(workspaceB, "agent-" + sessionB, ctxB, sessionB);

        Tool writeA = findWriteTool(setA);
        Tool writeB = findWriteTool(setB);

        CompletableFuture<Void> futureA = CompletableFuture.runAsync(() -> invokeWrite(writeA, "only-a.txt", "ONLY-A"));
        CompletableFuture<Void> futureB = CompletableFuture.runAsync(() -> invokeWrite(writeB, "only-b.txt", "ONLY-B"));
        futureA.get(15, TimeUnit.SECONDS);
        futureB.get(15, TimeUnit.SECONDS);

        assertThat(Files.readString(workspaceA.resolve("only-a.txt"))).isEqualTo("ONLY-A");
        assertThat(Files.readString(workspaceB.resolve("only-b.txt"))).isEqualTo("ONLY-B");
        assertThat(Files.exists(workspaceA.resolve("only-b.txt"))).isFalse();
        assertThat(Files.exists(workspaceB.resolve("only-a.txt"))).isFalse();

        factory.unregister(setA);
        factory.unregister(setB);
    }

    private static Tool findWriteTool(WorkspaceToolFactory.WorkspaceToolSet toolSet) {
        return toolSet.tools().stream()
                .filter(tool -> WorkmateToolIds.isWrite(tool.getCard().getId()))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static void invokeWrite(Tool tool, String path, String content) {
        try {
            Map<String, Object> result = (Map<String, Object>) tool.invoke(Map.of("path", path, "content", content));
            assertThat(result.get("success")).isEqualTo(true);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
