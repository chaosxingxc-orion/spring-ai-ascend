package com.huawei.ascend.examples.workmate.audit.chain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public final class AuditHashUtil {

    public static final String GENESIS_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private AuditHashUtil() {
    }

    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public static String payloadHash(String payloadJson) {
        return sha256Hex(payloadJson == null ? "" : payloadJson);
    }

    public static String entryHash(String prevHash, String canonicalPayload) {
        return sha256Hex(prevHash + canonicalPayload);
    }
}
