package com.huawei.ascend.edp.tools;

import com.huawei.ascend.edp.config.ActRuleConfig;
import com.huawei.ascend.edp.config.EdpConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(EdpaBusinessTools.class);

    /**
     * 构造 EDPAgent 内置业务工具列表，按 actrule.allowed_tools 配置驱动注册。
     *
     * <p>注册逻辑：</p>
     * <ol>
     *     <li>从 actrule.allowed_tools 读取允许的工具名称列表</li>
     *     <li>跳过 DeepAgent 原生工具（bash、skill_tool）</li>
     *     <li>通过 {@link EdpaToolRegistry} 按名称查找对应构建器并实例化</li>
     *     <li>场景级可通过叠加 actrule.allowed_tools 新增自己的工具（未来 SPI 扩展）</li>
     * </ol>
     *
     * @param edpConfig EDP 专有配置，用于动态生成 lite_todo_write 的 step_id 枠举
     * @param actrule 行为治理配置（从 actrule.yaml 加载），包含 allowed_tools 列表
     * @return 工具列表，顺序与 allowed_tools 一致
     */
    public static List<Tool> build(EdpConfig edpConfig, ActRuleConfig actrule) {
        List<Tool> tools = new ArrayList<>();

        if (actrule == null || actrule.getAllowedTools() == null || actrule.getAllowedTools().isEmpty()) {
            LOGGER.info("No allowed_tools configured in actrule, registering no business tools");
            return tools;
        }

        for (String toolName : actrule.getAllowedTools()) {
            // 跳过框架内部自动注册的工具（Core 框架 / DeepAgent 原生工具）
            if ("bash".equals(toolName) || "skill_tool".equals(toolName)
                    || "todo_create".equals(toolName) || "todo_modify".equals(toolName)
                    || "todo_list".equals(toolName) || "todo_get".equals(toolName)) {
                continue;
            }

            Tool tool = EdpaToolRegistry.build(toolName, edpConfig);
            if (tool != null) {
                tools.add(tool);
                LOGGER.debug("Registered business tool: {}", toolName);
            } else {
                LOGGER.warn("Unknown tool in allowed_tools: {}, skipping", toolName);
            }
        }
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
