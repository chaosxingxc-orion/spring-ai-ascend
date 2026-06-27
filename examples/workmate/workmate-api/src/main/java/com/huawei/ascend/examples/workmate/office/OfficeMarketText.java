package com.huawei.ascend.examples.workmate.office;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Display-text normalization for market assets. Asset packs may carry source-specific labels; this
 * hook lets the loader present neutral text. Bundled assets are already neutral, so the current
 * implementation is a pass-through, but the seam is kept for asset packs added later.
 */
final class OfficeMarketText {

    private OfficeMarketText() {}

    static String normalizeDisplayText(String value) {
        return value;
    }

    static String normalizeSource(String source) {
        return source;
    }

    static List<String> normalizeTags(List<String> tags) {
        return tags == null ? List.of() : tags;
    }

    static Map<String, String> normalizeDisplayMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return values;
        }
        Map<String, String> out = new LinkedHashMap<>();
        values.forEach((key, value) -> out.put(key, normalizeDisplayText(value)));
        return Map.copyOf(out);
    }

    static List<String> normalizeDisplayList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return values == null ? List.of() : values;
        }
        return values.stream().map(OfficeMarketText::normalizeDisplayText).toList();
    }
}
