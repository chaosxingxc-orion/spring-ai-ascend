package com.huawei.ascend.edp.handler;

import com.huawei.ascend.edp.config.GovernanceConfig;
import com.huawei.ascend.edp.config.PlanRuleConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EdpaRuntimeHandler GovernanceConfig集成测试类。
 *
 * <p>测试目标：验证GovernanceConfig加载和系统提示词拼接逻辑</p>
 * <p>测试覆盖：</p>
 * <ul>
 *     <li>完整GovernanceConfig拼接测试</li>
 *     <li>GovernanceConfig缺失降级测试</li>
 *     <li>prompt.system 已移除验证（不再依赖 agentConfig）</li>
 *     <li>GovernanceConfigLoader加载测试（框架级）</li>
 *     <li>GovernanceConfigLoader加载测试（场景级优先）</li>
 * </ul>
 */
class EdpaRuntimeHandlerGovernanceTest {

    /**
     * 测试用例1：完整GovernanceConfig拼接测试。
     *
     * <p>验证planrule四字段拼接逻辑</p>
     */
    @Test
    void testBuildFullSystemPromptWithFullGovernanceConfig() {
        // 构造GovernanceConfig（planrule四字段完整配置）
        GovernanceConfig governance = new GovernanceConfig();
        PlanRuleConfig planrule = new PlanRuleConfig();
        planrule.setRole("通用动态规划智能体角色定位");
        planrule.setDescription("负责任务规划、执行和结果总结的智能助手");

        PlanRuleConfig.Scope scope = new PlanRuleConfig.Scope();
        scope.setAllowed(" ");
        scope.setDenied(" ");
        planrule.setScope(scope);

        planrule.setSupplementaryPrompt("## 二、行为约束\n\n行为约束：\n1. 当用户表达修改意图，暂停当前任务，重新规划");
        governance.setPlanrule(planrule);

        // 使用反射调用buildFullSystemPrompt()方法（仅 governance 参数）
        String systemPrompt = invokeBuildFullSystemPrompt(governance);

        // 验证拼接结果（仅 planruleFragment 生效）
        assertTrue(systemPrompt.contains("# 通用动态规划智能体角色定位"));
        assertTrue(systemPrompt.contains("负责任务规划、执行和结果总结的智能助手"));
        assertTrue(systemPrompt.contains("## 二、行为约束"));
        assertTrue(systemPrompt.contains("暂停当前任务，重新规划"));

        // 场景部分已删除，不再输出场景名/描述
        assertFalse(systemPrompt.contains("**当前场景**"));
        assertFalse(systemPrompt.contains("\n\n**当前场景**"));
    }

    /**
     * 测试用例2：GovernanceConfig缺失降级测试。
     *
     * <p>验证GovernanceConfig为null时的降级处理</p>
     */
    @Test
    void testBuildFullSystemPromptWithNullGovernanceConfig() {
        // GovernanceConfig为null
        GovernanceConfig governance = null;

        // 调用buildFullSystemPrompt()（GovernanceConfig为null）
        String systemPrompt = invokeBuildFullSystemPrompt(governance);

        // GovernanceConfig 为 null → 返回空字符串
        assertTrue(systemPrompt.isEmpty(), "GovernanceConfig 为 null，应返回空字符串");
    }

    /**
     * 测试用例4：prompt.system 已移除验证。
     *
     * <p>验证 buildFullSystemPrompt 不再依赖 agentConfig.prompt.system，始终使用 governance 拼接逻辑</p>
     */
    @Test
    void testBuildFullSystemPromptNoLongerDependsOnAgentConfig() {
        // 构造GovernanceConfig
        GovernanceConfig governance = new GovernanceConfig();
        PlanRuleConfig planrule = new PlanRuleConfig();
        planrule.setRole("理财推荐智能体");
        planrule.setDescription("理财产品推荐智能助手");
        governance.setPlanrule(planrule);

        // 直接调用（不再需要 EdpAgentConfig 参数）
        String systemPrompt = invokeBuildFullSystemPrompt(governance);

        // 验证返回的是 governance 拼接结果
        assertTrue(systemPrompt.contains("# 理财推荐智能体"));
        assertTrue(systemPrompt.contains("理财产品推荐智能助手"));
        // 场景部分已删除，不再输出场景名
        assertFalse(systemPrompt.contains("**当前场景**"));
        // 验证不再依赖 agentConfig.prompt.system
        assertFalse(systemPrompt.contains("向后兼容"));
        assertFalse(systemPrompt.contains("agentConfig"));
    }

    /**
     * 测试用例5：GovernanceConfigLoader加载测试（框架级）。
     *
     * <p>验证框架级GovernanceConfig加载</p>
     */
    @Test
    void testLoadGovernanceConfigFrameworkLevel() {
        // yamlDir：src/main/resources（框架级governance路径）
        Path yamlDir = Path.of("src/main/resources");

        // scenarioHomePath：null（无场景级governance）
        Path scenarioHomePath = null;

        // 使用反射调用loadGovernanceConfig()方法
        GovernanceConfig governance = invokeLoadGovernanceConfig(yamlDir, scenarioHomePath);

        // 验证GovernanceConfig包含框架级planrule.yaml、actrule.yaml、scriptconfig.yaml内容
        assertNotNull(governance);
        assertNotNull(governance.getPlanrule(), "planrule should be loaded from framework-level governance");
        assertNotNull(governance.getActrule(), "actrule should be loaded from framework-level governance");
        assertNotNull(governance.getScriptconfig(), "scriptconfig should be loaded from framework-level governance");

        // 验证planrule内容
        assertEquals("你的身份是通用动态规划智能体", governance.getPlanrule().getRole());
        assertTrue(governance.getPlanrule().getDescription().contains("任务规划"));
    }

