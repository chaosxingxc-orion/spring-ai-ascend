package com.huawei.ascend.edp.config;

/**
 * Governance配置聚合模型。
 *
 * <p>定位：聚合三个治理域配置（planrule、actrule、scriptconfig）</p>
 * <p>作用：</p>
 * <ul>
 *     <li>统一管理三个治理域配置</li>
 *     <li>提供配置继承覆盖机制</li>
 *     <li>支持场景级配置覆盖框架级配置</li>
 * </ul>
 *
 * <p>继承覆盖规则：</p>
 * <ul>
 *     <li>planrule.scope字段：替代式覆盖（完全覆盖）</li>
 *     <li>planrule.supplementaryPrompt字段：替代式覆盖</li>
 *     <li>actrule.maxSubtasks等参数字段：继承式覆盖（只写差异）</li>
 *     <li>scriptconfig.summary字段：替代式覆盖</li>
 *     <li>scriptconfig.generalScripts字段：替代式覆盖</li>
 * </ul>
 */
public class GovernanceConfig {

    /** 身份域配置（planrule.yaml）。 */
    private PlanRuleConfig planrule;

    /** 规划域+执行域配置（actrule.yaml）。 */
    private ActRuleConfig actrule;

    /** 交互域配置（scriptconfig.yaml）。 */
    private ScriptConfig scriptconfig;

    public PlanRuleConfig getPlanrule() { return planrule; }
    public void setPlanrule(PlanRuleConfig planrule) { this.planrule = planrule; }

    public ActRuleConfig getActrule() { return actrule; }
    public void setActrule(ActRuleConfig actrule) { this.actrule = actrule; }

    public ScriptConfig getScriptconfig() { return scriptconfig; }
    public void setScriptconfig(ScriptConfig scriptconfig) { this.scriptconfig = scriptconfig; }

    /**
     * 合并场景级配置（场景级覆盖框架级）。
     *
     * <p>继承覆盖规则：字段级别的继承覆盖，不是文件级别的完全覆盖。</p>
     *
     * @param scenarioConfig 场景级GovernanceConfig
     */
    public void mergeScenarioConfig(GovernanceConfig scenarioConfig) {
        if (scenarioConfig == null) {
            return;
        }

        // 合并planrule配置
        if (scenarioConfig.getPlanrule() != null) {
            mergePlanrule(scenarioConfig.getPlanrule());
        }

        // 合并actrule配置
        if (scenarioConfig.getActrule() != null) {
            mergeActrule(scenarioConfig.getActrule());
        }

        // 合并scriptconfig配置
        if (scenarioConfig.getScriptconfig() != null) {
            mergeScriptconfig(scenarioConfig.getScriptconfig());
        }
    }

    /**
     * 合并planrule配置（替代式覆盖）。
     */
    private void mergePlanrule(PlanRuleConfig scenarioPlanrule) {
        if (this.planrule == null) {
            this.planrule = scenarioPlanrule;
            return;
        }

        // role: 继承式覆盖（只写差异）
        if (scenarioPlanrule.getRole() != null) {
            this.planrule.setRole(scenarioPlanrule.getRole());
        }

        // description: 继承式覆盖
        if (scenarioPlanrule.getDescription() != null) {
            this.planrule.setDescription(scenarioPlanrule.getDescription());
        }

        // scenarioName: 继承式覆盖（仅场景级配置，框架默认无值）
        if (scenarioPlanrule.getScenarioName() != null) {
            this.planrule.setScenarioName(scenarioPlanrule.getScenarioName());
        }

        // scenarioDescription: 继承式覆盖（仅场景级配置，框架默认无值）
        if (scenarioPlanrule.getScenarioDescription() != null) {
            this.planrule.setScenarioDescription(scenarioPlanrule.getScenarioDescription());
        }

        // scope: 替代式覆盖（完全覆盖）
        if (scenarioPlanrule.getScope() != null) {
            this.planrule.setScope(scenarioPlanrule.getScope());
        }

        // supplementaryPrompt: 替代式覆盖
        if (scenarioPlanrule.getSupplementaryPrompt() != null) {
            this.planrule.setSupplementaryPrompt(scenarioPlanrule.getSupplementaryPrompt());
        }

        // skillRouting: 继承式覆盖（框架默认无值，场景配置即最终值）
        if (scenarioPlanrule.getSkillRouting() != null) {
            this.planrule.setSkillRouting(scenarioPlanrule.getSkillRouting());
        }
    }

