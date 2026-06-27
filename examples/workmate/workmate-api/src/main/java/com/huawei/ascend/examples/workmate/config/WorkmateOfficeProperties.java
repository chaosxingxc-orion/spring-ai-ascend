package com.huawei.ascend.examples.workmate.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.office")
public record WorkmateOfficeProperties(String root) {

    public Path resolvedRoot() {
        String configured = root == null || root.isBlank() ? "../office" : root;
        return Path.of(configured).toAbsolutePath().normalize();
    }
}
