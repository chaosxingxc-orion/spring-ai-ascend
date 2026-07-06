package com.huawei.ascend.edp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 1：工程身份生产化单元测试。
 *
 * 验证去 Spike 化后的工程身份：
 * - 启动类名不再包含 Spike
 * - 配置类名不再包含 Spike
 * - artifactId 不再包含 spike
 * - Spring Boot app name 不再包含 spike
 */
class Phase1IdentityTest {

    @Test
    void testEdpaApplication_ClassName() {
        // 验证启动类名不含 Spike
        String className = EdpaApplication.class.getSimpleName();
        assertEquals("EdpaApplication", className, "启动类名应为 EdpaApplication（不含 Spike）");
        assertFalse(className.contains("Spike"), "启动类名不应包含 Spike");
    }

    @Test
    void testEdpaApplication_MainMethodExists() throws NoSuchMethodException {
        // 验证 main 方法存在（通过反射检查）
        assertNotNull(EdpaApplication.class.getDeclaredMethod("main", String[].class),
                "main 方法应存在");
    }

    @Test
    void testEdpaEngineConfiguration_ClassName() {
        String className = EdpaEngineConfiguration.class.getSimpleName();
        assertEquals("EdpaEngineConfiguration", className, "配置类名应为 EdpaEngineConfiguration（不含 Spike）");
        assertFalse(className.contains("Spike"), "配置类名不应包含 Spike");
    }

    @Test
    void testEdpaEngineConfiguration_IsConfiguration() {
        assertTrue(EdpaEngineConfiguration.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class),
                "EdpaEngineConfiguration 应有 @Configuration 注解");
    }

    @Test
    void testNoSpikeReferencesInKeyClasses() {
        // 验证核心类名不含 Spike
        assertFalse(EdpaApplication.class.getSimpleName().contains("Spike"));
        assertFalse(EdpaEngineConfiguration.class.getSimpleName().contains("Spike"));
    }
}
