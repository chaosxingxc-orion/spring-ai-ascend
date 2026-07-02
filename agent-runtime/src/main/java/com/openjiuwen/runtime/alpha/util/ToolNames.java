package com.openjiuwen.runtime.alpha.util;

/**
 * 工具名归一化（排毒层）。
 *
 * <p>TOOL_CALL 节点的 {@code description} 在执行期被原样当作工具名查找键
 * （{@code com.openjiuwen.runtime.alpha.executor.DefaultPregelExecutor} 的 {@code new ToolName(node.description())}）。
 * 若弱模型把 {@code DefaultPlanner.renderToolSignatures} 渲染的签名整段抄进 description
 * （如 {@code "getCaseStatus(caseNo: String)"}），执行期会因工具名不匹配而硬失败。本 helper 从含
 * 签名噪声的 description 中剥离出裸工具名（取首个 {@code (} 之前的部分），使执行期对 LLM 的抄写行为鲁棒。
 *
 * <p>属纵深防御的<b>排毒层</b>（排毒≠吸毒：仅剥离噪声恢复裸名，绝不解析/执行 description 内容）；
 * 与<b>预防层</b>（renderToolSignatures 两行排版，工具名独占一行降低抄写诱导）+
 * <b>可观测层</b>（PlanValidator 归一化时发 TOOL_DESC_NOISE WARNING 留痕）共同构成工具名归一化加固。
 * 三层独立，任一失守另有兜底——单点最优是错的（见 [[defense-in-depth-three-layers]]）。
 */
public final class ToolNames {

    private ToolNames() {}

    /**
     * 从 TOOL_CALL 的 description 中提取裸工具名：剥离前导 {@code "- "} 列表标记与首个 {@code (}
     * 之后的参数签名噪声。
     *
     * <p>无括号时返回去前导标记/空白后的内容（description 可能本就是裸名，也可能是非签名的
     * 别的错误——后者由 PlanValidator 的 UNKNOWN_TOOL WARNING / 执行期失败另行捕获，本方法不兜底）。
     *
     * <p>同时识别半角 {@code (}（U+0028）与全角 {@code （}（U+FF08）——对抗审查发现：CJK 输入法/弱模型
     * 常把半角括号自动转全角，若只认半角则全角签名 {@code "name（params）"} 原样返回，执行期 ToolName 不匹配
     * 硬失败，且因归一化前后字节不变连 TOOL_DESC_NOISE WARNING 都不触发（零可观测）。
     *
     * @param description TOOL_CALL 节点的 description（可能含 {@code "name(params)"} 签名噪声；null/空原样返回）
     * @return 裸工具名（首个半角/全角左括号之前、去前导 {@code "- "} 与空白）
     */
    public static String bareToolName(String description) {
        if (description == null || description.isEmpty()) {
            return description;
        }
        String s = description.trim();
        // 剥离 renderToolSignatures 的 "- " 列表前缀（若 LLM 连前缀一起抄）
        if (s.startsWith("- ")) {
            s = s.substring(2).trim();
        }
        // 对抗审查加固：取半角 '(' 与全角 '（' 中最早出现位置截断（CJK 输入法会把 ( 自动转全角，零可观测硬失败）
        int paren = s.indexOf('(');
        int fullParen = s.indexOf('（');
        if (fullParen >= 0 && (paren < 0 || fullParen < paren)) {
            paren = fullParen;
        }
        if (paren < 0) {
            return s;
        }
        return s.substring(0, paren).trim();
    }
}
