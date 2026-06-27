package com.huawei.ascend.examples.workmate.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Temp workspace/data directories shared by integration tests. */
public record WorkmateTestPaths(Path workspace, Path data) {

    public static WorkmateTestPaths create(String prefix) throws IOException {
        return new WorkmateTestPaths(
                Files.createTempDirectory(prefix + "-ws-"),
                Files.createTempDirectory(prefix + "-data-"));
    }

    public static String officeRoot() {
        return Path.of("../office").toAbsolutePath().normalize().toString();
    }
}
