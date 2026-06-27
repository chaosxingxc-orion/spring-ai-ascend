package com.huawei.ascend.examples.workmate.acp;

import java.util.LinkedHashMap;
import java.util.Map;

/** ACP sessionUpdate envelope for interchange / replay (W38). */
public record AcpSessionUpdate(String sessionUpdate, Map<String, Object> content, Map<String, Object> meta) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sessionUpdate", sessionUpdate);
        if (content != null && !content.isEmpty()) {
            map.put("content", content);
        }
        if (meta != null && !meta.isEmpty()) {
            map.put("_meta", meta);
        }
        return map;
    }
}
