package com.huawei.ascend.examples.workmate.support;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Registers the H2 + temp-dir baseline used by most {@code @SpringBootTest} slices. */
public final class WorkmateTestProperties {

    private WorkmateTestProperties() {
    }

    public static WorkmateTestPaths registerBaseline(DynamicPropertyRegistry registry, String dbName)
            throws IOException {
        WorkmateTestPaths paths = WorkmateTestPaths.create(dbName);
        registerWorkspace(registry, paths.workspace());
        registerDataPath(registry, paths.data());
        registerH2(registry, dbName);
        return paths;
    }

    public static void registerH2(DynamicPropertyRegistry registry, String dbName) {
        registry.add("spring.datasource.url", () -> h2Url(dbName));
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    public static void registerWorkspace(DynamicPropertyRegistry registry, Path workspace) {
        registry.add("workmate.workspace.base-path", workspace::toString);
    }

    public static void registerDataPath(DynamicPropertyRegistry registry, Path data) {
        registry.add("workmate.data.path", data::toString);
    }

    public static void registerOfficeRoot(DynamicPropertyRegistry registry) {
        registry.add("workmate.office.root", WorkmateTestPaths::officeRoot);
    }

    public static void registerStudioEnabled(DynamicPropertyRegistry registry, boolean enabled) {
        registry.add("workmate.studio.enabled", () -> Boolean.toString(enabled));
    }

    public static void registerCloudEnabled(DynamicPropertyRegistry registry, boolean enabled) {
        registry.add("workmate.cloud.enabled", () -> Boolean.toString(enabled));
    }

    public static void registerOAuthMockEnabled(DynamicPropertyRegistry registry, boolean enabled) {
        registry.add("workmate.oauth.mock-enabled", () -> Boolean.toString(enabled));
    }

    public static void registerMcpEnabled(DynamicPropertyRegistry registry, boolean enabled) {
        registry.add("workmate.mcp.enabled", () -> Boolean.toString(enabled));
    }

    public static void registerWebhookChannel(
            DynamicPropertyRegistry registry, String channel, boolean enabled, String secret) {
        registry.add("workmate.automation.webhooks." + channel + ".enabled", () -> Boolean.toString(enabled));
        registry.add("workmate.automation.webhooks." + channel + ".secret", () -> secret);
    }

    public static String h2Url(String dbName) {
        return "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    }
}
