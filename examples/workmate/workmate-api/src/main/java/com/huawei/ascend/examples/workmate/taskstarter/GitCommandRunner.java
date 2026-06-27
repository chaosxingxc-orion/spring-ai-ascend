package com.huawei.ascend.examples.workmate.taskstarter;

import com.huawei.ascend.examples.workmate.support.BoundedProcessRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GitCommandRunner {

    static final Duration GIT_TIMEOUT = Duration.ofMinutes(3);
    static final int MAX_GIT_OUTPUT_BYTES = 64 * 1024;

    public List<String> runLines(Path repo, List<String> args, boolean allowNetwork, String token) {
        String output = run(repo, args, allowNetwork, token, true);
        if (output.isBlank()) {
            return List.of();
        }
        return output.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
    }

    public void run(Path repo, List<String> args, boolean allowNetwork, String token) {
        run(repo, args, allowNetwork, token, true);
    }

    public String run(Path repo, List<String> args, boolean allowNetwork, String token, boolean failOnNonZeroExit) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        if (Files.isDirectory(repo)) {
            builder.directory(repo.toFile());
        }
        Map<String, String> env = new LinkedHashMap<>(builder.environment());
        if (!allowNetwork) {
            env.put("HTTP_PROXY", "http://127.0.0.1:9");
            env.put("HTTPS_PROXY", "http://127.0.0.1:9");
            env.put("http_proxy", "http://127.0.0.1:9");
            env.put("https_proxy", "http://127.0.0.1:9");
        }
        builder.redirectErrorStream(true);
        Path askpassScript = null;
        try {
            if (token != null && !token.isBlank()) {
                env.put("GIT_TERMINAL_PROMPT", "0");
                if (allowNetwork) {
                    askpassScript = createAskpassScript();
                    env.put("GIT_ASKPASS", askpassScript.toString());
                    env.put("WORKMATE_GIT_ASKPASS_TOKEN", token);
                }
            }
            builder.environment().clear();
            builder.environment().putAll(env);
            BoundedProcessRunner.Result result = BoundedProcessRunner.run(builder, GIT_TIMEOUT, MAX_GIT_OUTPUT_BYTES);
            if (failOnNonZeroExit && result.exitCode() != 0) {
                throw new IllegalStateException(
                        safeGitCommand(args, token) + " failed: " + redactSecret(result.output().trim(), token));
            }
            return result.output();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (ex instanceof IOException && ex.getMessage() != null && ex.getMessage().contains("timed out")) {
                throw new IllegalStateException("git command timed out: " + safeGitCommand(args, token), ex);
            }
            throw new IllegalStateException("git command failed: " + ex.getMessage(), ex);
        } finally {
            if (askpassScript != null) {
                try {
                    Files.deleteIfExists(askpassScript);
                } catch (IOException ignored) {
                    // Best-effort cleanup for a script that does not contain the token itself.
                }
            }
        }
    }

    static String safeGitCommand(List<String> args, String token) {
        return redactSecret("git " + String.join(" ", args), token);
    }

    static String redactSecret(String value, String token) {
        if (value == null || token == null || token.isBlank()) {
            return value;
        }
        return value.replace(token, "[redacted]");
    }

    static String requireSafeBranch(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("branch required");
        }
        String branch = value.trim();
        if (branch.startsWith("-")
                || branch.startsWith("/")
                || branch.endsWith("/")
                || branch.endsWith(".")
                || branch.contains("..")
                || branch.contains("@{")
                || branch.matches(".*[\\\\~^:?*\\[\\]\\s\\x00-\\x1F\\x7F].*")) {
            throw new IllegalArgumentException("Invalid git branch: " + value);
        }
        return branch;
    }

    private static Path createAskpassScript() throws IOException {
        Path script = Files.createTempFile("workmate-git-askpass-", ".sh");
        Files.writeString(
                script,
                "#!/bin/sh\nprintf '%s\\n' \"$WORKMATE_GIT_ASKPASS_TOKEN\"\n",
                StandardCharsets.UTF_8);
        script.toFile().setExecutable(true, true);
        return script;
    }
}
