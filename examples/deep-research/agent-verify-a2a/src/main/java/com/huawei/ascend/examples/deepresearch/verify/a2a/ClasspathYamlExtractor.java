/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.a2a;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Copies a classpath resource (assembly YAML inside agent-verify jar) to a temp
 * file for {@link com.huawei.ascend.agentsdk.factory.AgentFactory#toReactAgent(Path)}.
 */
final class ClasspathYamlExtractor {

    private ClasspathYamlExtractor() {
    }

    static Path extract(String classpathResource) {
        String normalized = normalize(classpathResource);
        try (InputStream in = ClasspathYamlExtractor.class.getResourceAsStream(normalized)) {
            if (in == null) {
                throw new IllegalStateException("yaml not on classpath: " + normalized);
            }
            Path tmpDir = Files.createTempDirectory("verify-agent-yaml-");
            tmpDir.toFile().deleteOnExit();
            Path target = tmpDir.resolve(fileNameOf(normalized));
            target.toFile().deleteOnExit();
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException ex) {
            throw new IllegalStateException("failed to extract yaml " + normalized, ex);
        }
    }

    private static String normalize(String classpathResource) {
        if (classpathResource.startsWith("classpath:")) {
            String stripped = classpathResource.substring("classpath:".length());
            return stripped.startsWith("/") ? stripped : "/" + stripped;
        }
        return classpathResource.startsWith("/") ? classpathResource : "/" + classpathResource;
    }

    private static String fileNameOf(String classpathResource) {
        int slash = classpathResource.lastIndexOf('/');
        return slash < 0 ? classpathResource : classpathResource.substring(slash + 1);
    }
}
