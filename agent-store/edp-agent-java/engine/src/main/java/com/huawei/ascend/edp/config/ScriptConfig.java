package com.huawei.ascend.edp.config;

import java.util.List;

/**
 * scriptconfig.yaml 配置模型。
 *
 * <p>定位：定义Agent与人交互的话术配置和执行总结格式</p>
 * <p>作用：</p>
 * <ul>
 *     <li>回答Agent如何与人协同（话术模板、推送模式）</li>
 *     <li>回答Agent何时需要人工参与（中断确认场景）</li>
 *     <li>定义执行结果的总结输出格式</li>
 * </ul>
 */
public class ScriptConfig {

    /** 通用话术配置。 */
    private GeneralScripts generalScripts;

    /** 思维链话术配置。 */
    private ThinkChunkScripts thinkChunkScripts;

    /** 执行总结格式配置。 */
    private Summary summary;

    /** ask_user 中断确认话术配置。 */
    private AskUserConfirm askUserConfirm;

    public GeneralScripts getGeneralScripts() { return generalScripts; }
    public void setGeneralScripts(GeneralScripts generalScripts) { this.generalScripts = generalScripts; }

    public ThinkChunkScripts getThinkChunkScripts() { return thinkChunkScripts; }
    public void setThinkChunkScripts(ThinkChunkScripts thinkChunkScripts) { this.thinkChunkScripts = thinkChunkScripts; }

    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }

    public AskUserConfirm getAskUserConfirm() { return askUserConfirm; }
    public void setAskUserConfirm(AskUserConfirm askUserConfirm) { this.askUserConfirm = askUserConfirm; }

    /**
     * 通用话术配置，用于业务流程状态（工具调用、中断、取消等）。
     */
    public static class GeneralScripts {
        private String toolStart;
        private String toolEnd;
        private String todoStart;
        private String todoEnd;
        private String todolistStart;
        private String todolistEnd;
        private String interruptStart;
        private String requestStart;
        private String planningStart;
        private String taskCancelled;
        private String cancelConfirm;
        private String outOfScope;

        public String getToolStart() { return toolStart; }
        public void setToolStart(String toolStart) { this.toolStart = toolStart; }

        public String getToolEnd() { return toolEnd; }
        public void setToolEnd(String toolEnd) { this.toolEnd = toolEnd; }

        public String getTodoStart() { return todoStart; }
        public void setTodoStart(String todoStart) { this.todoStart = todoStart; }

        public String getTodoEnd() { return todoEnd; }
        public void setTodoEnd(String todoEnd) { this.todoEnd = todoEnd; }

        public String getTodolistStart() { return todolistStart; }
        public void setTodolistStart(String todolistStart) { this.todolistStart = todolistStart; }

        public String getTodolistEnd() { return todolistEnd; }
        public void setTodolistEnd(String todolistEnd) { this.todolistEnd = todolistEnd; }

        public String getInterruptStart() { return interruptStart; }
        public void setInterruptStart(String interruptStart) { this.interruptStart = interruptStart; }

        public String getRequestStart() { return requestStart; }
        public void setRequestStart(String requestStart) { this.requestStart = requestStart; }

        public String getPlanningStart() { return planningStart; }
        public void setPlanningStart(String planningStart) { this.planningStart = planningStart; }

        public String getTaskCancelled() { return taskCancelled; }
        public void setTaskCancelled(String taskCancelled) { this.taskCancelled = taskCancelled; }

        public String getCancelConfirm() { return cancelConfirm; }
        public void setCancelConfirm(String cancelConfirm) { this.cancelConfirm = cancelConfirm; }

        public String getOutOfScope() { return outOfScope; }
        public void setOutOfScope(String outOfScope) { this.outOfScope = outOfScope; }
    }

    /**
     * 思维链话术配置，用于思维链展示（替代真实LLM thinking）。
     */
    public static class ThinkChunkScripts {
        /** 模式选择：real_stream 或 fixed_script。 */
        private String thinkChunkMode;

        /** 固定话术帧配置（仅 thinkChunkMode=fixed_script 时生效）。 */
        private ThinkChunkFixedScripts thinkChunkFixedScripts;

        public String getThinkChunkMode() { return thinkChunkMode; }
        public void setThinkChunkMode(String thinkChunkMode) { this.thinkChunkMode = thinkChunkMode; }

        public ThinkChunkFixedScripts getThinkChunkFixedScripts() { return thinkChunkFixedScripts; }
        public void setThinkChunkFixedScripts(ThinkChunkFixedScripts thinkChunkFixedScripts) { this.thinkChunkFixedScripts = thinkChunkFixedScripts; }
    }

    /**
     * 固定话术帧配置。
     */
    public static class ThinkChunkFixedScripts {
        private Boolean enabled;
        private Integer charsPerFrame;
        private Integer tokensBetweenFrames;
        private Integer minIntervalMs;
        private List<String> defaultScripts;
        private List<String> executionScripts;
        private List<String> resumeScripts;
        /** 按用户 query 关键词匹配的话术组列表（planning 阶段，继承式覆盖）。 */
        private List<QueryPattern> queryPatterns;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public Integer getCharsPerFrame() { return charsPerFrame; }
        public void setCharsPerFrame(Integer charsPerFrame) { this.charsPerFrame = charsPerFrame; }

        public Integer getTokensBetweenFrames() { return tokensBetweenFrames; }
        public void setTokensBetweenFrames(Integer tokensBetweenFrames) { this.tokensBetweenFrames = tokensBetweenFrames; }

        public Integer getMinIntervalMs() { return minIntervalMs; }
        public void setMinIntervalMs(Integer minIntervalMs) { this.minIntervalMs = minIntervalMs; }

        public List<String> getDefaultScripts() { return defaultScripts; }
        public void setDefaultScripts(List<String> defaultScripts) { this.defaultScripts = defaultScripts; }

        public List<String> getExecutionScripts() { return executionScripts; }
        public void setExecutionScripts(List<String> executionScripts) { this.executionScripts = executionScripts; }

        public List<String> getResumeScripts() { return resumeScripts; }
        public void setResumeScripts(List<String> resumeScripts) { this.resumeScripts = resumeScripts; }

        public List<QueryPattern> getQueryPatterns() { return queryPatterns; }
        public void setQueryPatterns(List<QueryPattern> queryPatterns) { this.queryPatterns = queryPatterns; }

        /**
         * 按关键词匹配的话术组。planning 阶段遍历列表，首个命中的关键词组即生效。
         */
        public static class QueryPattern {
            private List<String> keywords;
            private List<String> scripts;

            public List<String> getKeywords() { return keywords; }
            public void setKeywords(List<String> keywords) { this.keywords = keywords; }
            public List<String> getScripts() { return scripts; }
            public void setScripts(List<String> scripts) { this.scripts = scripts; }
        }
    }

    /**
     * 执行总结格式配置。
     */
    public static class Summary {
        private String format;
        private Integer maxLength;
        private List<String> requiredFields;

        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }

        public Integer getMaxLength() { return maxLength; }
        public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }

        public List<String> getRequiredFields() { return requiredFields; }
        public void setRequiredFields(List<String> requiredFields) { this.requiredFields = requiredFields; }
    }

    /**
     * ask_user 中断确认话术配置。
     *
     * <p>消费方：AskUserTemplateRail（spike 阶段，当前未读取 YAML，预留建模）。</p>
     */
    public static class AskUserConfirm {
        /** 购买确认话术模板，支持 {product_name}、{amount} 等占位符。 */
        private String purchaseConfirm;

        /** 取消确认话术模板。 */
        private String cancelConfirm;

        public String getPurchaseConfirm() { return purchaseConfirm; }
        public void setPurchaseConfirm(String purchaseConfirm) { this.purchaseConfirm = purchaseConfirm; }

        public String getCancelConfirm() { return cancelConfirm; }
        public void setCancelConfirm(String cancelConfirm) { this.cancelConfirm = cancelConfirm; }
    }
}