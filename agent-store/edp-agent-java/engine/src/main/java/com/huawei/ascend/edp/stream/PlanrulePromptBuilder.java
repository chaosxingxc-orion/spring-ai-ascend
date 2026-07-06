package com.huawei.ascend.edp.stream;

import com.huawei.ascend.edp.config.PlanRuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Planrule系统提示词拼接器。
 *
 * <p>职责：拼接planrule的4个字段生成系统提示词片段，替代Python版的markdown_body注入方式</p>
 * <p>对应Python版：_agent_rule.markdown_body（一到五章节）</p>
 * <p>拼接顺序：role → description → scope → supplementaryPrompt</p>
 *
 * <p>Python版系统提示词拼接方式：</p>
 * <pre>
 * system_prompt = _agent_rule.markdown_body  // 第一部分：角色定义、职责边界、行为约束
 * system_prompt = f"{system_prompt.strip()}\n\n{build_system_prompt().strip()}"  // 第二部分：工具说明
 * </pre>
 *
 * <p>Java版对应关系：</p>
 * <ul>
 *     <li>Python版markdown_body → PlanrulePromptBuilder.buildSystemPromptFragment()</li>
 *     <li>Python版build_system_prompt()返回内容 → ScenarioPromptBuilder.BASE_PROMPT</li>
 * </ul>
 *
 * <p>Role和description直接拼接，业务范围无数字章节标题，各字段之间用空行分隔。</p>
 */
public class PlanrulePromptBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanrulePromptBuilder.class);

    /**
     * 拼接planrule配置生成系统提示词片段。
     *
     * <p>拼接顺序：role → description → scope → supplementaryPrompt</p>
     * <p>摒弃数字章节标题，改用空行分隔各字段。</p>
     *
     * @param planrule planrule配置，null时返回默认提示词（降级处理）
     * @return 系统提示词片段（对应Python版的markdown_body）
     */
    public static String buildSystemPromptFragment(PlanRuleConfig planrule) {
        if (planrule == null) {
            LOGGER.warn("Planrule is null, using default system prompt");
            return getDefaultSystemPrompt();
        }

        LOGGER.info("PlanrulePromptBuilder: role=[{}], description=[{}], supplementaryPrompt=[{}], scope.allowed=[{}], scope.denied=[{}]",
                planrule.getRole(),
                planrule.getDescription(),
                planrule.getSupplementaryPrompt(),
                planrule.getScope() != null ? planrule.getScope().getAllowed() : "null",
                planrule.getScope() != null ? planrule.getScope().getDenied() : "null");

        StringBuilder sb = new StringBuilder();

        // 1. 角色定义（role字段）
        if (isNotEmpty(planrule.getRole())) {
            sb.append("# ").append(planrule.getRole()).append("\n\n");
        }

        // 2. 角色描述（description字段）
        if (isNotEmpty(planrule.getDescription())) {
            sb.append(planrule.getDescription()).append("\n\n");
        }

        // 3. 场景上下文（scenarioName + scenarioDescription，仅场景模式有值）
        if (isNotEmpty(planrule.getScenarioName())) {
            sb.append("**当前场景**：").append(planrule.getScenarioName()).append("\n");
        }
        if (isNotEmpty(planrule.getScenarioDescription())) {
            sb.append(planrule.getScenarioDescription()).append("\n\n");
        } else if (isNotEmpty(planrule.getScenarioName())) {
            // scenarioDescription 为空时也保证空行分隔
            sb.append("\n");
        }

        // 4. 业务范围（scope字段）
        PlanRuleConfig.Scope scope = planrule.getScope();
        if (scope != null) {
            boolean hasScopeContent = false;
            StringBuilder scopeSb = new StringBuilder();

            // allowed字段：非空且非" "时才拼接（" "表示默认配置无业务范围限制）
            if (isNotEmpty(scope.getAllowed()) && !scope.getAllowed().equals(" ")) {
                scopeSb.append("**当前支持的业务**：").append(scope.getAllowed()).append("\n");
                hasScopeContent = true;
            }

            // denied字段：非空且非" "时才拼接（" "表示默认配置无禁止业务）
            if (isNotEmpty(scope.getDenied()) && !scope.getDenied().equals(" ")) {
                scopeSb.append("**禁止的业务**：").append(scope.getDenied()).append("\n");
                hasScopeContent = true;
            }

            // 只有当scope中至少有一个字段非空时，才添加scope内容
            if (hasScopeContent) {
                sb.append("\n");
                sb.append(scopeSb);
            }
        }

        // 5. Skill路由（skillRouting字段）- 场景级，框架默认无值
        java.util.List<PlanRuleConfig.SkillRoute> skillRouting = planrule.getSkillRouting();
        if (skillRouting != null && !skillRouting.isEmpty()) {
            sb.append("\n**Skill 路由**：\n");
            for (PlanRuleConfig.SkillRoute r : skillRouting) {
                sb.append("- ").append(r.getTrigger())
                  .append(" → ").append(r.getSkill())
                  .append("（priority=").append(r.getPriority()).append("）\n");
            }
        }

        // 6. 补充提示词（supplementaryPrompt字段）- 直接拼接，内容灵活（可以是行为约束、使用说明、注意事项等）
        if (isNotEmpty(planrule.getSupplementaryPrompt())) {
            sb.append(planrule.getSupplementaryPrompt()).append("\n");
        }

        String result = sb.toString().trim();
        LOGGER.info("PlanrulePromptBuilder: final fragment length={}, content=\n{}", result.length(), result);
        return result;
    }

    /**
     * 默认系统提示词（降级提示词）。
     *
     * <p>降级场景：配置加载失败、文件损坏、planrule为null等异常情况</p>
     * <p>内容对齐planrule.yaml默认配置：通用动态规划智能体角色定位</p>
     */
    private static String getDefaultSystemPrompt() {
        return "# 通用动态规划智能体\n\n你是一个智能助手，负责任务规划、执行和结果总结。";
    }

    /**
     * 判断字符串是否非空（非null且非空字符串）。
     *
     * @param str 待判断字符串
     * @return true表示非空，false表示null或空字符串
     */
    private static boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }
}