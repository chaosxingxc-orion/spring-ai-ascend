package com.huawei.ascend.edp.stream;

import com.huawei.ascend.edp.config.PlanRuleConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PlanrulePromptBuilder测试类。
 *
 * <p>测试目标：验证planrule四字段拼接逻辑的正确性</p>
 * <p>测试覆盖：</p>
 * <ul>
 *     <li>完整配置拼接测试</li>
 *     <li>部分字段缺失测试</li>
 *     <li>null配置降级测试</li>
 *     <li>空字符串字段测试</li>
 *     <li>Python版markdown_body格式对齐测试</li>
 *     <li>多行supplementaryPrompt测试</li>
 * </ul>
 */
class PlanrulePromptBuilderTest {

    /**
     * 测试用例1：完整配置拼接测试。
     *
     * <p>验证planrule四字段完整拼接逻辑</p>
     */
    @Test
    void testBuildSystemPromptFragmentWithFullConfig() {
        PlanRuleConfig planrule = new PlanRuleConfig();
        planrule.setRole("通用动态规划智能体角色定位");
        planrule.setDescription("负责任务规划、执行和结果总结的智能助手");

        PlanRuleConfig.Scope scope = new PlanRuleConfig.Scope();
        scope.setAllowed("理财产品推荐、筛选、购买");
        scope.setDenied("股票交易、期货交易");
        planrule.setScope(scope);

        planrule.setSupplementaryPrompt("## 二、行为约束\n\n行为约束规则：\n1. 当用户表达修改意图，暂停当前任务，重新规划");

        String result = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);

