package com.huawei.ascend.examples.workmate.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceShellExecutorTest {

    @TempDir
    Path workspace;

    @Test
    void runsCommandInsideWorkspace() throws Exception {
        WorkspaceShellExecutor executor = new WorkspaceShellExecutor();

        Map<String, Object> result = executor.run(workspace, "pwd");

        assertThat(result.get("exitCode")).isEqualTo(0);
        assertThat(String.valueOf(result.get("output"))).contains(workspace.toString());
    }

    @Test
    void createsFileWithinWorkspace() throws Exception {
        WorkspaceShellExecutor executor = new WorkspaceShellExecutor();

        Map<String, Object> result = executor.run(workspace, "echo hello > demo.txt && cat demo.txt");

        assertThat(result.get("exitCode")).isEqualTo(0);
        assertThat(String.valueOf(result.get("output"))).contains("hello");
        assertThat(Files.exists(workspace.resolve("demo.txt"))).isTrue();
    }

    @Test
    void wrapRestrictedCommandPinsWorkingDirectory() {
        String wrapped = WorkspaceShellExecutor.wrapRestrictedCommand(workspace, "ls");

        assertThat(wrapped).contains("cd '" + workspace.toString().replace("'", "'\"'\"'") + "'");
        assertThat(wrapped).contains("WORKMATE_NETWORK=disabled");
        assertThat(wrapped).contains("HTTP_PROXY=http://127.0.0.1:9");
    }

    @Test
    void timesOutLongRunningCommand() {
        WorkspaceShellExecutor executor = new WorkspaceShellExecutor();

        assertThatThrownBy(() -> executor.run(workspace, "sleep 60"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void truncatesLargeOutputWhileReading() throws Exception {
        WorkspaceShellExecutor executor = new WorkspaceShellExecutor();

        Map<String, Object> result = executor.run(workspace, "python3 -c 'print(\"x\" * 70000)'");

        assertThat(result.get("exitCode")).isEqualTo(0);
        assertThat(String.valueOf(result.get("output"))).contains("...(truncated)");
        assertThat(String.valueOf(result.get("output")).length()).isLessThan(66_000);
    }
}
