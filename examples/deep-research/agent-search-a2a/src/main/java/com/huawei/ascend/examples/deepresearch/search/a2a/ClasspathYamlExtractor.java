package com.huawei.ascend.examples.deepresearch.search.a2a;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Copies a classpath resource (the assembly YAML lives inside the
 * agent-search jar at {@code /agent/agent.{prod,stub}.yaml}) to a temp file
 * and returns its {@link Path}. {@link com.huawei.ascend.agentsdk.factory.AgentFactory}
 * only accepts file paths, so we extract once at boot and hand the path to it.
 */
final class ClasspathYamlExtractor {

    private ClasspathYamlExtractor() {
    }

    static Path extract(String classpathResource) {
        try (InputStream in = ClasspathYamlExtractor.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("yaml not on classpath: " + classpathResource);
            }
            Path tmpDir = Files.createTempDirectory("search-agent-yaml-");
            tmpDir.toFile().deleteOnExit();
            Path target = tmpDir.resolve(fileNameOf(classpathResource));
            target.toFile().deleteOnExit();
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException ex) {
            throw new IllegalStateException("failed to extract yaml " + classpathResource, ex);
        }
    }

    private static String fileNameOf(String classpathResource) {
        int slash = classpathResource.lastIndexOf('/');
        return slash < 0 ? classpathResource : classpathResource.substring(slash + 1);
    }
}