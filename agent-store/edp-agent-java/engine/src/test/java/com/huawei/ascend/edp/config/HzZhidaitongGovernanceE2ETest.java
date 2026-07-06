package com.huawei.ascend.edp.config;

import com.huawei.ascend.edp.stream.PlanrulePromptBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * hz-zhidaitong 场景 Governance 配置端到端测试。
 *
 * <p>验证链路：YAML 文件 → GovernanceConfigLoader → merge → PlanrulePromptBuilder → 系统提示词</p>
 *
 * <p>覆盖范围：</p>
 * <ul>
 *     <li>scope.allowed / scope.denied 字段名修正后的正确反序列化</li>
 *     <li>scenario_name / scenario_description 字段名修正后的正确反序列化</li>
 *     <li>框架级 + 场景级合并后 scope 值的正确传递</li>
 *     <li>System Prompt 中包含正确的业务范围约束</li>
 *     <li>回归：不允许旧字段名 allowed_business / denied_business / scenarioName 残留生效</li>
 * </ul>
 *
 * <p>注意：GovernanceConfigLoader 的 YAML_MAPPER 使用 SNAKE_CASE 命名策略，
 * 所有 YAML key 必须用 snake_case。</p>
 */
@DisplayName("hz-zhidaitong Governance 配置端到端测试")
class HzZhidaitongGovernanceE2ETest {

    @TempDir
    Path tempDir;

    /**
     * 第一节：YAML 加载正确性
     */
    @Nested
    @DisplayName("1. YAML 加载正确性")
    class YamlLoading {

        /**
         * 测试1.1：直接从真实 hz-zhidaitong 场景目录加载 planrule.yaml，
         * 验证 scope.allowed / scope.denied / scenario_name 被正确反序列化。
         */
        @Test
        @DisplayName("1.1 真实场景目录加载 — 所有字段正确反序列化")
        void testLoadHzZhidaitongScenarioFromRealDir() throws Exception {
            Path scenarioPath = Path.of("scenarios/hz-zhidaitong/governance").toAbsolutePath();

            if (!Files.exists(scenarioPath)) {
                System.out.println("[SKIP] scenarios/hz-zhidaitong/governance 不存在，跳过真实目录测试");
                return;
            }

            GovernanceConfig config = GovernanceConfigLoader.load(scenarioPath);
            assertNotNull(config.getPlanrule(), "planrule 应被加载");

            // scenario_name 正确反序列化
            assertEquals("杭研智贷通", config.getPlanrule().getScenarioName(),
                    "scenario_name 应被正确反序列化");
            assertEquals("杭研智贷通贷款申请、审批、放款全流程",
                    config.getPlanrule().getScenarioDescription(),
                    "scenario_description 应被正确反序列化");

            // scope 正确反序列化
            assertNotNull(config.getPlanrule().getScope(), "scope 不应为 null");
            assertNotNull(config.getPlanrule().getScope().getAllowed(),
                    "scope.allowed 应被正确反序列化（不是 null）");
            assertTrue(config.getPlanrule().getScope().getAllowed().contains("智贷通贷款申请"),
                    "scope.allowed 应包含 '智贷通贷款申请'");
            assertTrue(config.getPlanrule().getScope().getAllowed().contains("贷款审批"),
                    "scope.allowed 应包含 '贷款审批'");
            assertTrue(config.getPlanrule().getScope().getAllowed().contains("放款流程"),
                    "scope.allowed 应包含 '放款流程'");

            assertNotNull(config.getPlanrule().getScope().getDenied(),
                    "scope.denied 应被正确反序列化（不是 null）");
            assertTrue(config.getPlanrule().getScope().getDenied().contains("理财相关业务"),
                    "scope.denied 应包含 '理财相关业务'");
            assertTrue(config.getPlanrule().getScope().getDenied().contains("信用卡相关业务"),
                    "scope.denied 应包含 '信用卡相关业务'");
        }

