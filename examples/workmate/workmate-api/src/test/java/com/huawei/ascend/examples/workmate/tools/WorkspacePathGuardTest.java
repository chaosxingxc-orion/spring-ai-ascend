package com.huawei.ascend.examples.workmate.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspacePathGuardTest {

    @TempDir
    Path workspace;

    @Test
    void resolvesRelativePathWithinWorkspace() throws Exception {
        Path file = WorkspacePathGuard.resolve(workspace, "hello.md");
        Files.writeString(file, "hi");
        assertThat(WorkspacePathGuard.readText(file, 1024)).isEqualTo("hi");
    }

    @Test
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> WorkspacePathGuard.resolve(workspace, "../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSymlinkThatEscapesWorkspace(@TempDir Path outside) throws Exception {
        Path secret = outside.resolve("secret.txt");
        Files.writeString(secret, "secret");
        Files.createSymbolicLink(workspace.resolve("leak.txt"), secret);

        assertThatThrownBy(() -> WorkspacePathGuard.resolve(workspace, "leak.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes workspace");
    }

    @Test
    void rejectsWritesThroughSymlinkedParent(@TempDir Path outside) throws Exception {
        Files.createSymbolicLink(workspace.resolve("outside"), outside);

        assertThatThrownBy(() -> WorkspacePathGuard.resolve(workspace, "outside/new.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes workspace");
    }
}
