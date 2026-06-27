package com.huawei.ascend.examples.workmate.tools;

import com.huawei.ascend.examples.workmate.support.BoundedProcessRunner;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceShellExecutor {

    private static final int MAX_OUTPUT_BYTES = 64 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    public Map<String, Object> run(Path workspaceRoot, String command) throws IOException, InterruptedException {
        Path workspace = workspaceRoot.toAbsolutePath().normalize();
        String wrapped = wrapRestrictedCommand(workspace, command);

        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-lc", wrapped)
                .directory(workspace.toFile())
                .redirectErrorStream(true);
        builder.environment().putAll(restrictedEnvironment());

        BoundedProcessRunner.Result result = BoundedProcessRunner.run(builder, TIMEOUT, MAX_OUTPUT_BYTES);
        return Map.of("exitCode", result.exitCode(), "output", result.output());
    }

    /**
     * Wrap an agent command for local execution.
     *
     * <p><b>This is NOT a security sandbox.</b> It only sets proxy env vars (best-effort "no network"
     * for tools that honor {@code HTTP_PROXY} — bypassable by {@code curl --noproxy}, raw sockets, ssh,
     * nc, etc.), {@code cd}s into the workspace, and applies best-effort {@code ulimit} caps. There is
     * no namespace/chroot/cgroup isolation. The real guardrail is the PreToolUse HITL risk policy;
     * production isolation must use a container/bwrap profile (see ADR-010).</p>
     */
    static String wrapRestrictedCommand(Path workspace, String command) {
        String escapedWorkspace = shellEscape(workspace.toString());
        // Best-effort resource caps. We intentionally do NOT cap process count (ulimit -u): the
        // command runs inside the API's JVM process tree, whose existing thread/process count can
        // already exceed a small -u limit and make every legitimate fork fail. CPU-time and
        // file-size caps are safe and still bound runaway commands.
        return """
                ulimit -t 120 2>/dev/null || true
                ulimit -f 2097152 2>/dev/null || true
                set -euo pipefail
                export HTTP_PROXY=http://127.0.0.1:9
                export HTTPS_PROXY=http://127.0.0.1:9
                export http_proxy=http://127.0.0.1:9
                export https_proxy=http://127.0.0.1:9
                export ALL_PROXY=http://127.0.0.1:9
                export WORKMATE_NETWORK=disabled
                cd %s
                %s
                """.formatted(escapedWorkspace, command);
    }

    static Map<String, String> restrictedEnvironment() {
        Map<String, String> env = new HashMap<>();
        env.put("HTTP_PROXY", "http://127.0.0.1:9");
        env.put("HTTPS_PROXY", "http://127.0.0.1:9");
        env.put("http_proxy", "http://127.0.0.1:9");
        env.put("https_proxy", "http://127.0.0.1:9");
        env.put("ALL_PROXY", "http://127.0.0.1:9");
        env.put("WORKMATE_NETWORK", "disabled");
        return env;
    }

    static void destroyProcessTree(Process process) {
        BoundedProcessRunner.destroyProcessTree(process);
    }

    private static String shellEscape(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
