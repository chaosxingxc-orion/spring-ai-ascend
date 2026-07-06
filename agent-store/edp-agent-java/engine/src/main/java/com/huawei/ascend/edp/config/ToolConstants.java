package com.huawei.ascend.edp.config;

/**
 * 工具名常量集中地（与框架 Core 工具名对齐）。
 *
 * <p>所有 Rail 引用工具名时必须使用此常量类，禁止硬编码字符串字面量。</p>
 */
public final class ToolConstants {

    private ToolConstants() {}

    /** Core TaskPlanningRail 提供的 Todo 工具。 */
    public static final String TODO_CREATE = "todo_create";
    public static final String TODO_MODIFY = "todo_modify";
    public static final String TODO_LIST = "todo_list";
    public static final String TODO_GET = "todo_get";

    /** 业务工具。 */
    public static final String CALL_MCP = "call_mcp";
    public static final String CALL_VERSATILE = "call_versatile";
    public static final String ASK_USER = "ask_user";
    public static final String CANCEL_TASK = "cancel_task";

    /** 脚本执行工具。 */
    public static final String BASH = "bash";
    public static final String SKILL_TOOL = "skill_tool";
}
