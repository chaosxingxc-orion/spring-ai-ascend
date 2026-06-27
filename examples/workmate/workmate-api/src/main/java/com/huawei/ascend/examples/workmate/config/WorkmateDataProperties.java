package com.huawei.ascend.examples.workmate.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.data")
public record WorkmateDataProperties(String path) {

    public Path resolvedPath() {
        String configured = path == null || path.isBlank() ? "./data" : path;
        return Path.of(configured).toAbsolutePath().normalize();
    }
}
