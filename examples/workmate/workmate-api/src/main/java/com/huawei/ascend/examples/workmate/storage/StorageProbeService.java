package com.huawei.ascend.examples.workmate.storage;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.springframework.stereotype.Service;

@Service
public class StorageProbeService {

    public long directorySizeBytes(Path root) {
        if (root == null || !Files.exists(root)) {
            return 0L;
        }
        final long[] total = {0L};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    total[0] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            return 0L;
        }
        return total[0];
    }
}
