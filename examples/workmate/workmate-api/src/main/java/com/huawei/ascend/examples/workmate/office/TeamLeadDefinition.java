package com.huawei.ascend.examples.workmate.office;

import java.util.Map;

public record TeamLeadDefinition(String name, Map<String, String> title, String avatar) {

    /** Backward-compatible constructor (pre A3: single-string title). */
    public TeamLeadDefinition(String name, String singleTitle, String avatar) {
        this(name, singleTitle != null && !singleTitle.isBlank() ? Map.of("zh", singleTitle) : Map.of(), avatar);
    }

    public TeamLeadDefinition {
        if (title == null) {
            title = Map.of();
        }
    }

    public String resolvedTitle() {
        return resolvedTitle("zh");
    }

    public String resolvedTitle(String lang) {
        if (title.isEmpty()) {
            return "";
        }
        if (lang != null && title.containsKey(lang)) {
            return title.get(lang);
        }
        if (title.containsKey("zh")) {
            return title.get("zh");
        }
        return title.values().iterator().next();
    }

    public static TeamLeadDefinition fallback(String teamName) {
        return new TeamLeadDefinition(
                teamName != null && !teamName.isBlank() ? teamName + " 团长" : "团长",
                Map.of("zh", "团队负责人"),
                null);
    }
}
