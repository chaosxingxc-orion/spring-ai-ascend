package com.bank.financial.kit.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny fluent builder for a tool's JSON-Schema {@code inputParams} map, so
 * declaring a backend tool in Java reads cleanly:
 *
 * <pre>
 *   Schemas.object()
 *          .required("cardId", "string", "信用卡号后四位")
 *          .optional("month", "string", "账期 YYYY-MM")
 *          .build()
 * </pre>
 *
 * Keeps the common case (a flat object of typed properties) one line per field
 * instead of hand-nesting maps — the kind of "make the easy thing easy" ergonomics
 * IBM's frameworks leaned on.
 */
public final class Schemas {

    private final Map<String, Object> properties = new LinkedHashMap<>();
    private final List<String> required = new ArrayList<>();

    private Schemas() {
    }

    public static Schemas object() {
        return new Schemas();
    }

    public Schemas required(String name, String type, String description) {
        properties.put(name, Map.of("type", type, "description", description));
        required.add(name);
        return this;
    }

    public Schemas optional(String name, String type, String description) {
        properties.put(name, Map.of("type", type, "description", description));
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", List.copyOf(required));
        }
        return schema;
    }
}
