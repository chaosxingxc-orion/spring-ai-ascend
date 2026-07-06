package com.huawei.ascend.edp.tools;

import com.huawei.ascend.edp.config.EdpConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * EDPAgent 内置业务工具聚合入口。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>集中定义 EDPAgent spike 阶段内置业务工具名称。</li>
 *     <li>聚合 5 个独立工具类，向注册器提供统一 build 入口。</li>
 *     <li>提供工具构造和 Schema 构造的包内共享方法。</li>
 * </ul>
 */
public final class EdpaBusinessTools {

    /**
     * 轻量 Todo 写入工具名，对齐 Python EDPAgent 的 lite_todo_write。
     */
    public static final String TOOL_LITE_TODO_WRITE = "lite_todo_write";

    /**
     * MCP 沙箱调用工具名。
     */
    public static final String TOOL_CALL_MCP = "call_mcp";

    /**
     * Versatile Agent 委托调用工具名。
     */
    public static final String TOOL_CALL_VERSATILE = "call_versatile";

    /**
     * 用户追问工具名。该工具需要触发 OpenJiuwen interrupt，而不是普通工具返回。
     */
    public static final String TOOL_ENHANCED_ASK_USER = "ask_user";

    /**
     * 任务取消工具名。
     */
    public static final String TOOL_CANCEL_TASK = "cancel_task";

    private EdpaBusinessTools() {
    }

    /**
     * 构造 EDPAgent 内置业务工具列表。
     *
     * @param edpConfig EDP 专有配置，用于动态生成 lite_todo_write 的 step_id 枚举
     * @return 工具列表，包含 lite_todo_write、call_mcp、call_versatile、ask_user、cancel_task
     */
    public static List<Tool> build(EdpConfig edpConfig) {
        List<Tool> tools = new ArrayList<>();
        tools.add(LiteTodoWriteTool.build(edpConfig));
        tools.add(CallMcpTool.build());
        tools.add(CallVersatileTool.build());
        tools.add(EnhancedAskUserTool.build());
        tools.add(CancelTaskTool.build());
        return tools;
    }

    static Tool localTool(String name, String description, Map<String, Object> inputParams,
            Function<Map<String, Object>, Object> function) {
        ToolCard card = ToolCard.builder()
                .id(name)
                .name(name)
                .description(description)
                .inputParams(inputParams)
                .build();
        return new LocalFunction(card, function);
    }

    static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", required);
    }

    static Map<String, Object> stringProp(String description) {
        return Map.of("type", "string", "description", description);
    }

    static Map<String, Object> objectProp(String description) {
        return Map.of("type", "object", "description", description);
    }

    static Map<String, Object> arrayProp(String description) {
        return Map.of("type", "array", "description", description, "items", Map.of("type", "string"));
    }
}
