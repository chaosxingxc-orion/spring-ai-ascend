package com.huawei.ascend.edp.config;

import java.util.List;
import java.util.Map;

/**
 * actrule.yaml 配置模型。
 *
 * <p>定位：定义Agent的任务执行约束与行为边界</p>
 * <p>作用：</p>
 * <ul>
 *     <li>回答Agent如何执行任务</li>
 *     <li>控制执行过程中的步数限制、子任务数量上限</li>
 *     <li>约束Agent可调用的工具集合</li>
 * </ul>
 */
public class ActRuleConfig {

    /** 限制单层最大子任务数量。 */
    private Integer maxSubtasks;

    /** 限制最大执行步数。 */
    private Integer maxSteps;

    /** 允许调用的工具列表（继承式覆盖）。 */
    private List<String> allowedTools;

    /** 是否启用任务循环（从 framework.options.enableTaskLoop 迁移）。 */
    private Boolean enableTaskLoop;

    /** Skill 加载模式（从 skills.mode 迁移）。
     * 可选值：all（列出所有 Skill）、auto_list（动态搜索 Skill）、none（关闭 Skill 系统）。
     */
    private String skillMode;

    /** 单个工具调用次数上限，key 为工具名，value 为上限值。 */
    private Map<String, Integer> toolLimits;

    public Integer getMaxSubtasks() { return maxSubtasks; }
    public void setMaxSubtasks(Integer maxSubtasks) { this.maxSubtasks = maxSubtasks; }

    public Integer getMaxSteps() { return maxSteps; }
    public void setMaxSteps(Integer maxSteps) { this.maxSteps = maxSteps; }

    public List<String> getAllowedTools() { return allowedTools; }
    public void setAllowedTools(List<String> allowedTools) { this.allowedTools = allowedTools; }

    public Boolean getEnableTaskLoop() { return enableTaskLoop; }
    public void setEnableTaskLoop(Boolean enableTaskLoop) { this.enableTaskLoop = enableTaskLoop; }

    public String getSkillMode() { return skillMode; }
    public void setSkillMode(String skillMode) { this.skillMode = skillMode; }

    public Map<String, Integer> getToolLimits() { return toolLimits; }
    public void setToolLimits(Map<String, Integer> toolLimits) { this.toolLimits = toolLimits; }

    // ========== todolist 字段（替代式覆盖，框架默认无值） ==========

    /** 任务定义列表（场景级替代式覆盖，框架默认无值）。 */
    private List<TodolistEntry> todolistEntries;

    /** 动态路径规则列表。 */
    private List<TodolistPath> todolistDynamicPaths;

    public List<TodolistEntry> getTodolistEntries() { return todolistEntries; }
    public void setTodolistEntries(List<TodolistEntry> entries) { this.todolistEntries = entries; }

    public List<TodolistPath> getTodolistDynamicPaths() { return todolistDynamicPaths; }
    public void setTodolistDynamicPaths(List<TodolistPath> paths) { this.todolistDynamicPaths = paths; }

    /**
     * 任务定义（catalog_id 为内部主键）。
     */
    public static class TodolistEntry {
        private String catalogId;
        private String content;
        private String description;
        private List<String> dependsOn;
        private String skill;

        public String getCatalogId() { return catalogId; }
        public void setCatalogId(String catalogId) { this.catalogId = catalogId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getDependsOn() { return dependsOn; }
        public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn; }
        public String getSkill() { return skill; }
        public void setSkill(String skill) { this.skill = skill; }
    }

    /**
     * 动态路径规则（LLM 根据业务结果自主判断是否切换）。
     */
    public static class TodolistPath {
        private String pathId;
        private String description;
        private String trigger;
        private List<String> skipSteps;
        private String redirect;

        public String getPathId() { return pathId; }
        public void setPathId(String pathId) { this.pathId = pathId; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getTrigger() { return trigger; }
        public void setTrigger(String trigger) { this.trigger = trigger; }
        public List<String> getSkipSteps() { return skipSteps; }
        public void setSkipSteps(List<String> skipSteps) { this.skipSteps = skipSteps; }
        public String getRedirect() { return redirect; }
        public void setRedirect(String redirect) { this.redirect = redirect; }
    }
}