package com.openjiuwen.core.kernel.model;

/**
 * 工具调用结果。
 * 一次 @Tool 方法的执行结果。
 *
 * @param toolName 工具名称
 * @param success  是否成功
 * @param result   成功时的返回值（可序列化为 JSON）
 * @param error    失败时的错误信息
 */
public record ToolResult(
    ToolName toolName,
    boolean success,
    Object result,
    String error
) {

    /** 创建成功的 ToolResult */
    public static ToolResult ok(ToolName toolName, Object result) {
        return new ToolResult(toolName, true, result, null);
    }

    /** 创建失败的 ToolResult */
    public static ToolResult fail(ToolName toolName, String error) {
        return new ToolResult(toolName, false, null, error);
    }
}
