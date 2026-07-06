package com.huawei.ascend.edp.stream;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillScriptsCollector Skill 话术收集单元测试。
 *
 * 验证阶段 2 架构包结构生产化中的 Skill 业务话术收集机制。
 */
class SkillScriptsCollectorTest {

    @Test
    void testCollectSkillScripts_NonExistentDir() {
        Map<String, String> scripts = SkillScriptsCollector.collectSkillScripts(Path.of("/nonexistent/skills"));
        assertNotNull(scripts, "不存在的目录应返回空 map");
        assertEquals(0, scripts.size(), "不存在的目录应返回空 map");
    }

    @Test
    void testCollectSkillScripts_NullDir() {
        // null path 会导致 NPE，测试该行为
        assertThrows(NullPointerException.class,
                () -> SkillScriptsCollector.collectSkillScripts(null),
                "null path 应抛 NullPointerException");
    }

    @Test
    void testCollectSkillScripts_WealthDemoSkills() throws IOException {
        Path skillsDir = Path.of("../../scenarios/wealth-demo/skills").toAbsolutePath().normalize();
        if (Files.exists(skillsDir)) {
            Map<String, String> scripts = SkillScriptsCollector.collectSkillScripts(skillsDir);
            assertNotNull(scripts, "收集结果不应为 null");
            assertTrue(scripts.size() > 0, "wealth-demo skills 应至少有一条话术");

            // 验证 product_recommend_skill 的话术
            assertTrue(scripts.containsKey("product_recommend_success") || scripts.containsKey("product_recommend_empty"),
                    "product_recommend_skill 应包含推荐成功或推荐空话术");
        } else {
            System.out.println("SKIP: wealth-demo skills directory not found at " + skillsDir);
        }
    }

    @Test
    void testCollectSkillScripts_EmptySkillsDir() throws IOException {
        // 创建临时空目录测试
        Path tempDir = Files.createTempDirectory("empty_skills");
        try {
            Map<String, String> scripts = SkillScriptsCollector.collectSkillScripts(tempDir);
            assertEquals(0, scripts.size(), "空 skills 目录应返回空 map");
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void testCollectSkillScripts_SkillWithYaml() throws IOException {
        // 创建临时 Skill 目录模拟结构
        Path tempDir = Files.createTempDirectory("test_skills");
        Path skillDir = tempDir.resolve("test_skill");
        Files.createDirectories(skillDir);
        String yamlContent = "name: test_skill\nscripts:\n  test_key: \"test_value\"";
        Files.writeString(skillDir.resolve("SKILL.yaml"), yamlContent);

        try {
            Map<String, String> scripts = SkillScriptsCollector.collectSkillScripts(tempDir);
            assertEquals(1, scripts.size(), "应收集 1 条话术");
            assertEquals("test_value", scripts.get("test_key"), "话术内容应匹配");
        } finally {
            Files.deleteIfExists(skillDir.resolve("SKILL.yaml"));
            Files.deleteIfExists(skillDir);
            Files.deleteIfExists(tempDir);
        }
    }
}