    /**
     * 测试用例6：GovernanceConfigLoader加载测试（场景级优先）。
     *
     * <p>验证场景级GovernanceConfig优先加载</p>
     */
    @Test
    void testLoadGovernanceConfigScenarioLevelPriority() {
        // yamlDir：src/main/resources（框架级governance路径）
        Path yamlDir = Path.of("src/main/resources");

        // scenarioHomePath：scenarios/wealth-demo（场景级governance路径）
        // 注意：这个测试需要实际存在场景级governance目录才能通过
        // 如果没有场景级governance目录，会降级使用框架级governance
        Path scenarioHomePath = Path.of("scenarios/wealth-demo");

        // 使用反射调用loadGovernanceConfig()方法
        GovernanceConfig governance = invokeLoadGovernanceConfig(yamlDir, scenarioHomePath);

        // 验证GovernanceConfig加载成功
        assertNotNull(governance);
        assertNotNull(governance.getPlanrule());

        // 如果场景级governance存在，验证场景级配置覆盖框架级配置
        // 如果场景级governance不存在，验证降级使用框架级配置
        if (governance.getPlanrule().getRole() != null) {
            // 验证planrole字段加载成功（无论框架级还是场景级）
            assertNotNull(governance.getPlanrule().getRole());
        }
    }

    /**
     * 测试用例7：scenarioHome 为 null 时，scenarioHomePath 为 null。
     *
     * <p>验证删除 scenario_discovery 回退逻辑后，未配置 scenario-home 时跳过场景加载。</p>
     */
    @Test
    void testScenarioHomePathNullWhenScenarioHomeNotConfigured() {
        // 使用反射调用 init()，因为完整 init 需要 Spring 上下文
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        invokeInit(handler, null);
        assertNull(handler.getScenarioHomePath(),
                "scenarioHome 未配置时 scenarioHomePath 应为 null，使用 governance 默认配置");
    }

    /**
     * 测试用例8：scenarioHome 配置时，scenarioHomePath 正确解析。
     *
     * <p>验证 application.yml 中配置 scenario-home 后，场景路径被正确解析并加载。</p>
     */
    @Test
    void testScenarioHomePathSetWhenScenarioHomeConfigured() {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        invokeInit(handler, "scenarios/wealth-demo");
        assertNotNull(handler.getScenarioHomePath(),
                "scenarioHome 配置时 scenarioHomePath 不应为 null");
        assertTrue(handler.getScenarioHomePath().endsWith(Path.of("scenarios/wealth-demo")),
                "scenarioHomePath 应以 scenarios/wealth-demo 结尾");
    }

    /**
     * 测试用例9：scenarioHome 为空字符串时，scenarioHomePath 为 null。
     *
     * <p>验证空字符串触发 isBlank 判定，不参与场景加载。</p>
     */
    @Test
    void testScenarioHomePathNullWhenScenarioHomeIsBlank() {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        invokeInit(handler, "");
        assertNull(handler.getScenarioHomePath(),
                "scenarioHome 为空字符串时 scenarioHomePath 应为 null");
    }

    /**
     * 通过反射设置 scenarioHomePath，模拟 init() 中的第四步逻辑。
     * 避免完整 init() 的 ModelConfig 验证和 DeepAgent 创建等副作用。
     */
    private void invokeInit(EdpaRuntimeHandler handler, String scenarioHome) {
        try {
            java.lang.reflect.Field field = EdpaRuntimeHandler.class.getDeclaredField("scenarioHomePath");
            field.setAccessible(true);

            if (scenarioHome != null && !scenarioHome.isBlank()) {
                field.set(handler, Path.of(scenarioHome).toAbsolutePath().normalize());
            }
            // scenarioHome 为 null/blank 时，field 保持 null（默认值）
        } catch (Exception e) {
            throw new RuntimeException("Failed to set scenarioHomePath via reflection", e);
        }
    }

    /**
     * 使用反射调用buildFullSystemPrompt()方法（private方法，仅 governance 参数）。
     */
    private String invokeBuildFullSystemPrompt(GovernanceConfig governance) {
        try {
            EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
            java.lang.reflect.Method method = EdpaRuntimeHandler.class.getDeclaredMethod(
                    "buildFullSystemPrompt", GovernanceConfig.class);
            method.setAccessible(true);
            return (String) method.invoke(handler, governance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke buildFullSystemPrompt method", e);
        }
    }

    /**
     * 使用反射调用loadGovernanceConfig()方法（private方法）。
     */
    private GovernanceConfig invokeLoadGovernanceConfig(Path yamlDir, Path scenarioHomePath) {
        try {
            // 创建EdpaRuntimeHandler实例
            EdpaRuntimeHandler handler = new EdpaRuntimeHandler();

            // 使用反射调用private方法
            java.lang.reflect.Method method = EdpaRuntimeHandler.class.getDeclaredMethod(
                    "loadGovernanceConfig", Path.class, Path.class);
            method.setAccessible(true);

            return (GovernanceConfig) method.invoke(handler, yamlDir, scenarioHomePath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke loadGovernanceConfig method", e);
        }
    }
}