    /**
     * 合并actrule配置（继承式覆盖）。
     */
    private void mergeActrule(ActRuleConfig scenarioActrule) {
        if (this.actrule == null) {
            this.actrule = scenarioActrule;
            return;
        }

        // 继承式覆盖：只写差异，未覆盖字段自动继承Default值
        if (scenarioActrule.getMaxSubtasks() != null) {
            this.actrule.setMaxSubtasks(scenarioActrule.getMaxSubtasks());
        }
        if (scenarioActrule.getMaxSteps() != null) {
            this.actrule.setMaxSteps(scenarioActrule.getMaxSteps());
        }
        if (scenarioActrule.getAllowedTools() != null) {
            // 叠加合并：框架工具 + 场景扩展工具，去重但保持顺序
            java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(
                    this.actrule.getAllowedTools() != null ? this.actrule.getAllowedTools() : java.util.List.of());
            merged.addAll(scenarioActrule.getAllowedTools());
            this.actrule.setAllowedTools(new java.util.ArrayList<>(merged));
        }
        if (scenarioActrule.getEnableTaskLoop() != null) {
            this.actrule.setEnableTaskLoop(scenarioActrule.getEnableTaskLoop());
        }
        if (scenarioActrule.getSkillMode() != null) {
            this.actrule.setSkillMode(scenarioActrule.getSkillMode());
        }
        if (scenarioActrule.getToolLimits() != null) {
            this.actrule.setToolLimits(scenarioActrule.getToolLimits());
        }

        // todolistEntries: 替代式覆盖（场景提供完整定义，框架默认无值）
        if (scenarioActrule.getTodolistEntries() != null) {
            this.actrule.setTodolistEntries(scenarioActrule.getTodolistEntries());
        }
        // todolistDynamicPaths: 替代式覆盖
        if (scenarioActrule.getTodolistDynamicPaths() != null) {
            this.actrule.setTodolistDynamicPaths(scenarioActrule.getTodolistDynamicPaths());
        }
    }

    /**
     * 合成scriptconfig配置（逐字段继承式覆盖）。
     */
    private void mergeScriptconfig(ScriptConfig scenarioScriptconfig) {
        if (this.scriptconfig == null) {
            this.scriptconfig = scenarioScriptconfig;
            return;
        }

        // generalScripts: 继承式覆盖（逐字段合并，未被场景覆盖的字段继承框架默认值）
        if (scenarioScriptconfig.getGeneralScripts() != null) {
            mergeGeneralScripts(scenarioScriptconfig.getGeneralScripts());
        }

        // thinkChunkScripts: 继承式覆盖
        if (scenarioScriptconfig.getThinkChunkScripts() != null) {
            mergeThinkChunkScripts(scenarioScriptconfig.getThinkChunkScripts());
        }

        // summary: 替代式覆盖
        if (scenarioScriptconfig.getSummary() != null) {
            this.scriptconfig.setSummary(scenarioScriptconfig.getSummary());
        }

        // askUserConfirm: 继承式覆盖
        if (scenarioScriptconfig.getAskUserConfirm() != null) {
            mergeAskUserConfirm(scenarioScriptconfig.getAskUserConfirm());
        }
    }

    /**
     * 合并 askUserConfirm 配置（继承式覆盖，逐字段合并）。
     */
    private void mergeAskUserConfirm(ScriptConfig.AskUserConfirm scenarioConfirm) {
        if (this.scriptconfig.getAskUserConfirm() == null) {
            this.scriptconfig.setAskUserConfirm(new ScriptConfig.AskUserConfirm());
        }
        ScriptConfig.AskUserConfirm target = this.scriptconfig.getAskUserConfirm();

        if (scenarioConfirm.getPurchaseConfirm() != null) {
            target.setPurchaseConfirm(scenarioConfirm.getPurchaseConfirm());
        }
        if (scenarioConfirm.getCancelConfirm() != null) {
            target.setCancelConfirm(scenarioConfirm.getCancelConfirm());
        }
    }

