package com.huawei.ascend.edp.config;

import java.util.List;
import java.util.Map;

/**
 * edp-config.yaml 专有配置模型。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>承载 EDPAgent 专有 YAML 配置的反序列化结果。</li>
 *     <li>向业务工具 Schema、业务 Rails、模型采样参数和话术模板提供配置。</li>
 *     <li>作为 Python EDPAgent 配置迁移到 Java spike 的主要配置承载对象。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>各字段 getter/setter：供 Jackson 反序列化和业务代码读取。</li>
 *     <li>{@link Scope}：业务范围配置。</li>
 *     <li>{@link Limits}：迭代、输入和工具调用限制。</li>
 *     <li>{@link TodolistStep}：轻量 Todo 步骤定义。</li>
 *     <li>{@link Utterances}：话术配置路径。</li>
 *     <li>{@link ThinkChunk}：思考过程分片配置。</li>
 *     <li>{@link LlmSampling}：模型采样参数。</li>
 *     <li>{@link Summary}：最终摘要配置。</li>
 *     <li>{@link Memory}：记忆开关配置。</li>
 * </ul>
 */
public class EdpConfig {

    /** 业务范围配置。 */
    private Scope scope;

    /** 规划步骤文本列表。 */
    private List<String> planningSteps;

    /** 执行限制配置。 */
    private Limits limits;

    /** 轻量 Todo 步骤定义。 */
    private List<TodolistStep> todolistSteps;

    /** 话术模板配置。 */
    private Utterances utterances;

    /** 思考分片配置。 */
    private ThinkChunk thinkChunk;

    /** 模型采样参数配置。 */
    private LlmSampling llmSampling;

    /** 最终摘要格式配置。 */
    private Summary summary;

    public Scope getScope() { return scope; }
    public void setScope(Scope scope) { this.scope = scope; }

    public List<String> getPlanningSteps() { return planningSteps; }
    public void setPlanningSteps(List<String> planningSteps) { this.planningSteps = planningSteps; }

    public Limits getLimits() { return limits; }
    public void setLimits(Limits limits) { this.limits = limits; }

    public List<TodolistStep> getTodolistSteps() { return todolistSteps; }
    public void setTodolistSteps(List<TodolistStep> todolistSteps) { this.todolistSteps = todolistSteps; }

    public Utterances getUtterances() { return utterances; }
    public void setUtterances(Utterances utterances) { this.utterances = utterances; }

    public ThinkChunk getThinkChunk() { return thinkChunk; }
    public void setThinkChunk(ThinkChunk thinkChunk) { this.thinkChunk = thinkChunk; }

