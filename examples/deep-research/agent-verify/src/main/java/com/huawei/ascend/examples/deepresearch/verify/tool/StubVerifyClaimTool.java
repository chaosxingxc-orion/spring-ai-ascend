/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stub verify_claim tool: looks up verdict by claim hash in fixtures/verdicts.json.
 * Unmatched claims return {@code verdict=insufficient, confidence=0.3}.
 */
public final class StubVerifyClaimTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static List<Map<String, Object>> cache;

    private StubVerifyClaimTool() {}

    public static Map<String, Object> verify(Map<String, Object> inputs) {
        String claim = asString(inputs.get("claim"));
        if (claim == null || claim.isBlank()) {
            return insufficientResult(claim);
        }

        String hash = hash8(claim);
        List<Map<String, Object>> fixtures = loadFixtures();

        for (Map<String, Object> entry : fixtures) {
            if (hash.equals(asString(entry.get("claim_hash")))) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("verdict", entry.getOrDefault("verdict", "insufficient"));
                out.put("confidence", entry.getOrDefault("confidence", 0.3));
                out.put("supporting_excerpts", entry.getOrDefault("supporting_excerpts", List.of()));
                out.put("contradicting_excerpts", entry.getOrDefault("contradicting_excerpts", List.of()));
                out.put("suggested_followup_query", entry.getOrDefault("suggested_followup_query", null));
                return out;
            }
        }

        return insufficientResult(claim);
    }

    private static Map<String, Object> insufficientResult(String claim) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("verdict", "insufficient");
        out.put("confidence", 0.3);
        out.put("supporting_excerpts", List.of());
        out.put("contradicting_excerpts", List.of());
        out.put("suggested_followup_query", claim != null ? claim : "");
        return out;
    }

    private static synchronized List<Map<String, Object>> loadFixtures() {
        if (cache != null) return cache;
        try (InputStream is = StubVerifyClaimTool.class.getResourceAsStream("/fixtures/verdicts.json")) {
            if (is == null) {
                cache = List.of();
                return cache;
            }
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            cache = MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            cache = List.of();
        }
        return cache;
    }

    static String hash8(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private static String asString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
