package com.huawei.ascend.edp.tools;

import com.huawei.ascend.edp.config.EdpConfig;
import com.openjiuwen.core.foundation.tool.Tool;

import java.util.Map;
import java.util.function.Function;

/**
 * EDPAgent 内置工具注册表。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>建立工具名称（YAML 中 allowed_tools 列表）到 Java 构建函数的映射。</li>
 *     <li>使 {@link EdpaBusinessTools#build} 可以按配置驱动注册，而非硬编码。</li>
 *     <li>场景级自定义工具可通过 SPI 扩展（未来）。</li>
 * </ul>
 *
 * <p>注意：</p>
 * <ul>
 *     <li>bash、skill_tool 是 DeepAgent 原生工具，不在此注册表中。</li>
 *     <li>todo_create / todo_modify / todo_list / todo_get 由 Core 框架 TaskPlanningRail 自动注册。</li>
 * </ul>
 */
public final class EdpaToolRegistry {

    private static final Map<String, Function<EdpConfig, Tool>> BUILTIN_TOOLS = Map.of(
            EdpaBusinessTools.TOOL_CALL_MCP, cfg -> CallMcpTool.build(),
            EdpaBusinessTools.TOOL_CALL_VERSATILE, cfg -> CallVersatileTool.build(),
            EdpaBusinessTools.TOOL_ENHANCED_ASK_USER, cfg -> EnhancedAskUserTool.build(),
            EdpaBusinessTools.TOOL_CANCEL_TASK, cfg -> CancelTaskTool.build()
    );

    private EdpaToolRegistry() {
    }

    /**
     * 根据工具名称构建工具实例。
     *
     * @param name 工具名称（如 "call_mcp"）
     * @param edpConfig EDP 专有配置
     * @return 工具实例，未知名称为 null
     */
    public static Tool build(String name, EdpConfig edpConfig) {
        Function<EdpConfig, Tool> builder = BUILTIN_TOOLS.get(name);
        return builder != null ? builder.apply(edpConfig) : null;
    }

    /**
     * 判断是否为已知内置工具。
     *
     * @param name 工具名称
     * @return true 如果此名称在注册表中
     */
    public static boolean isBuiltin(String name) {
        return BUILTIN_TOOLS.containsKey(name);
    }
}