        /**
         * 测试1.2：使用 @TempDir 模拟修正后的 hz-zhidaitong planrule.yaml，
         * 验证 snake_case 字段能被 Jackson SNAKE_CASE 策略正确映射。
         */
        @Test
        @DisplayName("1.2 内联 YAML — snake_case 字段正确反序列化")
        void testLoadInlineHzZhidaitongPlanrule() throws Exception {
            Path govDir = tempDir.resolve("governance");
            Files.createDirectories(govDir);

            String planruleYaml = "planrule:\n" +
                    "  scenario_name: '杭研智贷通'\n" +
                    "  scenario_description: '杭研智贷通贷款申请、审批、放款全流程'\n" +
                    "  scope:\n" +
                    "    allowed: '智贷通贷款申请、贷款审批、放款流程'\n" +
                    "    denied: '理财相关业务、信用卡相关业务'\n" +
                    "  skill_routing: []\n";
            Files.writeString(govDir.resolve("planrule.yaml"), planruleYaml);

            GovernanceConfig config = GovernanceConfigLoader.load(govDir);
            assertNotNull(config.getPlanrule());

            PlanRuleConfig planrule = config.getPlanrule();
            assertEquals("杭研智贷通", planrule.getScenarioName());
            assertEquals("杭研智贷通贷款申请、审批、放款全流程", planrule.getScenarioDescription());

            assertNotNull(planrule.getScope(), "scope 应存在");
            assertEquals("智贷通贷款申请、贷款审批、放款流程", planrule.getScope().getAllowed());
            assertEquals("理财相关业务、信用卡相关业务", planrule.getScope().getDenied());

            assertNotNull(planrule.getSkillRouting(), "skillRouting 应不为 null（空列表）");
            assertTrue(planrule.getSkillRouting().isEmpty(), "skillRouting 应为空列表");
        }
    }

    /**
     * 第二节：框架+场景合并正确性
     */
    @Nested
    @DisplayName("2. 框架+场景合并正确性")
    class Merge {

        /**
         * 测试2.1：框架级默认 + hz-zhidaitong 场景级合并，
         * 验证 scope 被场景级替代式覆盖，scenario_name 生效。
         */
        @Test
        @DisplayName("2.1 框架+场景合并 — scope 替代式覆盖")
        void testMergeFrameworkAndHzZhidaitongScope() throws Exception {
            Path frameworkDir = tempDir.resolve("framework");
            Files.createDirectories(frameworkDir);
            createFrameworkConfig(frameworkDir);

            Path scenarioDir = tempDir.resolve("scenario-hz");
            Files.createDirectories(scenarioDir);
            String planruleYaml = "planrule:\n" +
                    "  scenario_name: '杭研智贷通'\n" +
                    "  scenario_description: '杭研智贷通贷款申请、审批、放款全流程'\n" +
                    "  scope:\n" +
                    "    allowed: '智贷通贷款申请、贷款审批、放款流程'\n" +
                    "    denied: '理财相关业务、信用卡相关业务'\n" +
                    "  skill_routing: []\n";
            Files.writeString(scenarioDir.resolve("planrule.yaml"), planruleYaml);
            Files.writeString(scenarioDir.resolve("actrule.yaml"), "actrule:\n");
            Files.writeString(scenarioDir.resolve("scriptconfig.yaml"), "scriptconfig:\n");

            GovernanceConfig merged = GovernanceConfigLoader.loadWithPriority(scenarioDir, frameworkDir);

            assertNotNull(merged.getPlanrule());
            PlanRuleConfig planrule = merged.getPlanrule();

            // role 继承框架级
            assertEquals("你的身份是通用动态规划智能体", planrule.getRole());
            // scenarioName 来自场景级
            assertEquals("杭研智贷通", planrule.getScenarioName());
            // scope 被场景级替代式覆盖
            assertNotNull(planrule.getScope());
            assertEquals("智贷通贷款申请、贷款审批、放款流程", planrule.getScope().getAllowed());
            assertEquals("理财相关业务、信用卡相关业务", planrule.getScope().getDenied());

            // actrule 继承框架级
            assertNotNull(merged.getActrule());
            assertEquals(50, merged.getActrule().getMaxSubtasks());
            assertTrue(merged.getActrule().getAllowedTools().contains("bash"));
        }

