/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Resolves the DeepAgent YAML spec to a filesystem path for {@link com.huawei.ascend.agentsdk.factory.AgentFactory}.
 *
 * <p>When resources are served from a directory (IDE / exploded classes), the YAML file is
 * returned directly so {@code agentMd} relative paths keep working. When resources live
 * inside a jar, the {@code agent/} and {@code prompts/} trees are materialized to a temp
 * directory first.
 */
public final class DeepResearchAgentSpecMaterializer {

    private DeepResearchAgentSpecMaterializer() {
    }

    public static Path materializeProdYaml() {
        return materialize(DeepResearchConstants.PROD_YAML_RESOURCE);
    }

    public static Path materialize(String yamlResource) {
        String normalized = normalizeResourcePath(yamlResource);
        URL yamlUrl = resourceUrl(normalized);
        if (yamlUrl == null) {
            throw new IllegalArgumentException("Missing classpath resource: " + normalized);
        }
        if ("file".equals(yamlUrl.getProtocol())) {
            try {
                return Path.of(yamlUrl.toURI());
            } catch (URISyntaxException error) {
                throw new IllegalStateException("Invalid resource URI: " + yamlUrl, error);
            }
        }
        try {
            Path root = Files.createTempDirectory("deep-research-agent-spec-");
            copyResource("agent/deepagent.prod.yaml", root.resolve("agent/deepagent.prod.yaml"));
            copyResource(
                    DeepResearchConstants.SYSTEM_PROMPT_RESOURCE,
                    root.resolve(DeepResearchConstants.SYSTEM_PROMPT_RESOURCE));
            return root.resolve("agent/deepagent.prod.yaml");
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to materialize deep-research agent spec", error);
        }
    }

    private static void copyResource(String resourcePath, Path target) throws IOException {
        URL url = resourceUrl(resourcePath);
        if (url == null) {
            throw new IllegalArgumentException("Missing classpath resource: " + resourcePath);
        }
        Files.createDirectories(target.getParent());
        try (InputStream input = url.openStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static URL resourceUrl(String resourcePath) {
        return DeepResearchConstants.class.getClassLoader().getResource(resourcePath);
    }

    private static String normalizeResourcePath(String resourcePath) {
        String normalized = resourcePath == null ? "" : resourcePath.trim();
        if (normalized.startsWith("classpath:")) {
            normalized = normalized.substring("classpath:".length());
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
