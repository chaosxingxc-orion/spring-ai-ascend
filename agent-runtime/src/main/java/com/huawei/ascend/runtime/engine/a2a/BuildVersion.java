package com.huawei.ascend.runtime.engine.a2a;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads the agent-runtime build version from the Maven-filtered classpath resource
 * {@code agent-runtime-build.properties}. Falls back to the package manifest
 * {@code Implementation-Version}, then to {@code "0.1.0"} when running outside
 * of a packaged jar (e.g., from test sources or IDE runs).
 */
public final class BuildVersion {

    private static final String FALLBACK = "0.1.0";

    private BuildVersion() {
    }

    /** Returns the resolved build version string. Never null, never blank. */
    public static String resolve() {
        // Primary: Maven-filtered properties file present on the classpath.
        try (InputStream is = BuildVersion.class.getResourceAsStream(
                "/agent-runtime-build.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String v = props.getProperty("agent-runtime.build.version");
                if (v != null && !v.isBlank() && !v.startsWith("${")) {
                    return v.trim();
                }
            }
        } catch (IOException ignored) {
            // fall through to next strategy
        }
        // Secondary: jar manifest Implementation-Version (set by maven-jar-plugin by default).
        String manifest = BuildVersion.class.getPackage().getImplementationVersion();
        if (manifest != null && !manifest.isBlank()) {
            return manifest.trim();
        }
        return FALLBACK;
    }
}
