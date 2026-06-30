package com.openjiuwen.runtime.alpha.util;

/**
 * Prompt 安全工具——防止 LLM prompt 注入的 XML 转义。
 *
 * 统一转义逻辑，避免 DefaultPlanner/DefaultVerifier 各自维护副本。
 * 覆盖全部 5 个 XML 标准实体 + null 字节 + 控制字符 + CDATA 结束序列。
 */
public final class PromptSecurity {

    private PromptSecurity() {}

    /**
     * 对字符串进行 XML 安全转义，用于将用户输入嵌入 LLM prompt 的 XML 标签中。
     *
     * <p>转义规则（按执行顺序）：
     * <ol>
     *   <li>剥离 null 字节 (U+0000)</li>
     *   <li>剥离非法 XML 控制字符 (U+0001-U+001F，保留 TAB/LF/CR)</li>
     *   <li>拆分 CDATA 结束序列 {@code ]]>}</li>
     *   <li>转义 5 个 XML 标准实体: {@code & < > " '}</li>
     * </ol>
     */
    public static String escapeXml(String input) {
        if (input == null) return "";
        // 控制字符正则：U+0001-U+001F 中排除 TAB(09), LF(0A), CR(0D)
        String cleaned = input.replace("\0", "");
        cleaned = cleaned.replaceAll("[\\u0001-\\u0008\\u000b\\u000c\\u000e-\\u001f]", "");
        cleaned = cleaned.replace("]]>", "]]&gt;");
        return cleaned
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
