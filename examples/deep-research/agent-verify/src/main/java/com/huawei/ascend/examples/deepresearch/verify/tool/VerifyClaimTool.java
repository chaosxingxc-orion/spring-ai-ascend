/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cross-source claim verification tool (pure text processing, no LLM calls).
 *
 * <p>Signature: {@code public static Map<String, Object> verify(Map<String, Object> inputs)} —
 * compatible with agent-sdk YAML {@code ref.type=file} tool wiring.
 *
 * <p>This tool extracts and structures evidence; the ReAct agent's system prompt
 * drives the final verdict/confidence judgment using its own LLM.
 */
public final class VerifyClaimTool {

    private static final Set<String> OFFICIAL_DOMAINS = Set.of(
            "volcengine.com", "bailian.aliyun.com", "bigmodel.cn",
            "moonshot.cn", "deepseek.com", "baidu.com",
            "cloud.tencent.com", "qwenlm.ai"
    );

    // For numeric claim extraction
    private static final Pattern NUMERIC_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(万|千|[KkMm]|亿)?");

    // For temporal claim extraction
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{4})\\s*[-年]\\s*(Q[1-4]|\\d{1,2}\\s*月)?");

    private VerifyClaimTool() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> verify(Map<String, Object> inputs) {
        String claim = asString(inputs.get("claim"));
        List<Map<String, Object>> sources = (List<Map<String, Object>>) inputs.getOrDefault("sources", List.of());
        String claimType = asString(inputs.get("claim_type"));

        if (claim == null || claim.isBlank()) {
            return errorResult("claim is required");
        }
        if (claimType == null || claimType.isBlank()) {
            claimType = "qualitative";
        }

        List<Map<String, Object>> evidence = new ArrayList<>();
        int authoritative = 0;
        int supporting = 0;
        int contradicting = 0;
        int noData = 0;

        for (Map<String, Object> src : sources) {
            String url = asString(src.get("url"));
            String excerpt = asString(src.get("excerpt"));
            boolean isAuth = isAuthoritative(url);

            Map<String, Object> e = new LinkedHashMap<>();
            e.put("url", url);
            e.put("is_authoritative", isAuth);

            if (excerpt.isBlank()) {
                e.put("match_status", "no_data");
                e.put("relevant_excerpts", List.of());
                e.put("contradicting_excerpts", List.of());
                e.put("extracted_values", Map.of());
                evidence.add(e);
                noData++;
                continue;
            }

            if (isAuth) authoritative++;

            switch (claimType) {
                case "numeric" -> processNumericClaim(e, claim, excerpt);
                case "categorical" -> processCategoricalClaim(e, claim, excerpt);
                case "temporal" -> processTemporalClaim(e, claim, excerpt);
                default -> processQualitativeClaim(e, claim, excerpt);
            }

            String status = asString(e.get("match_status"));
            if ("exact_match".equals(status) || "partial_match".equals(status)) {
                supporting++;
            } else if ("mismatch".equals(status)) {
                contradicting++;
            } else {
                noData++;
            }

            evidence.add(e);
        }

        Map<String, Object> aggregate = new LinkedHashMap<>();
        aggregate.put("total_sources", sources.size());
        aggregate.put("authoritative_sources", authoritative);
        aggregate.put("supporting_count", supporting);
        aggregate.put("contradicting_count", contradicting);
        aggregate.put("insufficient_data_count", noData);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("claim_summary", extractKeyFacts(claim, claimType));
        out.put("evidence", evidence);
        out.put("aggregate", aggregate);
        return out;
    }

    // --- Numeric claim processing ---
    private static void processNumericClaim(Map<String, Object> e, String claim, String excerpt) {
        List<Double> claimNums = extractNumbers(claim);
        List<Double> excerptNums = extractNumbers(excerpt);

        if (claimNums.isEmpty()) {
            e.put("match_status", "no_data");
            e.put("relevant_excerpts", List.of());
            e.put("contradicting_excerpts", List.of());
            e.put("extracted_values", Map.of("claim_numbers", claimNums, "excerpt_numbers", excerptNums));
            return;
        }

        List<String> matching = new ArrayList<>();
        List<String> contradicting = new ArrayList<>();
        for (double cn : claimNums) {
            boolean found = false;
            for (double en : excerptNums) {
                if (Math.abs(cn - en) / Math.max(cn, 1e-10) < 0.05) {
                    matching.add(String.format("claim=%.6f matched excerpt=%.6f", cn, en));
                    found = true;
                    break;
                }
            }
            if (!found && !excerptNums.isEmpty()) {
                contradicting.add(String.format("claim=%.6f not found in excerpt numbers=%s", cn, excerptNums));
            }
        }

        if (!matching.isEmpty() && contradicting.isEmpty()) {
            e.put("match_status", "exact_match");
        } else if (!matching.isEmpty()) {
            e.put("match_status", "partial_match");
        } else if (!contradicting.isEmpty()) {
            e.put("match_status", "mismatch");
        } else {
            e.put("match_status", "no_data");
        }

        e.put("relevant_excerpts", matching);
        e.put("contradicting_excerpts", contradicting);
        e.put("extracted_values", Map.of("claim_numbers", claimNums, "excerpt_numbers", excerptNums));
    }

    // --- Categorical claim processing ---
    private static void processCategoricalClaim(Map<String, Object> e, String claim, String excerpt) {
        List<String> claimTerms = extractKeyTerms(claim);
        String excerptLower = excerpt.toLowerCase();

        List<String> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String term : claimTerms) {
            if (excerptLower.contains(term.toLowerCase())) {
                found.add(term);
            } else {
                missing.add(term);
            }
        }

        if (!missing.isEmpty() && found.isEmpty()) {
            e.put("match_status", "mismatch");
        } else if (!missing.isEmpty()) {
            e.put("match_status", "partial_match");
        } else if (!found.isEmpty()) {
            e.put("match_status", "exact_match");
        } else {
            e.put("match_status", "no_data");
        }

        e.put("relevant_excerpts", found);
        e.put("contradicting_excerpts", missing);
        e.put("extracted_values", Map.of("claim_terms", claimTerms, "found_in_excerpt", found, "missing_from_excerpt", missing));
    }

    // --- Temporal claim processing ---
    private static void processTemporalClaim(Map<String, Object> e, String claim, String excerpt) {
        List<String> claimDates = extractDates(claim);
        List<String> excerptDates = extractDates(excerpt);

        if (claimDates.isEmpty()) {
            e.put("match_status", "no_data");
            e.put("relevant_excerpts", List.of());
            e.put("contradicting_excerpts", List.of());
            e.put("extracted_values", Map.of("claim_dates", claimDates, "excerpt_dates", excerptDates));
            return;
        }

        List<String> matching = new ArrayList<>();
        for (String cd : claimDates) {
            if (excerpt.contains(cd) || excerptDates.contains(cd)) {
                matching.add(cd);
            }
        }

        if (!matching.isEmpty() && matching.size() == claimDates.size()) {
            e.put("match_status", "exact_match");
        } else if (!matching.isEmpty()) {
            e.put("match_status", "partial_match");
        } else {
            e.put("match_status", "mismatch");
        }

        e.put("relevant_excerpts", matching);
        e.put("contradicting_excerpts",
                claimDates.stream().filter(d -> !matching.contains(d)).toList());
        e.put("extracted_values", Map.of("claim_dates", claimDates, "excerpt_dates", excerptDates));
    }

    // --- Qualitative claim processing ---
    private static void processQualitativeClaim(Map<String, Object> e, String claim, String excerpt) {
        List<String> terms = extractKeyTerms(claim);
        List<String> contextSnippets = new ArrayList<>();

        for (String term : terms) {
            int idx = excerpt.toLowerCase().indexOf(term.toLowerCase());
            if (idx >= 0) {
                int start = Math.max(0, idx - 20);
                int end = Math.min(excerpt.length(), idx + term.length() + 50);
                contextSnippets.add("..." + excerpt.substring(start, end) + "...");
            }
        }

        if (contextSnippets.isEmpty()) {
            e.put("match_status", "no_data");
        } else if (contextSnippets.size() >= terms.size() * 0.7) {
            e.put("match_status", "partial_match");
        } else {
            e.put("match_status", "no_data");
        }

        e.put("relevant_excerpts", contextSnippets);
        e.put("contradicting_excerpts", List.of());
        e.put("extracted_values", Map.of("claim_terms", terms, "matched_terms_count", contextSnippets.size()));
    }

    // --- Utility methods ---
    private static boolean isAuthoritative(String url) {
        String domain = extractDomain(url);
        return OFFICIAL_DOMAINS.contains(domain);
    }

    static String extractDomain(String url) {
        if (url == null || url.isBlank()) return "";
        String host = url.replaceFirst("^https?://", "");
        int slash = host.indexOf('/');
        if (slash > 0) host = host.substring(0, slash);
        if (host.startsWith("www.")) host = host.substring(4);
        return host.toLowerCase();
    }

    static List<Double> extractNumbers(String text) {
        List<Double> nums = new ArrayList<>();
        Matcher m = NUMERIC_PATTERN.matcher(text);
        while (m.find()) {
            double val = Double.parseDouble(m.group(1));
            String unit = m.group(2);
            if (unit != null) {
                val = switch (unit.toLowerCase()) {
                    case "万" -> val * 10000;
                    case "亿" -> val * 100000000;
                    case "千" -> val * 1000;
                    case "k" -> val * 1000;
                    case "m" -> val * 1000000;
                    default -> val;
                };
            }
            nums.add(val);
        }
        return nums;
    }

    static List<String> extractDates(String text) {
        List<String> dates = new ArrayList<>();
        Matcher m = DATE_PATTERN.matcher(text);
        while (m.find()) {
            String year = m.group(1);
            String rest = m.group(2);
            dates.add(rest != null ? year + "-" + rest.trim() : year);
        }
        return dates;
    }

    static List<String> extractKeyTerms(String text) {
        // Extract Chinese words (2+ chars) and English words (3+ chars)
        List<String> terms = new ArrayList<>();
        Matcher chineseMatcher = Pattern.compile("[\\u4e00-\\u9fff]{2,}").matcher(text);
        while (chineseMatcher.find()) {
            terms.add(chineseMatcher.group());
        }
        Matcher englishMatcher = Pattern.compile("[a-zA-Z]{3,}").matcher(text);
        while (englishMatcher.find()) {
            terms.add(englishMatcher.group());
        }
        return terms;
    }

    static String extractKeyFacts(String claim, String claimType) {
        return switch (claimType) {
            case "numeric" -> "Numbers: " + extractNumbers(claim);
            case "categorical" -> "Categories: " + extractKeyTerms(claim);
            case "temporal" -> "Dates: " + extractDates(claim);
            default -> "Terms: " + extractKeyTerms(claim);
        };
    }

    private static Map<String, Object> errorResult(String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("claim_summary", message);
        out.put("evidence", List.of());
        out.put("aggregate", Map.of("error", message));
        return out;
    }

    private static String asString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