        /**
         * 测试2.2：回归验证 — 旧字段名 allowed_business / denied_business / scenarioName 不会被 Jackson 反序列化。
         *
         * <p>SNAKE_CASE 策略下，camelCase 的 scenarioName 会被映射为 scenarioname（不匹配 POJO）。
         * allowed_business / denied_business 完全不匹配 scope 结构。</p>
         */
        @Test
        @DisplayName("2.2 回归验证 — 旧字段名全部静默丢弃")
        void testOldFieldNamesAreSilentlyDropped() throws Exception {
            Path govDir = tempDir.resolve("old-fields");
            Files.createDirectories(govDir);

            String oldPlanruleYaml = "planrule:\n" +
                    "  scenarioName: '杭研智贷通'\n" +
                    "  scenarioDescription: '贷款全流程'\n" +
                    "  allowed_business:\n" +
                    "    - '智贷通贷款申请'\n" +
                    "    - '贷款审批'\n" +
                    "  denied_business:\n" +
                    "    - '理财相关业务'\n" +
                    "    - '信用卡相关业务'\n" +
                    "  skill_routing: []\n";
            Files.writeString(govDir.resolve("planrule.yaml"), oldPlanruleYaml);

            GovernanceConfig config = GovernanceConfigLoader.load(govDir);
            assertNotNull(config.getPlanrule());

            // scenarioName (camelCase) → SNAKE_CASE 映射为 scenarioname → 不匹配 → null
            assertNull(config.getPlanrule().getScenarioName(),
                    "camelCase 的 scenarioName 不应被 Jackson SNAKE_CASE 策略识别");

            // scope 不应被旧字段映射
            assertNull(config.getPlanrule().getScope(),
                    "allowed_business 不应映射到 scope，scope 应为 null");
        }
    }

    /**
     * 第三节：System Prompt 端到端
     */
    @Nested
    @DisplayName("3. System Prompt 端到端")
    class SystemPrompt {

        /**
         * 测试3.1：合并后的 planrule → buildSystemPromptFragment，
         * 验证生成的系统提示词中包含 hz-zhidaitong 的业务范围约束。
         */
        @Test
        @DisplayName("3.1 hz-zhidaitong 合并配置 → 系统提示词包含业务范围")
        void testHzZhidaitongSystemPromptContainsScopeConstraints() throws Exception {
            Path frameworkDir = tempDir.resolve("framework");
            Files.createDirectories(frameworkDir);
            createFrameworkConfig(frameworkDir);

            Path scenarioDir = tempDir.resolve("scenario-hz");
            Files.createDirectories(scenarioDir);
            String planruleYaml = "planrule:\n" +
                    "  scenario_name: '杭研智贷通'\n" +
                    "  scenario_description: '杭研智贷通贷款申请、审批、放款全流程'\n" +
                    "  scope:\n" +
                    "    allowed: '智贷通贷款申请、贷款审批、放款流程'\n" +
                    "    denied: '理财相关业务、信用卡相关业务'\n" +
                    "  skill_routing: []\n";
            Files.writeString(scenarioDir.resolve("planrule.yaml"), planruleYaml);
            Files.writeString(scenarioDir.resolve("actrule.yaml"), "actrule:\n");
            Files.writeString(scenarioDir.resolve("scriptconfig.yaml"), "scriptconfig:\n");

            GovernanceConfig merged = GovernanceConfigLoader.loadWithPriority(scenarioDir, frameworkDir);
            String prompt = PlanrulePromptBuilder.buildSystemPromptFragment(merged.getPlanrule());

            assertNotNull(prompt, "系统提示词不应为 null");
            assertFalse(prompt.isEmpty(), "系统提示词不应为空");

            // 场景上下文
            assertTrue(prompt.contains("**当前场景**：杭研智贷通"),
                    "系统提示词应包含场景名称");

            // scope.allowed
            assertTrue(prompt.contains("**当前支持的业务**："),
                    "系统提示词应包含 '**当前支持的业务**：' 前缀");
            assertTrue(prompt.contains("智贷通贷款申请"));
            assertTrue(prompt.contains("贷款审批"));
            assertTrue(prompt.contains("放款流程"));

            // scope.denied
            assertTrue(prompt.contains("**禁止的业务**："),
                    "系统提示词应包含 '**禁止的业务**：' 前缀");
            assertTrue(prompt.contains("理财相关业务"));
            assertTrue(prompt.contains("信用卡相关业务"));
        }

