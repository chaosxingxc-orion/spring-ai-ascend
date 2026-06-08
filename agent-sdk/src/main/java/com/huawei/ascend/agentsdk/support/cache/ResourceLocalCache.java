package com.huawei.ascend.agentsdk.support.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ResourceLocalCache {
    public Path ensureRoot(Path root) {
        try {
            Files.createDirectories(root);
            return root.toAbsolutePath().normalize();
        } catch (IOException error) {
            throw new LocalCacheException("Failed to create agent SDK cache root: " + root, error);
        }
    }
}

