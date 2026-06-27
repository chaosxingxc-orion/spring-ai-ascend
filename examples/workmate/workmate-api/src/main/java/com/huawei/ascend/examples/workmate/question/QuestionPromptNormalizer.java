package com.huawei.ascend.examples.workmate.question;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Stable keys for AskUserQuestion prompts (the reference workbench: one HITL card per confirmation step). */
public final class QuestionPromptNormalizer {

    private QuestionPromptNormalizer() {}

    public static String normalize(String question) {
        if (question == null) {
            return "";
        }
        return question.trim().replaceAll("\\s+", " ");
    }

    /**
     * Groups leader SOP prompts that differ only in wording (e.g. "在开始…请确认" vs "请确认以下研究参数")
     * into the same confirmation step so resume does not spawn duplicate cards.
     */
    public static String semanticKey(String question, List<String> options) {
        String normalized = normalize(question);
        List<String> opts = options == null ? List.of() : options;

        if (normalized.contains("大纲") && (normalized.contains("确认") || normalized.contains("审阅"))) {
            return "hitl:outline";
        }
        if (normalized.contains("时效窗口")
                || (normalized.contains("近1年") && normalized.contains("近2年"))) {
            return "hitl:time-window";
        }
        // Mode options (完整/快速/单章模式) are themselves the strong signal for the
        // execution-mode confirmation step, regardless of the exact question wording
        // ("请确认执行模式" vs "在开始…请确认以下研究参数"), so resume groups them as one card.
        if (opts.stream().anyMatch(QuestionPromptNormalizer::isModeOption)) {
            return "hitl:research-mode";
        }
        if (opts.stream().anyMatch(QuestionPromptNormalizer::isDetailParamOption)
                || (normalized.contains("研究参数") && opts.size() >= 4)) {
            return "hitl:research-params";
        }
        return "hitl:prompt:" + sha256Hex(normalized + "|" + optionsFingerprint(opts)).substring(0, 16);
    }

    public static String messageId(String question) {
        return messageId(question, List.of());
    }

    public static String messageId(String question, List<String> options) {
        return "question-" + sha256Hex(semanticKey(question, options)).substring(0, 16);
    }

    private static boolean isModeOption(String option) {
        return option.contains("完整模式") || option.contains("快速模式") || option.contains("单章模式");
    }

    private static boolean isDetailParamOption(String option) {
        return option.contains("报告语言")
                || option.contains("引用格式")
                || option.contains("输出格式")
                || option.startsWith("时效窗口：");
    }

    private static String optionsFingerprint(List<String> options) {
        if (options.isEmpty()) {
            return "";
        }
        List<String> sorted = new ArrayList<>();
        for (String option : options) {
            if (option != null && !option.isBlank()) {
                sorted.add(option.trim());
            }
        }
        sorted.sort(String::compareTo);
        return String.join("\n", sorted);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