        /**
         * 测试3.2：仅 framework 加载 → 系统提示词不含业务范围
         */
        @Test
        @DisplayName("3.2 仅框架配置 → 系统提示词不含业务范围（空 scope 被跳过）")
        void testFrameworkOnlyPromptHasNoScope() throws Exception {
            Path frameworkDir = tempDir.resolve("framework-only");
            Files.createDirectories(frameworkDir);
            createFrameworkConfig(frameworkDir);

            GovernanceConfig config = GovernanceConfigLoader.load(frameworkDir);
            String prompt = PlanrulePromptBuilder.buildSystemPromptFragment(config.getPlanrule());

            assertNotNull(prompt);
            assertFalse(prompt.contains("**当前支持的业务**："),
                    "框架级 scope.allowed 为空时不应输出业务范围行");
            assertFalse(prompt.contains("**禁止的业务**："),
                    "框架级 scope.denied 为空时不应输出禁止业务行");
        }

        /**
         * 测试3.3：端到端顺序验证 — role → description → scenarioName → scenarioDescription → scope
         */
        @Test
        @DisplayName("3.3 系统提示词拼接顺序验证")
        void testHzZhidaitongPromptOrder() throws Exception {
            Path frameworkDir = tempDir.resolve("framework");
            Files.createDirectories(frameworkDir);
            createFrameworkConfig(frameworkDir);

            Path scenarioDir = tempDir.resolve("scenario-hz");
            Files.createDirectories(scenarioDir);
            String planruleYaml = "planrule:\n" +
                    "  scenario_name: '杭研智贷通'\n" +
                    "  scenario_description: '杭研智贷通贷款申请、审批、放款全流程'\n" +
                    "  scope:\n" +
                    "    allowed: '智贷通贷款申请、贷款审批、放款流程'\n" +
                    "    denied: '理财相关业务、信用卡相关业务'\n" +
                    "  skill_routing: []\n";
            Files.writeString(scenarioDir.resolve("planrule.yaml"), planruleYaml);
            Files.writeString(scenarioDir.resolve("actrule.yaml"), "actrule:\n");
            Files.writeString(scenarioDir.resolve("scriptconfig.yaml"), "scriptconfig:\n");

            GovernanceConfig merged = GovernanceConfigLoader.loadWithPriority(scenarioDir, frameworkDir);
            String prompt = PlanrulePromptBuilder.buildSystemPromptFragment(merged.getPlanrule());

            int rolePos = prompt.indexOf("# 你的身份是通用动态规划智能体");
            int descPos = prompt.indexOf("负责任务规划");
            int scenarioPos = prompt.indexOf("**当前场景**：杭研智贷通");
            int allowedPos = prompt.indexOf("**当前支持的业务**：");
            int deniedPos = prompt.indexOf("**禁止的业务**：");

            assertTrue(rolePos >= 0, "应包含 role");
            assertTrue(descPos > rolePos, "description 应在 role 之后");
            assertTrue(scenarioPos > descPos, "scenarioName 应在 description 之后");
            assertTrue(allowedPos > scenarioPos, "scope.allowed 应在 scenarioName 之后");
            assertTrue(deniedPos > allowedPos, "scope.denied 应在 scope.allowed 之后");
        }
    }

    /**
     * 第四节：与 wealth-demo 交叉验证
     */
    @Nested
    @DisplayName("4. 跨场景交叉验证")
    class CrossScenario {

