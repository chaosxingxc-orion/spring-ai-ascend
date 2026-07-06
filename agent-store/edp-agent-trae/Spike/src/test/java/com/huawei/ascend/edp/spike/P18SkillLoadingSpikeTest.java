package com.huawei.ascend.edp.spike;

import com.huawei.ascend.edp.config.EdpAgentConfig;
import com.huawei.ascend.edp.config.EdpAgentConfigLoader;
import com.huawei.ascend.edp.handler.EdpaRuntimeHandler;
import com.openjiuwen.core.singleagent.skills.Skill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("spike")
@DisplayName("P18: Skill 加载穿刺验证")
class P18SkillLoadingSpikeTest {

    @Test
    @DisplayName("P18-1: edp-agent.yaml 解析 src/main/resources/skills 配置")
    void edpAgentYamlParsesSkillDirectories() {
        EdpAgentConfig config = EdpAgentConfigLoader.load(Path.of("src/main/resources/edp-agent.yaml"));

        assertNotNull(config.getSkills());
        assertEquals(List.of("./skills"), config.getSkills().getDirectories());
        assertEquals("all", config.getSkills().getMode());
        assertTrue(Files.exists(Path.of("src/main/resources/skills/product_recommend_skill/SKILL.md")));
    }

    @Test
    @DisplayName("P18-2: EdpaRuntimeHandler 初始化后 OpenJiuwen SkillUtil 完成加载")
    void runtimeRegistersSkillsIntoOpenJiuwenSkillUtil() {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        handler.init(
                Path.of("src/main/resources/edp-agent.yaml").toString(),
                Path.of("src/main/resources/edp-config.yaml").toString());

        assertNotNull(handler.getDeepAgent().getAgent().getSkillUtil());
        assertTrue(handler.getDeepAgent().getAgent().getSkillUtil().hasSkill());
        List<String> skillNames = handler.getDeepAgent().getAgent().getSkillUtil().getSkillManager().getNames();
        System.out.println("[P18_TEST_REPORT] P18-2 | loadedSkillNames=" + skillNames);
        assertTrue(skillNames.contains("product_recommend_skill"));
        assertTrue(skillNames.contains("product_select_skill"));
        assertTrue(skillNames.contains("fund_planning_skill"));
    }

    @Test
    @DisplayName("P18-3: Skill prompt 包含 SKILL.md 路径和产品推荐 Skill")
    void skillPromptContainsLoadedSkillMetadata() {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        handler.init(
                Path.of("src/main/resources/edp-agent.yaml").toString(),
                Path.of("src/main/resources/edp-config.yaml").toString());

        String skillPrompt = handler.getDeepAgent().getAgent().getSkillUtil().getSkillPrompt();
        System.out.println("[P18_TEST_REPORT] P18-3 | skillPrompt=" + skillPrompt);

        assertTrue(skillPrompt.contains("product_recommend_skill"));
        assertTrue(skillPrompt.contains("Skill directory file path"));
        Skill productRecommend = handler.getDeepAgent().getAgent().getSkillUtil()
                .getSkillManager().get("product_recommend_skill");
        assertNotNull(productRecommend);
        assertTrue(productRecommend.getDirectory().replace('\\', '/')
                .endsWith("src/main/resources/skills/product_recommend_skill"));
    }
}
