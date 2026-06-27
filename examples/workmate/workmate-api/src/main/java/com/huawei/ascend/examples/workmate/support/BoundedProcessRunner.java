package com.huawei.ascend.examples.workmate.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded stdout capture, timeout, and process-tree teardown for subprocess helpers. */
public final class BoundedProcessRunner {

    public record Result(int exitCode, String output, boolean truncated) {}

    private BoundedProcessRunner() {}

    public static Result run(ProcessBuilder builder, Duration timeout, int maxOutputBytes)
            throws IOException, InterruptedException {
        Process process = builder.start();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        AtomicBoolean truncated = new AtomicBoolean(false);
        Thread reader = Thread.startVirtualThread(() -> {
            try {
                readBounded(process, buffer, truncated, maxOutputBytes);
            } catch (IOException ignored) {
                // process may already be destroyed
            }
        });

        boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            destroyProcessTree(process);
            reader.join(1_000);
            throw new IOException("command timed out after " + timeout.toSeconds() + "s");
        }
        reader.join(1_000);

        String output = buffer.toString(StandardCharsets.UTF_8);
        if (truncated.get()) {
            output = output + "\n...(truncated)";
        }
        return new Result(process.exitValue(), output, truncated.get());
    }

    public static void readBounded(
            Process process, ByteArrayOutputStream buffer, AtomicBoolean truncated, int maxOutputBytes)
            throws IOException {
        byte[] chunk = new byte[8192];
        int stored = 0;
        int read;
        while ((read = process.getInputStream().read(chunk)) != -1) {
            int remaining = maxOutputBytes - stored;
            if (remaining > 0) {
                int toWrite = Math.min(read, remaining);
                buffer.write(chunk, 0, toWrite);
                stored += toWrite;
            }
            if (read > remaining) {
                truncated.set(true);
            }
        }
    }

    public static void destroyProcessTree(Process process) {
        process.descendants().forEach(handle -> handle.destroyForcibly());
        process.destroyForcibly();
    }
}
