package com.huawei.ascend.tools.architecture.fragment;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Emitter: scans {@code docs/governance/rules/rule-*.md} cards and emits one
 * {@code element ... "SAA Rule"} per active card. Rule cards are the sole rule
 * authority; the rule identifier is {@code rule_<sanitized-id>} so that
 * PrinciplesFragmentEmitter can target each rule with an `operationalised_by`
 * relationship. Cards whose frontmatter {@code status} is not {@code active}
 * are skipped.
 */
public final class RulesFragmentEmitter {

    private RulesFragmentEmitter() {
    }

    public static void main(String[] args) throws IOException {
        Path repoRoot = Path.of(argValue(args, "--repo", "."));
        Path output = Path.of(argValue(args, "--output", "architecture/generated/rules.dsl"));
        Path rulesDir = repoRoot.resolve("docs/governance/rules");

        // rule id -> {title, level}; TreeMap keeps output sorted by id deterministically.
        Map<String, String[]> rules = new TreeMap<>();
        try (var stream = Files.list(rulesDir)) {
            for (Path card : (Iterable<Path>) stream.sorted()::iterator) {
                String name = card.getFileName().toString();
                if (!name.startsWith("rule-") || !name.endsWith(".md")) {
                    continue;
                }
                Map<String, Object> fm = frontMatter(card);
                if (fm == null) {
                    continue;
                }
                Object status = fm.get("status");
                if (status != null && !"active".equals(String.valueOf(status).trim())) {
                    continue;
                }
                Object ruleId = fm.get("rule_id");
                if (ruleId == null || String.valueOf(ruleId).isBlank()) {
                    continue;
                }
                String id = String.valueOf(ruleId).trim();
                String title = fm.get("title") == null ? id : String.valueOf(fm.get("title")).trim();
                String level = fm.get("level") == null ? "L1" : String.valueOf(fm.get("level")).trim();
                rules.putIfAbsent(id, new String[]{title, level});
            }
        }

        try (FragmentWriter.StagedFragment frag = FragmentWriter.open(
                output,
                "docs/governance/rules/rule-*.md cards (status: active)",
                RulesFragmentEmitter.class.getName(),
                rules.size())) {

            StringBuilder buf = frag.buf();
            for (Map.Entry<String, String[]> e : rules.entrySet()) {
                String id = e.getKey();
                String title = e.getValue()[0];
                String level = e.getValue()[1];
                String identifier = "rule_" + FragmentWriter.safeId(id);
                String saaId = "RULE-" + id;

                buf.append(identifier).append(" = element \"")
                        .append(FragmentWriter.escape("Rule " + id))
                        .append("\" \"Rule\" \"")
                        .append(FragmentWriter.escape(title))
                        .append("\" \"SAA Rule\" {\n");

                Map<String, String> props = new LinkedHashMap<>();
                props.put("saa.id", saaId);
                props.put("saa.kind", "rule");
                props.put("saa.level", level);
                props.put("saa.view", "scenarios");
                props.put("saa.status", "shipped");
                props.put("saa.sourceAdr", "ADR-0086");
                FragmentWriter.writeProperties(buf, props);
                buf.append("}\n\n");
            }
        }

        System.out.println("RulesFragmentEmitter wrote " + rules.size() + " rules to " + output);
    }

    /**
     * Parse the YAML frontmatter block (delimited by lines that are exactly
     * {@code ---}) from a rule card. Returns null when the file has no
     * frontmatter or it does not parse to a mapping.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> frontMatter(Path card) throws IOException {
        List<String> lines = Files.readAllLines(card, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).trim().equals("---")) {
            return null;
        }
        StringBuilder fm = new StringBuilder();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("---")) {
                Object loaded = new Yaml().load(fm.toString());
                return loaded instanceof Map ? (Map<String, Object>) loaded : null;
            }
            fm.append(lines.get(i)).append('\n');
        }
        return null;
    }

    private static String argValue(String[] args, String key, String def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) {
                return args[i + 1];
            }
        }
        return def;
    }
}