        // 验证拼接结果包含所有字段
        assertTrue(result.contains("# 通用动态规划智能体角色定位"));
        assertTrue(result.contains("负责任务规划、执行和结果总结的智能助手"));
        assertTrue(result.contains("**当前支持的业务**：理财产品推荐、筛选、购买"));
        assertTrue(result.contains("**禁止的业务**：股票交易、期货交易"));
        // supplementaryPrompt直接拼接，不加固定标题
        assertTrue(result.contains("## 二、行为约束\n\n行为约束规则：\n1. 当用户表达修改意图，暂停当前任务，重新规划"));
    }

    /**
     * 测试用例2：部分字段缺失测试。
     *
     * <p>验证planrule部分字段缺失时的降级处理</p>
     */
    @Test
    void testBuildSystemPromptFragmentWithPartialConfig() {
        PlanRuleConfig planrule = new PlanRuleConfig();
        planrule.setRole("理财推荐智能体");
        planrule.setDescription(null);  // description缺失

        PlanRuleConfig.Scope scope = new PlanRuleConfig.Scope();
        scope.setAllowed("理财产品推荐");
        scope.setDenied(null);  // denied缺失
        planrule.setScope(scope);

        planrule.setSupplementaryPrompt(null);  // supplementaryPrompt缺失

        String result = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);

        // 验证拼接结果只包含存在的字段
        assertTrue(result.contains("# 理财推荐智能体"));
        assertFalse(result.contains("负责任务规划"));  // description缺失，不应包含
        assertTrue(result.contains("**当前支持的业务**：理财产品推荐"));
        assertFalse(result.contains("**禁止的业务**"));  // denied缺失，不应包含
        // supplementaryPrompt缺失，不应包含任何补充内容
        assertFalse(result.contains("行为约束"));  // supplementaryPrompt缺失，不应包含
    }

    /**
     * 测试用例3：null配置降级测试。
     *
     * <p>验证planrule配置为null时的默认提示词返回</p>
     */
    @Test
    void testBuildSystemPromptFragmentWithNullConfig() {
        PlanRuleConfig planrule = null;

        String result = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);

        // 验证返回默认提示词
        assertEquals("# 通用动态规划智能体\n\n你是一个智能助手，负责任务规划、执行和结果总结。", result);
    }

    /**
     * 测试用例4：空字符串字段测试。
     *
     * <p>验证planrule字段为空字符串时的跳过处理</p>
     */
    @Test
    void testBuildSystemPromptFragmentWithEmptyStrings() {
        PlanRuleConfig planrule = new PlanRuleConfig();
        planrule.setRole("  ");  // 空字符串（只有空格）
        planrule.setDescription("");  // 空字符串

        PlanRuleConfig.Scope scope = new PlanRuleConfig.Scope();
        scope.setAllowed(" ");  // 空格字符串（默认配置标识）
        scope.setDenied("");  // 空字符串
        planrule.setScope(scope);

        planrule.setSupplementaryPrompt("");  // 空字符串

        String result = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);

        // 验证空字符串字段都被跳过，返回空字符串
        assertTrue(result.isEmpty());
    }

    /**
     * 测试用例5：默认配置加载测试。
     *
     * <p>验证governance配置文件加载后的拼接结果（对齐planrule.yaml默认配置）</p>
     */
    @Test
    void testBuildSystemPromptFragmentWithDefaultGovernanceConfig() {
        PlanRuleConfig planrule = new PlanRuleConfig();
        planrule.setRole("通用动态规划智能体角色定位");
        planrule.setDescription("负责任务规划、执行和结果总结的智能助手");

        PlanRuleConfig.Scope scope = new PlanRuleConfig.Scope();
        scope.setAllowed(" ");  // 默认配置：空格字符串（无业务范围限制）
        scope.setDenied(" ");  // 默认配置：空格字符串（无禁止业务）
        planrule.setScope(scope);

        // supplementaryPrompt内容灵活，可以是行为约束、使用说明、注意事项等
        planrule.setSupplementaryPrompt("## 二、行为约束\n\n行为约束：\n1. 当用户表达修改意图，暂停当前任务，重新规划\n2. 当遇到以下情况，**调用 `ask_user` 工具**暂停执行，等待用户补充：\n- 关键参数缺失\n- 敏感操作需用户确认\n- 用户输入有歧义");

        String result = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);

        // 验证拼接结果符合planrule.yaml默认配置
        assertTrue(result.contains("# 通用动态规划智能体角色定位"));
        assertTrue(result.contains("负责任务规划、执行和结果总结的智能助手"));
        assertFalse(result.contains("**当前支持的业务**"));  // allowed为" "，应该被跳过
        assertFalse(result.contains("**禁止的业务**"));  // denied为" "，应该被跳过
        // supplementaryPrompt直接拼接（包含自己的章节标题）
        assertTrue(result.contains("## 二、行为约束\n\n行为约束："));
        assertTrue(result.contains("关键参数缺失"));
        assertTrue(result.contains("敏感操作需用户确认"));
    }

    /**
     * 测试用例6：多行supplementaryPrompt测试。
     *
     * <p>验证多行supplementaryPrompt的正确拼接（内容灵活，可以是行为约束、使用说明等）</p>
     */
    @Test
    void testBuildSystemPromptFragmentWithMultilineSupplementaryPrompt() {
        PlanRuleConfig planrule = new PlanRuleConfig();
        // supplementaryPrompt内容灵活，这里测试"使用说明"示例（不加"## 二、行为约束"固定标题）
        planrule.setSupplementaryPrompt(
            "## 三、使用说明\n\n" +
            "工具使用规则：\n" +
            "1. 当用户表达修改意图，暂停当前任务，重新规划\n" +
            "2. 当遇到以下情况，调用 ask_user 工具暂停执行：\n" +
            "   - 关键参数缺失\n" +
            "   - 敏感操作需用户确认"
        );

        String result = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);

        // 验证多行supplementaryPrompt直接拼接（不加固定标题）
        assertTrue(result.contains("## 三、使用说明\n\n"));  // supplementaryPrompt自己的章节标题
        assertTrue(result.contains("工具使用规则："));
        assertTrue(result.contains("1. 当用户表达修改意图，暂停当前任务，重新规划"));
        assertTrue(result.contains("2. 当遇到以下情况，调用 ask_user 工具暂停执行："));
        assertTrue(result.contains("- 关键参数缺失"));
        assertTrue(result.contains("- 敏感操作需用户确认"));
    }

    /**
     * 测试用例7：与Python版markdown_body格式对齐测试。
     *
     * <p>验证Java版拼接结果与Python版markdown_body格式对齐</p>
     */
    @Test
    void testAlignmentWithPythonMarkdownBodyFormat() {
        PlanRuleConfig planrule = new PlanRuleConfig();
        planrule.setRole("EDP 动态规划智能体");
        planrule.setDescription("你是一名企业级动态规划智能体，使用思考—规划—执行—观察—反思循环处理用户请求");

        PlanRuleConfig.Scope scope = new PlanRuleConfig.Scope();
        scope.setAllowed("理财产品推荐、筛选、购买");
        scope.setDenied("股票交易、期货交易");
        planrule.setScope(scope);

        // supplementaryPrompt内容灵活，这里使用"行为约束"示例（包含自己的章节标题）
        planrule.setSupplementaryPrompt("## 二、行为约束\n\n行为约束：\n1. 工具执行失败时，在 thought 中记录原因");

        String result = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);

        // 验证格式对齐Python版markdown_body
        // 标题格式：# [role]
        assertTrue(result.startsWith("# EDP 动态规划智能体"));

        // supplementaryPrompt直接拼接，可以是任意章节标题（这里包含自己的"## 二、行为约束"）
        assertTrue(result.contains("## 二、行为约束\n\n行为约束："));

        // 业务范围格式：**当前支持的业务**：
        assertTrue(result.contains("**当前支持的业务**：理财产品推荐、筛选、购买"));
        assertTrue(result.contains("**禁止的业务**：股票交易、期货交易"));
    }

    /**
     * 测试用例8：场景名称和描述拼接（完整的场景上下文）。
     *
     * <p>验证 scenarioName 和 scenarioDescription 按正确顺序拼接在 description 之后、scope 之前</p>
     */
    @Test
    void testBuildSystemPromptWithScenarioNameAndDescription() {
        PlanRuleConfig planrule = new PlanRuleConfig();
        planrule.setRole("你的身份是通用动态规划智能体");
        planrule.setDescription("负责任务规划、执行和结果总结。");
        planrule.setScenarioName("理财购买");
        planrule.setScenarioDescription("理财产品推荐、筛选、购买全流程");

        PlanRuleConfig.Scope scope = new PlanRuleConfig.Scope();
        scope.setAllowed("理财产品推荐、筛选、购买");
        scope.setDenied("基金相关业务");
        planrule.setScope(scope);

        String result = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);

        // 验证拼接顺序：role → description → scenarioName → scenarioDescription → scope
        int rolePos = result.indexOf("# 你的身份是通用动态规划智能体");
        int descPos = result.indexOf("负责任务规划、执行和结果总结。");
        int scenarioNamePos = result.indexOf("**当前场景**：理财购买");
        int scenarioDescPos = result.indexOf("理财产品推荐、筛选、购买全流程");
        int scopePos = result.indexOf("**当前支持的业务**");

        assertTrue(rolePos < descPos, "role 应在 description 之前");
        assertTrue(descPos < scenarioNamePos, "description 应在 scenarioName 之前");
        assertTrue(scenarioNamePos < scenarioDescPos, "scenarioName 应在 scenarioDescription 之前");
        assertTrue(scenarioDescPos < scopePos, "scenarioDescription 应在 scope 之前");

        // 验证内容
        assertTrue(result.contains("**当前场景**：理财购买"));
        assertTrue(result.contains("理财产品推荐、筛选、购买全流程"));
    }

    /**
     * 测试用例9：仅场景名称无描述时的拼接。
     *
     * <p>验证只有 scenarioName 没有 scenarioDescription 时，格式仍然正确（有空行分隔）</p>
     */
    @Test
    void testBuildSystemPromptWithScenarioNameOnly() {
        PlanRuleConfig planrule = new PlanRuleConfig();
        planrule.setScenarioName("杭研智贷通");
        // scenarioDescription 为 null
        PlanRuleConfig.Scope scope = new PlanRuleConfig.Scope();
        scope.setAllowed("贷款审批");
        planrule.setScope(scope);

        String result = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);

        // 验证：只输出场景名称，有空行与后面分隔
        assertTrue(result.contains("**当前场景**：杭研智贷通"));
        assertFalse(result.contains("全流程"), "scenarioDescription 为 null 时不应出现描述内容");

        // 验证：scenarioName 在 scope.allowed 之前
        int scenarioNamePos = result.indexOf("**当前场景**：杭研智贷通");
        int scopePos = result.indexOf("**当前支持的业务**");
        assertTrue(scenarioNamePos < scopePos, "scenarioName 应在 scope 之前");
    }

    /**
     * 测试用例10：无场景名称和描述时的拼接（向后兼容）。
     *
     * <p>验证未设置 scenarioName/scenarioDescription 时，系统提示词不包含场景上下文行</p>
     */
    @Test
    void testBuildSystemPromptWithoutScenarioContext() {
        PlanRuleConfig planrule = new PlanRuleConfig();
        planrule.setRole("通用动态规划智能体角色定位");
        PlanRuleConfig.Scope scope = new PlanRuleConfig.Scope();
        scope.setAllowed("理财产品推荐");
        planrule.setScope(scope);

        String result = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);

        // 验证：不应包含场景上下文
        assertFalse(result.contains("**当前场景**"), "无 scenarioName 时不应出现场景名");
        // 验证：scope 正常输出
        assertTrue(result.contains("**当前支持的业务**：理财产品推荐"));
    }

    /**
     * 测试用例11：完整的 wealth-demo 场景提示词拼接（端到端）。
     *
     * <p>模拟 wealth-demo 场景合并后的 planrule 配置，验证完整拼接结果</p>
     */
    @Test
    void testBuildSystemPromptForWealthDemoScenario() {
        PlanRuleConfig planrule = new PlanRuleConfig();
        planrule.setRole("你的身份是通用动态规划智能体");
        planrule.setDescription("你的核心职责是负责任务规划、执行和结果总结。");
        planrule.setScenarioName("理财购买");
        planrule.setScenarioDescription("理财产品推荐、筛选、购买全流程");

        PlanRuleConfig.Scope scope = new PlanRuleConfig.Scope();
        scope.setAllowed("理财产品推荐、筛选、购买、银行账户余额查询、银行账户间转账");
        scope.setDenied("基金相关业务、股票相关业务、保险相关业务");
        planrule.setScope(scope);

        planrule.setSupplementaryPrompt("你采用「规划—执行—观察—反思」ReAct 循环处理用户请求。");

        String result = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);

        // 验证完整拼接顺序（使用顺序断言，避免换行符精确匹配的脆弱性）
        assertTrue(result.startsWith("# 你的身份是通用动态规划智能体"), "应以 role 开头");

        int roleEnd = result.indexOf("你的核心职责");
        int scenarioStart = result.indexOf("**当前场景**：理财购买");
        int scopeStart = result.indexOf("**当前支持的业务**");
        int suppStart = result.indexOf("你采用「规划—执行—观察—反思」");

        assertTrue(roleEnd > 0, "应包含 description");
        assertTrue(roleEnd < scenarioStart, "description 应在 scenarioName 之前");
        assertTrue(scenarioStart < scopeStart, "scenarioName 应在 scope 之前");
        assertTrue(scopeStart < suppStart, "scope 应在 supplementaryPrompt 之前");

        // 验证关键内容存在
        assertTrue(result.contains("**当前场景**：理财购买"));
        assertTrue(result.contains("理财产品推荐、筛选、购买全流程"));
        assertTrue(result.contains("理财产品推荐、筛选、购买、银行账户余额查询、银行账户间转账"));
        assertTrue(result.contains("基金相关业务、股票相关业务、保险相关业务"));
        assertTrue(result.contains("你采用「规划—执行—观察—反思」ReAct 循环处理用户请求。"));
    }

    /**
     * 测试用例12：skillRouting 注入和顺序验证。
     *
     * <p>验证 skillRouting 在 scope 之后、supplementaryPrompt 之前注入，格式正确</p>
     */
    @Test
    void testBuildSystemPromptWithSkillRouting() {
        PlanRuleConfig planrule = new PlanRuleConfig();
        planrule.setRole("你的身份是通用动态规划智能体");
        planrule.setDescription("负责任务规划、执行和结果总结。");

        PlanRuleConfig.Scope scope = new PlanRuleConfig.Scope();
        scope.setAllowed("理财产品推荐");
        scope.setDenied("基金相关业务");
        planrule.setScope(scope);

        List<PlanRuleConfig.SkillRoute> routes = new ArrayList<>();
        PlanRuleConfig.SkillRoute r1 = new PlanRuleConfig.SkillRoute();
        r1.setTrigger("用户首次请求推荐理财产品");
        r1.setSkill("product_recommend_skill");
        r1.setPriority(1);
        routes.add(r1);

        PlanRuleConfig.SkillRoute r2 = new PlanRuleConfig.SkillRoute();
        r2.setTrigger("用户从推荐结果中选择产品");
        r2.setSkill("product_select_skill");
        r2.setPriority(2);
        routes.add(r2);

        planrule.setSkillRouting(routes);
        planrule.setSupplementaryPrompt("你采用ReAct循环。");

        String result = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);

        // 验证拼接顺序：scope → skillRouting → supplementaryPrompt
        int scopePos = result.indexOf("**禁止的业务**");
        int skillRoutingPos = result.indexOf("**Skill 路由**：");
        int suppPos = result.indexOf("你采用ReAct循环。");

        assertTrue(scopePos < skillRoutingPos, "scope 应在 skillRouting 之前");
        assertTrue(skillRoutingPos < suppPos, "skillRouting 应在 supplementaryPrompt 之前");

        // 验证内容格式
        assertTrue(result.contains("**Skill 路由**："));
        assertTrue(result.contains("- 用户首次请求推荐理财产品 → product_recommend_skill（priority=1）"));
        assertTrue(result.contains("- 用户从推荐结果中选择产品 → product_select_skill（priority=2）"));
    }

    /**
     * 测试用例13：skillRouting 为空或 null 时不注入。
     *
     * <p>验证 skillRouting 为空列表或 null 时，系统提示词不包含 "**Skill 路由**："</p>
     */
    @Test
    void testBuildSystemPromptWithEmptyOrNullSkillRouting() {
        PlanRuleConfig planrule = new PlanRuleConfig();
        planrule.setRole("你的身份是通用动态规划智能体");
        PlanRuleConfig.Scope scope = new PlanRuleConfig.Scope();
        scope.setAllowed("理财产品推荐");
        planrule.setScope(scope);

        // skillRouting 为 null
        String resultNull = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);
        assertFalse(resultNull.contains("**Skill 路由**"), "skillRouting 为 null 时不应注入 Skill 路由");

        // skillRouting 为空列表
        planrule.setSkillRouting(new ArrayList<>());
        String resultEmpty = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);
        assertFalse(resultEmpty.contains("**Skill 路由**"), "skillRouting 为空列表时不应注入 Skill 路由");
    }

    /**
     * 测试用例14：wealth-demo 场景完整拼接（含 skillRouting）。
     *
     * <p>模拟 wealth-demo 场景合并后的 planrule 配置，包含 skillRouting 的完整端到端验证</p>
     */
    @Test
    void testBuildSystemPromptForWealthDemoWithSkillRouting() {
        PlanRuleConfig planrule = new PlanRuleConfig();
        planrule.setRole("你的身份是通用动态规划智能体");
        planrule.setDescription("你的核心职责是负责任务规划、执行和结果总结。");
        planrule.setScenarioName("理财购买");
        planrule.setScenarioDescription("理财产品推荐、筛选、购买全流程");

        PlanRuleConfig.Scope scope = new PlanRuleConfig.Scope();
        scope.setAllowed("理财产品推荐、筛选、购买、银行账户余额查询、银行账户间转账");
        scope.setDenied("基金相关业务、股票相关业务、保险相关业务");
        planrule.setScope(scope);

        // 模拟 wealth-demo 的 4 条 skill_routing
        List<PlanRuleConfig.SkillRoute> routes = new ArrayList<>();
        PlanRuleConfig.SkillRoute r1 = new PlanRuleConfig.SkillRoute();
        r1.setTrigger("用户首次请求推荐理财产品");
        r1.setSkill("product_recommend_skill");
        r1.setPriority(1);
        routes.add(r1);
        PlanRuleConfig.SkillRoute r2 = new PlanRuleConfig.SkillRoute();
        r2.setTrigger("用户从推荐结果中选择产品");
        r2.setSkill("product_select_skill");
        r2.setPriority(2);
        routes.add(r2);
        PlanRuleConfig.SkillRoute r3 = new PlanRuleConfig.SkillRoute();
        r3.setTrigger("用户确认购买或需要资金筹划");
        r3.setSkill("fund_planning_skill");
        r3.setPriority(2);
        routes.add(r3);
        PlanRuleConfig.SkillRoute r4 = new PlanRuleConfig.SkillRoute();
        r4.setTrigger("用户请求换一批、追加筛选条件");
        r4.setSkill("interact_finance_rec_skill");
        r4.setPriority(1);
        routes.add(r4);
        planrule.setSkillRouting(routes);

        planrule.setSupplementaryPrompt("你采用「规划—执行—观察—反思」ReAct 循环处理用户请求。");

        String result = PlanrulePromptBuilder.buildSystemPromptFragment(planrule);

        // 验证完整拼接顺序：role → description → scenarioName → scenarioDescription → scope → skillRouting → supplementaryPrompt
        int roleEnd = result.indexOf("你的核心职责");
        int scenarioStart = result.indexOf("**当前场景**：理财购买");
        int scopeStart = result.indexOf("**当前支持的业务**");
        int scopeEnd = result.indexOf("基金相关业务、股票相关业务、保险相关业务");
        int skillRoutingStart = result.indexOf("**Skill 路由**：");
        int suppStart = result.indexOf("你采用「规划—执行—观察—反思」");

        assertTrue(roleEnd > 0, "应包含 description");
        assertTrue(roleEnd < scenarioStart, "description 应在 scenarioName 之前");
        assertTrue(scenarioStart < scopeStart, "scenarioName 应在 scope 之前");
        assertTrue(scopeStart < scopeEnd, "scope 内容应在 scope 开头之后");
        assertTrue(scopeEnd < skillRoutingStart, "scope 末尾应在 skillRouting 之前");
        assertTrue(skillRoutingStart < suppStart, "skillRouting 应在 supplementaryPrompt 之前");

        // 验证 skillRouting 内容
        assertTrue(result.contains("- 用户首次请求推荐理财产品 → product_recommend_skill（priority=1）"));
        assertTrue(result.contains("- 用户从推荐结果中选择产品 → product_select_skill（priority=2）"));
        assertTrue(result.contains("- 用户确认购买或需要资金筹划 → fund_planning_skill（priority=2）"));
        assertTrue(result.contains("- 用户请求换一批、追加筛选条件 → interact_finance_rec_skill（priority=1）"));

        // 验证其他内容仍然存在
        assertTrue(result.startsWith("# 你的身份是通用动态规划智能体"));
        assertTrue(result.contains("理财产品推荐、筛选、购买全流程"));
        assertTrue(result.contains("基金相关业务、股票相关业务、保险相关业务"));
        assertTrue(result.contains("你采用「规划—执行—观察—反思」ReAct 循环处理用户请求。"));
    }
}