    /**
     * 合并 generalScripts 配置（继承式覆盖，逐字段合并）。
     */
    private void mergeGeneralScripts(ScriptConfig.GeneralScripts scenarioScripts) {
        if (this.scriptconfig.getGeneralScripts() == null) {
            this.scriptconfig.setGeneralScripts(new ScriptConfig.GeneralScripts());
        }
        ScriptConfig.GeneralScripts target = this.scriptconfig.getGeneralScripts();

        if (scenarioScripts.getToolStart() != null) {
            target.setToolStart(scenarioScripts.getToolStart());
        }
        if (scenarioScripts.getToolEnd() != null) {
            target.setToolEnd(scenarioScripts.getToolEnd());
        }
        if (scenarioScripts.getTodoStart() != null) {
            target.setTodoStart(scenarioScripts.getTodoStart());
        }
        if (scenarioScripts.getTodoEnd() != null) {
            target.setTodoEnd(scenarioScripts.getTodoEnd());
        }
        if (scenarioScripts.getTodolistStart() != null) {
            target.setTodolistStart(scenarioScripts.getTodolistStart());
        }
        if (scenarioScripts.getTodolistEnd() != null) {
            target.setTodolistEnd(scenarioScripts.getTodolistEnd());
        }
        if (scenarioScripts.getInterruptStart() != null) {
            target.setInterruptStart(scenarioScripts.getInterruptStart());
        }
        if (scenarioScripts.getRequestStart() != null) {
            target.setRequestStart(scenarioScripts.getRequestStart());
        }
        if (scenarioScripts.getPlanningStart() != null) {
            target.setPlanningStart(scenarioScripts.getPlanningStart());
        }
        if (scenarioScripts.getTaskCancelled() != null) {
            target.setTaskCancelled(scenarioScripts.getTaskCancelled());
        }
        if (scenarioScripts.getCancelConfirm() != null) {
            target.setCancelConfirm(scenarioScripts.getCancelConfirm());
        }
        if (scenarioScripts.getOutOfScope() != null) {
            target.setOutOfScope(scenarioScripts.getOutOfScope());
        }
    }

    /**
     * 合并thinkChunkScripts配置（继承式覆盖）。
     */
    private void mergeThinkChunkScripts(ScriptConfig.ThinkChunkScripts scenarioThinkChunk) {
        if (this.scriptconfig.getThinkChunkScripts() == null) {
            this.scriptconfig.setThinkChunkScripts(scenarioThinkChunk);
            return;
        }

        ScriptConfig.ThinkChunkScripts defaultThinkChunk = this.scriptconfig.getThinkChunkScripts();

        if (scenarioThinkChunk.getThinkChunkMode() != null) {
            defaultThinkChunk.setThinkChunkMode(scenarioThinkChunk.getThinkChunkMode());
        }
        if (scenarioThinkChunk.getThinkChunkFixedScripts() != null) {
            if (defaultThinkChunk.getThinkChunkFixedScripts() == null) {
                defaultThinkChunk.setThinkChunkFixedScripts(scenarioThinkChunk.getThinkChunkFixedScripts());
            } else {
                mergeFixedScripts(defaultThinkChunk.getThinkChunkFixedScripts(),
                        scenarioThinkChunk.getThinkChunkFixedScripts());
            }
        }
    }

    /**
     * 合并固定话术帧配置（继承式覆盖各字段）。
     */
    private void mergeFixedScripts(ScriptConfig.ThinkChunkFixedScripts def,
                                   ScriptConfig.ThinkChunkFixedScripts scenario) {
        if (scenario.getEnabled() != null) { def.setEnabled(scenario.getEnabled()); }
        if (scenario.getCharsPerFrame() != null) { def.setCharsPerFrame(scenario.getCharsPerFrame()); }
        if (scenario.getTokensBetweenFrames() != null) { def.setTokensBetweenFrames(scenario.getTokensBetweenFrames()); }
        if (scenario.getMinIntervalMs() != null) { def.setMinIntervalMs(scenario.getMinIntervalMs()); }
        if (scenario.getDefaultScripts() != null) { def.setDefaultScripts(scenario.getDefaultScripts()); }
        if (scenario.getExecutionScripts() != null) { def.setExecutionScripts(scenario.getExecutionScripts()); }
        if (scenario.getResumeScripts() != null) { def.setResumeScripts(scenario.getResumeScripts()); }
        if (scenario.getQueryPatterns() != null) { def.setQueryPatterns(scenario.getQueryPatterns()); }
    }
}