        /**
         * 测试4.1：hz-zhidaitong 和 wealth-demo 各自加载，scope 互不干扰。
         */
        @Test
        @DisplayName("4.1 hz-zhidaitong 与 wealth-demo scope 互不干扰")
        void testBothScenariosLoadIndependently() throws Exception {
            Path frameworkDir = tempDir.resolve("framework");
            Files.createDirectories(frameworkDir);
            createFrameworkConfig(frameworkDir);

            // hz-zhidaitong
            Path hzDir = tempDir.resolve("hz-zhidaitong");
            Files.createDirectories(hzDir);
            String hzPlanrule = "planrule:\n" +
                    "  scenario_name: '杭研智贷通'\n" +
                    "  scenario_description: '贷款全流程'\n" +
                    "  scope:\n" +
                    "    allowed: '智贷通贷款申请、贷款审批'\n" +
                    "    denied: '理财相关业务、信用卡相关业务'\n" +
                    "  skill_routing: []\n";
            Files.writeString(hzDir.resolve("planrule.yaml"), hzPlanrule);
            Files.writeString(hzDir.resolve("actrule.yaml"), "actrule:\n");
            Files.writeString(hzDir.resolve("scriptconfig.yaml"), "scriptconfig:\n");

            // wealth-demo
            Path wdDir = tempDir.resolve("wealth-demo");
            Files.createDirectories(wdDir);
            String wdPlanrule = "planrule:\n" +
                    "  scenario_name: '理财购买'\n" +
                    "  scenario_description: '理财全流程'\n" +
                    "  scope:\n" +
                    "    allowed: '理财产品推荐、筛选、购买'\n" +
                    "    denied: '基金相关业务、股票相关业务'\n" +
                    "  skill_routing: []\n";
            Files.writeString(wdDir.resolve("planrule.yaml"), wdPlanrule);
            Files.writeString(wdDir.resolve("actrule.yaml"), "actrule:\n");
            Files.writeString(wdDir.resolve("scriptconfig.yaml"), "scriptconfig:\n");

            GovernanceConfig hzConfig = GovernanceConfigLoader.loadWithPriority(hzDir, frameworkDir);
            GovernanceConfig wdConfig = GovernanceConfigLoader.loadWithPriority(wdDir, frameworkDir);

            assertTrue(hzConfig.getPlanrule().getScope().getAllowed().contains("智贷通"));
            assertTrue(hzConfig.getPlanrule().getScope().getDenied().contains("理财相关业务"));
            assertTrue(wdConfig.getPlanrule().getScope().getAllowed().contains("理财产品推荐"));
            assertTrue(wdConfig.getPlanrule().getScope().getDenied().contains("基金相关业务"));

            assertFalse(hzConfig.getPlanrule().getScope().getAllowed().contains("理财产品推荐"),
                    "hz-zhidaitong scope 不应包含 wealth-demo 的内容");
            assertFalse(wdConfig.getPlanrule().getScope().getAllowed().contains("智贷通"),
                    "wealth-demo scope 不应包含 hz-zhidaitong 的内容");
        }
    }

    // ======================== 辅助方法 ========================

    /**
     * 创建框架级 governance 配置文件。
     * scope 使用空格字符串（模拟框架默认"无业务范围限制"）。
     */
    private void createFrameworkConfig(Path frameworkDir) throws Exception {
        String planruleYaml = "planrule:\n" +
                "  role: '你的身份是通用动态规划智能体'\n" +
                "  description: '负责任务规划、执行和结果总结。'\n" +
                "  scope:\n" +
                "    allowed: ' '\n" +
                "    denied: ' '\n";
        Files.writeString(frameworkDir.resolve("planrule.yaml"), planruleYaml);

        String actruleYaml = "actrule:\n" +
                "  max_subtasks: 50\n" +
                "  max_steps: 100\n" +
                "  skill_mode: all\n" +
                "  allowed_tools:\n" +
                "    - bash\n" +
                "    - skill_tool\n" +
                "    - call_versatile\n" +
                "    - call_mcp\n" +
                "    - ask_user\n" +
                "    - todo_create\n" +
                "    - todo_modify\n" +
                "    - todo_list\n" +
                "    - todo_get\n" +
                "    - cancel_task\n";
        Files.writeString(frameworkDir.resolve("actrule.yaml"), actruleYaml);

        String scriptconfigYaml = "scriptconfig:\n" +
                "  general_scripts:\n" +
                "    tool_start: '正在调用：{tool_name}'\n" +
                "    tool_end: '{tool_name} 执行完成'\n" +
                "  think_chunk_scripts:\n" +
                "    think_chunk_mode: fixed_script\n";
        Files.writeString(frameworkDir.resolve("scriptconfig.yaml"), scriptconfigYaml);
    }
}