    public LlmSampling getLlmSampling() { return llmSampling; }
    public void setLlmSampling(LlmSampling llmSampling) { this.llmSampling = llmSampling; }

    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }

    /**
     * 业务范围配置。
     */
    public static class Scope {
        /** 允许处理的业务范围描述。 */
        private String allowed;

        /** 超出业务范围时的提示话术。 */
        private String outOfScopeMessage;

        public String getAllowed() { return allowed; }
        public void setAllowed(String allowed) { this.allowed = allowed; }

        public String getOutOfScopeMessage() { return outOfScopeMessage; }
        public void setOutOfScopeMessage(String outOfScopeMessage) { this.outOfScopeMessage = outOfScopeMessage; }
    }

    /**
     * 执行限制配置。
     */
    public static class Limits {
        /** 单工具调用次数上限，key 为工具名。 */
        private Map<String, Integer> tasks;

        public Map<String, Integer> getTasks() { return tasks; }
        public void setTasks(Map<String, Integer> tasks) { this.tasks = tasks; }
    }

    /**
     * 轻量 Todo 步骤定义。
     */
    public static class TodolistStep {
        /** 步骤 ID，用于 lite_todo_write Schema 枚举。 */
        private int stepId;

        /** 步骤展示内容。 */
        private String content;

        /** 步骤关联技能名。 */
        private String skill;

        /** 依赖的前置步骤 ID 列表。 */
        private List<Integer> dependsOn;

        public int getStepId() { return stepId; }
        public void setStepId(int stepId) { this.stepId = stepId; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getSkill() { return skill; }
        public void setSkill(String skill) { this.skill = skill; }

        public List<Integer> getDependsOn() { return dependsOn; }
        public void setDependsOn(List<Integer> dependsOn) { this.dependsOn = dependsOn; }
    }

    /**
     * 话术模板配置。
     */
    public static class Utterances {
        /** 话术配置文件路径，当前指向 ScriptsConfig.md。 */
        private String configPath;

        public String getConfigPath() { return configPath; }
        public void setConfigPath(String configPath) { this.configPath = configPath; }
    }

    /**
     * 思考分片配置。
     */
    public static class ThinkChunk {
        /** 思考分片模式。 */
        private String mode;

        /** 每帧字符数。 */
        private int charsPerFrame;

        /** 两帧之间的 token 间隔。 */
        private int tokensBetweenFrames;

        /** 最小帧间隔，单位毫秒。 */
        private int minIntervalMs;

        /** 默认思考脚本。 */
        private List<String> defaultScripts;

        /** 按用户 query 关键词匹配的思考脚本。 */
        private List<QueryPattern> queryPatterns;

        /** 执行阶段脚本。 */
        private List<String> executionScripts;

        /** 恢复阶段脚本。 */
        private List<String> resumeScripts;

        /** 通用脚本列表。 */
        private List<String> scripts;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public int getCharsPerFrame() { return charsPerFrame; }
        public void setCharsPerFrame(int charsPerFrame) { this.charsPerFrame = charsPerFrame; }

        public int getTokensBetweenFrames() { return tokensBetweenFrames; }
        public void setTokensBetweenFrames(int tokensBetweenFrames) { this.tokensBetweenFrames = tokensBetweenFrames; }

        public int getMinIntervalMs() { return minIntervalMs; }
        public void setMinIntervalMs(int minIntervalMs) { this.minIntervalMs = minIntervalMs; }

        public List<String> getDefaultScripts() { return defaultScripts; }
        public void setDefaultScripts(List<String> defaultScripts) { this.defaultScripts = defaultScripts; }

        public List<QueryPattern> getQueryPatterns() { return queryPatterns; }
        public void setQueryPatterns(List<QueryPattern> queryPatterns) { this.queryPatterns = queryPatterns; }

        public List<String> getExecutionScripts() { return executionScripts; }
        public void setExecutionScripts(List<String> executionScripts) { this.executionScripts = executionScripts; }

        public List<String> getResumeScripts() { return resumeScripts; }
        public void setResumeScripts(List<String> resumeScripts) { this.resumeScripts = resumeScripts; }

        public List<String> getScripts() { return scripts; }
        public void setScripts(List<String> scripts) { this.scripts = scripts; }
    }

    /**
     * 用户 query 关键词到脚本的匹配配置。
     */
    public static class QueryPattern {
        /** 关键词列表。 */
        private List<String> keywords;

        /** 匹配关键词后使用的脚本列表。 */
        private List<String> scripts;

        public List<String> getKeywords() { return keywords; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }

        public List<String> getScripts() { return scripts; }
        public void setScripts(List<String> scripts) { this.scripts = scripts; }
    }

    /**
     * 模型采样参数配置。
     */
    public static class LlmSampling {
        /** 温度参数。 */
        private double temperature;

        /** top_p 采样参数。 */
        private double topP;

        /** 最大重试次数。 */
        private int maxRetries;

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public double getTopP() { return topP; }
        public void setTopP(double topP) { this.topP = topP; }

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    /**
     * 最终摘要配置。
     */
    public static class Summary {
        /** 摘要格式。 */
        private String format;

        /** 摘要最大长度。 */
        private int maxLength;

        /** 摘要必填字段列表。 */
        private List<String> requiredFields;

        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }

        public int getMaxLength() { return maxLength; }
        public void setMaxLength(int maxLength) { this.maxLength = maxLength; }

        public List<String> getRequiredFields() { return requiredFields; }
        public void setRequiredFields(List<String> requiredFields) { this.requiredFields = requiredFields; }
    }
}
